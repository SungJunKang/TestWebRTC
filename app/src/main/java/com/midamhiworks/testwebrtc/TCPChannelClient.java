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

import android.util.Log;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.concurrent.ExecutorService;
import org.webrtc.ThreadUtils;

/**
 * Replacement for WebSocketChannelClient for direct communication between two IP addresses. Handles
 * the signaling between the two clients using a TCP connection.
 * <p>
 * All public methods should be called from a looper executor thread
 * passed in a constructor, otherwise exception will be thrown.
 * All events are dispatched on the same thread.
 *
 * 두 IP 주소 간의 직접 통신을 위해 WebSocketChannelClient 를 대체합니다. 손잡이가 있습니다.
 * TCP 연결을 사용하는 두 클라이언트 간의 신호입니다.
 * <p>
 * 모든 공개 방법은 루퍼 실행자 스레드에서 호출해야 합니다.
 * 생성자를 통과하지 않으면 예외가 발생합니다.
 * 모든 이벤트는 동일한 스레드로 발송됩니다.
 */

/*
TCPChannelClient (필수)

- TCP Socket 을 통해 P2P 영상통화를 하기 위한 채널 클래스.
 */
public class TCPChannelClient {
  private static final String TAG = "TCPChannelClient";

  private final ExecutorService executor;
  private final ThreadUtils.ThreadChecker executorThreadCheck;
  private final TCPChannelEvents eventListener;
  private TCPSocket socket;

  /**
   * Callback interface for messages delivered on TCP Connection. All callbacks are invoked from the looper executor thread.
   * TCP 연결에서 배달된 메시지에 대한 콜백 인터페이스입니다. 모든 콜백은 러퍼 실행자 스레드에서 호출됩니다.
   */
  public interface TCPChannelEvents {
    void onTCPConnected(boolean server);
    void onTCPMessage(String message);
    void onTCPError(String description);
    void onTCPClose();
  }

  /**
   * Initializes the TCPChannelClient. If IP is a local IP address, starts a listening server on that IP. If not, instead connects to the IP.
   * TCPChannelClient를 초기화합니다. IP가 로컬 IP 주소인 경우 해당 IP에서 수신 서버를 시작합니다. 그렇지 않으면 IP에 연결합니다.
   *
   * @param eventListener Listener that will receive events from the client.
   * @param ip            IP address to listen on or connect to.
   * @param port          Port to listen on or connect to.
   */
  public TCPChannelClient(
      ExecutorService executor, TCPChannelEvents eventListener, String ip, int port) {
    this.executor = executor;
    executorThreadCheck = new ThreadUtils.ThreadChecker();
    executorThreadCheck.detachThread();
    this.eventListener = eventListener;

    InetAddress address;
    try {
      address = InetAddress.getByName(ip);
    } catch (UnknownHostException e) {
      reportError("Invalid IP address.");
      return;
    }

    if (address.isAnyLocalAddress()) {
      socket = new TCPSocketServer(address, port);
    } else {
      socket = new TCPSocketClient(address, port);
    }

    socket.start();
  }

  /**
   * Disconnects the client if not already disconnected. This will fire the onTCPClose event.
   * 아직 연결이 끊어지지 않은 경우 클라이언트의 연결을 끊습니다. OnTCPClose 이벤트가 작동합니다.
   */
  public void disconnect() {
    executorThreadCheck.checkIsOnValidThread();

    socket.disconnect();
  }

  /**
   * Sends a message on the socket.
   *
   * @param message Message to be sent.
   */
  public void send(String message) {
    executorThreadCheck.checkIsOnValidThread();

    socket.send(message);
  }

  /**
   * Helper method for firing onTCPError events. Calls onTCPError on the executor thread.
   * TCPError 이벤트에서 점화하기 위한 도우미 방법입니다. 실행자 스레드에서 onTCPError를 호출합니다.
   */
  private void reportError(final String message) {
    Log.e(TAG, "TCP Error: " + message);
    executor.execute(() -> eventListener.onTCPError(message));
  }

  /**
   * Base class for server and client sockets. Contains a listening thread that will call eventListener.onTCPMessage on new messages.
   * 서버 및 클라이언트 소켓의 기본 클래스입니다. 새 메시지에서 eventListener.onTCPMessage 를 호출할 듣기 스레드가 포함되어 있습니다.
   */
  private abstract class TCPSocket extends Thread {
    // Lock for editing out and rawSocket
    protected final Object rawSocketLock;
    @Nullable
    private PrintWriter out;
    @Nullable
    private Socket rawSocket;

    /**
     * Connect to the peer, potentially a slow operation.
     * 피어에 연결하면 작업이 느려질 수 있습니다.
     *
     * @return Socket connection, null if connection failed.
     */
    @Nullable
    public abstract Socket connect();

    /** Returns true if sockets is a server rawSocket. */
    public abstract boolean isServer();

    TCPSocket() {
      rawSocketLock = new Object();
    }

