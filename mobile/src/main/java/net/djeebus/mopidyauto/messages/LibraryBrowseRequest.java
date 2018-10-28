package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class LibraryBrowseRequest {
    @SerializedName("uri")
    private String uri;

    public LibraryBrowseRequest(String uri) {
        this.uri = uri;
    }
}
