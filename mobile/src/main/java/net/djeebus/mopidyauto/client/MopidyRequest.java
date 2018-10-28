package net.djeebus.mopidyauto.client;

import com.google.gson.annotations.SerializedName;

public final class MopidyRequest {
    private static int nextRequestId = 0;

    protected transient MopidyCallback callback;

    @SerializedName("method")
    private final String method;

    @SerializedName("jsonrpc")
    private final String version;

    @SerializedName("params")
    private final Object params;

    @SerializedName("id")
    private final int requestId;
    public int getRequestId() { return this.requestId; }

    protected MopidyRequest(String method) {
        this(method, null);
    }

    public MopidyRequest(String method, MopidyCallback callback) {
        this(method, null, callback);
    }

    protected MopidyRequest(String method, Object params) {
        this(method, params, null);
    }

    public MopidyRequest(String method, Object params, MopidyCallback callback) {
        this(method, params, "2.0", callback);
    }

    private MopidyRequest(String method, Object params, String version, MopidyCallback callback) {
        if (params == null) {
            // this value cannot be null
            params = new Object();
        }

        this.callback = callback;
        this.method = method;
        this.requestId = ++nextRequestId;
        this.version = version;
        this.params = params;
    }
}

