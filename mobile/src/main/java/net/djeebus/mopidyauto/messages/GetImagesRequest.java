package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class GetImagesRequest {
    @SerializedName("uris")
    String[] uris;

    public GetImagesRequest(String[] uris) {
        this.uris = uris;
    }
}
