package net.djeebus.mopidyauto.client;

import com.google.gson.JsonElement;

public interface MopidyCallback {
    void onResponse(JsonElement response);
}
