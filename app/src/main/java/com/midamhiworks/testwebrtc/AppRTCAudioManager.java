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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import com.midamhiworks.testwebrtc.util.AppRTCUtils;
import org.webrtc.ThreadUtils;

/**
 * AppRTCAudioManager manages all audio related parts of the AppRTC demo.
 *
 * AppRTCAudioManager 는 AppRTC 데모에서 모든 오디오 관련 부분을 관리합니다.
 */

/*
AppRTCAudioManager (필수)

- 음성(입·출력)을 종합적으로 관리하여 전송하는데 필요한 클래스
 */
public class AppRTCAudioManager {
  private static final String TAG = "AppRTCAudioManager";
  private static final String SPEAKERPHONE_AUTO = "auto";
  private static final String SPEAKERPHONE_TRUE = "true";
  private static final String SPEAKERPHONE_FALSE = "false";

  /**
   * AudioDevice is the names of possible audio devices that we currently
   * support.
   */
  public enum AudioDevice { SPEAKER_PHONE, WIRED_HEADSET, EARPIECE, BLUETOOTH, NONE }

  /** AudioManager state. */
  public enum AudioManagerState {
    UNINITIALIZED,
    PREINITIALIZED,
    RUNNING,
  }

  /** Selected audio device change event. */
  public interface AudioManagerEvents {
    // Callback fired once audio device is changed or list of available audio devices changed.
    void onAudioDeviceChanged(
        AudioDevice selectedAudioDevice, Set<AudioDevice> availableAudioDevices);
  }

  private final Context apprtcContext;
  @Nullable
  private AudioManager audioManager;

  @Nullable
  private AudioManagerEvents audioManagerEvents;
  private AudioManagerState amState;
  private int savedAudioMode = AudioManager.MODE_INVALID;
  private boolean savedIsSpeakerPhoneOn;
  private boolean savedIsMicrophoneMute;
  private boolean hasWiredHeadset;

  // Default audio device; speaker phone for video calls or earpiece for audio only calls.
  // 기본 오디오 장치, 비디오 통화용 스피커 전화 또는 오디오 전용 통화용 이어피스입니다.
  private AudioDevice defaultAudioDevice;

  // Contains the currently selected audio device.
  // This device is changed automatically using a certain scheme where e.g.
  // a wired headset "wins" over speaker phone. It is also possible for a user to explicitly select a device (and overrid any predefined scheme).
  // See |userSelectedAudioDevice| for details.
  // 현재 선택된 오디오 장치를 포함합니다.
  // 이 장치는 스피커폰으로 유선 헤드셋을 "윈"하는 특정 방식을 사용하여 자동으로 변경됩니다. 또한 사용자가 장치를 명시적으로 선택하고 미리 정의된 체계를 재정의할 수도 있습니다.
  // |userSelected 를 참조하세요. 자세한 내용은 AudioDevice|을(를) 참조하시기 바랍니다.
  private AudioDevice selectedAudioDevice;

  // Contains the user-selected audio device which overrides the predefined selection scheme.
  // TODO(henrika): always set to AudioDevice.NONE today. Add support for
  // explicit selection based on choice by userSelectedAudioDevice.
  // 미리 정의된 선택 체계를 재정의하는 사용자가 선택한 오디오 장치를 포함합니다.
  // TODO(헨리카): 항상 AudioDevice로 설정합니다. 사용자가 선택한 선택에 따라 명시적 선택을 위한 지원을 추가합니다. 오디오 장치입니다.
  private AudioDevice userSelectedAudioDevice;

  // Contains speakerphone setting: auto, true or false
  // 스피커폰 설정(자동, 참 또는 거짓)이 포함되어 있습니다.
  private final String useSpeakerphone;

  // Proximity sensor object. It measures the proximity of an object in cm relative to the view screen of a device and can therefore be used to
  // assist device switching (close to ear <=> use headset earpiece if available, far from ear <=> use speaker phone).
  // 근접 센서 객체입니다. 이 장치는 장치의 화면과 비교하여 물체의 근접도를 cm 단위로 측정하므로
  // 장치 전환을 지원하는 데 사용할 수 있습니다(사용 가능한 경우 헤드셋 이어피스를 사용합니다(사용 가능한 경우 귀 <=> 스피커폰에서 멀리 떨어져 있음).
  @Nullable private AppRTCProximitySensor proximitySensor;

