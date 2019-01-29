package net.djeebus.mopidyauto.client;

import android.graphics.Bitmap;
import android.util.Log;
import android.util.SparseArray;
import com.google.gson.*;

import java.io.IOException;

public abstract class MopidyClient {
    private final String TAG = "MopidyClient";
    private final SparseArray<MopidyCallback> callbacks = new SparseArray<>();
    private MopidyWebSocketClient.EventListener eventListener;

    public interface EventListener {
        void onEvent(String event, JsonObject message);
    }

    public interface BitmapCallback {
        void run(Bitmap bitmap);
    }

    public void setEventListener(EventListener eventListener) {
        this.eventListener = eventListener;
    }

    public void request(String method) {
        this.request(method, null);
    }

    public void request(String method, Object params) {
        this.request(method, params, null);
    }

    public void request(String method, MopidyCallback callback) {
        this.request(method, null, callback);
    }
    public void request(String method, Object params, MopidyCallback callback) {
        this.request(new MopidyRequest(method, params, callback));
    }

    public void request(MopidyRequest... requests) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.serializeNulls();
        Gson gson = gsonBuilder.create();

        String data = gson.toJson(requests);

        for (MopidyRequest request : requests){
            if (request.callback != null) {
                this.callbacks.put(request.getRequestId(), request.callback);
            }
        }

        Log.i(TAG, "Sending: " + data);
        this.send(data);
    }

    void handleMessage(String text) {
        Log.i(TAG, "response: " + text);
        Gson gson = new Gson();
        if (text.charAt(0) == '{') {
            text = "[" + text + "]";
        }

        JsonArray responses = gson.fromJson(text, JsonArray.class);
        for (int index = 0; index < responses.size(); index++) {
            JsonObject response = (JsonObject) responses.get(index);

            if (response.has("event")) {
                String event = response.get("event").getAsString();
                if (eventListener == null) {
                    Log.w(TAG, "Handled message w/o event handler");
                    continue;
                }

                eventListener.onEvent(event, response);
                return;
            }

            int requestId = response.get("id").getAsInt();
            JsonElement result = response.get("result");

            MopidyCallback callback = callbacks.get(requestId);
            if (callback != null) {
                callbacks.remove(requestId);
                callback.onResponse(result);
            }
        }
    }

    public abstract boolean isConnected();
    public abstract void open(String host) throws IOException;
    abstract void send(String request);
    public abstract void close();
    protected abstract void onClosed();
    public abstract void getBitmapFromURL(String uri, BitmapCallback callback);
}
