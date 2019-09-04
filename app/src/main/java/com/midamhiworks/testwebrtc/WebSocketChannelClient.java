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

import android.os.Handler;
import android.util.Log;

import androidx.annotation.Nullable;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import com.midamhiworks.testwebrtc.util.AsyncHttpURLConnection;
import com.midamhiworks.testwebrtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * WebSocket client implementation.
 *
 * <p>All public methods should be called from a looper executor thread
 * passed in a constructor, otherwise exception will be thrown.
 * All events are dispatched on the same thread.
 *
 * WebSocket 클라이언트 구현입니다.
 *
 * 모든 공개 방법은 루퍼 실행자 스레드에서 호출해야 합니다.
 * 생성자를 통과하지 않으면 예외가 발생합니다.
 * 모든 이벤트는 동일한 스레드로 발송됩니다.
 */

/*
WebSocketChannelClient (필수)

- https://appr.tc 를 사용하여 영상통화를 하기 위해 필요한 클래스

- autobahn 라이브러리를 사용하여 웹 통신을 진행 함.

- WSS, WSS POST, TURN ,STUN 서버를 사용하여 가장 최적화 된 경로를 찾도록 함.
 */
public class WebSocketChannelClient {
  private static final String TAG = "WSChannelRTCClient";
  private static final int CLOSE_TIMEOUT = 1000;
  private final WebSocketChannelEvents events;
  private final Handler handler;
  private WebSocketConnection ws;
  private String wsServerUrl;
  private String postServerUrl;
  @Nullable
  private String roomID;
  @Nullable
  private String clientID;
  private WebSocketConnectionState state;
  // Do not remove this member variable. If this is removed, the observer gets garbage collected and this causes test breakages.
  // 이 멤버 변수를 제거하지 않습니다. 이를 제거하면 관찰자가 가비지를 수집하여 테스트가 중단됩니다.
  private WebSocketObserver wsObserver;
  private final Object closeEventLock = new Object();
  private boolean closeEvent;
  // WebSocket send queue. Messages are added to the queue when WebSocket client is not registered and are consumed in register() call.
  // WebSocket 송신 대기열입니다. WebSocket 클라이언트가 등록되어 있지 않고 register() 호출에 사용되는 경우 메시지가 대기열에 추가됩니다.
  private final List<String> wsSendQueue = new ArrayList<>();

  /**
   * Possible WebSocket connection states.
   */
  public enum WebSocketConnectionState { NEW, CONNECTED, REGISTERED, CLOSED, ERROR }

  /**
   * Callback interface for messages delivered on WebSocket.
   * All events are dispatched from a looper executor thread.
   */
  public interface WebSocketChannelEvents {
    void onWebSocketMessage(final String message);
    void onWebSocketClose();
    void onWebSocketError(final String description);
  }

  public WebSocketChannelClient(Handler handler, WebSocketChannelEvents events) {
    this.handler = handler;
    this.events = events;
    roomID = null;
    clientID = null;
    state = WebSocketConnectionState.NEW;
  }

  public WebSocketConnectionState getState() {
    return state;
  }

  public void connect(final String wsUrl, final String postUrl) {
    checkIfCalledOnValidThread();
    if (state != WebSocketConnectionState.NEW) {
      Log.e(TAG, "WebSocket is already connected.");
      return;
    }
    wsServerUrl = wsUrl;
    postServerUrl = postUrl;
    closeEvent = false;

    Log.d(TAG, "Connecting WebSocket to: " + wsUrl + ". Post URL: " + postUrl);
    ws = new WebSocketConnection();
    wsObserver = new WebSocketObserver();
    try {
      ws.connect(new URI(wsServerUrl), wsObserver);
    } catch (URISyntaxException e) {
      reportError("URI error: " + e.getMessage());
    } catch (WebSocketException e) {
      reportError("WebSocket connection error: " + e.getMessage());
    }
  }

  public void register(final String roomID, final String clientID) {
    checkIfCalledOnValidThread();
    this.roomID = roomID;
    this.clientID = clientID;
    if (state != WebSocketConnectionState.CONNECTED) {
      Log.w(TAG, "WebSocket register() in state " + state);
      return;
    }
    Log.d(TAG, "Registering WebSocket for room " + roomID + ". ClientID: " + clientID);
    JSONObject json = new JSONObject();
    try {
      json.put("cmd", "register");
      json.put("roomid", roomID);
      json.put("clientid", clientID);
      Log.d(TAG, "C->WSS: " + json.toString());
      ws.sendTextMessage(json.toString());
      state = WebSocketConnectionState.REGISTERED;
      // Send any previously accumulated messages.
      for (String sendMessage : wsSendQueue) {
        send(sendMessage);
      }
      wsSendQueue.clear();
    } catch (JSONException e) {
      reportError("WebSocket register JSON error: " + e.getMessage());
    }
  }

