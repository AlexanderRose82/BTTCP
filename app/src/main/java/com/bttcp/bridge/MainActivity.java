package com.bttcp.bridge;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private static final int REQ_PERMISSIONS = 1001;

    private EditText macInput;
    private EditText hostInput;
    private EditText portInput;
    private CheckBox showToastsCheckbox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestNeededPermissions();

        SharedPreferences prefs = getSharedPreferences(BridgeService.PREFS_NAME, MODE_PRIVATE);
        String savedMac = prefs.getString(BridgeService.KEY_BT_MAC, BridgeService.DEFAULT_BT_MAC);
        String savedHost = prefs.getString(BridgeService.KEY_HOST, BridgeService.DEFAULT_HOST);
        int savedPort = prefs.getInt(BridgeService.KEY_PORT, BridgeService.DEFAULT_PORT);
        boolean savedShowToasts = prefs.getBoolean(BridgeService.KEY_SHOW_TOASTS, BridgeService.DEFAULT_SHOW_TOASTS);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(56, 160, 56, 64);

        Space topSpacer = new Space(this);
        topSpacer.setMinimumHeight(100);
        layout.addView(topSpacer);

        TextView macLabel = new TextView(this);
        macLabel.setText("Bluetooth Device MAC");
        macLabel.setTextSize(14f);
        layout.addView(macLabel);

        macInput = new EditText(this);
        macInput.setText(savedMac);
        macInput.setHint("00:1D:A5:68:98:8B");
        macInput.setSingleLine(true);
        layout.addView(macInput);

        Button pickDeviceBtn = new Button(this);
        pickDeviceBtn.setText("Pick Paired Bluetooth Device");
        LinearLayout.LayoutParams pickParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pickParams.setMargins(0, 12, 0, 32);
        pickDeviceBtn.setLayoutParams(pickParams);
        pickDeviceBtn.setOnClickListener(v -> showPairedDevicesDialog());
        layout.addView(pickDeviceBtn);

        TextView hostLabel = new TextView(this);
        hostLabel.setText("TCP Listen Host");
        hostLabel.setTextSize(14f);
        layout.addView(hostLabel);

        hostInput = new EditText(this);
        hostInput.setText(savedHost);
        hostInput.setHint("127.0.0.1");
        hostInput.setSingleLine(true);
        layout.addView(hostInput);

        TextView portLabel = new TextView(this);
        portLabel.setText("TCP Listen Port");
        portLabel.setTextSize(14f);
        portLabel.setPadding(0, 24, 0, 0);
        layout.addView(portLabel);

        portInput = new EditText(this);
        portInput.setText(String.valueOf(savedPort));
        portInput.setHint("35000");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setSingleLine(true);
        layout.addView(portInput);

        showToastsCheckbox = new CheckBox(this);
        showToastsCheckbox.setText("Show toast notifications");
        showToastsCheckbox.setChecked(savedShowToasts);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cbParams.setMargins(0, 28, 0, 36);
        showToastsCheckbox.setLayoutParams(cbParams);
        layout.addView(showToastsCheckbox);

        Button saveBtn = new Button(this);
        saveBtn.setText("Save Settings");
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        saveParams.setMargins(0, 0, 0, 36);
        saveBtn.setLayoutParams(saveParams);
        saveBtn.setOnClickListener(v -> {
            if (saveSettingsToPrefs()) {
                maybeToast("Settings saved");
            }
        });
        layout.addView(saveBtn);

        Button startBtn = new Button(this);
        startBtn.setText("Start Bridge Service");
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        startParams.setMargins(0, 0, 0, 28);
        startBtn.setLayoutParams(startParams);
        startBtn.setOnClickListener(v -> startBridgeService());
        layout.addView(startBtn);

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Bridge Service");
        layout.addView(stopBtn);
        stopBtn.setOnClickListener(v -> stopBridgeService());

        scrollView.addView(layout);

        scrollView.setOnApplyWindowInsetsListener((view, insets) -> {
            int topInset = insets.getSystemWindowInsetTop();
            int bottomInset = insets.getSystemWindowInsetBottom();
            layout.setPadding(56, Math.max(160, topInset + 96), 56, Math.max(64, bottomInset + 48));
            return insets;
        });

        setContentView(scrollView);
    }

    private void maybeToast(String message) {
        if (showToastsCheckbox != null && showToastsCheckbox.isChecked()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private boolean saveSettingsToPrefs() {
        String mac = macInput.getText().toString().trim().toUpperCase();
        String host = hostInput.getText().toString().trim();
        String portStr = portInput.getText().toString().trim();
        boolean showToasts = showToastsCheckbox.isChecked();

        if (!BluetoothAdapter.checkBluetoothAddress(mac)) {
            Toast.makeText(this, "Invalid MAC address (use XX:XX:XX:XX:XX:XX)", Toast.LENGTH_LONG).show();
            return false;
        }
        if (host.isEmpty()) {
            host = BridgeService.DEFAULT_HOST;
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                Toast.makeText(this, "Port must be between 1 and 65535", Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid port number", Toast.LENGTH_LONG).show();
            return false;
        }

        macInput.setText(mac);
        hostInput.setText(host);
        portInput.setText(String.valueOf(port));

        getSharedPreferences(BridgeService.PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putString(BridgeService.KEY_BT_MAC, mac)
            .putString(BridgeService.KEY_HOST, host)
            .putInt(BridgeService.KEY_PORT, port)
            .putBoolean(BridgeService.KEY_SHOW_TOASTS, showToasts)
            .apply();
        return true;
    }

    private void showPairedDevicesDialog() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant Bluetooth permission first", Toast.LENGTH_SHORT).show();
            requestNeededPermissions();
            return;
        }

        BluetoothAdapter adapter = getBluetoothAdapter();
        if (adapter == null) {
            Toast.makeText(this, "No Bluetooth adapter found", Toast.LENGTH_SHORT).show();
            return;
        }

        Set<BluetoothDevice> bonded;
        try {
            bonded = adapter.getBondedDevices();
        } catch (SecurityException e) {
            Toast.makeText(this, "Missing Bluetooth permission to list paired devices", Toast.LENGTH_SHORT).show();
            return;
        }

        if (bonded == null || bonded.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found", Toast.LENGTH_SHORT).show();
            return;
        }

        final List<BluetoothDevice> deviceList = new ArrayList<>(bonded);
        final String[] labels = new String[deviceList.size()];
        for (int i = 0; i < deviceList.size(); i++) {
            BluetoothDevice dev = deviceList.get(i);
            String name = "Unknown";
            try {
                if (dev.getName() != null && !dev.getName().isEmpty()) {
                    name = dev.getName();
                }
            } catch (SecurityException ignored) {
            }
            labels[i] = name + "\n" + dev.getAddress();
        }

        new AlertDialog.Builder(this)
            .setTitle("Select Paired Device")
            .setItems(labels, (dialog, which) -> {
                String chosenMac = deviceList.get(which).getAddress();
                macInput.setText(chosenMac);
                saveSettingsToPrefs();
                maybeToast("Selected " + chosenMac);
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private BluetoothAdapter getBluetoothAdapter() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BluetoothManager mgr = getSystemService(BluetoothManager.class);
            return mgr != null ? mgr.getAdapter() : null;
        }
        return BluetoothAdapter.getDefaultAdapter();
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

    private void startBridgeService() {
        if (!saveSettingsToPrefs()) {
            return;
        }

        String mac = macInput.getText().toString().trim();
        String host = hostInput.getText().toString().trim();
        int port = Integer.parseInt(portInput.getText().toString().trim());
        maybeToast("Starting bridge (" + mac + " -> " + host + ":" + port + ")");

        Intent intent = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopBridgeService() {
        maybeToast("Stopping BTTCP bridge service");
        Intent stopIntent = new Intent(this, BridgeService.class);
        stopIntent.setAction(BridgeService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(stopIntent);
        } else {
            startService(stopIntent);
        }
    }
}
