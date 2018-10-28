package net.djeebus.mopidyauto.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        InputStream inputStream;

        private EventReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        final static int SIZE_LENGTH = 4;

        int getUInt32(byte[] bytes) {
            byte a = bytes[0],
                 b = bytes[1],
                 c = bytes[2],
                 d = bytes[3];

            return (
                    ((a & 0xff) << 24) |
                    ((b & 0xff) << 16) |
                    ((c & 0xff) << 8) |
                     (d & 0xff));
        }

        @Override
        public synchronized void run() {
            try {
                byte[] lengthBytes = new byte[SIZE_LENGTH];

                while (MopidyBluetoothClient.this.isConnected()) {
                    Log.d(TAG, "Reading bytes for length");
                    int i = inputStream.read(lengthBytes, 0, SIZE_LENGTH);
                    if (i != SIZE_LENGTH) {
                        Log.e(TAG, "Failed to read 4 bytes");
                        return;
                    }
                    int length = getUInt32(lengthBytes);

                    // some of these messages require multiple reads
                    byte[] messageBytes = new byte[length];
                    int position = 0, messageRead;
                    do {
                        Log.d(TAG, "Reading bytes for message");
                        messageRead = inputStream.read(messageBytes, position, length - position);
                        if (messageRead <= 0) {
                            throw new IOException("Failed to read bytes");
                        }
                        position += messageRead;
                    } while (position < length);

                    String message = new String(messageBytes, 0, length, "UTF-8");

                    Log.d(TAG, "Handling message: " + message);
                    MopidyBluetoothClient.this.handleMessage(message);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in read loop", e);
            }

            handler.postDelayed(this, 1000);
        }
    }

    protected MopidyBluetoothClient() {
        this(BluetoothAdapter.getDefaultAdapter());
    }

    protected MopidyBluetoothClient(BluetoothAdapter bluetoothAdapter) {
        this.bluetoothAdapter = bluetoothAdapter;
    }

    public void close() {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                Log.w(TAG, "Failed to close stream", e);
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

        this.onClosed();
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
        if (outputStream == null) {
            Log.w(TAG, "client is not connected, returning");
            return;
        }

        ByteBuffer buffer = utf8.encode(request);
        byte[] messageData = buffer.array();
        int messageSize = buffer.limit();
        byte[] messageSizeData = ByteBuffer.allocate(4).putInt(messageSize).array();

        try {
            outputStream.write(messageSizeData, 0, messageSizeData.length);
            outputStream.write(messageData, 0, messageSize);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(TAG, "Failed to write data: " + request, e);
        }
    }

    @Override
    public boolean isConnected() { return this.socket != null; }
}
