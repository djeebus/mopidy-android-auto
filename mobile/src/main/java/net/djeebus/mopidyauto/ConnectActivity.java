package net.djeebus.mopidyauto;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.*;
import net.djeebus.mopidyauto.client.MopidyClient;

import java.util.ArrayList;

public class ConnectActivity extends Activity {
    static final String TAG = "ConnectActivity";
    static final String SERVICE_TYPE = "_http._tcp";

    EditText url;
    MopidyClient client;
    ListView deviceContainer;

    NsdManager nsdManager;
    NsdManager.DiscoveryListener discoveryListener;
    NsdManager.ResolveListener resolveListener;
    WifiManager wifi;
    WifiManager.MulticastLock lock;

    ArrayList<DiscoveredDevice> devices = new ArrayList<>();

    class DiscoveredDevice {
        public String url;
        public String name;

        DiscoveredDevice(String name, String url) {
            this.url = url;
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        this.setContentView(R.layout.connect);

        Button button = this.findViewById(R.id.connect);
        button.setOnClickListener(view -> saveMopidyUrl());

        deviceContainer = this.findViewById(R.id.discovered_hosts);
        url = this.findViewById(R.id.url);

        ListAdapter adapter = new ArrayAdapter<>(
                this, R.layout.discovered_host, R.id.text, devices);
        deviceContainer.setAdapter(adapter);
        deviceContainer.setOnItemClickListener(this::onDeviceClicked);

        String host = this.loadHost();
        url.setText(host);

        Context appContext = this.getApplicationContext();
        nsdManager = (NsdManager)appContext.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            Log.w(TAG, "Failed to find NSD manager");
            return;
        }

        wifi = (WifiManager)appContext.getSystemService(Context.WIFI_SERVICE);
        if (wifi == null) {
            Log.w(TAG, "Failed to find wifi manager");
            return;
        }

        client = new MopidyClient() {
            @Override
            protected void onClosed() {
                Log.i(TAG, "Failed to connect");
            }
        };

        resolveListener = createResolveListener();
        discoveryListener = createDiscoveryListener();

        super.onCreate(savedInstanceState);
    }

    private void onDeviceClicked(AdapterView<?> adapterView, View view, int i, long l) {
        Adapter adapter = adapterView.getAdapter();
        DiscoveredDevice device = (DiscoveredDevice) adapter.getItem(i);
        this.url.setText(device.url);
        this.saveMopidyUrl();
    }

    NsdManager.ResolveListener createResolveListener() {
        return new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo nsdServiceInfo, int i) {
                Log.e(TAG, "Failed to resolve service");
            }

            @Override
            public void onServiceResolved(NsdServiceInfo nsdServiceInfo) {
                Log.i(TAG, "Successfully resolved service");
                String url = "http:/" + nsdServiceInfo.getHost() + ":" + nsdServiceInfo.getPort();
                deviceContainer.post(() ->
                        devices.add(new DiscoveredDevice(nsdServiceInfo.getServiceName(), url)));
            }
        };
    }

    NsdManager.DiscoveryListener createDiscoveryListener() {
        Log.i(TAG, "Creating listener");
        return new NsdManager.DiscoveryListener() {

            @Override
            public void onStartDiscoveryFailed(String s, int i) {
                Log.i(TAG, "onStartDiscoveryFailed: " + s);
            }

            @Override
            public void onStopDiscoveryFailed(String s, int i) {
                Log.i(TAG, "onStopDiscoveryFailed: " + s);
            }

            @Override
            public void onDiscoveryStarted(String s) {
                Log.i(TAG, "onDiscoveryStarted: " + s);
            }

            @Override
            public void onDiscoveryStopped(String s) {
                Log.i(TAG, "onDiscoveryStopped: " + s);
            }

            @Override
            public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
                Log.i(TAG, "Found a service, resolving");
                nsdManager.resolveService(nsdServiceInfo, resolveListener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
                Log.i(TAG, "onServiceLost: " + nsdServiceInfo.getHost() + ":" + nsdServiceInfo.getPort());
            }
        };
    }

    void startListening() {
        Log.i(TAG, "start listening");
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        lock = wifi.createMulticastLock("mopidy-discover");
        lock.acquire();
    }

    void stopListening() {
        Log.i(TAG, "Stop listening");
        nsdManager.stopServiceDiscovery(discoveryListener);
        lock.release();
    }

    @Override
    protected void onResume() {
        startListening();

        super.onResume();
    }

    @Override
    protected void onPause() {
        stopListening();

        super.onPause();
    }

    @Override
    protected void onDestroy() {
        client.close();

        super.onDestroy();
    }

    public final static String PREFS_CONFIG = "mopidy-auto-config";
    public final static String PREFS_CONFIG_HOST = "host";

    String loadHost() {
        SharedPreferences preferences =
                this.getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE);
        return preferences.getString(PREFS_CONFIG_HOST, "");
    }

    void saveHost(String host) {
        SharedPreferences preferences =
                this.getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(PREFS_CONFIG_HOST, host);
        edit.apply();
    }

    void saveMopidyUrl() {
        String host = this.url.getText().toString();
        client.open(host);

        client.request("core.get_version", response -> {
            saveHost(host);
            finish();
        });
    }
}
