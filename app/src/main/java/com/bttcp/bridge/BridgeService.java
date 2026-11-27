package com.bttcp.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class BridgeService extends Service {

    public static final String DEFAULT_BT_MAC = "00:1D:A5:68:98:8B";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 35000;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "bttcp_channel";
    private static final int NOTIFICATION_ID = 1;

    private volatile boolean running = false;
    private ServerSocket serverSocket;
    private BluetoothSocket btSocket;
    private Thread acceptThread;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "com.bttcp.bridge.ACTION_STOP".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Listening on " + DEFAULT_HOST + ":" + DEFAULT_PORT));

        if (!running) {
            running = true;
            acceptThread = new Thread(this::runServerLoop, "BTTCP-AcceptLoop");
            acceptThread.start();
        }
        return START_STICKY;
    }

    private void runServerLoop() {
        try {
            InetAddress bindAddr = InetAddress.getByName(DEFAULT_HOST);
            serverSocket = new ServerSocket(DEFAULT_PORT, 1, bindAddr);

            while (running) {
                Socket tcpClient = null;
                try {
                    tcpClient = serverSocket.accept();
                    handleClient(tcpClient);
                } catch (IOException e) {
                    if (!running) break;
                } finally {
                    closeQuietly(tcpClient);
                }
            }
        } catch (IOException e) {
            // Failed to bind port
        } finally {
            cleanup();
        }
    }

    private void handleClient(Socket tcpClient) {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            return;
        }

        try {
            BluetoothDevice device = adapter.getRemoteDevice(DEFAULT_BT_MAC);
            btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
            adapter.cancelDiscovery();
            btSocket.connect();

            final InputStream btIn = btSocket.getInputStream();
            final OutputStream btOut = btSocket.getOutputStream();
            final InputStream tcpIn = tcpClient.getInputStream();
            final OutputStream tcpOut = tcpClient.getOutputStream();

            Thread tcpToBt = new Thread(() -> pipeStream(tcpIn, btOut), "BTTCP-TCP2BT");
            Thread btToTcp = new Thread(() -> pipeStream(btIn, tcpOut), "BTTCP-BT2TCP");

            tcpToBt.start();
            btToTcp.start();

            tcpToBt.join();
            btToTcp.join();
        } catch (Exception e) {
            // Connection error; client will reconnect on next poll cycle
        } finally {
            closeQuietly(btSocket);
            btSocket = null;
        }
    }

    private void pipeStream(InputStream in, OutputStream out) {
        byte[] buffer = new byte[512];
        try {
            int n;
            while (running && (n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BluetoothManager mgr = getSystemService(BluetoothManager.class);
            return mgr != null ? mgr.getAdapter() : null;
        }
        return BluetoothAdapter.getDefaultAdapter();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                "BTTCP Bridge",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(ch);
            }
        }
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        return builder
            .setContentTitle("BTTCP Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build();
    }

    private void cleanup() {
        running = false;
        closeQuietly(btSocket);
        closeQuietly(serverSocket);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
