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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

import android.car.settings.CarSettings;
import android.os.Handler;
import android.os.RemoteException;
import android.provider.Settings;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.IWindowManager;

import androidx.test.filters.SmallTest;

import com.android.systemui.SysuiTestCase;
import com.android.wm.shell.common.DisplayController;
import com.android.wm.shell.common.DisplayImeController;
import com.android.wm.shell.common.DisplayInsetsController;
import com.android.wm.shell.common.TransactionPool;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DisplaySystemBarsControllerTest extends SysuiTestCase {

    private DisplaySystemBarsController mController;

    private static final int DISPLAY_ID = 1;

    @Mock
    private IWindowManager mIWindowManager;
    @Mock
    private DisplayController mDisplayController;
    @Mock
    private DisplayInsetsController mDisplayInsetsController;
    @Mock
    private Handler mHandler;
    @Mock
    private TransactionPool mTransactionPool;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mController = new DisplaySystemBarsController(
                mContext,
                mIWindowManager,
                mDisplayController,
                mDisplayInsetsController,
                mHandler,
                mTransactionPool
        );
    }

    @Test
    public void onDisplayAdded_loadsBarControlPolicyFilters() {
        String text = "sample text";
        Settings.Global.putString(
                mContext.getContentResolver(),
                CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE,
                text
        );

        mController.onDisplayAdded(DISPLAY_ID);

        assertThat(BarControlPolicy.sSettingValue).isEqualTo(text);
    }
}
