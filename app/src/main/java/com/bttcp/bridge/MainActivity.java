package com.bttcp.bridge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        layout.setPadding(48, 48, 48, 48);

        TextView info = new TextView(this);
        info.setText(
            "Target BT: " + BridgeService.DEFAULT_BT_MAC + "\n" +
            "TCP Listen: " + BridgeService.DEFAULT_HOST + ":" + BridgeService.DEFAULT_PORT
        );
        info.setPadding(0, 0, 0, 32);

        Button startBtn = new Button(this);
        startBtn.setText("Start Bridge Service");
        startBtn.setOnClickListener(v -> startBridgeService());

        Button stopBtn = new Button(this);
        stopBtn.setText("Stop Bridge Service");
        stopBtn.setOnClickListener(v -> stopBridgeService());

        layout.addView(info);
        layout.addView(startBtn);
        layout.addView(stopBtn);
        setContentView(layout);
    }

    private void requestNeededPermissions() {
        List<String> needed = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.BLUETOOTH_CONNECT);
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
        Intent intent = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void stopBridgeService() {
        stopService(new Intent(this, BridgeService.class));
    }
}
