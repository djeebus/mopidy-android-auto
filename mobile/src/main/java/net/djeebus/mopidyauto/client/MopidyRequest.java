package net.djeebus.mopidyauto.client;

import com.google.gson.annotations.SerializedName;

public class MopidyRequest {
    private static int nextRequestId = 0;

    @SerializedName("method")
    private final String method;

    @SerializedName("jsonrpc")
    private final String version;

    @SerializedName("params")
    private final Object params;

    @SerializedName("id")
    private final int requestId;
    public int getRequestId() { return this.requestId; }

    public MopidyRequest(String method) {
        this(method, null);
    }

    public MopidyRequest(String method, Object params) {
        this(method, params, "2.0");
    }

    public MopidyRequest(String method, Object params, String version) {
        if (params == null) {
            // this value cannot be null
            params = new Object();
        }

        this.method = method;
        this.requestId = ++nextRequestId;
        this.version = version;
        this.params = params;
    }
}

