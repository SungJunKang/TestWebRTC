/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.lang.RuntimeException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.midamhiworks.testwebrtc.AppRTCAudioManager.AudioDevice;
import com.midamhiworks.testwebrtc.AppRTCAudioManager.AudioManagerEvents;
import com.midamhiworks.testwebrtc.AppRTCClient.RoomConnectionParameters;
import com.midamhiworks.testwebrtc.AppRTCClient.SignalingParameters;
import com.midamhiworks.testwebrtc.PeerConnectionClient.DataChannelParameters;
import com.midamhiworks.testwebrtc.PeerConnectionClient.PeerConnectionParameters;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

/**
 * Activity for peer connection call setup, call waiting and call view.
 *
 * 피어 연결 통화 설정, 통화 대기 및 통화 보기를 위한 Activity 입니다.
 */

/*
CallActivity (필수)

- 영상통화를 하는 화면 클래스

- ConnectActivity 를 통해 넘어온 인텐트들의 값을 분석하여 WebSocket 통신인지, TCP 통신이지 파악하여 AppRTCClient 를 생성한 뒤, 영상통화를 시작함.
 */
public class CallActivity extends Activity implements AppRTCClient.SignalingEvents,
                                                      PeerConnectionClient.PeerConnectionEvents,
                                                      CallFragment.OnCallEvents {
  private static final String TAG = "CallRTCClient";

  public static final String EXTRA_ROOMID = "com.midamhiworks.testwebrtc.ROOMID";
  public static final String EXTRA_URLPARAMETERS = "com.midamhiworks.testwebrtc.URLPARAMETERS";
  public static final String EXTRA_LOOPBACK = "com.midamhiworks.testwebrtc.LOOPBACK";
  public static final String EXTRA_VIDEO_CALL = "com.midamhiworks.testwebrtc.VIDEO_CALL";
  public static final String EXTRA_SCREENCAPTURE = "com.midamhiworks.testwebrtc.SCREENCAPTURE";
  public static final String EXTRA_CAMERA2 = "com.midamhiworks.testwebrtc.CAMERA2";
  public static final String EXTRA_VIDEO_WIDTH = "com.midamhiworks.testwebrtc.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT = "com.midamhiworks.testwebrtc.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS = "com.midamhiworks.testwebrtc.VIDEO_FPS";
  public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED = "com.midamhiworks.testwebrtc.VIDEO_CAPTUREQUALITYSLIDER";
  public static final String EXTRA_VIDEO_BITRATE = "com.midamhiworks.testwebrtc.VIDEO_BITRATE";
  public static final String EXTRA_VIDEOCODEC = "com.midamhiworks.testwebrtc.VIDEOCODEC";
  public static final String EXTRA_HWCODEC_ENABLED = "com.midamhiworks.testwebrtc.HWCODEC";
  public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "com.midamhiworks.testwebrtc.CAPTURETOTEXTURE";
  public static final String EXTRA_FLEXFEC_ENABLED = "com.midamhiworks.testwebrtc.FLEXFEC";
  public static final String EXTRA_AUDIO_BITRATE = "com.midamhiworks.testwebrtc.AUDIO_BITRATE";
  public static final String EXTRA_AUDIOCODEC = "com.midamhiworks.testwebrtc.AUDIOCODEC";
  public static final String EXTRA_NOAUDIOPROCESSING_ENABLED = "com.midamhiworks.testwebrtc.NOAUDIOPROCESSING";
  public static final String EXTRA_AECDUMP_ENABLED = "com.midamhiworks.testwebrtc.AECDUMP";
  public static final String EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED = "com.midamhiworks.testwebrtc.SAVE_INPUT_AUDIO_TO_FILE";
  public static final String EXTRA_OPENSLES_ENABLED = "com.midamhiworks.testwebrtc.OPENSLES";
  public static final String EXTRA_DISABLE_BUILT_IN_AEC = "com.midamhiworks.testwebrtc.DISABLE_BUILT_IN_AEC";
  public static final String EXTRA_DISABLE_BUILT_IN_AGC = "com.midamhiworks.testwebrtc.DISABLE_BUILT_IN_AGC";
  public static final String EXTRA_DISABLE_BUILT_IN_NS = "com.midamhiworks.testwebrtc.DISABLE_BUILT_IN_NS";
  public static final String EXTRA_DISABLE_WEBRTC_AGC_AND_HPF = "com.midamhiworks.testwebrtc.DISABLE_WEBRTC_GAIN_CONTROL";
  public static final String EXTRA_DISPLAY_HUD = "com.midamhiworks.testwebrtc.DISPLAY_HUD";
  public static final String EXTRA_TRACING = "com.midamhiworks.testwebrtc.TRACING";
  public static final String EXTRA_CMDLINE = "com.midamhiworks.testwebrtc.CMDLINE";
  public static final String EXTRA_RUNTIME = "com.midamhiworks.testwebrtc.RUNTIME";
  public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "com.midamhiworks.testwebrtc.VIDEO_FILE_AS_CAMERA";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE = "com.midamhiworks.testwebrtc.SAVE_REMOTE_VIDEO_TO_FILE";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH = "com.midamhiworks.testwebrtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT = "com.midamhiworks.testwebrtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
  public static final String EXTRA_USE_VALUES_FROM_INTENT = "com.midamhiworks.testwebrtc.USE_VALUES_FROM_INTENT";
  public static final String EXTRA_DATA_CHANNEL_ENABLED = "com.midamhiworks.testwebrtc.DATA_CHANNEL_ENABLED";
  public static final String EXTRA_ORDERED = "com.midamhiworks.testwebrtc.ORDERED";
  public static final String EXTRA_MAX_RETRANSMITS_MS = "com.midamhiworks.testwebrtc.MAX_RETRANSMITS_MS";
  public static final String EXTRA_MAX_RETRANSMITS = "com.midamhiworks.testwebrtc.MAX_RETRANSMITS";
  public static final String EXTRA_PROTOCOL = "com.midamhiworks.testwebrtc.PROTOCOL";
  public static final String EXTRA_NEGOTIATED = "com.midamhiworks.testwebrtc.NEGOTIATED";
  public static final String EXTRA_ID = "com.midamhiworks.testwebrtc.ID";
  public static final String EXTRA_ENABLE_RTCEVENTLOG = "com.midamhiworks.testwebrtc.ENABLE_RTCEVENTLOG";

  private static final int CAPTURE_PERMISSION_REQUEST_CODE = 1;

  // List of mandatory application permissions.
  // 필수 응용 프로그램 사용 권한 목록입니다.
  private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
      "android.permission.RECORD_AUDIO", "android.permission.INTERNET"};

  // Peer connection statistics callback period in ms.
  // 피어 연결 통계 콜백 기간(ms)입니다.
  private static final int STAT_CALLBACK_PERIOD = 1000;

  private static class ProxyVideoSink implements VideoSink {
    private VideoSink target;

    @Override
    synchronized public void onFrame(VideoFrame frame) {
      if (target == null) {
        Logging.d(TAG, "Dropping frame in proxy because target is null.");
        return;
      }

      target.onFrame(frame);
    }

    synchronized public void setTarget(VideoSink target) {
      this.target = target;
    }
  }

  private final ProxyVideoSink remoteProxyRenderer = new ProxyVideoSink();
  private final ProxyVideoSink localProxyVideoSink = new ProxyVideoSink();
  @Nullable private PeerConnectionClient peerConnectionClient;
  @Nullable
  private AppRTCClient appRtcClient;
  @Nullable
  private SignalingParameters signalingParameters;
  @Nullable private AppRTCAudioManager audioManager;
  @Nullable
  private SurfaceViewRenderer pipRenderer;
  @Nullable
  private SurfaceViewRenderer fullscreenRenderer;
  @Nullable
  private VideoFileRenderer videoFileRenderer;
  private final List<VideoSink> remoteSinks = new ArrayList<>();
  private Toast logToast;
  private boolean commandLineRun;
  private boolean activityRunning;
  private RoomConnectionParameters roomConnectionParameters;
  @Nullable
  private PeerConnectionParameters peerConnectionParameters;
  private boolean connected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs;
  private boolean micEnabled = true;
  private boolean screencaptureEnabled;
  private static Intent mediaProjectionPermissionResultData;
  private static int mediaProjectionPermissionResultCode;
  // True if local view is in the fullscreen renderer.
  // 전체 화면 렌더러에 로컬 보기가 있는 경우 True 입니다.
  private boolean isSwappedFeeds;

  // Controls
  // 제어합니다.
  private CallFragment callFragment;
  private HudFragment hudFragment;
  private CpuMonitor cpuMonitor;

  @Override
  // TODO(bugs.webrtc.org/8580): LayoutParams.FLAG_TURN_SCREEN_ON and LayoutParams.FLAG_SHOW_WHEN_LOCKED are deprecated.
  // TODO(bugs.webrtc.org/8580): LayoutParams(레이아웃 매개 변수)입니다.FLAG_TURN_SCREEN_ON 및 LayoutParams 입니다.FLAG_SHOW_WHEN_LOCKED 는 더 이상 사용되지 않습니다.
  @SuppressWarnings("deprecation")
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    // Set window styles for fullscreen-window size. Needs to be done before adding content.
    // 전체 화면-창 크기에 대한 창 스타일을 설정합니다. 내용을 추가하기 전에 수행해야 합니다.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_FULLSCREEN | LayoutParams.FLAG_KEEP_SCREEN_ON
        | LayoutParams.FLAG_SHOW_WHEN_LOCKED | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());
    setContentView(R.layout.activity_call);

    connected = false;
    signalingParameters = null;

    // Create UI controls.
    // UI 컨트롤을 만듭니다.
    pipRenderer = findViewById(R.id.pip_video_view);
    fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
    callFragment = new CallFragment();
    hudFragment = new HudFragment();

    // Show/hide call control fragment on view click.
    // 뷰에 통화 제어 fragment 를 표시/숨깁니다. 클릭
    View.OnClickListener listener = view -> toggleCallControlFragmentVisibility();

    // Swap feeds on pip view click.
    // pip view 클릭 시 swap feeds on pip view 클릭 시 swap feeds 입니다.
    pipRenderer.setOnClickListener(view -> setSwappedFeeds(!isSwappedFeeds));

    fullscreenRenderer.setOnClickListener(listener);
    remoteSinks.add(remoteProxyRenderer);

    final Intent intent = getIntent();
    final EglBase eglBase = EglBase.create();

    // Create video renderers.
    // 비디오 렌더러를 만듭니다.
    pipRenderer.init(eglBase.getEglBaseContext(), null);
    pipRenderer.setScalingType(ScalingType.SCALE_ASPECT_FIT);
    String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

    // When saveRemoteVideoToFile is set we save the video from the remote to a file.
    // saveRemoteVideoToFile 을 설정하면 비디오를 원격에서 파일로 저장합니다.
    if (saveRemoteVideoToFile != null) {
      int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
      int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
      try {
        videoFileRenderer = new VideoFileRenderer(
            saveRemoteVideoToFile, videoOutWidth, videoOutHeight, eglBase.getEglBaseContext());
        remoteSinks.add(videoFileRenderer);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to open video file for output: " + saveRemoteVideoToFile, e);
      }
    }
    fullscreenRenderer.init(eglBase.getEglBaseContext(), null);
    fullscreenRenderer.setScalingType(ScalingType.SCALE_ASPECT_FILL);

    pipRenderer.setZOrderMediaOverlay(true);
    pipRenderer.setEnableHardwareScaler(true /* enabled */);
    fullscreenRenderer.setEnableHardwareScaler(false /* enabled */);
    // Start with local feed in fullscreen and swap it to the pip when the call is connected.
    // 전체 화면에서 로컬 피드를 시작하고 통화가 연결되면 피드로 바꿉니다.
    setSwappedFeeds(true /* isSwappedFeeds */);

    // Check for mandatory permissions.
    // 필수 사용 권한을 확인합니다.
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        logAndToast("Permission " + permission + " is not granted");
        setResult(RESULT_CANCELED);
        finish();
        return;
      }
    }

    Uri roomUri = intent.getData();
    if (roomUri == null) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    // Get Intent parameters.
    // Intent 매개변수를 가져옵니다.
    String roomId = intent.getStringExtra(EXTRA_ROOMID);
    Log.d(TAG, "Room ID: " + roomId);
    if (roomId == null || roomId.length() == 0) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Incorrect room ID in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }

    boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
    boolean tracing = intent.getBooleanExtra(EXTRA_TRACING, false);

    int videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0);
    int videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0);

    screencaptureEnabled = intent.getBooleanExtra(EXTRA_SCREENCAPTURE, false);
    // If capturing format is not specified for screencapture, use screen resolution.
    // 캡처 형식이 화면 캡처에 지정되지 않은 경우 화면 해상도를 사용합니다.
    if (screencaptureEnabled && videoWidth == 0 && videoHeight == 0) {
      DisplayMetrics displayMetrics = getDisplayMetrics();
      videoWidth = displayMetrics.widthPixels;
      videoHeight = displayMetrics.heightPixels;
    }
    DataChannelParameters dataChannelParameters = null;
    if (intent.getBooleanExtra(EXTRA_DATA_CHANNEL_ENABLED, false)) {
      dataChannelParameters = new DataChannelParameters(intent.getBooleanExtra(EXTRA_ORDERED, true),
          intent.getIntExtra(EXTRA_MAX_RETRANSMITS_MS, -1),
          intent.getIntExtra(EXTRA_MAX_RETRANSMITS, -1), intent.getStringExtra(EXTRA_PROTOCOL),
          intent.getBooleanExtra(EXTRA_NEGOTIATED, false), intent.getIntExtra(EXTRA_ID, -1));
    }
    peerConnectionParameters =
        new PeerConnectionParameters(intent.getBooleanExtra(EXTRA_VIDEO_CALL, true), loopback,
            tracing, videoWidth, videoHeight, intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
            intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0), intent.getStringExtra(EXTRA_VIDEOCODEC),
            intent.getBooleanExtra(EXTRA_HWCODEC_ENABLED, true),
            intent.getBooleanExtra(EXTRA_FLEXFEC_ENABLED, false),
            intent.getIntExtra(EXTRA_AUDIO_BITRATE, 0), intent.getStringExtra(EXTRA_AUDIOCODEC),
            intent.getBooleanExtra(EXTRA_NOAUDIOPROCESSING_ENABLED, false),
            intent.getBooleanExtra(EXTRA_AECDUMP_ENABLED, false),
            intent.getBooleanExtra(EXTRA_SAVE_INPUT_AUDIO_TO_FILE_ENABLED, false),
            intent.getBooleanExtra(EXTRA_OPENSLES_ENABLED, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AEC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_AGC, false),
            intent.getBooleanExtra(EXTRA_DISABLE_BUILT_IN_NS, false),
            intent.getBooleanExtra(EXTRA_DISABLE_WEBRTC_AGC_AND_HPF, false),
            intent.getBooleanExtra(EXTRA_ENABLE_RTCEVENTLOG, false), dataChannelParameters);
    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
    int runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

    Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

    // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the standard WebSocketRTCClient.
    // 연결 클라이언트를 만듭니다. 룸 이름이 IP인 경우 DirectRTCClient 를 사용합니다. 그렇지 않으면 표준 WebSocketRTCClient를 사용합니다.
    if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
      appRtcClient = new WebSocketRTCClient(this);
    } else {
      Log.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
      appRtcClient = new DirectRTCClient(this);
    }
    // Create connection parameters.
    // 연결 매개 변수를 만듭니다.
    String urlParameters = intent.getStringExtra(EXTRA_URLPARAMETERS);
    roomConnectionParameters =
        new RoomConnectionParameters(roomUri.toString(), roomId, loopback, urlParameters);

    // Create CPU monitor
    // CPU 모니터를 생성합니다.
    if (CpuMonitor.isSupported()) {
      cpuMonitor = new CpuMonitor(this);
      hudFragment.setCpuMonitor(cpuMonitor);
    }

    // Send intent arguments to fragments.
    // fragments 에 intent 인수를 보냅니다.
    callFragment.setArguments(intent.getExtras());
    hudFragment.setArguments(intent.getExtras());
    // Activate call and HUD fragments and start the call.
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.add(R.id.call_fragment_container, callFragment);
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();

    // For command line execution run connection for <runTimeMs> and exit.
    // 명령줄 실행의 경우 [runTimeMs]에 대한 연결을 실행하고 종료합니다.
    if (commandLineRun && runTimeMs > 0) {
      (new Handler()).postDelayed(() -> disconnect(), runTimeMs);
    }

    // Create peer connection client.
    // 피어 연결 클라이언트를 만듭니다.
    peerConnectionClient = new PeerConnectionClient(
        getApplicationContext(), eglBase, peerConnectionParameters, CallActivity.this);
    PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
    if (loopback) {
      options.networkIgnoreMask = 0;
    }
    peerConnectionClient.createPeerConnectionFactory(options);

    if (screencaptureEnabled) {
      startScreenCapture();
    } else {
      startCall();
    }
  }

  @TargetApi(17)
  private DisplayMetrics getDisplayMetrics() {
    DisplayMetrics displayMetrics = new DisplayMetrics();
    WindowManager windowManager =
        (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
    windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
    return displayMetrics;
  }

  @TargetApi(19)
  private static int getSystemUiVisibility() {
    int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
    }
    return flags;
  }

  @TargetApi(21)
  private void startScreenCapture() {
    MediaProjectionManager mediaProjectionManager =
        (MediaProjectionManager) getApplication().getSystemService(
            Context.MEDIA_PROJECTION_SERVICE);
    startActivityForResult(
        mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode != CAPTURE_PERMISSION_REQUEST_CODE)
      return;
    mediaProjectionPermissionResultCode = resultCode;
    mediaProjectionPermissionResultData = data;
    startCall();
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(this) && getIntent().getBooleanExtra(EXTRA_CAMERA2, true);
  }

  private boolean captureToTexture() {
    return getIntent().getBooleanExtra(EXTRA_CAPTURETOTEXTURE_ENABLED, false);
  }

  private @Nullable VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    // First, try to find front facing camera
    // 먼저 전면 카메라를 찾습니다.
    Logging.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    // Front facing camera not found, try something else
    // 전면 카메라를 찾을 수 없습니다. 다른 방법을 시도해 보십시오.
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  @TargetApi(21)
  private @Nullable VideoCapturer createScreenCapturer() {
    if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
      reportError("User didn't give permission to capture the screen.");
      return null;
    }
    return new ScreenCapturerAndroid(
        mediaProjectionPermissionResultData, new MediaProjection.Callback() {
      @Override
      public void onStop() {
        reportError("User revoked permission to capture the screen.");
      }
    });
  }

  // Activity interfaces
  @Override
  public void onStop() {
    super.onStop();
    activityRunning = false;
    // Don't stop the video when using screencapture to allow user to show other apps to the remote end.
    // 스크린캡처 사용 시 동영상을 중지하지 마십시오. 다른 앱을 원격으로 표시할 수 있습니다.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.stopVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.pause();
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    activityRunning = true;
    // Video is not paused for screencapture. See onPause.
    // 화면 캡처를 위해 비디오가 일시 중지되지 않습니다. 일시 중지(OnPause)를 참조합니다.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.startVideoSource();
    }
    if (cpuMonitor != null) {
      cpuMonitor.resume();
    }
  }

  @Override
  protected void onDestroy() {
    Thread.setDefaultUncaughtExceptionHandler(null);
    disconnect();
    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
    super.onDestroy();
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override
  public void onCallHangUp() {
    disconnect();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    fullscreenRenderer.setScalingType(scalingType);
  }

  @Override
  public void onCaptureFormatChange(int width, int height, int framerate) {
    if (peerConnectionClient != null) {
      peerConnectionClient.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override
  public boolean onToggleMic() {
    if (peerConnectionClient != null) {
      micEnabled = !micEnabled;
      peerConnectionClient.setAudioEnabled(micEnabled);
    }
    return micEnabled;
  }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {
    if (!connected || !callFragment.isAdded()) {
      return;
    }
    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void startCall() {
    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }
    callStartedTimeMs = System.currentTimeMillis();

    // Start room connection.
    logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    appRtcClient.connectToRoom(roomConnectionParameters);

    // Create and audio manager that will take care of audio routing, audio modes, audio device enumeration etc.
    // 오디오 라우팅, 오디오 모드, 오디오 장치 열거 등을 처리할 오디오 관리자를 만듭니다.
    audioManager = AppRTCAudioManager.create(getApplicationContext());
    // Store existing audio settings and change audio mode to MODE_IN_COMMUNICATION for best possible VoIP performance.
    // 기존 오디오 설정을 저장하고 최상의 VoIP 성능을 위해 오디오 모드를 MODE_IN_COMMUNICATION 으로 변경합니다.
    Log.d(TAG, "Starting the audio manager...");
    // This method will be called each time the number of available audio devices has changed.
    // 이 방법은 사용 가능한 오디오 장치의 수가 변경될 때마다 호출됩니다.
    audioManager.start((audioDevice, availableAudioDevices) -> onAudioManagerDevicesChanged(audioDevice, availableAudioDevices));
  }

  // Should be called from UI thread
  private void callConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "Call connected: delay=" + delta + "ms");
    if (peerConnectionClient == null || isError) {
      Log.w(TAG, "Call is connected in closed or error state");
      return;
    }
    // Enable statistics callback.
    peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
    setSwappedFeeds(false /* isSwappedFeeds */);
  }

  // This method is called when the audio manager reports audio device change, e.g. from wired headset to speakerphone.
  // 이 방법은 오디오 관리자가 오디오 장치 변경을 보고할 때 호출됩니다(예: 유선 헤드셋에서 스피커폰으로).
  private void onAudioManagerDevicesChanged(
      final AudioDevice device, final Set<AudioDevice> availableDevices) {
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);
    // TODO(henrika): add callback handler.
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  // 원격 리소스에서 연결을 끊고 로컬 리소스를 폐기한 후 종료합니다.
  private void disconnect() {
    activityRunning = false;
    remoteProxyRenderer.setTarget(null);
    localProxyVideoSink.setTarget(null);
    if (appRtcClient != null) {
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
    }
    if (pipRenderer != null) {
      pipRenderer.release();
      pipRenderer = null;
    }
    if (videoFileRenderer != null) {
      videoFileRenderer.release();
      videoFileRenderer = null;
    }
    if (fullscreenRenderer != null) {
      fullscreenRenderer.release();
      fullscreenRenderer = null;
    }
    if (peerConnectionClient != null) {
      peerConnectionClient.close();
      peerConnectionClient = null;
    }
    if (audioManager != null) {
      audioManager.stop();
      audioManager = null;
    }
    if (connected && !isError) {
      setResult(RESULT_OK);
    } else {
      setResult(RESULT_CANCELED);
    }
    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
          .setTitle(getText(R.string.channel_error_title))
          .setMessage(errorMessage)
          .setCancelable(false)
          .setNeutralButton(R.string.ok,
                  (dialog, id) -> {
                    dialog.cancel();
                    disconnect();
                  })
          .create()
          .show();
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  private void reportError(final String description) {
    runOnUiThread(() -> {
      if (!isError) {
        isError = true;
        disconnectWithErrorMessage(description);
      }
    });
  }

  private @Nullable VideoCapturer createVideoCapturer() {
    final VideoCapturer videoCapturer;
    String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
    if (videoFileAsCamera != null) {
      try {
        videoCapturer = new FileVideoCapturer(videoFileAsCamera);
      } catch (IOException e) {
        reportError("Failed to open video file for emulated camera");
        return null;
      }
    } else if (screencaptureEnabled) {
      return createScreenCapturer();
    } else if (useCamera2()) {
      if (!captureToTexture()) {
        reportError(getString(R.string.camera2_texture_only_error));
        return null;
      }

      Logging.d(TAG, "Creating capturer using camera2 API.");
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    } else {
      Logging.d(TAG, "Creating capturer using camera1 API.");
      videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
    }
    if (videoCapturer == null) {
      reportError("Failed to open camera");
      return null;
    }
    return videoCapturer;
  }

  private void setSwappedFeeds(boolean isSwappedFeeds) {
    Logging.d(TAG, "setSwappedFeeds: " + isSwappedFeeds);
    this.isSwappedFeeds = isSwappedFeeds;
    localProxyVideoSink.setTarget(isSwappedFeeds ? fullscreenRenderer : pipRenderer);
    remoteProxyRenderer.setTarget(isSwappedFeeds ? pipRenderer : fullscreenRenderer);
    fullscreenRenderer.setMirror(isSwappedFeeds);
    pipRenderer.setMirror(!isSwappedFeeds);
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and are routed to UI thread.
  // 모든 콜백은 웹 소켓 신호 루퍼 스레드에서 호출되고 UI 스레드로 라우팅됩니다.
  private void onConnectedToRoomInternal(final SignalingParameters params) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    signalingParameters = params;
    logAndToast("Creating peer connection, delay=" + delta + "ms");
    VideoCapturer videoCapturer = null;
    if (peerConnectionParameters.videoCallEnabled) {
      videoCapturer = createVideoCapturer();
    }
    peerConnectionClient.createPeerConnection(
        localProxyVideoSink, remoteSinks, videoCapturer, signalingParameters);

    if (signalingParameters.initiator) {
      logAndToast("Creating OFFER...");
      // Create offer. Offer SDP will be sent to answering client in PeerConnectionEvents.onLocalDescription event.
      // 오퍼링을 작성합니다. PeerConnectionEvents.onLocalDescription 이벤트의 응답 클라이언트로 SDP가 전송됩니다.
      peerConnectionClient.createOffer();
    } else {
      if (params.offerSdp != null) {
        peerConnectionClient.setRemoteDescription(params.offerSdp);
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in PeerConnectionEvents.onLocalDescription event.
        // 답변을 작성합니다. PeerConnectionEvents.onLocalDescription 이벤트의 오퍼링 클라이언트에 응답 SDP가 전송됩니다.
        peerConnectionClient.createAnswer();
      }
      if (params.iceCandidates != null) {
        // Add remote ICE candidates from room.
        for (IceCandidate iceCandidate : params.iceCandidates) {
          peerConnectionClient.addRemoteIceCandidate(iceCandidate);
        }
      }
    }
  }

  @Override
  public void onConnectedToRoom(final SignalingParameters params) {
    runOnUiThread(() -> onConnectedToRoomInternal(params));
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
        return;
      }
      logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
      peerConnectionClient.setRemoteDescription(sdp);
      if (!signalingParameters.initiator) {
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in PeerConnectionEvents.onLocalDescription event.
        // 답변을 작성합니다. PeerConnectionEvents.onLocalDescription 이벤트의 오퍼링 클라이언트에 응답 SDP가 전송됩니다.
        peerConnectionClient.createAnswer();
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.addRemoteIceCandidate(candidate);
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(() -> {
      if (peerConnectionClient == null) {
        Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
        return;
      }
      peerConnectionClient.removeRemoteIceCandidates(candidates);
    });
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(() -> {
      logAndToast("Remote end hung up; dropping PeerConnection");
      disconnect();
    });
  }

  @Override
  public void onChannelError(final String description) {
    reportError(description);
  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and are routed to UI thread.
  // 로컬 피어 연결 SDP 및 ICE 후보를 원격 파티로 보냅니다.
  // 모든 콜백은 피어 연결 클라이언트 루퍼 스레드에서 호출되고 UI 스레드로 라우팅됩니다.
  @Override
  public void onLocalDescription(final SessionDescription sdp) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
        if (signalingParameters.initiator) {
          appRtcClient.sendOfferSdp(sdp);
        } else {
          appRtcClient.sendAnswerSdp(sdp);
        }
      }
      if (peerConnectionParameters.videoMaxBitrate > 0) {
        Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
        peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
      }
    });
  }

  @Override
  public void onIceCandidate(final IceCandidate candidate) {
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onIceCandidatesRemoved(final IceCandidate[] candidates) {
    runOnUiThread(() -> {
      if (appRtcClient != null) {
        appRtcClient.sendLocalIceCandidateRemovals(candidates);
      }
    });
  }

  @Override
  public void onIceConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> logAndToast("ICE connected, delay=" + delta + "ms"));
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(() -> logAndToast("ICE disconnected"));
  }

  @Override
  public void onConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(() -> {
      logAndToast("DTLS connected, delay=" + delta + "ms");
      connected = true;
      callConnected();
    });
  }

  @Override
  public void onDisconnected() {
    runOnUiThread(() -> {
      logAndToast("DTLS disconnected");
      connected = false;
      disconnect();
    });
  }

  @Override
  public void onPeerConnectionClosed() {}

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    runOnUiThread(() -> {
       if (!isError && connected) {
        hudFragment.updateEncoderStatistics(reports);
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    reportError(description);
  }
}
