/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Settings fragment for AppRTC.
 *
 * AppRTC 에 대한 설정 Fragment 입니다.
 */

/*
SettingsFragment (선택)

- SettingsActivity 에서, 설정이 저장된 preference.xml 파일을 로드시키는 클래스. SettingsActivity 클래스와 마찬가지로 지울거면 같이 지움.
 */
public class SettingsFragment extends PreferenceFragment {
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // Load the preferences from an XML resource
    addPreferencesFromResource(R.xml.preferences);
  }
}
