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
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.Nullable;

import com.midamhiworks.testwebrtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import com.midamhiworks.testwebrtc.WebSocketChannelClient.WebSocketChannelEvents;
import com.midamhiworks.testwebrtc.WebSocketChannelClient.WebSocketConnectionState;
import com.midamhiworks.testwebrtc.util.AsyncHttpURLConnection;
import com.midamhiworks.testwebrtc.util.AsyncHttpURLConnection.AsyncHttpEvents;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 *
 * https://appr.tc과 채팅하기 위한 신호를 협상합니다.
 * apprtc AppEngine 웹 앱의 클라이언트 <->서버 세부 정보를 사용합니다.
 *
 * <p>사용 방법: 메시지 처리기를 등록하는 이 개체의 인스턴스를 만듭니다.
 * connectToRoom()을 호출합니다. 룸 연결이 설정되면 해당 룸에 연결할 수 있습니다.
 * onConnectedToRoom() 콜백을 룸 매개 변수와 함께 호출합니다.
 * 다른 당사자에게 메시지를 보낼 수 있습니다(현지 얼음 후보 및 응답 SDP).
 * WebSocket 연결이 설정된 후 전송됩니다.
 */

/*
WebSocketRTCClient (필수)

- 웹소켓을 사용하여 영상통화를 하기 위해 필요한 클래스

- AppRTCClient 인터페이스 및 WebSocketChannelClient 클래스를 사용하여 웹 통신을 진행함.
 */
