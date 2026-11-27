package com.bttcp.bridge;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNeededPermissions();

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 160, 56, 64);

        Space topSpacer = new Space(this);
        topSpacer.setMinimumHeight(120);
        layout.addView(topSpacer);

        TextView info = new TextView(this);
        info.setText(
            "Target BT: " + BridgeService.DEFAULT_BT_MAC + "\n" +
            "TCP Listen: " + BridgeService.DEFAULT_HOST + ":" + BridgeService.DEFAULT_PORT
        );
        info.setTextSize(16f);
        info.setPadding(0, 0, 0, 72);
        layout.addView(info);

        Button startBtn = new Button(this);
        startBtn.setText("Start Bridge Service");
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        startParams.setMargins(0, 0, 0, 36);
        startBtn.setLayoutParams(startParams);
        startBtn.setOnClickListener(v -> startBridgeService());

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Bridge Service");
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        stopBtn.setLayoutParams(stopParams);
        stopBtn.setOnClickListener(v -> stopBridgeService());

        layout.addView(startBtn);
        layout.addView(stopBtn);

        layout.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            view.setPadding(56, Math.max(160, topInset + 96), 56, Math.max(64, bottomInset + 48));
            return insets;
        });

        setContentView(layout);
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_SCAN);
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
        if (!needed.isEmpty()) {
            requestPermissions(needed.toArray(new String[0]), REQ_PERMISSIONS);
        }
    }

    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private String checkBluetoothReadyMessage() {
        if (!hasBluetoothConnectPermission()) {
            return "Bluetooth permission missing";
        }
        BluetoothAdapter adapter;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BluetoothManager mgr = getSystemService(BluetoothManager.class);
            adapter = (mgr != null) ? mgr.getAdapter() : null;
        } else {
            adapter = BluetoothAdapter.getDefaultAdapter();
        }
        if (adapter == null) {
            return "No Bluetooth adapter found";
        }
        if (!adapter.isEnabled()) {
            return "Bluetooth is OFF";
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(BridgeService.DEFAULT_BT_MAC);
            if (device == null) {
                return "Target BT device not found";
            }
        } catch (IllegalArgumentException e) {
            return "Invalid BT MAC address";
        }
        return "BT ready (" + BridgeService.DEFAULT_BT_MAC + ")";
    }

    private void startBridgeService() {
        String btStatus = checkBluetoothReadyMessage();
        Toast.makeText(this, "Starting bridge: " + btStatus, Toast.LENGTH_LONG).show();

        Intent intent = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopBridgeService() {
        Toast.makeText(this, "Stopping BTTCP bridge service", Toast.LENGTH_SHORT).show();
        Intent stopIntent = new Intent(this, BridgeService.class);
        stopIntent.setAction(BridgeService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }
    }
}
