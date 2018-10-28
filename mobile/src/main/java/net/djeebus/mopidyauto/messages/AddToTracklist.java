package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class AddToTracklist {
    @SerializedName("uri")
    private String uri;

    @SerializedName("at_position")
    private Long atPosition;

    public AddToTracklist(String uri) {
        this.uri = uri;
        this.atPosition = 0L;
    }
}
