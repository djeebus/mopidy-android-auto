package net.djeebus.mopidyauto.messages;

import com.google.gson.annotations.SerializedName;

public class Seek {
    @SerializedName("time_position")
    private Long timePosition;

    public Seek(Long timePosition) {
        this.timePosition = timePosition;
    }
}
