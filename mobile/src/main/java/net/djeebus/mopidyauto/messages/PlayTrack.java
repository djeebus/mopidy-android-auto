package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class PlayTrack {
    @SerializedName("tlid")
    private Integer tlid;

    public PlayTrack(Integer tlid) {
        this.tlid = tlid;
    }
}
