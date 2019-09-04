/*
 *  Copyright 2018 The WebRTC project authors. All Rights Reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc;

import android.media.AudioFormat;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import org.webrtc.audio.JavaAudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule.SamplesReadyCallback;

/**
 * Implements the AudioRecordSamplesReadyCallback interface and writes recorded raw audio samples to an output file.
 *
 * AudioRecordSamplesReadyCallback 인터페이스를 구현하고 기록된 원시 오디오 샘플을 출력 파일에 기록합니다.
 */
/*
 RecordedAudioToFileController (제거)

- Settings 메뉴에서, Save input audio to file 와 관련된 클래스.

- 영상통화 하는 데 필요없다고 판단 함.
 */
public class RecordedAudioToFileController implements SamplesReadyCallback {
  private static final String TAG = "RecordedAudioToFile";
  private static final long MAX_FILE_SIZE_IN_BYTES = 58348800L;

  private final Object lock = new Object();
  private final ExecutorService executor;
  @Nullable
  private OutputStream rawAudioFileOutputStream;
  private boolean isRunning;
  private long fileSizeInBytes;

  public RecordedAudioToFileController(ExecutorService executor) {
    Log.d(TAG, "ctor");
    this.executor = executor;
  }

  /**
   * Should be called on the same executor thread as the one provided at construction.
   * 시공 시 제공된 것과 동일한 실행자 나사산에 대해 호출해야 합니다.
   */
  public boolean start() {
    Log.d(TAG, "start");
    if (!isExternalStorageWritable()) {
      Log.e(TAG, "Writing to external media is not possible");
      return false;
    }
    synchronized (lock) {
      isRunning = true;
    }
    return true;
  }

  /**
   * Should be called on the same executor thread as the one provided at construction.
   * 시공 시 제공된 것과 동일한 실행자 나사산에 대해 호출해야 합니다.
   */
  public void stop() {
    Log.d(TAG, "stop");
    synchronized (lock) {
      isRunning = false;
      if (rawAudioFileOutputStream != null) {
        try {
          rawAudioFileOutputStream.close();
        } catch (IOException e) {
          Log.e(TAG, "Failed to close file with saved input audio: " + e);
        }
        rawAudioFileOutputStream = null;
      }
      fileSizeInBytes = 0;
    }
  }

  // Checks if external storage is available for read and write.
  private boolean isExternalStorageWritable() {
    String state = Environment.getExternalStorageState();
    if (Environment.MEDIA_MOUNTED.equals(state)) {
      return true;
    }
    return false;
  }

  // Utilizes audio parameters to create a file name which contains sufficient information so that the file can be played using an external file player.
  // 오디오 매개 변수를 사용하여 외부 파일 플레이어를 사용하여 파일을 재생할 수 있도록 충분한 정보가 포함된 파일 이름을 생성합니다.
  // Example: /sdcard/recorded_audio_16bits_48000Hz_mono.pcm.
  private void openRawAudioOutputFile(int sampleRate, int channelCount) {
    final String fileName = Environment.getExternalStorageDirectory().getPath() + File.separator
        + "recorded_audio_16bits_" + String.valueOf(sampleRate) + "Hz"
        + ((channelCount == 1) ? "_mono" : "_stereo") + ".pcm";
    final File outputFile = new File(fileName);
    try {
      rawAudioFileOutputStream = new FileOutputStream(outputFile);
    } catch (FileNotFoundException e) {
      Log.e(TAG, "Failed to open audio output file: " + e.getMessage());
    }
    Log.d(TAG, "Opened file for recording: " + fileName);
  }

  // Called when new audio samples are ready.
  @Override
  public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples) {
    // The native audio layer on Android should use 16-bit PCM format.
    if (samples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) {
      Log.e(TAG, "Invalid audio format");
      return;
    }
    synchronized (lock) {
      // Abort early if stop() has been called.
      if (!isRunning) {
        return;
      }
      // Open a new file for the first callback only since it allows us to add audio parameters to the file name.
      // 파일 이름에 오디오 매개 변수를 추가할 수 있으므로 첫 번째 콜백에 대해서만 새 파일을 엽니다.
      if (rawAudioFileOutputStream == null) {
        openRawAudioOutputFile(samples.getSampleRate(), samples.getChannelCount());
        fileSizeInBytes = 0;
      }
    }
    // Append the recorded 16-bit audio samples to the open output file.
    // 기록된 16비트 오디오 샘플을 열린 출력 파일에 추가합니다.
    executor.execute(() -> {
      if (rawAudioFileOutputStream != null) {
        try {
          // Set a limit on max file size. 58348800 bytes corresponds to approximately 10 minutes of recording in mono at 48kHz.
          // 최대 파일 크기에 대한 제한을 설정합니다. 58348800바이트는 48kHz에서 모노 단위로 약 10분 분량의 레코딩에 해당합니다.
          if (fileSizeInBytes < MAX_FILE_SIZE_IN_BYTES) {
            // Writes samples.getData().length bytes to output stream.
            rawAudioFileOutputStream.write(samples.getData());
            fileSizeInBytes += samples.getData().length;
          }
        } catch (IOException e) {
          Log.e(TAG, "Failed to write audio to file: " + e.getMessage());
        }
      }
    });
  }
}
