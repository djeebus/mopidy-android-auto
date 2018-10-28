package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class LibraryBrowse {
    @SerializedName("uri")
    private String uri;

    public LibraryBrowse(String uri) {
        this.uri = uri;
    }
}
