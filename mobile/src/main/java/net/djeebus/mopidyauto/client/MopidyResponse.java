package net.djeebus.mopidyauto.client;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

public class MopidyResponse {
    @SerializedName("jsonrpc")
    private String version;

    @SerializedName("id")
    private int id;
    public int getRequestId() { return this.id; }
    public void setRequestId(int id) { this.id = id; }

    @SerializedName("result")
    private JsonElement result;
    public JsonElement getResult() { return this.result; }
    public void setResult(JsonElement element) { this.result = result; }
}
