package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class BatchAddToTracklist {
    @SerializedName("uris")
    String[] uris;

    public BatchAddToTracklist(String[] uris) {
        this.uris = uris;
    }
}