public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelEvents {
  private static final String TAG = "WSRTCClient";
  private static final String ROOM_JOIN = "join";
  private static final String ROOM_MESSAGE = "message";
  private static final String ROOM_LEAVE = "leave";

  private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

  private enum MessageType { MESSAGE, LEAVE }

  private final Handler handler;
  private boolean initiator;
  private SignalingEvents events;
  private WebSocketChannelClient wsClient;
  private ConnectionState roomState;
  private RoomConnectionParameters connectionParameters;
  private String messageUrl;
  private String leaveUrl;

  public WebSocketRTCClient(SignalingEvents events) {
    this.events = events;
    roomState = ConnectionState.NEW;
    final HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }

  // --------------------------------------------------------------------
  // AppRTCClient interface implementation.
  // Asynchronously connect to an AppRTC room URL using supplied connection parameters, retrieves room parameters and connect to WebSocket server.
  // AppRTCClient 인터페이스를 구현합니다.
   // 제공된 연결 매개 변수를 사용하여 AppRTC 룸 URL에 비동기식으로 연결하고 룸 매개 변수를 검색한 후 WebSocket 서버에 연결합니다.
  @Override
  public void connectToRoom(RoomConnectionParameters connectionParameters) {
    this.connectionParameters = connectionParameters;
    handler.post(() -> connectToRoomInternal());
  }

  @Override
  public void disconnectFromRoom() {
    handler.post(() -> {
      disconnectFromRoomInternal();
      handler.getLooper().quit();
    });
  }

  // Connects to room - function runs on a local looper thread.
  private void connectToRoomInternal() {
    String connectionUrl = getConnectionUrl(connectionParameters);
    Log.d(TAG, "Connect to room: " + connectionUrl);
    roomState = ConnectionState.NEW;
    wsClient = new WebSocketChannelClient(handler, this);

    RoomParametersFetcherEvents callbacks = new RoomParametersFetcherEvents() {
      @Override
      public void onSignalingParametersReady(final SignalingParameters params) {
        WebSocketRTCClient.this.handler.post(() -> WebSocketRTCClient.this.signalingParametersReady(params));
      }

      @Override
      public void onSignalingParametersError(String description) {
        WebSocketRTCClient.this.reportError(description);
      }
    };

    new RoomParametersFetcher(connectionUrl, null, callbacks).makeRequest();
  }

  // Disconnect from room and send bye messages - runs on a local looper thread.
  private void disconnectFromRoomInternal() {
    Log.d(TAG, "Disconnect. Room state: " + roomState);
    if (roomState == ConnectionState.CONNECTED) {
      Log.d(TAG, "Closing room.");
      sendPostMessage(MessageType.LEAVE, leaveUrl, null);
    }
    roomState = ConnectionState.CLOSED;
    if (wsClient != null) {
      wsClient.disconnect(true);
    }
  }

  // Helper functions to get connection, post message and leave message URLs
  // 연결을 얻고 메시지를 게시하고 메시지 URL을 남기는 도우미 기능입니다.
  private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
    return connectionParameters.roomUrl + "/" + ROOM_JOIN + "/" + connectionParameters.roomId
        + getQueryString(connectionParameters);
  }

  private String getMessageUrl(
      RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
    return connectionParameters.roomUrl + "/" + ROOM_MESSAGE + "/" + connectionParameters.roomId
        + "/" + signalingParameters.clientId + getQueryString(connectionParameters);
  }

  private String getLeaveUrl(
      RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
    return connectionParameters.roomUrl + "/" + ROOM_LEAVE + "/" + connectionParameters.roomId + "/"
        + signalingParameters.clientId + getQueryString(connectionParameters);
  }

  private String getQueryString(RoomConnectionParameters connectionParameters) {
    if (connectionParameters.urlParameters != null) {
      return "?" + connectionParameters.urlParameters;
    } else {
      return "";
    }
  }

  // Callback issued when room parameters are extracted. Runs on local looper thread.
  // 룸 매개변수를 추출할 때 콜백이 발급됩니다. 로컬 루퍼 스레드에서 실행됩니다.
  private void signalingParametersReady(final SignalingParameters signalingParameters) {
    Log.d(TAG, "Room connection completed.");
    if (connectionParameters.loopback
        && (!signalingParameters.initiator || signalingParameters.offerSdp != null)) {
      reportError("Loopback room is busy.");
      return;
    }
    if (!connectionParameters.loopback && !signalingParameters.initiator
        && signalingParameters.offerSdp == null) {
      Log.w(TAG, "No offer SDP in room response.");
    }
    initiator = signalingParameters.initiator;
    messageUrl = getMessageUrl(connectionParameters, signalingParameters);
    leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
    Log.d(TAG, "Message URL: " + messageUrl);
    Log.d(TAG, "Leave URL: " + leaveUrl);
    roomState = ConnectionState.CONNECTED;

    // Fire connection and signaling parameters events.
    // 연결 및 신호 파라미터 이벤트를 실행합니다.
    events.onConnectedToRoom(signalingParameters);

    // Connect and register WebSocket client.
    // WebSocket 클라이언트를 연결하고 등록합니다.
    wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
    wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
  }

  // Send local offer SDP to the other participant.
  // 다른 참가자에게 로컬 오퍼링 SDP를 보냅니다.
  @Override
  public void sendOfferSdp(final SessionDescription sdp) {
    handler.post(() -> {
      if (roomState != ConnectionState.CONNECTED) {
        reportError("Sending offer SDP in non connected state.");
        return;
      }
      JSONObject json = new JSONObject();
      jsonPut(json, "sdp", sdp.description);
      jsonPut(json, "type", "offer");
      sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
      if (connectionParameters.loopback) {
        // In loopback mode rename this offer to answer and route it back.
        SessionDescription sdpAnswer = new SessionDescription(
            SessionDescription.Type.fromCanonicalForm("answer"), sdp.description);
        events.onRemoteDescription(sdpAnswer);
      }
    });
  }

  // Send local answer SDP to the other participant.
  // 다른 참가자에게 로컬 응답 SDP를 보냅니다.
  @Override
  public void sendAnswerSdp(final SessionDescription sdp) {
    handler.post(() -> {
      if (connectionParameters.loopback) {
        Log.e(TAG, "Sending answer in loopback mode.");
        return;
      }
      JSONObject json = new JSONObject();
      jsonPut(json, "sdp", sdp.description);
      jsonPut(json, "type", "answer");
      wsClient.send(json.toString());
    });
  }

  // Send Ice candidate to the other participant.
  @Override
  public void sendLocalIceCandidate(final IceCandidate candidate) {
    handler.post(() -> {
      JSONObject json = new JSONObject();
      jsonPut(json, "type", "candidate");
      jsonPut(json, "label", candidate.sdpMLineIndex);
      jsonPut(json, "id", candidate.sdpMid);
      jsonPut(json, "candidate", candidate.sdp);
      if (initiator) {
        // Call initiator sends ice candidates to GAE server.
        if (roomState != ConnectionState.CONNECTED) {
          reportError("Sending ICE candidate in non connected state.");
          return;
        }
        sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
        if (connectionParameters.loopback) {
          events.onRemoteIceCandidate(candidate);
        }
      } else {
        // Call receiver sends ice candidates to websocket server.
        wsClient.send(json.toString());
      }
    });
  }

  // Send removed Ice candidates to the other participant.
  @Override
  public void sendLocalIceCandidateRemovals(final IceCandidate[] candidates) {
    handler.post(() -> {
      JSONObject json = new JSONObject();
      jsonPut(json, "type", "remove-candidates");
      JSONArray jsonArray = new JSONArray();
      for (final IceCandidate candidate : candidates) {
        jsonArray.put(toJsonCandidate(candidate));
      }
      jsonPut(json, "candidates", jsonArray);
      if (initiator) {
        // Call initiator sends ice candidates to GAE server.
        if (roomState != ConnectionState.CONNECTED) {
          reportError("Sending ICE candidate removals in non connected state.");
          return;
        }
        sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
        if (connectionParameters.loopback) {
          events.onRemoteIceCandidatesRemoved(candidates);
        }
      } else {
        // Call receiver sends ice candidates to websocket server.
        wsClient.send(json.toString());
      }
    });
  }

  // --------------------------------------------------------------------
  // WebSocketChannelEvents interface implementation.
  // All events are called by WebSocketChannelClient on a local looper thread (passed to WebSocket client constructor).
  // WebSocketChannelEvents 인터페이스 구현입니다.
  // 모든 이벤트는 WebSocketChannelClient 가 로컬 루퍼 스레드를 통해 호출합니다(WebSocket 클라이언트 생성자에게 전달됨).
  @Override
  public void onWebSocketMessage(final String msg) {
    if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {
      Log.e(TAG, "Got WebSocket message in non registered state.");
      return;
    }
    try {
      JSONObject json = new JSONObject(msg);
      String msgText = json.getString("msg");
      String errorText = json.optString("error");
      if (msgText.length() > 0) {
        json = new JSONObject(msgText);
        String type = json.optString("type");
        if (type.equals("candidate")) {
          events.onRemoteIceCandidate(toJavaCandidate(json));
        } else if (type.equals("remove-candidates")) {
          JSONArray candidateArray = json.getJSONArray("candidates");
          IceCandidate[] candidates = new IceCandidate[candidateArray.length()];
          for (int i = 0; i < candidateArray.length(); ++i) {
            candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
          }
          events.onRemoteIceCandidatesRemoved(candidates);
        } else if (type.equals("answer")) {
          if (initiator) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
            events.onRemoteDescription(sdp);
          } else {
            reportError("Received answer for call initiator: " + msg);
          }
        } else if (type.equals("offer")) {
          if (!initiator) {
            SessionDescription sdp = new SessionDescription(
                SessionDescription.Type.fromCanonicalForm(type), json.getString("sdp"));
            events.onRemoteDescription(sdp);
          } else {
            reportError("Received offer for call receiver: " + msg);
          }
        } else if (type.equals("bye")) {
          events.onChannelClose();
        } else {
          reportError("Unexpected WebSocket message: " + msg);
        }
      } else {
        if (errorText != null && errorText.length() > 0) {
          reportError("WebSocket error message: " + errorText);
        } else {
          reportError("Unexpected WebSocket message: " + msg);
        }
      }
    } catch (JSONException e) {
      reportError("WebSocket message JSON parsing error: " + e.toString());
    }
  }

  @Override
  public void onWebSocketClose() {
    events.onChannelClose();
  }

  @Override
  public void onWebSocketError(String description) {
    reportError("WebSocket error: " + description);
  }

  // --------------------------------------------------------------------
  // Helper functions.
  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    handler.post(() -> {
      if (roomState != ConnectionState.ERROR) {
        roomState = ConnectionState.ERROR;
        events.onChannelError(errorMessage);
      }
    });
  }

  // Put a |key|->|value| mapping in |json|.
  private static void jsonPut(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  // Send SDP or ICE candidate to a room server.
  private void sendPostMessage(
      final MessageType messageType, final String url, @Nullable final String message) {
    String logInfo = url;
    if (message != null) {
      logInfo += ". Message: " + message;
    }
    Log.d(TAG, "C->GAE: " + logInfo);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
          @Override
          public void onHttpError(String errorMessage) {
            reportError("GAE POST error: " + errorMessage);
          }

          @Override
          public void onHttpComplete(String response) {
            if (messageType == MessageType.MESSAGE) {
              try {
                JSONObject roomJson = new JSONObject(response);
                String result = roomJson.getString("result");
                if (!result.equals("SUCCESS")) {
                  reportError("GAE POST error: " + result);
                }
              } catch (JSONException e) {
                reportError("GAE POST JSON error: " + e.toString());
              }
            }
          }
        });
    httpConnection.send();
  }

  // Converts a Java candidate to a JSONObject.
  private JSONObject toJsonCandidate(final IceCandidate candidate) {
    JSONObject json = new JSONObject();
    jsonPut(json, "label", candidate.sdpMLineIndex);
    jsonPut(json, "id", candidate.sdpMid);
    jsonPut(json, "candidate", candidate.sdp);
    return json;
  }

  // Converts a JSON candidate to a Java object.
  IceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new IceCandidate(
        json.getString("id"), json.getInt("label"), json.getString("candidate"));
  }
}
