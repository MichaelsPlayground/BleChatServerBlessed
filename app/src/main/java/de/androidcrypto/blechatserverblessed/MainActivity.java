package de.androidcrypto.blechatserverblessed;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * This app is taken from https://github.com/weliem/bluetooth-server-example
 * I modified the example very slightly to be compatible to the example app
 * for the library blessed-android (https://github.com/weliem/blessed-android)
 */

public class MainActivity extends AppCompatActivity {

    // new in part 2
    /* Local UI */
    SwitchMaterial bluetoothEnabled, advertisingActive, deviceConnected, subscriptionsEnabled;
    com.google.android.material.textfield.TextInputEditText connectedDevices;
    // new in part 3
    com.google.android.material.textfield.TextInputEditText batteryLevel;
    // new in part 4
    com.google.android.material.textfield.TextInputEditText receivedMessage;

    private static final int REQUEST_ENABLE_BT = 1;
    private static final int ACCESS_LOCATION_REQUEST = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // new in part 2
        registerReceiver(advertiserStateReceiver, new IntentFilter((BluetoothServer.BLUETOOTH_SERVER_ADVERTISER)));
        registerReceiver(connectionStateReceiver, new IntentFilter((BluetoothServer.BLUETOOTH_SERVER_CONNECTION)));
        registerReceiver(subscriptionStateReceiver, new IntentFilter((BluetoothServer.BLUETOOTH_SERVER_SUBSCRIPTION)));
        // new in part 3
        registerReceiver(batteryLevelStateReceiver, new IntentFilter((BatteryService.BLUETOOTH_SERVER_BATTERY_LEVEL)));
        registerReceiver(connectedDevicesStateReceiver, new IntentFilter((BluetoothServer.BLUETOOTH_SERVER_CONNECTED_DEVICES)));
        // new in chat
        registerReceiver(receivedMessageStateReceiver, new IntentFilter((ChatService.BLUETOOTH_CHAT)));

        // new in part 2
        bluetoothEnabled = findViewById(R.id.swMainBleEnabled);
        advertisingActive = findViewById(R.id.swMainAdvertisingActive);
        deviceConnected = findViewById(R.id.swMainDeviceConnected);
        subscriptionsEnabled = findViewById(R.id.swMainSubscriptionsEnabled);
        connectedDevices = findViewById(R.id.etMainConnectedDevices);
        // new in part 3
        batteryLevel = findViewById(R.id.etMainBatteryLevel);
        // new in chat
        receivedMessage = findViewById(R.id.etMainReceivedMessage);

        // this is for debug purposes - it leaves the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onResume() {
        super.onResume();

        if (!isBluetoothEnabled()) {
            bluetoothEnabled.setChecked(false); // added in part 2
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            bluetoothEnabled.setChecked(true); // added in part 2
            checkPermissions();
        }
    }

    // new in part 2
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(advertiserStateReceiver);
        unregisterReceiver(connectionStateReceiver);
        unregisterReceiver(subscriptionStateReceiver);
        // new in part 3
        unregisterReceiver(batteryLevelStateReceiver);
        unregisterReceiver(connectedDevicesStateReceiver);
        // new in chat
        unregisterReceiver(receivedMessageStateReceiver);
    }

    private boolean isBluetoothEnabled() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null) return false;
        return bluetoothAdapter.isEnabled();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] missingPermissions = getMissingPermissions(getRequiredPermissions());
            if (missingPermissions.length > 0) {
                requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST);
            } else {
                permissionsGranted();
            }
        }
    }

    private String[] getMissingPermissions(String[] requiredPermissions) {
        List<String> missingPermissions = new ArrayList<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            for (String requiredPermission : requiredPermissions) {
                if (getApplicationContext().checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                    missingPermissions.add(requiredPermission);
                }
            }
        }
        return missingPermissions.toArray(new String[0]);
    }

    private String[] getRequiredPermissions() {
        int targetSdkVersion = getApplicationInfo().targetSdkVersion;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && targetSdkVersion >= Build.VERSION_CODES.S) {
            return new String[]{Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) {
            return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        } else return new String[]{Manifest.permission.ACCESS_COARSE_LOCATION};
    }

    private void permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler();
        }
    }

    private boolean areLocationServicesEnabled() {
        LocationManager locationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            Timber.e("could not get location manager");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled();
        } else {
            boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            return isGpsEnabled || isNetworkEnabled;
        }
    }

    private boolean checkLocationServices() {
        if (!areLocationServicesEnabled()) {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location services are not enabled")
                    .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                    .setPositiveButton("Enable", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // if this button is clicked, just close
                            // the dialog box and do nothing
                            dialog.cancel();
                        }
                    })
                    .create()
                    .show();
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        // Check if all permission were granted
        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            permissionsGranted();
        } else {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Location permission is required for scanning Bluetooth peripherals")
                    .setMessage("Please grant permissions")
                    .setPositiveButton("Retry", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialogInterface.cancel();
                            checkPermissions();
                        }
                    })
                    .create()
                    .show();
        }
    }

    private void initBluetoothHandler()
    {
        BluetoothServer.getInstance(getApplicationContext());
    }

    /**
     * section for broadcast
     */

    // new in part 2
    private final BroadcastReceiver advertiserStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(BluetoothServer.BLUETOOTH_SERVER_ADVERTISER_EXTRA);
            if (dataStatus == null) return;
            if (dataStatus.equals("ON")) {
                advertisingActive.setChecked(true);
            } else {
                advertisingActive.setChecked(false);
            }
        }
    };

    // new in part 2
    private final BroadcastReceiver connectionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(BluetoothServer.BLUETOOTH_SERVER_CONNECTION_EXTRA);
            if (dataStatus == null) return;
            if (dataStatus.contains("connected")) {
                deviceConnected.setChecked(true);
            } else {
                deviceConnected.setChecked(false);
            }
            //String newConnectionLog = dataStatus + "\n" + connectionLog.getText().toString();
            //connectionLog.setText(newConnectionLog);
        }
    };

    // new in part 2
    private final BroadcastReceiver subscriptionStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(BluetoothServer.BLUETOOTH_SERVER_SUBSCRIPTION_EXTRA);
            if (dataStatus == null) return;
            if (dataStatus.contains("enabled")) {
                subscriptionsEnabled.setChecked(true);
            } else {
                subscriptionsEnabled.setChecked(false);
            }
        }
    };

    // new in part 3
    private final BroadcastReceiver batteryLevelStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(BatteryService.BLUETOOTH_SERVER_BATTERY_LEVEL_EXTRA);
            if (dataStatus == null) return;
            String resultString = "The remaining battery level is " +
                    dataStatus + " %";
            batteryLevel.setText(resultString);
        }
    };

    private final BroadcastReceiver connectedDevicesStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(BluetoothServer.BLUETOOTH_SERVER_CONNECTED_DEVICES_EXTRA);
            if (dataStatus == null) return;
            String resultString = "These devices are connected:\n" +
                    dataStatus;
            connectedDevices.setText(resultString);
        }
    };

    // new in chat
    private final BroadcastReceiver receivedMessageStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String dataStatus = intent.getStringExtra(ChatService.BLUETOOTH_CHAT_EXTRA);
            if (dataStatus == null) return;
            receivedMessage.setText(dataStatus);
        }
    };
}