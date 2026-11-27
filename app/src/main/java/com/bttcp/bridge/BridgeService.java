package com.bttcp.bridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class BridgeService extends Service {

    public static final String DEFAULT_BT_MAC = "00:1D:A5:68:98:8B";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 35000;
    public static final String ACTION_STOP = "com.bttcp.bridge.ACTION_STOP";

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "bttcp_channel";
    private static final int NOTIFICATION_ID = 1;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
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
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            showToast("Disconnecting BTTCP bridge...");
            cleanup();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        startForeground(NOTIFICATION_ID, buildNotification("Listening on " + DEFAULT_HOST + ":" + DEFAULT_PORT));

        if (!running) {
            running = true;
            acceptThread = new Thread(this::runServerLoop, "BTTCP-AcceptLoop");
            acceptThread.start();
            showToast("Bridge listening on " + DEFAULT_HOST + ":" + DEFAULT_PORT);
        } else {
            showToast("Bridge already running");
        }
        return START_STICKY;
    }

    private void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show());
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
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
                    showToast("TCP error: " + e.getMessage());
                } finally {
                    closeQuietly(tcpClient);
                }
            }
        } catch (IOException e) {
            showToast("Failed to bind " + DEFAULT_HOST + ":" + DEFAULT_PORT);
        } finally {
            cleanup();
        }
    }

    private BluetoothSocket openRfcommWithFallback(BluetoothAdapter adapter, BluetoothDevice device) throws Exception {
        try {
            adapter.cancelDiscovery();
        } catch (SecurityException ignored) {
        }

        BluetoothSocket secureSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
        try {
            secureSocket.connect();
            return secureSocket;
        } catch (IOException secureErr) {
            closeQuietly(secureSocket);
            // Fallback for ELM327 clones that reject encrypted/SDP channel negotiation
            BluetoothSocket insecureSocket = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID);
            try {
                insecureSocket.connect();
                return insecureSocket;
            } catch (IOException insecureErr) {
                closeQuietly(insecureSocket);
                // Last resort: reflection on RFCOMM channel 1
                try {
                    Method m = device.getClass().getMethod("createRfcommSocket", int.class);
                    BluetoothSocket chan1Socket = (BluetoothSocket) m.invoke(device, 1);
                    if (chan1Socket != null) {
                        chan1Socket.connect();
                        return chan1Socket;
                    }
                } catch (Exception ignored) {
                }
                throw insecureErr;
            }
        }
    }

    private void handleClient(Socket tcpClient) {
        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            showToast("BT failed: no Bluetooth adapter");
            return;
        }
        if (!adapter.isEnabled()) {
            showToast("BT failed: Bluetooth is OFF");
            return;
        }

        try {
            showToast("Connecting to BT " + DEFAULT_BT_MAC + "...");
            updateNotification("Connecting to " + DEFAULT_BT_MAC + "...");
            BluetoothDevice device = adapter.getRemoteDevice(DEFAULT_BT_MAC);
            btSocket = openRfcommWithFallback(adapter, device);

            showToast("BT connected to " + DEFAULT_BT_MAC);
            updateNotification("Connected to " + DEFAULT_BT_MAC + " (:" + DEFAULT_PORT + ")");

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
        } catch (SecurityException e) {
            showToast("BT failed: missing Bluetooth permission");
        } catch (Exception e) {
            String reason = (e.getMessage() != null && !e.getMessage().isEmpty())
                ? e.getMessage()
                : e.getClass().getSimpleName();
            showToast("BT failed: " + reason);
        } finally {
            closeQuietly(btSocket);
            btSocket = null;
            if (running) {
                updateNotification("Listening on " + DEFAULT_HOST + ":" + DEFAULT_PORT);
            }
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
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, openAppIntent, flags);

        Intent disconnectIntent = new Intent(this, BridgeService.class);
        disconnectIntent.setAction(ACTION_STOP);
        PendingIntent disconnectPendingIntent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            disconnectPendingIntent = PendingIntent.getForegroundService(this, 1, disconnectIntent, flags);
        } else {
            disconnectPendingIntent = PendingIntent.getService(this, 1, disconnectIntent, flags);
        }

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        Notification.Action disconnectAction = new Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Disconnect",
            disconnectPendingIntent
        ).build();

        return builder
            .setContentTitle("BTTCP Bridge")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(contentIntent)
            .addAction(disconnectAction)
            .setOngoing(true)
            .build();
    }

    private void cleanup() {
        running = false;
        closeQuietly(btSocket);
        btSocket = null;
        closeQuietly(serverSocket);
        serverSocket = null;
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
        showToast("BTTCP bridge stopped");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
