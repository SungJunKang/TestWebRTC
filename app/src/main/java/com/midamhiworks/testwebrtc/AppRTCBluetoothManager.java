/*
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Set;
import com.midamhiworks.testwebrtc.util.AppRTCUtils;
import org.webrtc.ThreadUtils;

/**
 * AppRTCProximitySensor manages functions related to Bluetoth devices in the AppRTC demo.
 *
 * AppRTC 근접성입니다. 센서는 AppRTC 데모에서 Bluetooth 장치와 관련된 기능을 관리합니다.
 */

/*
AppRTCBluetoothManager (선택)

- 이어폰 등 음성과 관련된 블루투스 장치를 관리하는 데 필요한 클래스

- 기본적인 장치로 영상통화 하는 데 없어도 무방하지만 지우려면 코드를 여러번 수정해야 함. 안지우는걸 추천
 */
public class AppRTCBluetoothManager {
  private static final String TAG = "AppRTCBluetoothManager";

  // Timeout interval for starting or stopping audio to a Bluetooth SCO device.
  // Bluetooth SCO 장치의 오디오 시작 또는 중지 시간 간격입니다.
  private static final int BLUETOOTH_SCO_TIMEOUT_MS = 4000;
  // Maximum number of SCO connection attempts.
  // 최대 SCO 연결 시도 횟수입니다.
  private static final int MAX_SCO_CONNECTION_ATTEMPTS = 2;

  // Bluetooth connection state.
  // Bluetooth 연결 상태입니다.
  public enum State {
    // Bluetooth is not available; no adapter or Bluetooth is off.
    // Bluetooth 를 사용할 수 없습니다. 어댑터 또는 Bluetooth 가 꺼져 있지 않습니다.
    UNINITIALIZED,
    // Bluetooth error happened when trying to start Bluetooth.
    // Bluetooth 를 시작할 때 Bluetooth 오류가 발생했습니다.
    ERROR,
    // Bluetooth proxy object for the Headset profile exists, but no connected headset devices, SCO is not started or disconnected.
    // 헤드셋 프로파일에 대한 Bluetooth 프록시 개체가 있지만 연결된 헤드셋 장치가 없습니다. SCO 가 시작되거나 연결이 끊어지지 않았습니다.
    HEADSET_UNAVAILABLE,
    // Bluetooth proxy object for the Headset profile connected, connected Bluetooth headset present, but SCO is not started or disconnected.
    // 연결된 Bluetooth 헤드셋 프로필에 대한 Bluetooth 프록시 개체가 있지만 SCO 가 시작되거나 연결이 끊어지지 않았습니다.
    HEADSET_AVAILABLE,
    // Bluetooth audio SCO connection with remote device is closing.
    // 원격 장치와의 Bluetooth 오디오 SCO 연결이 닫힙니다.
    SCO_DISCONNECTING,
    // Bluetooth audio SCO connection with remote device is initiated.
    // 원격 장치와의 Bluetooth 오디오 SCO 연결이 시작됩니다.
    SCO_CONNECTING,
    // Bluetooth audio SCO connection with remote device is established.
    // Bluetooth 오디오 SCO 와 원격 장치를 연결합니다.
    SCO_CONNECTED
  }

  private final Context apprtcContext;
  private final AppRTCAudioManager apprtcAudioManager;
  @Nullable
  private final AudioManager audioManager;
  private final Handler handler;

  int scoConnectionAttempts;
  private State bluetoothState;
  private final BluetoothProfile.ServiceListener bluetoothServiceListener;
  @Nullable
  private BluetoothAdapter bluetoothAdapter;
  @Nullable
  private BluetoothHeadset bluetoothHeadset;
  @Nullable
  private BluetoothDevice bluetoothDevice;
  private final BroadcastReceiver bluetoothHeadsetReceiver;

  // Runs when the Bluetooth timeout expires. We use that timeout after calling startScoAudio() or stopScoAudio() because we're not guaranteed to get a
  // callback after those calls.
  // Bluetooth 시간 초과가 만료될 때 실행됩니다. startScoAudio() 또는 stopScoAudio()를 호출한 후 시간 초과를 사용합니다. 왜냐하면 우리는 다음 시간 초과를 받을 수 없기 때문입니다.
  // call 후 콜백합니다.
  private final Runnable bluetoothTimeoutRunnable = () -> bluetoothTimeout();

