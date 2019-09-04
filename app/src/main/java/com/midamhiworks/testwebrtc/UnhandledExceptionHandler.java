/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.midamhiworks.testwebrtc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.util.Log;
import android.util.TypedValue;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Singleton helper: install a default unhandled exception handler which shows
 * an informative dialog and kills the app.  Useful for apps whose
 * error-handling consists of throwing RuntimeExceptions.
 * NOTE: almost always more useful to
 * Thread.setDefaultUncaughtExceptionHandler() rather than
 * Thread.setUncaughtExceptionHandler(), to apply to background threads as well.
 *
 * Singleton 도우미: 처리되지 않은 기본 예외 처리기를 설치합니다.
 * 정보를 제공하는 대화로 앱을 죽입니다. 다음과 같은 기능을 가진 앱에 유용합니다.
 * 오류 처리는 런타임을 던지는 것으로 구성됩니다. 예외입니다.
 * 참고: 거의 항상 더 유용합니다.
 * Thread.setDefaultUncaughtExceptionHandler() 대신 다음을 수행합니다.
 * Thread.setUncaughExceptionHandler()를 백그라운드 스레드에도 적용합니다.
 */

/*
UnhandledExceptionHandler (필수)

- 기본적인 예외상황 외, 기타 예외상황(통신의 문제상 등)이 발생했을 때 다이얼로그로 보여주는 클래스.

- 주로 Throwable 을 통해 야기된 오류를 여기서 보여줌
 */
public class UnhandledExceptionHandler implements Thread.UncaughtExceptionHandler {
  private static final String TAG = "AppRTCMobileActivity";
  private final Activity activity;

  public UnhandledExceptionHandler(final Activity activity) {
    this.activity = activity;
  }

  @Override
  public void uncaughtException(Thread unusedThread, final Throwable e) {
    activity.runOnUiThread(() -> {
      String title = "Fatal error: " + getTopLevelCauseMessage(e);
      String msg = getRecursiveStackTrace(e);
      TextView errorView = new TextView(activity);
      errorView.setText(msg);
      errorView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8);
      ScrollView scrollingContainer = new ScrollView(activity);
      scrollingContainer.addView(errorView);
      Log.e(TAG, title + "\n\n" + msg);
      DialogInterface.OnClickListener listener = (dialog, which) -> {
        dialog.dismiss();
        System.exit(1);
      };
      AlertDialog.Builder builder = new AlertDialog.Builder(activity);
      builder.setTitle(title)
          .setView(scrollingContainer)
          .setPositiveButton("Exit", listener)
          .show();
    });
  }

  // Returns the Message attached to the original Cause of |t|.
  // 원래 원인인 |t|에 첨부된 메시지를 반환합니다.
  private static String getTopLevelCauseMessage(Throwable t) {
    Throwable topLevelCause = t;
    while (topLevelCause.getCause() != null) {
      topLevelCause = topLevelCause.getCause();
    }
    return topLevelCause.getMessage();
  }

  // Returns a human-readable String of the stacktrace in |t|, recursively through all Causes that led to |t|.
  // |t|에서 사람이 읽을 수 있는 스택 추적 문자열을 반환합니다. |t|로 이어지는 모든 원인을 반복해서 설명합니다.
  private static String getRecursiveStackTrace(Throwable t) {
    StringWriter writer = new StringWriter();
    t.printStackTrace(new PrintWriter(writer));
    return writer.toString();
  }
}
