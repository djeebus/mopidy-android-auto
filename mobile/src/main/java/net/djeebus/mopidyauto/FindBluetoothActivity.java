package net.djeebus.mopidyauto;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.*;
import net.djeebus.mopidyauto.client.MopidyBluetoothClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class FindBluetoothActivity extends Activity {
    static final String TAG = "FindBluetoothActivity";

    static final int REQUEST_ENABLE_BT = 1;

    EditText url;
    ListView deviceContainer;

    ArrayList<DiscoveredDevice> devices = new ArrayList<>();
    private MopidyBluetoothClient client;

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

        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        List<String> s = new ArrayList<>();
        for(BluetoothDevice bt : pairedDevices)
           devices.add(new DiscoveredDevice(bt.getName(), bt.getAddress()));

        client = new MopidyBluetoothClient(bluetoothAdapter) {
            @Override
            protected void onClosed() {
                Log.i(TAG, "Failed to connect");
            }
        };

        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onDestroy() {
        client.close();

        super.onDestroy();
    }

    private void onDeviceClicked(AdapterView<?> adapterView, View view, int i, long l) {
        Adapter adapter = adapterView.getAdapter();
        DiscoveredDevice device = (DiscoveredDevice) adapter.getItem(i);
        this.url.setText(device.url);
        this.saveMopidyUrl();
    }

    public final static String PREFS_CONFIG = "mopidy-auto-config";
    public final static String BT_ADDR = "bt_addr";

    String loadHost() {
        SharedPreferences preferences =
                this.getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE);
        return preferences.getString(BT_ADDR, "");
    }

    void saveHost(String host) {
        SharedPreferences preferences =
                this.getSharedPreferences(PREFS_CONFIG, MODE_PRIVATE);
        SharedPreferences.Editor edit = preferences.edit();
        edit.putString(BT_ADDR, host);
        edit.apply();
    }

    void saveMopidyUrl() {
        String host = this.url.getText().toString();

        try {
            client.open(host);
        }
        catch (IOException e) {
            Log.w(TAG, "Failed to connect");
        }

        client.request("core.get_version", response -> {
            saveHost(host);
            finish();
        });
    }
}