  /**
   * Implementation of an interface that notifies BluetoothProfile IPC clients when they have been connected to or disconnected from the service.
   * BluetoothProfile IPC 클라이언트가 서비스에 연결되었거나 연결이 끊겼을 때 이를 알리는 인터페이스를 구현합니다.
   */
  private class BluetoothServiceListener implements BluetoothProfile.ServiceListener {
    @Override
    // Called to notify the client when the proxy object has been connected to the service.
    // Once we have the profile proxy object, we can use it to monitor the state of the connection and perform other operations that are relevant to the headset profile.
    // 프록시 개체가 서비스에 연결되었을 때 클라이언트에 알리기 위해 호출됩니다.
    // 프로파일 프록시 개체가 있으면 이 개체를 사용하여 연결 상태를 모니터링하고 헤드셋 프로파일과 관련된 다른 작업을 수행할 수 있습니다.
    public void onServiceConnected(int profile, BluetoothProfile proxy) {
      if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
        return;
      }
      Log.d(TAG, "BluetoothServiceListener.onServiceConnected: BT state=" + bluetoothState);
      // Android only supports one connected Bluetooth Headset at a time.
      bluetoothHeadset = (BluetoothHeadset) proxy;
      updateAudioDeviceState();
      Log.d(TAG, "onServiceConnected done: BT state=" + bluetoothState);
    }

