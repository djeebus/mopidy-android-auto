package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class PlayTrackRequest {
    @SerializedName("tlid")
    private Integer tlid;

    public PlayTrackRequest(Integer tlid) {
        this.tlid = tlid;
    }
}