    /**
     * The listening thread.
     */
    @Override
    public void run() {
      Log.d(TAG, "Listening thread started...");

      // Receive connection to temporary variable first, so we don't block.
      // 임시 변수에 대한 연결을 먼저 수신하여 차단하지 않습니다.
      Socket tempSocket = connect();
      BufferedReader in;

      Log.d(TAG, "TCP connection established.");

      synchronized (rawSocketLock) {
        if (rawSocket != null) {
          Log.e(TAG, "Socket already existed and will be replaced.");
        }

        rawSocket = tempSocket;

        // Connecting failed, error has already been reported, just exit.
        // 연결하지 못했습니다. 오류가 이미 보고되었습니다. 그냥 종료하십시오.
        if (rawSocket == null) {
          return;
        }

        try {
          out = new PrintWriter(
              new OutputStreamWriter(rawSocket.getOutputStream(), Charset.forName("UTF-8")), true);
          in = new BufferedReader(
              new InputStreamReader(rawSocket.getInputStream(), Charset.forName("UTF-8")));
        } catch (IOException e) {
          reportError("Failed to open IO on rawSocket: " + e.getMessage());
          return;
        }
      }

      Log.v(TAG, "Execute onTCPConnected");
      executor.execute(() -> {
        Log.v(TAG, "Run onTCPConnected");
        eventListener.onTCPConnected(isServer());
      });

      while (true) {
        final String message;
        try {
          message = in.readLine();
        } catch (IOException e) {
          synchronized (rawSocketLock) {
            // If socket was closed, this is expected.
            if (rawSocket == null) {
              break;
            }
          }

          reportError("Failed to read from rawSocket: " + e.getMessage());
          break;
        }

        // No data received, rawSocket probably closed.
        if (message == null) {
          break;
        }

        executor.execute(() -> {
          Log.v(TAG, "Receive: " + message);
          eventListener.onTCPMessage(message);
        });
      }

      Log.d(TAG, "Receiving thread exiting...");

      // Close the rawSocket if it is still open.
      disconnect();
    }

    /** Closes the rawSocket if it is still open. Also fires the onTCPClose event. */
    public void disconnect() {
      try {
        synchronized (rawSocketLock) {
          if (rawSocket != null) {
            rawSocket.close();
            rawSocket = null;
            out = null;

            executor.execute(() -> eventListener.onTCPClose());
          }
        }
      } catch (IOException e) {
        reportError("Failed to close rawSocket: " + e.getMessage());
      }
    }

    /**
     * Sends a message on the socket. Should only be called on the executor thread.
     */
    public void send(String message) {
      Log.v(TAG, "Send: " + message);

      synchronized (rawSocketLock) {
        if (out == null) {
          reportError("Sending data on closed socket.");
          return;
        }

        out.write(message + "\n");
        out.flush();
      }
    }
  }

  private class TCPSocketServer extends TCPSocket {
    // Server socket is also guarded by rawSocketLock.
    @Nullable
    private ServerSocket serverSocket;

    final private InetAddress address;
    final private int port;

    public TCPSocketServer(InetAddress address, int port) {
      this.address = address;
      this.port = port;
    }

    /** Opens a listening socket and waits for a connection. */
    @Nullable
    @Override
    public Socket connect() {
      Log.d(TAG, "Listening on [" + address.getHostAddress() + "]:" + port);

      final ServerSocket tempSocket;
      try {
        tempSocket = new ServerSocket(port, 0, address);
      } catch (IOException e) {
        reportError("Failed to create server socket: " + e.getMessage());
        return null;
      }

      synchronized (rawSocketLock) {
        if (serverSocket != null) {
          Log.e(TAG, "Server rawSocket was already listening and new will be opened.");
        }

        serverSocket = tempSocket;
      }

      try {
        return tempSocket.accept();
      } catch (IOException e) {
        reportError("Failed to receive connection: " + e.getMessage());
        return null;
      }
    }

    /** Closes the listening socket and calls super. */
    @Override
    public void disconnect() {
      try {
        synchronized (rawSocketLock) {
          if (serverSocket != null) {
            serverSocket.close();
            serverSocket = null;
          }
        }
      } catch (IOException e) {
        reportError("Failed to close server socket: " + e.getMessage());
      }

      super.disconnect();
    }

    @Override
    public boolean isServer() {
      return true;
    }
  }

  private class TCPSocketClient extends TCPSocket {
    final private InetAddress address;
    final private int port;

    public TCPSocketClient(InetAddress address, int port) {
      this.address = address;
      this.port = port;
    }

    /** Connects to the peer. */
    @Nullable
    @Override
    public Socket connect() {
      Log.d(TAG, "Connecting to [" + address.getHostAddress() + "]:" + Integer.toString(port));

      try {
        return new Socket(address, port);
      } catch (IOException e) {
        reportError("Failed to connect: " + e.getMessage());
        return null;
      }
    }

    @Override
    public boolean isServer() {
      return false;
    }
  }
}