  // Handles all tasks related to Bluetooth headset devices.
  // Bluetooth 헤드셋 장치와 관련된 모든 작업을 처리합니다.
  private final AppRTCBluetoothManager bluetoothManager;

  // Contains a list of available audio devices. A Set collection is used to avoid duplicate elements.
  // 사용 가능한 오디오 장치 목록이 들어 있습니다. 집합은 중복 요소를 방지하는 데 사용됩니다.
  private Set<AudioDevice> audioDevices = new HashSet<>();

  // Broadcast receiver for wired headset intent broadcasts.
  // 유선 헤드셋 intent 브로드캐스트 수신기입니다.
  private BroadcastReceiver wiredHeadsetReceiver;

  // Callback method for changes in audio focus.
  // 오디오 초점 변경에 대한 콜백 방법입니다.
  @Nullable
  private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener;

  /**
   * This method is called when the proximity sensor reports a state change,
   * e.g. from "NEAR to FAR" or from "FAR to NEAR".
   */
  private void onProximitySensorChangedState() {
    if (!useSpeakerphone.equals(SPEAKERPHONE_AUTO)) {
      return;
    }

    // The proximity sensor should only be activated when there are exactly two available audio devices.
    // 근접 센서는 사용 가능한 오디오 장치가 정확히 두 개 있는 경우에만 활성화해야 합니다.
    if (audioDevices.size() == 2 && audioDevices.contains(AppRTCAudioManager.AudioDevice.EARPIECE)
        && audioDevices.contains(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE)) {
      if (proximitySensor.sensorReportsNearState()) {
        // Sensor reports that a "handset is being held up to a person's ear", or "something is covering the light sensor".
        // 센서에서 "핸드셋을 사람의 귀에 대고 있다" 또는 "광센서를 덮고 있는 것이 있다"고 보고합니다.
        setAudioDeviceInternal(AppRTCAudioManager.AudioDevice.EARPIECE);
      } else {
        // Sensor reports that a "handset is removed from a person's ear", or "the light sensor is no longer covered".
        // 센서에서 "핸드셋이 사람의 귀에서 제거됨" 또는 "조도 센서가 더 이상 가려지지 않음"이 보고됩니다.
        setAudioDeviceInternal(AppRTCAudioManager.AudioDevice.SPEAKER_PHONE);
      }
    }
  }

