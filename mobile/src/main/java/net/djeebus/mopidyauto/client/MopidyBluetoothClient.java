package net.djeebus.mopidyauto.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.graphics.Bitmap;
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

    private final static int SIZE_LENGTH = 4;

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    private class EventReader implements Runnable {
        InputStream inputStream;

        private EventReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

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
                    if (inputStream.available() <= 0) {
                        Log.v(TAG, "Waiting for data ... ");
                        break;
                    }

                    Log.d(TAG, "Reading 4 bytes for length");
                    int i = inputStream.read(lengthBytes, 0, SIZE_LENGTH);
                    if (i != SIZE_LENGTH) {
                        Log.e(TAG, "Failed to read 4 bytes");
                        return;
                    }

                    int length = getUInt32(lengthBytes);
                    Log.i(TAG, "Packet size will be " + length + " bytes");

                    // some of these messages require multiple reads
                    byte[] messageBytes = new byte[length];
                    int position = 0, messageRead;
                    do {
                        Log.d(TAG, "Reading bytes for message");
                        messageRead = inputStream.read(messageBytes, position, length - position);
                        if (messageRead <= 0) {
                            throw new IOException("Failed to read bytes");
                        }
                        Log.d(TAG, "Read " + messageRead + " bytes");
                        position += messageRead;
                    } while (position < length);

                    String message = new String(messageBytes, 0, length, "UTF-8");
                    Log.d(TAG, "Handling message: " + message);

                    MopidyBluetoothClient.this.handleMessage(message);
                }
            } catch (IOException e) {
                Log.e(TAG, "Error in read loop", e);
                MopidyBluetoothClient.this.close();
                return;
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

    public void open(String host) throws IOException {
        BluetoothDevice device = this.bluetoothAdapter.getRemoteDevice(host);
        if (device == null) {
            Log.w(TAG, "Failed to find device");
            return;
        }

        try {
            Log.i(TAG, "Creating socket");
            this.socket = device.createRfcommSocketToServiceRecord(MOPIDY_RPC_UUID);

            Log.i(TAG, "Connecting socket");
            this.socket.connect();

            Log.i(TAG, "Getting output stream");
            this.outputStream = this.socket.getOutputStream();

            Log.i(TAG, "Getting input stream");
            InputStream inputStream = this.socket.getInputStream();
            this.handler.post(new EventReader(inputStream));
        } catch (IOException e) {
            Log.e(TAG, "Failed to connect");
            this.close();
            throw e;
        }
    }

    @Override
    void send(String request) {
        if (!this.isConnected()) {
            Log.w(TAG, "client is not connected, returning");
            return;
        }

        byte[] requestBytes = request.getBytes(utf8);
        int requestLength = requestBytes.length;
        ByteBuffer sizeBuffer = ByteBuffer.allocate(SIZE_LENGTH).putInt(requestLength);

        ByteBuffer message = ByteBuffer.allocate(SIZE_LENGTH + requestLength)
                .put(sizeBuffer.array())
                .put(requestBytes);

        byte[] messageData = message.array();
        Log.v(TAG, "Sending message: " + request);
        try {
            Log.i(TAG, "Writing " + message.capacity() + " bytes");
            outputStream.write(messageData, 0, messageData.length);
            outputStream.flush();
            Log.i(TAG, "Sent " + message.capacity() + " bytes");
        } catch (IOException e) {
            Log.e(TAG, "Failed to write data: " + request, e);
            this.close();
        }
    }

    @Override
    public boolean isConnected() {
        if (this.socket == null) {
            return false;
        }

        return this.socket.isConnected();
    }

    @Override
    public Bitmap getBitmapFromURL(String uri) {
        return null;
    }
}
