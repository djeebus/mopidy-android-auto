package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class SearchRequest {
    @SerializedName("any")
    String[] parts;

    public SearchRequest(String[] parts) {
        this.parts = parts;
    }
}