    @Override
    /** Notifies the client when the proxy object has been disconnected from the service. */
    public void onServiceDisconnected(int profile) {
      if (profile != BluetoothProfile.HEADSET || bluetoothState == State.UNINITIALIZED) {
        return;
      }
      Log.d(TAG, "BluetoothServiceListener.onServiceDisconnected: BT state=" + bluetoothState);
      stopScoAudio();
      bluetoothHeadset = null;
      bluetoothDevice = null;
      bluetoothState = State.HEADSET_UNAVAILABLE;
      updateAudioDeviceState();
      Log.d(TAG, "onServiceDisconnected done: BT state=" + bluetoothState);
    }
  }

  // Intent broadcast receiver which handles changes in Bluetooth device availability.
  // Detects headset changes and Bluetooth SCO state changes.
  // Bluetooth 장치 가용성의 변경 사항을 처리하는 Intent 브로드캐스트 수신기입니다.
  // 헤드셋 변경 및 Bluetooth SCO 상태 변경을 탐지합니다.
  private class BluetoothHeadsetBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (bluetoothState == State.UNINITIALIZED) {
        return;
      }
      final String action = intent.getAction();
      // Change in connection state of the Headset profile. Note that the change does not tell us anything about whether we're streaming
      // audio to BT over SCO. Typically received when user turns on a BT headset while audio is active using another audio device.
      // 헤드셋 프로파일의 연결 상태를 변경합니다. 변경 사항으로는 스트리밍 여부에 대해 아무것도 알 수 없습니다.
      // SCO 를 통해 BT로 오디오를 전달합니다. 일반적으로 다른 오디오 장치를 사용하여 오디오가 활성 상태인 동안 사용자가 BT 헤드셋을 켤 때 수신됩니다.
      if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
        final int state =
            intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);
        Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                + "a=ACTION_CONNECTION_STATE_CHANGED, "
                + "s=" + stateToString(state) + ", "
                + "sb=" + isInitialStickyBroadcast() + ", "
                + "BT state: " + bluetoothState);
        if (state == BluetoothHeadset.STATE_CONNECTED) {
          scoConnectionAttempts = 0;
          updateAudioDeviceState();
        } else if (state == BluetoothHeadset.STATE_CONNECTING) {
          // No action needed.
          // 작업이 필요하지 않습니다.
        } else if (state == BluetoothHeadset.STATE_DISCONNECTING) {
          // No action needed.
          // 작업이 필요하지 않습니다.
        } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
          // Bluetooth is probably powered off during the call.
          // 통화 중에는 블루투스의 전원이 꺼져 있을 수 있습니다.
          stopScoAudio();
          updateAudioDeviceState();
        }
        // Change in the audio (SCO) connection state of the Headset profile.
        // Typically received after call to startScoAudio() has finalized.
        // 헤드셋 프로파일의 오디오(SCO) 연결 상태를 변경합니다.
        // 일반적으로 StartScoAudio()를 호출한 후 수신됩니다.
      } else if (action.equals(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED)) {
        final int state = intent.getIntExtra(
            BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
        Log.d(TAG, "BluetoothHeadsetBroadcastReceiver.onReceive: "
                + "a=ACTION_AUDIO_STATE_CHANGED, "
                + "s=" + stateToString(state) + ", "
                + "sb=" + isInitialStickyBroadcast() + ", "
                + "BT state: " + bluetoothState);
        if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED) {
          cancelTimer();
          if (bluetoothState == State.SCO_CONNECTING) {
            Log.d(TAG, "+++ Bluetooth audio SCO is now connected");
            bluetoothState = State.SCO_CONNECTED;
            scoConnectionAttempts = 0;
            updateAudioDeviceState();
          } else {
            Log.w(TAG, "Unexpected state BluetoothHeadset.STATE_AUDIO_CONNECTED");
          }
        } else if (state == BluetoothHeadset.STATE_AUDIO_CONNECTING) {
          Log.d(TAG, "+++ Bluetooth audio SCO is now connecting...");
        } else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED) {
          Log.d(TAG, "+++ Bluetooth audio SCO is now disconnected");
          if (isInitialStickyBroadcast()) {
            Log.d(TAG, "Ignore STATE_AUDIO_DISCONNECTED initial sticky broadcast.");
            return;
          }
          updateAudioDeviceState();
        }
      }
      Log.d(TAG, "onReceive done: BT state=" + bluetoothState);
    }
  }

  /** Construction. */
  static AppRTCBluetoothManager create(Context context, AppRTCAudioManager audioManager) {
    Log.d(TAG, "create" + AppRTCUtils.getThreadInfo());
    return new AppRTCBluetoothManager(context, audioManager);
  }

  protected AppRTCBluetoothManager(Context context, AppRTCAudioManager audioManager) {
    Log.d(TAG, "ctor");
    ThreadUtils.checkIsOnMainThread();
    apprtcContext = context;
    apprtcAudioManager = audioManager;
    this.audioManager = getAudioManager(context);
    bluetoothState = State.UNINITIALIZED;
    bluetoothServiceListener = new BluetoothServiceListener();
    bluetoothHeadsetReceiver = new BluetoothHeadsetBroadcastReceiver();
    handler = new Handler(Looper.getMainLooper());
  }

  /** Returns the internal state. */
  public State getState() {
    ThreadUtils.checkIsOnMainThread();
    return bluetoothState;
  }

  /**
   * Activates components required to detect Bluetooth devices and to enable BT SCO (audio is routed via BT SCO) for the headset profile.
   * The end state will be HEADSET_UNAVAILABLE but a state machine has started which will start a state change sequence where the final outcome depends on
   * if/when the BT headset is enabled.
   * Example of state change sequence when start() is called while BT device is connected and enabled:
   *   UNINITIALIZED --> HEADSET_UNAVAILABLE --> HEADSET_AVAILABLE -->
   *   SCO_CONNECTING --> SCO_CONNECTED <==> audio is now routed via BT SCO.
   * Note that the AppRTCAudioManager is also involved in driving this state change.
   *
   * 헤드셋 프로필에 대해 Bluetooth 장치를 감지하고 BT SCO(오디오가 BT SCO를 통해 라우팅됨)를 활성화하는 데 필요한 구성 요소를 활성화합니다.
   * 종료 상태는 HEADSET_UNAVAILABLE이 되지만 최종 결과가 좌우되는 상태 변경 시퀀스가 시작됩니다.
   * BT 헤드셋이 활성화된 경우입니다.
   * BT 장치가 연결되어 있고 활성화된 상태에서 시작()이 호출될 때 상태 변경 시퀀스의 예입니다.
   * Uninitalized --> HEADSET_UNAVAILABLE --> HEADSET_AVAILABLE -->
   * SCO_CONNECTING --> SCO_CONECTED <==> 오디오는 이제 BT SCO를 통해 라우팅됩니다.
   * AppRTCAudioManager는 이러한 상태 변화를 이끄는 데에도 관여합니다.
   */
  public void start() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "start");
    if (!hasPermission(apprtcContext, android.Manifest.permission.BLUETOOTH)) {
      Log.w(TAG, "Process (pid=" + Process.myPid() + ") lacks BLUETOOTH permission");
      return;
    }
    if (bluetoothState != State.UNINITIALIZED) {
      Log.w(TAG, "Invalid BT state");
      return;
    }
    bluetoothHeadset = null;
    bluetoothDevice = null;
    scoConnectionAttempts = 0;
    // Get a handle to the default local Bluetooth adapter.
    bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    if (bluetoothAdapter == null) {
      Log.w(TAG, "Device does not support Bluetooth");
      return;
    }
    // Ensure that the device supports use of BT SCO audio for off call use cases.
    // 장치가 오프 콜 사용 사례에 대해 BT SCO 오디오 사용을 지원하는지 확인합니다.
    if (!audioManager.isBluetoothScoAvailableOffCall()) {
      Log.e(TAG, "Bluetooth SCO audio is not available off call");
      return;
    }
    logBluetoothAdapterInfo(bluetoothAdapter);
    // Establish a connection to the HEADSET profile (includes both Bluetooth Headset and Hands-Free) proxy object and install a listener.
    // HEADSET 프로파일(Bluetooth Headset 및 Hands-Free) 프록시 개체에 대한 연결을 설정하고 수신기를 설치합니다.
    if (!getBluetoothProfileProxy(
            apprtcContext, bluetoothServiceListener, BluetoothProfile.HEADSET)) {
      Log.e(TAG, "BluetoothAdapter.getProfileProxy(HEADSET) failed");
      return;
    }
    // Register receivers for BluetoothHeadset change notifications.
    // BluetoothHeadset 변경 통지를 위해 수신기를 등록합니다.
    IntentFilter bluetoothHeadsetFilter = new IntentFilter();
    // Register receiver for change in connection state of the Headset profile.
    // 헤드셋 프로필의 연결 상태 변화를 위해 수신기를 등록합니다.
    bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
    // Register receiver for change in audio connection state of the Headset profile.
    // 헤드셋 프로필의 오디오 연결 상태 변화를 위해 수신기를 등록합니다.
    bluetoothHeadsetFilter.addAction(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED);
    registerReceiver(bluetoothHeadsetReceiver, bluetoothHeadsetFilter);
    Log.d(TAG, "HEADSET profile state: "
            + stateToString(bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)));
    Log.d(TAG, "Bluetooth proxy for headset profile has started");
    bluetoothState = State.HEADSET_UNAVAILABLE;
    Log.d(TAG, "start done: BT state=" + bluetoothState);
  }

  /** Stops and closes all components related to Bluetooth audio. */
  public void stop() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "stop: BT state=" + bluetoothState);
    if (bluetoothAdapter == null) {
      return;
    }
    // Stop BT SCO connection with remote device if needed.
    // 필요한 경우 원격 장치와의 BT SCO 연결을 중지합니다.
    stopScoAudio();
    // Close down remaining BT resources.
    // 나머지 BT 리소스를 닫습니다.
    if (bluetoothState == State.UNINITIALIZED) {
      return;
    }
    unregisterReceiver(bluetoothHeadsetReceiver);
    cancelTimer();
    if (bluetoothHeadset != null) {
      bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
      bluetoothHeadset = null;
    }
    bluetoothAdapter = null;
    bluetoothDevice = null;
    bluetoothState = State.UNINITIALIZED;
    Log.d(TAG, "stop done: BT state=" + bluetoothState);
  }

  /**
   * Starts Bluetooth SCO connection with remote device.
   * Note that the phone application always has the priority on the usage of the SCO connection for telephony.
   * If this method is called while the phone is in call it will be ignored.
   * Similarly, if a call is received or sent while an application is using the SCO connection,
   * the connection will be lost for the application and NOT returned automatically when the call
   * ends.
   * Also note that: up to and including API version JELLY_BEAN_MR1, this method initiates a
   * virtual voice call to the Bluetooth headset. After API version JELLY_BEAN_MR2 only a raw SCO audio connection is established.
   * TODO(henrika): should we add support for virtual voice call to BT headset also for JBMR2 and higher.
   * It might be required to initiates a virtual voice call since many devices do not accept SCO audio without a "call".
   *
   * 원격 장치와 Bluetooth SCO 연결을 시작합니다.
   * 전화 애플리케이션은 항상 전화에 대한 SCO 연결 사용에 우선합니다.
   * 전화가 걸려오는 동안 이 방법이 호출되면 무시됩니다.
   * 마찬가지로, 응용 프로그램이 SCO 연결을 사용하는 동안 통화가 수신되거나 전송되면,
   * 응용 프로그램에 대한 연결이 끊기고 호출 시 자동으로 반환되지 않습니다.
   * 끝납니다.
   * 또한 API 버전 JELLY_BEAN_MR1까지 포함하면 이 방법은 다음과 같이 시작됩니다.
   * Bluetooth 헤드셋으로 가상 음성 통화를 합니다. API 버전 JELLY_BEAN_MR2 이후에는 원시 SCO 오디오 연결만 설정됩니다.
   * TODO(헨리카): BT 헤드셋에 JBMR2 및 더 높아요
   * 많은 디바이스가 "통화" 없이 SCO 오디오를 수신하지 않으므로 가상 음성 통화를 시작해야 할 수 있습니다.
   */
  public boolean startScoAudio() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "startSco: BT state=" + bluetoothState + ", "
            + "attempts: " + scoConnectionAttempts + ", "
            + "SCO is on: " + isScoOn());
    if (scoConnectionAttempts >= MAX_SCO_CONNECTION_ATTEMPTS) {
      Log.e(TAG, "BT SCO connection fails - no more attempts");
      return false;
    }
    if (bluetoothState != State.HEADSET_AVAILABLE) {
      Log.e(TAG, "BT SCO connection fails - no headset available");
      return false;
    }
    // Start BT SCO channel and wait for ACTION_AUDIO_STATE_CHANGED.
    // BT SCO 채널을 시작하고 ACTION_AUDIO_STATE_CHANGED를 기다립니다.
    Log.d(TAG, "Starting Bluetooth SCO and waits for ACTION_AUDIO_STATE_CHANGED...");
    // The SCO connection establishment can take several seconds, hence we cannot rely on the
    // connection to be available when the method returns but instead register to receive the
    // intent ACTION_SCO_AUDIO_STATE_UPDATED and wait for the state to be SCO_AUDIO_STATE_CONNECTED.
    // SCO 연결 설정에는 몇 초가 소요될 수 있으므로 이 작업에 의존할 수 없습니다.
    // 메서드가 반환될 때 연결을 사용할 수 있지만 대신 등록하여 수신합니다.
    //Action_SCO_AUDIO_STATE_UPDATED를 실행하고 상태가 SCO_AUDIO_STATE_CONNECTED 상태가 될 때까지 기다립니다.
    bluetoothState = State.SCO_CONNECTING;
    audioManager.startBluetoothSco();
    audioManager.setBluetoothScoOn(true);
    scoConnectionAttempts++;
    startTimer();
    Log.d(TAG, "startScoAudio done: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
    return true;
  }

  /** Stops Bluetooth SCO connection with remote device. */
  public void stopScoAudio() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "stopScoAudio: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
    if (bluetoothState != State.SCO_CONNECTING && bluetoothState != State.SCO_CONNECTED) {
      return;
    }
    cancelTimer();
    audioManager.stopBluetoothSco();
    audioManager.setBluetoothScoOn(false);
    bluetoothState = State.SCO_DISCONNECTING;
    Log.d(TAG, "stopScoAudio done: BT state=" + bluetoothState + ", "
            + "SCO is on: " + isScoOn());
  }

  /**
   * Use the BluetoothHeadset proxy object (controls the Bluetooth Headset Service via IPC) to update the list of connected devices for the HEADSET profile.
   * The internal state will change to HEADSET_UNAVAILABLE or to HEADSET_AVAILABLE and |bluetoothDevice| will be mapped to the connected device if available.
   * BluetoothHeadset 프록시 개체(IPC를 통해 Bluetooth Headset Service 제어)를 사용하여 HEADSET 프로파일에 연결된 장치 목록을 업데이트합니다.
   * 내부 상태는 HEADSET_UNAVAILABLE 또는 HEADSET_AVAILABLE 로 변경되며, |블루투스Device|가 가능한 경우 연결된 장치에 매핑됩니다.
   */
  public void updateDevice() {
    if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
      return;
    }
    Log.d(TAG, "updateDevice");
    // Get connected devices for the headset profile.
    // Returns the set of devices which are in state STATE_CONNECTED. The BluetoothDevice class is just a thin wrapper for a Bluetooth hardware address.
    // 헤드셋 프로필에 대한 연결된 장치를 가져옵니다.
    // STATE_CONNECTED 상태인 디바이스 집합을 반환합니다. BluetoothDevice 클래스는 Bluetooth 하드웨어 주소에 대한 얇은 포장기에 불과합니다.
    List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
    if (devices.isEmpty()) {
      bluetoothDevice = null;
      bluetoothState = State.HEADSET_UNAVAILABLE;
      Log.d(TAG, "No connected bluetooth headset");
    } else {
      // Always use first device in list. Android only supports one device.
      // 목록의 첫 번째 장치를 항상 사용합니다. Android는 하나의 장치만 지원합니다.
      bluetoothDevice = devices.get(0);
      bluetoothState = State.HEADSET_AVAILABLE;
      Log.d(TAG, "Connected bluetooth headset: "
              + "name=" + bluetoothDevice.getName() + ", "
              + "state=" + stateToString(bluetoothHeadset.getConnectionState(bluetoothDevice))
              + ", SCO audio=" + bluetoothHeadset.isAudioConnected(bluetoothDevice));
    }
    Log.d(TAG, "updateDevice done: BT state=" + bluetoothState);
  }

  /**
   * Stubs for test mocks.
   */
  @Nullable
  protected AudioManager getAudioManager(Context context) {
    return (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
  }

  protected void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    apprtcContext.registerReceiver(receiver, filter);
  }

  protected void unregisterReceiver(BroadcastReceiver receiver) {
    apprtcContext.unregisterReceiver(receiver);
  }

  protected boolean getBluetoothProfileProxy(
      Context context, BluetoothProfile.ServiceListener listener, int profile) {
    return bluetoothAdapter.getProfileProxy(context, listener, profile);
  }

  protected boolean hasPermission(Context context, String permission) {
    return apprtcContext.checkPermission(permission, Process.myPid(), Process.myUid())
        == PackageManager.PERMISSION_GRANTED;
  }

  /** Logs the state of the local Bluetooth adapter. */
  @SuppressLint("HardwareIds")
  protected void logBluetoothAdapterInfo(BluetoothAdapter localAdapter) {
    Log.d(TAG, "BluetoothAdapter: "
            + "enabled=" + localAdapter.isEnabled() + ", "
            + "state=" + stateToString(localAdapter.getState()) + ", "
            + "name=" + localAdapter.getName() + ", "
            + "address=" + localAdapter.getAddress());
    // Log the set of BluetoothDevice objects that are bonded (paired) to the local adapter.
    // 로컬 어댑터에 접합(페어링)된 BluetoothDevice 개체 집합을 기록합니다.
    Set<BluetoothDevice> pairedDevices = localAdapter.getBondedDevices();
    if (!pairedDevices.isEmpty()) {
      Log.d(TAG, "paired devices:");
      for (BluetoothDevice device : pairedDevices) {
        Log.d(TAG, " name=" + device.getName() + ", address=" + device.getAddress());
      }
    }
  }

  /** Ensures that the audio manager updates its list of available audio devices. */
  private void updateAudioDeviceState() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "updateAudioDeviceState");
    apprtcAudioManager.updateAudioDeviceState();
  }

  /** Starts timer which times out after BLUETOOTH_SCO_TIMEOUT_MS milliseconds. */
  private void startTimer() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "startTimer");
    handler.postDelayed(bluetoothTimeoutRunnable, BLUETOOTH_SCO_TIMEOUT_MS);
  }

  /** Cancels any outstanding timer tasks. */
  private void cancelTimer() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "cancelTimer");
    handler.removeCallbacks(bluetoothTimeoutRunnable);
  }

  /**
   * Called when start of the BT SCO channel takes too long time. Usually
   * happens when the BT device has been turned on during an ongoing call.
   */
  private void bluetoothTimeout() {
    ThreadUtils.checkIsOnMainThread();
    if (bluetoothState == State.UNINITIALIZED || bluetoothHeadset == null) {
      return;
    }
    Log.d(TAG, "bluetoothTimeout: BT state=" + bluetoothState + ", "
            + "attempts: " + scoConnectionAttempts + ", "
            + "SCO is on: " + isScoOn());
    if (bluetoothState != State.SCO_CONNECTING) {
      return;
    }
    // Bluetooth SCO should be connecting; check the latest result.
    // Bluetooth SCO가 연결되어 있어야 합니다. 최신 결과를 확인하십시오.
    boolean scoConnected = false;
    List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
    if (devices.size() > 0) {
      bluetoothDevice = devices.get(0);
      if (bluetoothHeadset.isAudioConnected(bluetoothDevice)) {
        Log.d(TAG, "SCO connected with " + bluetoothDevice.getName());
        scoConnected = true;
      } else {
        Log.d(TAG, "SCO is not connected with " + bluetoothDevice.getName());
      }
    }
    if (scoConnected) {
      // We thought BT had timed out, but it's actually on; updating state.
      // BT가 시간 초과된 줄 알았는데, 실제로 켜져 있습니다. 상태를 업데이트합니다.
      bluetoothState = State.SCO_CONNECTED;
      scoConnectionAttempts = 0;
    } else {
      // Give up and "cancel" our request by calling stopBluetoothSco().
      // 포기하고 StopBluetoothSco()를 호출하여 요청을 "취소"합니다.
      Log.w(TAG, "BT failed to connect after timeout");
      stopScoAudio();
    }
    updateAudioDeviceState();
    Log.d(TAG, "bluetoothTimeout done: BT state=" + bluetoothState);
  }

  /** Checks whether audio uses Bluetooth SCO. */
  private boolean isScoOn() {
    return audioManager.isBluetoothScoOn();
  }

  /** Converts BluetoothAdapter states into local string representations. */
  private String stateToString(int state) {
    switch (state) {
      case BluetoothAdapter.STATE_DISCONNECTED:
        return "DISCONNECTED";
      case BluetoothAdapter.STATE_CONNECTED:
        return "CONNECTED";
      case BluetoothAdapter.STATE_CONNECTING:
        return "CONNECTING";
      case BluetoothAdapter.STATE_DISCONNECTING:
        return "DISCONNECTING";
      case BluetoothAdapter.STATE_OFF:
        return "OFF";
      case BluetoothAdapter.STATE_ON:
        return "ON";
      case BluetoothAdapter.STATE_TURNING_OFF:
        // Indicates the local Bluetooth adapter is turning off. Local clients should immediately attempt graceful disconnection of any remote links.
        // 로컬 Bluetooth 어댑터가 꺼져 있음을 나타냅니다. 로컬 클라이언트는 즉시 모든 원격 링크를 정상적으로 분리하려고 시도해야 합니다.
        return "TURNING_OFF";
      case BluetoothAdapter.STATE_TURNING_ON:
        // Indicates the local Bluetooth adapter is turning on. However local clients should wait for STATE_ON before attempting to use the adapter.
        // 로컬 Bluetooth 어댑터가 켜져 있음을 나타냅니다. 그러나 로컬 클라이언트는 어댑터를 사용하기 전에 STATE_ON을 기다려야 합니다.
        return  "TURNING_ON";
      default:
        return "INVALID";
    }
  }
}
