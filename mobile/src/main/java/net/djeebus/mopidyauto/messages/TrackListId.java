package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class TrackListId {
    @SerializedName("tlid")
    Long tlid;

    public TrackListId(Long queueId) {
        this.tlid = queueId;
    }
}