  public void send(String message) {
    checkIfCalledOnValidThread();
    switch (state) {
      case NEW:
      case CONNECTED:
        // Store outgoing messages and send them after websocket client is registered.
        // 발신 메시지를 저장하고 웹 소켓 클라이언트가 등록되면 발송합니다.
        Log.d(TAG, "WS ACC: " + message);
        wsSendQueue.add(message);
        return;
      case ERROR:
      case CLOSED:
        Log.e(TAG, "WebSocket send() in error or closed state : " + message);
        return;
      case REGISTERED:
        JSONObject json = new JSONObject();
        try {
          json.put("cmd", "send");
          json.put("msg", message);
          message = json.toString();
          Log.d(TAG, "C->WSS: " + message);
          ws.sendTextMessage(message);
        } catch (JSONException e) {
          reportError("WebSocket send JSON error: " + e.getMessage());
        }
        break;
    }
  }

  // This call can be used to send WebSocket messages before WebSocket connection is opened.
  // WebSocket 연결을 열기 전에 WebSocket 메시지를 보내는 데 이 통화를 사용할 수 있습니다.
  public void post(String message) {
    checkIfCalledOnValidThread();
    sendWSSMessage("POST", message);
  }

  public void disconnect(boolean waitForComplete) {
    checkIfCalledOnValidThread();
    Log.d(TAG, "Disconnect WebSocket. State: " + state);
    if (state == WebSocketConnectionState.REGISTERED) {
      // Send "bye" to WebSocket server.
      send("{\"type\": \"bye\"}");
      state = WebSocketConnectionState.CONNECTED;
      // Send http DELETE to http WebSocket server.
      sendWSSMessage("DELETE", "");
    }
    // Close WebSocket in CONNECTED or ERROR states only.
    if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
      ws.disconnect();
      state = WebSocketConnectionState.CLOSED;

      // Wait for websocket close event to prevent websocket library from sending any pending messages to deleted looper thread.
      // Webocket Library가 삭제된 looper 스레드에 보류 중인 메시지를 보내지 못하도록 웹 소켓 닫기 이벤트를 기다립니다.
      if (waitForComplete) {
        synchronized (closeEventLock) {
          while (!closeEvent) {
            try {
              closeEventLock.wait(CLOSE_TIMEOUT);
              break;
            } catch (InterruptedException e) {
              Log.e(TAG, "Wait error: " + e.toString());
            }
          }
        }
      }
    }
    Log.d(TAG, "Disconnecting WebSocket done.");
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    handler.post(() -> {
      if (state != WebSocketConnectionState.ERROR) {
        state = WebSocketConnectionState.ERROR;
        events.onWebSocketError(errorMessage);
      }
    });
  }

  // Asynchronously send POST/DELETE to WebSocket server.
  // POST/DELETE를 WebSocket 서버로 비동기식으로 전송합니다.
  private void sendWSSMessage(final String method, final String message) {
    String postUrl = postServerUrl + "/" + roomID + "/" + clientID;
    Log.d(TAG, "WS " + method + " : " + postUrl + " : " + message);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection(method, postUrl, message, new AsyncHttpEvents() {
          @Override
          public void onHttpError(String errorMessage) {
            reportError("WS " + method + " error: " + errorMessage);
          }

          @Override
          public void onHttpComplete(String response) {}
        });
    httpConnection.send();
  }

  // Helper method for debugging purposes. Ensures that WebSocket method is called on a looper thread.
  // 디버깅을 위한 도우미 방법입니다. 루퍼 스레드에서 WebSocket 메서드를 호출합니다.
  private void checkIfCalledOnValidThread() {
    if (Thread.currentThread() != handler.getLooper().getThread()) {
      throw new IllegalStateException("WebSocket method is not called on valid thread");
    }
  }

  private class WebSocketObserver implements WebSocketConnectionObserver {
    @Override
    public void onOpen() {
      Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
      handler.post(() -> {
        state = WebSocketConnectionState.CONNECTED;
        // Check if we have pending register request.
        if (roomID != null && clientID != null) {
          register(roomID, clientID);
        }
      });
    }

    @Override
    public void onClose(WebSocketCloseNotification code, String reason) {
      Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: "
              + state);
      synchronized (closeEventLock) {
        closeEvent = true;
        closeEventLock.notify();
      }
      handler.post(() -> {
        if (state != WebSocketConnectionState.CLOSED) {
          state = WebSocketConnectionState.CLOSED;
          events.onWebSocketClose();
        }
      });
    }

    @Override
    public void onTextMessage(String payload) {
      Log.d(TAG, "WSS->C: " + payload);
      final String message = payload;
      handler.post(() -> {
        if (state == WebSocketConnectionState.CONNECTED
            || state == WebSocketConnectionState.REGISTERED) {
          events.onWebSocketMessage(message);
        }
      });
    }

    @Override
    public void onRawTextMessage(byte[] payload) {}

    @Override
    public void onBinaryMessage(byte[] payload) {}
  }
}
