/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2017
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.github.jtmsp.websocket;

import com.github.jtmsp.websocket.jsonrpc.JSONRPC;
import com.github.jtmsp.websocket.jsonrpc.JSONRPCResult;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.websocket.CloseReason;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

/**
 * Websocket wrapper for tendermint JSON-RPC
 * 
 * @author @wolfposd
 *
 */
public class Websocket {

    public static String DEFAULT_DESTINATION = "ws://127.0.0.1:46657/websocket";

    private WebSocketClient wsSession;
    private Map<String, WSResponse> callbacks = new HashMap<>();
    private Gson gson = new Gson();
    private URI destination;
    private WebsocketStatus status;

    /**
     * Creates a new websocket to the default destination<br>
     * Websocket must be opened with {@link #reconnectWebsocket()}
     */
    public Websocket() {
        this(null);
    }

    /**
     * Creates a new websocket to the default destination<br>
     * Websocket must be opened with {@link #reconnectWebsocket()}
     * 
     * @param status
     *            will be notified about status changes, can be
     *            <code>null</code>
     */
    public Websocket(WebsocketStatus status) {
        try {
            destination = new URI(DEFAULT_DESTINATION);
        } catch (URISyntaxException e) {
        }
        this.status = status;

        if (this.status == null)
            this.status = new WebsocketStatus() {
            };
    }

    /**
     * Creates a new websocket to the destination<br>
     * Websocket must be opened with {@link #reconnectWebsocket()}
     * 
     * @param destination
     *            destination URI
     * @param status
     *            will be notified about status changes, can be
     *            <code>null</code>
     */
    public Websocket(URI destination, WebsocketStatus status) {
        this.destination = destination;
        this.status = status;
        if (this.status == null)
            this.status = new WebsocketStatus() {
            };
    }

    /**
     * Tries to open this websocket, if its already opened nothing happens
     * 
     * @throws WebsocketException
     */
    public void reconnectWebsocket() throws WebsocketException {

        if (wsSession == null || !wsSession.isOpen()) {

            wsSession = new WebSocketClient(destination) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Websocket.this.onOpen();
                }

                @Override
                public void onMessage(String message) {
                    Websocket.this.onMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Websocket.this.onClose(
                            new CloseReason(
                                    CloseReason.CloseCodes.getCloseCode(code),
                                    reason)
                    );
                }

                @Override
                public void onError(Exception ex) {
                    Websocket.this.onError(ex);
                }
            };

            try {
                wsSession.connectBlocking();
            } catch (InterruptedException e) {
                throw new WebsocketException(e);
            }

        }
    }

    /**
     * Tries to open this websocket, if its already opened nothing happens
     * 
     * @throws WebsocketException
     */
    public void connect() throws WebsocketException {
        this.reconnectWebsocket();
    }

    /**
     * Disconnects this websocket<br>
     * It will send a NORMAL_CLOSURE to the WebsocketStatus
     * 
     * @throws WebsocketException
     */
    public void disconnect() throws WebsocketException {
        try {
            if (wsSession != null) {
                wsSession.closeBlocking();
            }
        } catch (InterruptedException e) {
            throw new WebsocketException(e);
        }
    }

    /**
     * Is this websocket connection open?
     */
    public boolean isOpen() {
        return wsSession != null && wsSession.isOpen();
    }

    /**
     * Sends a message towards the node, notifies the callback on response
     * 
     * @param rpc
     *            message to send
     * @param callback
     *            callback to notify
     */
    public void sendMessage(JSONRPC rpc, WSResponse callback) {
        callbacks.put(rpc.id, callback);
        wsSession.send(gson.toJson(rpc));
    }

    private void onOpen() {
        status.wasOpened();
    }

    private void onError(Throwable t) {
        status.hadError(t);
    }

    private void onClose(CloseReason cr) {
        status.wasClosed(cr);
    }

    private void onMessage(String m) {
        try {
            JSONRPCResult result = gson.fromJson(m, JSONRPCResult.class);

            WSResponse cb = callbacks.get(result.id);
            if (cb != null) {
                cb.onJSONRPCResult(result);
                callbacks.remove(result.id);
            }
        } catch (JsonSyntaxException e) {
            status.hadError(e);
        }
    }
}
