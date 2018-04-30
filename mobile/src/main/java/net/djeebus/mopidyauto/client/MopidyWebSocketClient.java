package net.djeebus.mopidyauto.client;

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import okhttp3.*;
import okio.ByteString;

public abstract class MopidyWebSocketClient extends MopidyClient {
    private final String TAG = "MopidyWebSocketClient";

    private WebSocket webSocket;

    protected abstract void onClosed();

    private class MopidyListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.i(TAG, "onOpen");
            MopidyWebSocketClient.this.webSocket = webSocket;
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "onClosed");
            MopidyWebSocketClient.this.webSocket = null;
            MopidyWebSocketClient.this.onClosed();
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "onClosing");
            super.onClosing(webSocket, code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, @Nullable Response response) {
            Log.e(TAG, "onFailure", t);
            if (response != null) {
                Log.e(TAG, "onFailure: " + response.message());
            }

            MopidyWebSocketClient.this.close();

            super.onFailure(webSocket, t, response);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            handleMessage(text);
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.i(TAG, "onMessage 2");
            super.onMessage(webSocket, bytes);
        }
    }

    @Override
    public void open(String host) {
        this.close();

        if (!host.contains("://")) {
            host = "ws://" + host;
        }

        Log.i(TAG, "Creating request");
        String url = Uri.parse(host).buildUpon()
                .scheme("ws")
                .path("/mopidy/ws")
                .toString();

        Log.i(TAG, "Connecting to " + url);
        Request request = new Request.Builder().url(url).build();

        Log.i(TAG, "Creating listener");
        MopidyListener listener = new MopidyListener();

        Log.i(TAG, "Creating client");
        OkHttpClient client = new OkHttpClient();

        Log.i(TAG, "creating web socket");
        webSocket = client.newWebSocket(request, listener);

        Log.i(TAG, "Socket created");
    }

    public boolean isConnected() {
        return this.webSocket != null;
    }

    @Override
    public void close() {
        if (this.webSocket == null) {
            return;
        }

        Log.i(TAG, "Closing client");
        webSocket.close(1001, "see ya");
    }

    @Override
    void send(String request) {
        this.webSocket.send(request);
    }
}
