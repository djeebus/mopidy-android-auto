package net.djeebus.mopidyauto.client;

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import okhttp3.*;
import okio.ByteString;

import java.util.HashMap;

public class MopidyClient {
    private final String TAG = "MopidyClient";

    private final HashMap<Integer, MopidyCallback> callbacks = new HashMap<>();
    private EventListener eventListener;

    private WebSocket webSocket;
    private String host;

    public interface EventListener {
        void onEvent(String event, JsonObject message);
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    private class MopidyListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.i(TAG, "onOpen");
            MopidyClient.this.webSocket = webSocket;
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.i(TAG, "onClosed");
            MopidyClient.this.webSocket = null;
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

            if (host != null) {
                // try again!
                open(host);
            }

            super.onFailure(webSocket, t, response);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.i(TAG, "response: " + text);
            Gson gson = new Gson();
            JsonObject response = gson.fromJson(text, JsonObject.class);
            if (response.has("event")) {
                String event = response.get("event").getAsString();
                eventListener.onEvent(event, response);
                return;
            }

            int requestId = response.get("id").getAsInt();
            JsonElement result = response.get("result");

            if (callbacks.containsKey(requestId)) {
                MopidyCallback callback = callbacks.remove(requestId);
                callback.onResponse(result);
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.i(TAG, "onMessage 2");
            super.onMessage(webSocket, bytes);
        }
    }

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

    public void close() {
        this.host = null;

        if (this.webSocket == null) {
            return;
        }

        Log.i(TAG, "Closing client");
        webSocket.close(1001, "see ya");
    }

    public void request(String method) {
        this.request(method, null);
    }

    public void request(String method, MopidyCallback callback) {
        this.request(new MopidyRequest(method), callback);
    }

    public void request(String method, Object params) {
        this.request(new MopidyRequest(method, params), null);
    }

    public void request(String method, Object params, MopidyCallback callback) {
        this.request(new MopidyRequest(method, params), callback);
    }

    private void request(MopidyRequest request, MopidyCallback callback) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.serializeNulls();
        Gson gson = gsonBuilder.create();

        Log.i(TAG, "serializing");
        String data = gson.toJson(request);

        if (callback != null) {
            this.callbacks.put(request.getRequestId(), callback);
        }

        Log.i(TAG, "Sending: " + data);
        this.webSocket.send(data);
    }
}