  /* Receiver which handles changes in wired headset availability. */
  /* 유선 헤드셋 가용성의 변경 사항을 처리하는 수신기입니다. */
  private class WiredHeadsetReceiver extends BroadcastReceiver {
    private static final int STATE_UNPLUGGED = 0;
    private static final int STATE_PLUGGED = 1;
    private static final int HAS_NO_MIC = 0;
    private static final int HAS_MIC = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
      int state = intent.getIntExtra("state", STATE_UNPLUGGED);
      int microphone = intent.getIntExtra("microphone", HAS_NO_MIC);
      String name = intent.getStringExtra("name");
      Log.d(TAG, "WiredHeadsetReceiver.onReceive" + AppRTCUtils.getThreadInfo() + ": "
              + "a=" + intent.getAction() + ", s="
              + (state == STATE_UNPLUGGED ? "unplugged" : "plugged") + ", m="
              + (microphone == HAS_MIC ? "mic" : "no mic") + ", n=" + name + ", sb="
              + isInitialStickyBroadcast());
      hasWiredHeadset = (state == STATE_PLUGGED);
      updateAudioDeviceState();
    }
  }

  /** Construction. */
  static AppRTCAudioManager create(Context context) {
    return new AppRTCAudioManager(context);
  }

  private AppRTCAudioManager(Context context) {
    Log.d(TAG, "ctor");
    ThreadUtils.checkIsOnMainThread();
    apprtcContext = context;
    audioManager = ((AudioManager) context.getSystemService(Context.AUDIO_SERVICE));
    bluetoothManager = AppRTCBluetoothManager.create(context, this);
    wiredHeadsetReceiver = new WiredHeadsetReceiver();
    amState = AudioManagerState.UNINITIALIZED;

    SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    useSpeakerphone = sharedPreferences.getString(context.getString(R.string.pref_speakerphone_key),
        context.getString(R.string.pref_speakerphone_default));
    Log.d(TAG, "useSpeakerphone: " + useSpeakerphone);
    if (useSpeakerphone.equals(SPEAKERPHONE_FALSE)) {
      defaultAudioDevice = AudioDevice.EARPIECE;
    } else {
      defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    // Create and initialize the proximity sensor.
    // Tablet devices (e.g. Nexus 7) does not support proximity sensors.
    // Note that, the sensor will not be active until start() has been called.
    // 근접 센서를 만들고 초기화합니다.
    // 태블릿 장치(예: Nexus 7)는 근접 센서를 지원하지 않습니다.
    // 이 센서는 start()이 호출될 때까지 활성화되지 않습니다.
    proximitySensor = AppRTCProximitySensor.create(context,
        // This method will be called each time a state change is detected.
        // Example: user holds his hand over the device (closer than ~5 cm), or removes his hand from the device.
            // 이 방법은 상태 변화가 감지될 때마다 호출됩니다.
            // 예: 사용자가 손을 기기 위로 잡거나(~5cm보다 가까운 거리) 손을 장치에서 제거합니다.
        this ::onProximitySensorChangedState);

    Log.d(TAG, "defaultAudioDevice: " + defaultAudioDevice);
    AppRTCUtils.logDeviceInfo(TAG);
  }

  @SuppressWarnings("deprecation") // TODO(henrika): audioManager.requestAudioFocus() is deprecated.
  public void start(AudioManagerEvents audioManagerEvents) {
    Log.d(TAG, "start");
    ThreadUtils.checkIsOnMainThread();
    if (amState == AudioManagerState.RUNNING) {
      Log.e(TAG, "AudioManager is already active");
      return;
    }
    // TODO(henrika): perhaps call new method called preInitAudio() here if UNINITIALIZED.

    Log.d(TAG, "AudioManager starts...");
    this.audioManagerEvents = audioManagerEvents;
    amState = AudioManagerState.RUNNING;

    // Store current audio state so we can restore it when stop() is called.
    // 현재 오디오 상태를 저장하여 stop()를 호출할 때 복원할 수 있습니다.
    savedAudioMode = audioManager.getMode();
    savedIsSpeakerPhoneOn = audioManager.isSpeakerphoneOn();
    savedIsMicrophoneMute = audioManager.isMicrophoneMute();
    hasWiredHeadset = hasWiredHeadset();

    // Create an AudioManager.OnAudioFocusChangeListener instance.
    // Called on the listener to notify if the audio focus for this listener has been changed.
    // The |focusChange| value indicates whether the focus was gained, whether the focus was lost,
    // and whether that loss is transient, or whether the new focus holder will hold it for an unknown amount of time.
    // 오디오 관리자를 만듭니다.OnAudioFocusChangeListener 인스턴스입니다.
    // 수신기에 호출되어 이 수신기에 대한 오디오 초점이 변경되었는지 여부를 알립니다.
    // The |pocusChange| 값은 초점 획득 여부, 초점 손실 여부를 나타냅니다.
    // 그리고 그 손실이 일시적인지, 또는 새로운 포커스 홀더가 알 수 없는 시간 동안 이 손실을 유지할 것인지의 여부입니다.
    // TODO(henrika): possibly extend support of handling audio-focus changes. Only contains
    // logging for now.
    audioFocusChangeListener = focusChange -> {
      final String typeOfChange;
      switch (focusChange) {
        case AudioManager.AUDIOFOCUS_GAIN:
          typeOfChange = "AUDIOFOCUS_GAIN";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE";
          break;
        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
          typeOfChange = "AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK";
          break;
        case AudioManager.AUDIOFOCUS_LOSS:
          typeOfChange = "AUDIOFOCUS_LOSS";
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
          typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT";
          break;
        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
          typeOfChange = "AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK";
          break;
        default:
          typeOfChange = "AUDIOFOCUS_INVALID";
          break;
      }
      Log.d(TAG, "onAudioFocusChange: " + typeOfChange);
    };

    // Request audio playout focus (without ducking) and install listener for changes in focus.
    // 오디오 재생 포커스를 요청하고(움직이지 않음) 포커스를 설치하여 포커스의 변경 사항을 확인합니다.
    int result = audioManager.requestAudioFocus(audioFocusChangeListener,
        AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
      Log.d(TAG, "Audio focus request granted for VOICE_CALL streams");
    } else {
      Log.e(TAG, "Audio focus request failed");
    }

    // Start by setting MODE_IN_COMMUNICATION as default audio mode. It is required to be in this mode when playout and/or recording starts for
    // best possible VoIP performance.
    // MODE_IN_COMMUNICATION 을 기본 오디오 모드로 설정하는 것으로 시작합니다. 최상의 VoIP 성능을 위해 재생 및/또는 녹화가 시작될 때 이 모드에 있어야 합니다.
    audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);

    // Always disable microphone mute during a WebRTC call.
    // WebRTC 호출 중에는 항상 마이크 음소거를 비활성화합니다.
    setMicrophoneMute(false);

    // Set initial device states.
    // 초기 장치 상태를 설정합니다.
    userSelectedAudioDevice = AudioDevice.NONE;
    selectedAudioDevice = AudioDevice.NONE;
    audioDevices.clear();

    // Initialize and start Bluetooth if a BT device is available or initiate detection of new (enabled) BT devices.
    // BT 장치를 사용할 수 있는 경우 Bluetooth를 초기화하고 시작하거나 새(활성화) BT 장치의 탐지를 시작합니다.
    bluetoothManager.start();

    // Do initial selection of audio device. This setting can later be changed either by adding/removing a BT or wired headset or by covering/uncovering
    // the proximity sensor.
    // 오디오 장치를 처음 선택합니다. 나중에 BT 또는 유선 헤드셋을 추가/제거하거나 근접 센서를 덮거나 분리하여 이 설정을 변경할 수 있습니다.
    updateAudioDeviceState();

    // Register receiver for broadcast intents related to adding/removing a wired headset.
    // 유선 헤드셋 추가/제거와 관련된 브로드캐스트 인텐트에 수신기를 등록합니다.
    registerReceiver(wiredHeadsetReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));
    Log.d(TAG, "AudioManager started");
  }

  @SuppressWarnings("deprecation") // TODO(henrika): audioManager.abandonAudioFocus() is deprecated.
  public void stop() {
    Log.d(TAG, "stop");
    ThreadUtils.checkIsOnMainThread();
    if (amState != AudioManagerState.RUNNING) {
      Log.e(TAG, "Trying to stop AudioManager in incorrect state: " + amState);
      return;
    }
    amState = AudioManagerState.UNINITIALIZED;

    unregisterReceiver(wiredHeadsetReceiver);

    bluetoothManager.stop();

    // Restore previously stored audio states.
    // 이전에 저장된 오디오 상태를 복원합니다.
    setSpeakerphoneOn(savedIsSpeakerPhoneOn);
    setMicrophoneMute(savedIsMicrophoneMute);
    audioManager.setMode(savedAudioMode);

    // Abandon audio focus. Gives the previous focus owner, if any, focus.
    // 오디오 포커스를 해제합니다. 이전 포커스 소유자에게 포커스를 부여합니다(있는 경우).
    audioManager.abandonAudioFocus(audioFocusChangeListener);
    audioFocusChangeListener = null;
    Log.d(TAG, "Abandoned audio focus for VOICE_CALL streams");

    if (proximitySensor != null) {
      proximitySensor.stop();
      proximitySensor = null;
    }

    audioManagerEvents = null;
    Log.d(TAG, "AudioManager stopped");
  }

  /** Changes selection of the currently active audio device. */
  private void setAudioDeviceInternal(AudioDevice device) {
    Log.d(TAG, "setAudioDeviceInternal(device=" + device + ")");
    AppRTCUtils.assertIsTrue(audioDevices.contains(device));

    switch (device) {
      case SPEAKER_PHONE:
        setSpeakerphoneOn(true);
        break;
      case EARPIECE:
        setSpeakerphoneOn(false);
        break;
      case WIRED_HEADSET:
        setSpeakerphoneOn(false);
        break;
      case BLUETOOTH:
        setSpeakerphoneOn(false);
        break;
      default:
        Log.e(TAG, "Invalid audio device selection");
        break;
    }
    selectedAudioDevice = device;
  }

  /**
   * Changes default audio device.
   * TODO(henrika): add usage of this method in the AppRTCMobile client.
   */
  public void setDefaultAudioDevice(AudioDevice defaultDevice) {
    ThreadUtils.checkIsOnMainThread();
    switch (defaultDevice) {
      case SPEAKER_PHONE:
        defaultAudioDevice = defaultDevice;
        break;
      case EARPIECE:
        if (hasEarpiece()) {
          defaultAudioDevice = defaultDevice;
        } else {
          defaultAudioDevice = AudioDevice.SPEAKER_PHONE;
        }
        break;
      default:
        Log.e(TAG, "Invalid default audio device selection");
        break;
    }
    Log.d(TAG, "setDefaultAudioDevice(device=" + defaultAudioDevice + ")");
    updateAudioDeviceState();
  }

  /** Changes selection of the currently active audio device. */
  public void selectAudioDevice(AudioDevice device) {
    ThreadUtils.checkIsOnMainThread();
    if (!audioDevices.contains(device)) {
      Log.e(TAG, "Can not select " + device + " from available " + audioDevices);
    }
    userSelectedAudioDevice = device;
    updateAudioDeviceState();
  }

  /** Returns current set of available/selectable audio devices. */
  public Set<AudioDevice> getAudioDevices() {
    ThreadUtils.checkIsOnMainThread();
    return Collections.unmodifiableSet(new HashSet<>(audioDevices));
  }

  /** Returns the currently selected audio device. */
  public AudioDevice getSelectedAudioDevice() {
    ThreadUtils.checkIsOnMainThread();
    return selectedAudioDevice;
  }

  /** Helper method for receiver registration. */
  private void registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
    apprtcContext.registerReceiver(receiver, filter);
  }

  /** Helper method for unregistration of an existing receiver. */
  private void unregisterReceiver(BroadcastReceiver receiver) {
    apprtcContext.unregisterReceiver(receiver);
  }

  /** Sets the speaker phone mode. */
  private void setSpeakerphoneOn(boolean on) {
    boolean wasOn = audioManager.isSpeakerphoneOn();
    if (wasOn == on) {
      return;
    }
    audioManager.setSpeakerphoneOn(on);
  }

  /** Sets the microphone mute state. */
  private void setMicrophoneMute(boolean on) {
    boolean wasMuted = audioManager.isMicrophoneMute();
    if (wasMuted == on) {
      return;
    }
    audioManager.setMicrophoneMute(on);
  }

  /** Gets the current earpiece state. */
  private boolean hasEarpiece() {
    return apprtcContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
  }

  /**
   * Checks whether a wired headset is connected or not.
   * This is not a valid indication that audio playback is actually over the wired headset as audio routing depends on other conditions.
   * We only use it as an early indicator (during initialization) of an attached wired headset.
   * 유선 헤드셋의 연결 여부를 확인합니다.
   * 오디오 라우팅은 다른 조건에 따라 다르기 때문에 오디오 재생이 실제로 유선 헤드셋을 통해 이루어졌음을 나타내는 유효한 표시는 아닙니다.
   * 부착된 유선 헤드셋의 초기 표시(초기화 중)로만 사용합니다.
   */
  @Deprecated
  private boolean hasWiredHeadset() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
      return audioManager.isWiredHeadsetOn();
    } else {
      final AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL);
      for (AudioDeviceInfo device : devices) {
        final int type = device.getType();
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) {
          Log.d(TAG, "hasWiredHeadset: found wired headset");
          return true;
        } else if (type == AudioDeviceInfo.TYPE_USB_DEVICE) {
          Log.d(TAG, "hasWiredHeadset: found USB audio device");
          return true;
        }
      }
      return false;
    }
  }

  /**
   * Updates list of possible audio devices and make new device selection.
   * TODO(henrika): add unit test to verify all state transitions.
   */
  public void updateAudioDeviceState() {
    ThreadUtils.checkIsOnMainThread();
    Log.d(TAG, "--- updateAudioDeviceState: "
            + "wired headset=" + hasWiredHeadset + ", "
            + "BT state=" + bluetoothManager.getState());
    Log.d(TAG, "Device status: "
            + "available=" + audioDevices + ", "
            + "selected=" + selectedAudioDevice + ", "
            + "user selected=" + userSelectedAudioDevice);

    // Check if any Bluetooth headset is connected. The internal BT state will change accordingly.
    // TODO(henrika): perhaps wrap required state into BT manager.
    // Bluetooth 헤드셋이 연결되어 있는지 확인합니다. 이에 따라 내부 BT 상태가 변경됩니다.
    // TODO(헨리카): 아마도 필요한 상태를 BT 관리자로 포장할 것입니다.
    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_UNAVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_DISCONNECTING) {
      bluetoothManager.updateDevice();
    }

    // Update the set of available audio devices.
    // 사용 가능한 오디오 장치 세트를 업데이트합니다.
    Set<AudioDevice> newAudioDevices = new HashSet<>();

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE) {
      newAudioDevices.add(AudioDevice.BLUETOOTH);
    }

    if (hasWiredHeadset) {
      // If a wired headset is connected, then it is the only possible option.
      // 유선 헤드셋이 연결되어 있는 경우 이 옵션만 사용할 수 있습니다.
      newAudioDevices.add(AudioDevice.WIRED_HEADSET);
    } else {
      // No wired headset, hence the audio-device list can contain speaker phone (on a tablet), or speaker phone and earpiece (on mobile phone).
      // 유선 헤드셋이 없으므로 오디오 장치 목록에는 스피커폰(태블릿) 또는 스피커폰 및 이어피스(휴대폰)가 포함될 수 있습니다.
      newAudioDevices.add(AudioDevice.SPEAKER_PHONE);
      if (hasEarpiece()) {
        newAudioDevices.add(AudioDevice.EARPIECE);
      }
    }
    // Store state which is set to true if the device list has changed.
    // 장치 목록이 변경된 경우 true 로 설정된 상태를 저장합니다.
    boolean audioDeviceSetUpdated = !audioDevices.equals(newAudioDevices);
    // Update the existing audio device set.
    // 기존 오디오 장치 세트를 업데이트합니다.
    audioDevices = newAudioDevices;
    // Correct user selected audio devices if needed.
    // 필요한 경우 사용자가 선택한 오디오 장치를 수정합니다.
    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_UNAVAILABLE
        && userSelectedAudioDevice == AudioDevice.BLUETOOTH) {
      // If BT is not available, it can't be the user selection.
      // BT를 사용할 수 없는 경우 사용자가 선택할 수 없습니다.
      userSelectedAudioDevice = AudioDevice.NONE;
    }
    if (hasWiredHeadset && userSelectedAudioDevice == AudioDevice.SPEAKER_PHONE) {
      // If user selected speaker phone, but then plugged wired headset then make wired headset as user selected device.
      // 사용자가 스피커 전화를 선택한 후 유선 헤드셋을 연결한 경우 유선 헤드셋을 사용자가 선택한 장치로 만듭니다.
      userSelectedAudioDevice = AudioDevice.WIRED_HEADSET;
    }
    if (!hasWiredHeadset && userSelectedAudioDevice == AudioDevice.WIRED_HEADSET) {
      // If user selected wired headset, but then unplugged wired headset then make speaker phone as user selected device.
      // 사용자가 유선 헤드셋을 선택했지만 플러그를 뽑지 않은 경우, 스피커폰을 사용자가 선택한 장치로 만듭니다.
      userSelectedAudioDevice = AudioDevice.SPEAKER_PHONE;
    }

    // Need to start Bluetooth if it is available and user either selected it explicitly or user did not select any output device.
    // Bluetooth를 사용할 수 있고 사용자가 명시적으로 선택하거나 사용자가 출력 장치를 선택하지 않은 경우 Bluetooth를 시작해야 합니다.
    boolean needBluetoothAudioStart =
        bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        && (userSelectedAudioDevice == AudioDevice.NONE
               || userSelectedAudioDevice == AudioDevice.BLUETOOTH);

    // Need to stop Bluetooth audio if user selected different device and Bluetooth SCO connection is established or in the process.
    // 사용자가 다른 장치를 선택하고 Bluetooth SCO 연결이 설정되었거나 진행 중인 경우 Bluetooth 오디오를 중지해야 합니다.
    boolean needBluetoothAudioStop =
        (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED
            || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING)
        && (userSelectedAudioDevice != AudioDevice.NONE
               && userSelectedAudioDevice != AudioDevice.BLUETOOTH);

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.HEADSET_AVAILABLE
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTING
        || bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED) {
      Log.d(TAG, "Need BT audio: start=" + needBluetoothAudioStart + ", "
              + "stop=" + needBluetoothAudioStop + ", "
              + "BT state=" + bluetoothManager.getState());
    }

    // Start or stop Bluetooth SCO connection given states set earlier.
    // Bluetooth SCO 연결을 시작하거나 중지합니다(이전 설정 상태).
    if (needBluetoothAudioStop) {
      bluetoothManager.stopScoAudio();
      bluetoothManager.updateDevice();
    }

    if (needBluetoothAudioStart && !needBluetoothAudioStop) {
      // Attempt to start Bluetooth SCO audio (takes a few second to start).
      // Bluetooth SCO 오디오를 시작합니다(시작하는 데 몇 초 정도 소요됨).
      if (!bluetoothManager.startScoAudio()) {
        // Remove BLUETOOTH from list of available devices since SCO failed.
        // SCO 오류 이후 사용 가능한 장치 목록에서 BLUETOOTH 를 제거합니다.
        audioDevices.remove(AudioDevice.BLUETOOTH);
        audioDeviceSetUpdated = true;
      }
    }

    // Update selected audio device.
    // 선택한 오디오 장치를 업데이트합니다.
    final AudioDevice newAudioDevice;

    if (bluetoothManager.getState() == AppRTCBluetoothManager.State.SCO_CONNECTED) {
      // If a Bluetooth is connected, then it should be used as output audio device.
      // Note that it is not sufficient that a headset is available; an active SCO channel must also be up and running.
      // Bluetooth 가 연결된 경우 출력 오디오 장치로 사용해야 합니다.
      // 헤드셋을 사용할 수 있는 것으로는 충분하지 않습니다. 활성 SCO 채널도 가동 및 실행 중이어야 합니다.
      newAudioDevice = AudioDevice.BLUETOOTH;
    } else if (hasWiredHeadset) {
      // If a wired headset is connected, but Bluetooth is not, then wired headset is used as audio device.
      // 유선 헤드셋이 연결되어 있지만 Bluetooth가 연결되어 있지 않으면 유선 헤드셋이 오디오 장치로 사용됩니다.
      newAudioDevice = AudioDevice.WIRED_HEADSET;
    } else {
      // No wired headset and no Bluetooth, hence the audio-device list can contain speaker phone (on a tablet), or speaker phone and earpiece (on mobile phone).
      // |defaultAudioDevice| contains either AudioDevice.SPEAKER_PHONE or AudioDevice.EARPIECE depending on the user's selection.
      // 유선 헤드셋이 없고 블루투스가 없으므로 오디오 장치 목록에는 스피커 폰(태블릿) 또는 스피커 폰 및 이어피스(휴대폰)가 포함될 수 있습니다.
      // |기본값입니다.AudioDevice|에는 AudioDevice 가 포함되어 있습니다.SPEAKER_PHONE 또는 오디오 장치입니다.사용자의 선택에 따라 EARPIECE 가 달라집니다.
      newAudioDevice = defaultAudioDevice;
    }
    // Switch to new device but only if there has been any changes.
    // 변경된 경우에만 새 장치로 전환합니다.
    if (newAudioDevice != selectedAudioDevice || audioDeviceSetUpdated) {
      // Do the required device switch.
      // 필요한 장치 스위치를 실행합니다.
      setAudioDeviceInternal(newAudioDevice);
      Log.d(TAG, "New device status: "
              + "available=" + audioDevices + ", "
              + "selected=" + newAudioDevice);
      if (audioManagerEvents != null) {
        // Notify a listening client that audio device has been changed.
        // 청취 클라이언트에 오디오 장치가 변경되었음을 알립니다.
        audioManagerEvents.onAudioDeviceChanged(selectedAudioDevice, audioDevices);
      }
    }
    Log.d(TAG, "--- updateAudioDeviceState done");
  }
}
