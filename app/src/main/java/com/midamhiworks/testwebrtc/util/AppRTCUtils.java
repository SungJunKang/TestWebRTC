/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc.util;

import android.os.Build;
import android.util.Log;

/**
 * AppRTCUtils provides helper functions for managing thread safety.
 *
 * AppRTCUtils 는 스레드 안전성 관리를 위한 도우미 기능을 제공합니다.
 */

/*
util 패키지에 있는 AppRTCUtils (필수)

- 클래스 내의 설명은 스레드의 안전 관리를 위해 헬퍼를 제공한다고 되어있다.

- AudioManager, BluetoothManager, ProximitySensor 에서, 로그를 찍거나 시스템 정보를 출력하기 위한 클래스
 */
public final class AppRTCUtils {
  private AppRTCUtils() {}

  /** Helper method which throws an exception  when an assertion has failed. */
  public static void assertIsTrue(boolean condition) {
    if (!condition) {
      throw new AssertionError("Expected condition to be true");
    }
  }

  /** Helper method for building a string of thread information.*/
  public static String getThreadInfo() {
    return "@[name=" + Thread.currentThread().getName() + ", id=" + Thread.currentThread().getId()
        + "]";
  }

  /** Information about the current build, taken from system properties. */
  public static void logDeviceInfo(String tag) {
    Log.d(tag, "Android SDK: " + Build.VERSION.SDK_INT + ", "
            + "Release: " + Build.VERSION.RELEASE + ", "
            + "Brand: " + Build.BRAND + ", "
            + "Device: " + Build.DEVICE + ", "
            + "Id: " + Build.ID + ", "
            + "Hardware: " + Build.HARDWARE + ", "
            + "Manufacturer: " + Build.MANUFACTURER + ", "
            + "Model: " + Build.MODEL + ", "
            + "Product: " + Build.PRODUCT);
  }
}
