package net.djeebus.mopidyauto.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.UUID;

public abstract class MopidyBluetoothClient extends MopidyClient {
    private final String TAG = "MopidyBluetoothClient";

    private static final UUID MOPIDY_RPC_UUID = UUID.fromString("6e08ec37-60ec-4167-945e-9dd781ba6e1a");
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private Charset utf8 = Charset.forName("UTF-8");
    private final android.os.Handler handler = new android.os.Handler();

    private class EventReader implements Runnable {
        InputStreamReader reader;

        private EventReader(InputStream inputStream) {
            this.reader = new InputStreamReader(inputStream);
        }

        final static int OPEN = (int)'{';
        final static int CLOSE = (int)'}';

        StringBuilder s = new StringBuilder();
        int count = 0;

        @Override
        public synchronized void run() {
            // these messages are always wrapped in curly braces.
            try {
                while (reader.ready()) {
                    int i = reader.read();
                    if (i == 0) {
                        Log.d(TAG, "Stopped from null byte");
                        break;
                    }

                    if (i == -1) {
                        Log.d(TAG, "Stopped from no data read");
                        break;
                    }

                    s.append((char) i);
                    switch (i) {
                        case OPEN:
                            this.count += 1;
                            break;
                        case CLOSE:
                            this.count -= 1;
                            break;
                    }

                    send();
                }
            } catch (IOException e) {
                return;
            }

            send();

            handler.postDelayed(this, 1000);
        }

        void send() {
            if (count == 0 && s.length() > 0) {
                MopidyBluetoothClient.this.handleMessage(s.toString());
                s = new StringBuilder();
            }
        }
    }

    public MopidyBluetoothClient() {
        this(BluetoothAdapter.getDefaultAdapter());
    }

    public MopidyBluetoothClient(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void close() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close stream", e);
                return;
            }
            outputStream = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close socket", e);
            }
            socket = null;
        }
    }

    public void open(String host) {
        BluetoothDevice device = this.bluetoothAdapter.getRemoteDevice(host);
        if (device == null) {
            Log.w(TAG, "Failed to find device");
            return;
        }

        try {
            socket = device.createRfcommSocketToServiceRecord(MOPIDY_RPC_UUID);
        } catch (IOException e) {
            Log.w(TAG, "Failed to create socket", e);
            return;
        }

        try {
            socket.connect();
        } catch (IOException e) {
            Log.w(TAG, "Failed connect to device", e);
            return;
        }

        try {
            outputStream = socket.getOutputStream();
        } catch (IOException e) {
            Log.w(TAG, "Failed to get the output stream");
        }

        InputStream inputStream;
        try {
            inputStream = socket.getInputStream();
        } catch (IOException e) {
            Log.w(TAG, "Failed to get the input stream");
            inputStream = null;
        }

        if (inputStream != null) {
            this.handler.post(new EventReader(inputStream));
        }
    }

    @Override
    void send(String request) {
        ByteBuffer buffer = utf8.encode(request);
        try {
            Log.d(TAG, "Writing data: " + request);
            outputStream.write(buffer.array());
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write data: " + request, e);
        }
    }

    @Override
    public boolean isConnected() { return this.socket != null; }
}
