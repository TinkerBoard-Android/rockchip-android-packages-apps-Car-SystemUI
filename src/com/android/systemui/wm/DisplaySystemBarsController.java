/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.wm;

import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Slog;
import android.util.SparseArray;
import android.view.IDisplayWindowInsetsController;
import android.view.IWindowManager;
import android.view.InsetsController;
import android.view.InsetsState;
import android.view.InsetsVisibilities;
import android.view.WindowInsets.Type;

import androidx.annotation.VisibleForTesting;

import com.android.systemui.dagger.qualifiers.Main;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.TransactionPool;

import java.util.Objects;

/**
 * Controller that maps between displays and {@link IDisplayWindowInsetsController} in order to
 * give system bar control to SystemUI.
 * {@link R.bool#config_remoteInsetsControllerControlsSystemBars} determines whether this controller
 * takes control or not.
 */
public class DisplaySystemBarsController extends DisplayImeController {

    private static final String TAG = "DisplaySystemBarsController";

    private final Context mContext;
    private final DisplayController mDisplayController;
    private final Handler mHandler;
    private SparseArray<PerDisplay> mPerDisplaySparseArray;

    public DisplaySystemBarsController(
            Context context,
            IWindowManager wmService,
            DisplayController displayController,
            DisplayInsetsController displayInsetsController,
            @Main Handler mainHandler,
            TransactionPool transactionPool) {
        super(wmService, displayController, displayInsetsController, (r) -> mainHandler.post(r),
                transactionPool);
        mContext = context;
        mDisplayController = displayController;
        mHandler = mainHandler;
    }

    @Override
    public void onDisplayAdded(int displayId) {
        PerDisplay pd = new PerDisplay(displayId);
        pd.register();
        // Lazy loading policy control filters instead of during boot.
        if (mPerDisplaySparseArray == null) {
            mPerDisplaySparseArray = new SparseArray<>();
            BarControlPolicy.reloadFromSetting(mContext);
            BarControlPolicy.registerContentObserver(mContext, mHandler, () -> {
                int size = mPerDisplaySparseArray.size();
                for (int i = 0; i < size; i++) {
                    mPerDisplaySparseArray.valueAt(i).updateDisplayWindowRequestedVisibilities();
                }
            });
        }
        mPerDisplaySparseArray.put(displayId, pd);
    }

    @Override
    public void onDisplayRemoved(int displayId) {
        try {
            mWmService.setDisplayWindowInsetsController(displayId, null);
        } catch (RemoteException e) {
            Slog.w(TAG, "Unable to remove insets controller on display " + displayId);
        }
        mPerDisplaySparseArray.remove(displayId);
    }

    @VisibleForTesting
    class PerDisplay extends DisplayImeController.PerDisplay {

        int mDisplayId;
        InsetsController mInsetsController;
        InsetsVisibilities mRequestedVisibilities = new InsetsVisibilities();
        String mPackageName;

        PerDisplay(int displayId) {
            super(displayId, mDisplayController.getDisplayLayout(displayId).rotation());
            mDisplayId = displayId;
            mInsetsController = new InsetsController(
                    new DisplaySystemBarsInsetsControllerHost(mHandler, visibilities -> {
                        mRequestedVisibilities.set(visibilities);
                        updateDisplayWindowRequestedVisibilities();
                    }));
        }

        @Override
        public void insetsChanged(InsetsState insetsState) {
            super.insetsChanged(insetsState);
            mInsetsController.onStateChanged(insetsState);
            updateDisplayWindowRequestedVisibilities();
        }

        @Override
        public void hideInsets(@Type.InsetsType int types, boolean fromIme) {
            if ((types & Type.ime()) == 0) {
                mInsetsController.hide(types);
            } else {
                super.hideInsets(types, fromIme);
            }

        }

        @Override
        public void showInsets(@Type.InsetsType int types, boolean fromIme) {
            if ((types & Type.ime()) == 0) {
                mInsetsController.show(types);
            } else {
                super.showInsets(types, fromIme);
            }

        }

        @Override
        public void topFocusedWindowChanged(String packageName) {
            if (Objects.equals(mPackageName, packageName)) {
                return;
            }
            mPackageName = packageName;
            updateDisplayWindowRequestedVisibilities();
        }

        private void updateDisplayWindowRequestedVisibilities() {
            if (mPackageName == null) {
                return;
            }
            int[] barVisibilities = BarControlPolicy.getBarVisibilities(mPackageName);
            updateRequestedVisibilities(barVisibilities[0], /* visible= */ true);
            updateRequestedVisibilities(barVisibilities[1], /* visible= */ false);
            showInsets(barVisibilities[0], /* fromIme= */ false);
            hideInsets(barVisibilities[1], /* fromIme= */ false);
            try {
                mWmService.updateDisplayWindowRequestedVisibilities(mDisplayId,
                        mRequestedVisibilities);
            } catch (RemoteException e) {
                Slog.w(TAG, "Unable to update window manager service.");
            }
        }

        private void updateRequestedVisibilities(@Type.InsetsType int types, boolean visible) {
            ArraySet<Integer> internalTypes = InsetsState.toInternalType(types);
            for (int i = internalTypes.size() - 1; i >= 0; i--) {
                mRequestedVisibilities.setVisibility(internalTypes.valueAt(i), visible);
            }
        }
    }
}
