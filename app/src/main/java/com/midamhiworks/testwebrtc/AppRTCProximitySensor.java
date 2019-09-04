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

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.midamhiworks.testwebrtc.util.AppRTCUtils;
import org.webrtc.ThreadUtils;

/**
 * AppRTCProximitySensor manages functions related to the proximity sensor in
 * the AppRTC demo.
 * On most device, the proximity sensor is implemented as a boolean-sensor.
 * It returns just two values "NEAR" or "FAR". Thresholding is done on the LUX
 * value i.e. the LUX value of the light sensor is compared with a threshold.
 * A LUX-value more than the threshold means the proximity sensor returns "FAR".
 * Anything less than the threshold value and the sensor  returns "NEAR".
 *
 * AppRTC 근접성입니다.센서는 에 있는 근접 센서와 관련된 기능을 관리합니다.
 * AppRTC 데모입니다.
 * 대부분의 기기에서 근접 센서는 부울 센서로 구현됩니다.
 * "NEAR" 또는 "FAR" 두 값만 반환합니다. 임계값은 LUX 에서 수행됩니다.
 * 값, 즉 광 센서의 LUX 값을 임계값과 비교합니다.
 * LUX 값이 임계값보다 크면 근접 센서가 "FAR"을 반환합니다.
 * 임계값보다 작으면 센서가 "NEAR"을 반환합니다.
 */

/*
AppRTCProximitySensor (선택)

- 근접센서를 통해 기기를 감지하는 데 필요한 클래스

- 기본적인 장치로 영상통화 하는 데 없어도 무방하지만 지우려면 코드를 여러번 수정해야 함. 안지우는걸 추천
 */
public class  AppRTCProximitySensor implements SensorEventListener {
  private static final String TAG = "AppRTCProximitySensor";

  // This class should be created, started and stopped on one thread (e.g. the main thread). We use |nonThreadSafe| to ensure that this is the case.
  // Only active when |DEBUG| is set to true.
  // 이 클래스는 하나의 스레드(예: 메인 스레드)에서 생성, 시작 및 중지해야 합니다. 우리는 이것이 사실인지 확인하기 위해 |nonThreadSafe|를 사용합니다.
  // |DEBUG|이 true 로 설정된 경우에만 활성화됩니다.
  private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();

  private final Runnable onSensorStateListener;
  private final SensorManager sensorManager;
  @Nullable
  private Sensor proximitySensor;
  private boolean lastStateReportIsNear;

  /** Construction */
  static AppRTCProximitySensor create(Context context, Runnable sensorStateListener) {
    return new AppRTCProximitySensor(context, sensorStateListener);
  }

  private AppRTCProximitySensor(Context context, Runnable sensorStateListener) {
    Log.d(TAG, "AppRTCProximitySensor" + AppRTCUtils.getThreadInfo());
    onSensorStateListener = sensorStateListener;
    sensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
  }

  /**
   * Activate the proximity sensor. Also do initialization if called for the first time.
   * 근접 센서를 활성화합니다. 또한 처음 호출된 경우 초기화를 수행합니다.
   */
  public boolean start() {
    threadChecker.checkIsOnValidThread();
    Log.d(TAG, "start" + AppRTCUtils.getThreadInfo());
    if (!initDefaultSensor()) {
      // Proximity sensor is not supported on this device.
      // 이 장치에서는 근접 센서가 지원되지 않습니다.
      return false;
    }
    sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
    return true;
  }

  /** Deactivate the proximity sensor. */
  public void stop() {
    threadChecker.checkIsOnValidThread();
    Log.d(TAG, "stop" + AppRTCUtils.getThreadInfo());
    if (proximitySensor == null) {
      return;
    }
    sensorManager.unregisterListener(this, proximitySensor);
  }

  /** Getter for last reported state. Set to true if "near" is reported. */
  public boolean sensorReportsNearState() {
    threadChecker.checkIsOnValidThread();
    return lastStateReportIsNear;
  }

  @Override
  public final void onAccuracyChanged(Sensor sensor, int accuracy) {
    threadChecker.checkIsOnValidThread();
    AppRTCUtils.assertIsTrue(sensor.getType() == Sensor.TYPE_PROXIMITY);
    if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
      Log.e(TAG, "The values returned by this sensor cannot be trusted");
    }
  }

  @Override
  public final void onSensorChanged(SensorEvent event) {
    threadChecker.checkIsOnValidThread();
    AppRTCUtils.assertIsTrue(event.sensor.getType() == Sensor.TYPE_PROXIMITY);
    // As a best practice; do as little as possible within this method and avoid blocking.
    // 모범 사례로, 이 방법 내에서 가능한 한 적게 수행하고 차단을 피합니다.
    float distanceInCentimeters = event.values[0];
    if (distanceInCentimeters < proximitySensor.getMaximumRange()) {
      Log.d(TAG, "Proximity sensor => NEAR state");
      lastStateReportIsNear = true;
    } else {
      Log.d(TAG, "Proximity sensor => FAR state");
      lastStateReportIsNear = false;
    }

    // Report about new state to listening client. Client can then call sensorReportsNearState() to query the current state (NEAR or FAR).
    // 청취 클라이언트에 새 상태에 대해 보고합니다. 그런 다음 클라이언트는 sensorReportsNearState()를 호출하여 현재 상태(NEAR 또는 FAR)를 쿼리할 수 있습니다.
    if (onSensorStateListener != null) {
      onSensorStateListener.run();
    }

    Log.d(TAG, "onSensorChanged" + AppRTCUtils.getThreadInfo() + ": "
            + "accuracy=" + event.accuracy + ", timestamp=" + event.timestamp + ", distance="
            + event.values[0]);
  }

  /**
   * Get default proximity sensor if it exists. Tablet devices (e.g. Nexus 7) does not support this type of sensor and false will be returned in such cases.
   * 기본 근접 센서가 있으면 가져옵니다. Tablet 장치(예: Nexus 7)는 이러한 유형의 센서를 지원하지 않으며 이 경우 False 가 반환됩니다.
   */
  private boolean initDefaultSensor() {
    if (proximitySensor != null) {
      return true;
    }
    proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
    if (proximitySensor == null) {
      return false;
    }
    logProximitySensorInfo();
    return true;
  }

  /** Helper method for logging information about the proximity sensor. */
  private void logProximitySensorInfo() {
    if (proximitySensor == null) {
      return;
    }
    StringBuilder info = new StringBuilder("Proximity sensor: ");
    info.append("name=").append(proximitySensor.getName());
    info.append(", vendor: ").append(proximitySensor.getVendor());
    info.append(", power: ").append(proximitySensor.getPower());
    info.append(", resolution: ").append(proximitySensor.getResolution());
    info.append(", max range: ").append(proximitySensor.getMaximumRange());
    info.append(", min delay: ").append(proximitySensor.getMinDelay());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      // Added in API level 20.
      // API 레벨 20에 추가되었습니다.
      info.append(", type: ").append(proximitySensor.getStringType());
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      // Added in API level 21.
      // API 레벨 21에 추가되었습니다.
      info.append(", max delay: ").append(proximitySensor.getMaxDelay());
      info.append(", reporting mode: ").append(proximitySensor.getReportingMode());
      info.append(", isWakeUpSensor: ").append(proximitySensor.isWakeUpSensor());
    }
    Log.d(TAG, info.toString());
  }
}
