package net.djeebus.mopidyauto.client;

import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;

public abstract class MopidyClient {
    private final String TAG = "MopidyClient";
    private final HashMap<Integer, MopidyCallback> callbacks = new HashMap<>();
    private MopidyWebSocketClient.EventListener eventListener;

    public interface EventListener {
        void onEvent(String event, JsonObject message);
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
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
        this.send(data);
    }

    void handleMessage(String text) {
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

    public abstract boolean isConnected();
    public abstract void open(String host);
    abstract void send(String request);
    public abstract void close();
    protected abstract void onClosed();
}
