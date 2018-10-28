package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class SeekRequest {
    @SerializedName("time_position")
    private Long timePosition;

    public SeekRequest(Long timePosition) {
        this.timePosition = timePosition;
    }
}
