package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class GetImageDataRequest {
    @SerializedName("uri")
    String uri;

    public GetImageDataRequest(String uri) {
        this.uri = uri;
    }
}
