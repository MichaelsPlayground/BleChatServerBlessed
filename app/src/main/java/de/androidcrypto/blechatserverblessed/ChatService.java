package de.androidcrypto.blechatserverblessed;

import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ;
import static android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
import static android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY;
import static com.welie.blessed.BluetoothBytesParser.FORMAT_UINT8;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.welie.blessed.BluetoothBytesParser;
import com.welie.blessed.BluetoothCentral;
import com.welie.blessed.BluetoothPeripheralManager;
import com.welie.blessed.GattStatus;
import com.welie.blessed.ReadResponse;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import timber.log.Timber;

// complete class new in chat
class ChatService extends BaseService {

    public static final String BLUETOOTH_CHAT = "androidcrypto.bluetooth.chat";
    public static final String BLUETOOTH_CHAT_EXTRA = "androidcrypto.bluetooth.chat.extra";

    public static final String BLUETOOTH_SERVER_BATTERY_LEVEL = "androidcrypto.bluetoothserver.batterylevel";
    public static final String BLUETOOTH_SERVER_BATTERY_LEVEL_EXTRA = "androidcrypto.bluetoothserver.batterylevel.extra";
    Context mContext;

    private static final UUID BLUETOOTH_CHAT_SERVICE_UUID = UUID.fromString("00008dc1-c6a2-484f-9dae-93a63ad832a5");
    public static final UUID BLUETOOTH_CHAT_CHARACTERISTIC_UUID = UUID.fromString("00008dc2-c6a2-484f-9dae-93a63ad832a5");

    private @NotNull final BluetoothGattService service = new BluetoothGattService(BLUETOOTH_CHAT_SERVICE_UUID, SERVICE_TYPE_PRIMARY);
    private @NotNull final BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(BLUETOOTH_CHAT_CHARACTERISTIC_UUID, PROPERTY_READ | PROPERTY_NOTIFY | PROPERTY_WRITE_NO_RESPONSE, PERMISSION_READ | PERMISSION_WRITE);
    private @NotNull final Handler handler = new Handler(Looper.getMainLooper());
    private @NotNull final Runnable notifyRunnable = this::notifyChatMessage;
    private String currentMessage = "hello";
    private int currentBL = 100; // battery level goes from 100 to 0

    public ChatService(@NotNull BluetoothPeripheralManager peripheralManager, Context context) {
        super(peripheralManager);
        service.addCharacteristic(characteristic);
        characteristic.addDescriptor(getClientCharacteristicConfigurationDescriptor());
        mContext = context;
        // start the notifying on startup
        //notifyBatteryLevel();
    }

    @Override
    public void onCentralDisconnected(@NotNull BluetoothCentral central) {
        if (noCentralsConnected()) {
            // note: as the battery service should run without any connection or interaction this could get commented out
            // stopNotifying();
        }
    }

    @Override
    public ReadResponse onCharacteristicRead(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(BLUETOOTH_CHAT_CHARACTERISTIC_UUID)) {
            byte[] chatMessage = currentMessage.getBytes(StandardCharsets.UTF_8);
            return new ReadResponse(GattStatus.SUCCESS, chatMessage);
        }
        return super.onCharacteristicRead(central, characteristic);
    }

    @Override
    public GattStatus onCharacteristicWrite(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic, byte[] value) {
        if (characteristic.getUuid().equals(BLUETOOTH_CHAT_CHARACTERISTIC_UUID)) {
            currentMessage = new String(value, StandardCharsets.UTF_8);
            notifyCharacteristicChanged(value, characteristic);
        }
        return super.onCharacteristicWrite(central, characteristic, value);
    }

    @Override
    public void onCharacteristicWriteCompleted(@NonNull BluetoothCentral central, @NonNull BluetoothGattCharacteristic characteristic, @NonNull byte[] value) {
        Timber.d("new chat message updated to %s", new String(value, StandardCharsets.UTF_8));
        super.onCharacteristicWriteCompleted(central, characteristic, value);
    }

    @Override
    public void onNotifyingEnabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(BLUETOOTH_CHAT_CHARACTERISTIC_UUID)) {
            // note: as the battery service should run without any connection or interaction this could get commented out
            // notifyBatteryLevel();
        }
    }

    @Override
    public void onNotifyingDisabled(@NotNull BluetoothCentral central, @NotNull BluetoothGattCharacteristic characteristic) {
        if (characteristic.getUuid().equals(BLUETOOTH_CHAT_CHARACTERISTIC_UUID)) {
            // note: as the battery service should run without any connection or interaction this could get commented out
            // stopNotifying();
        }
    }

    private void notifyChatMessage() {
        //final byte[] value = currentMessage.getBytes(StandardCharsets.UTF_8);
        //notifyCharacteristicChanged(value, characteristic);
    }

    /*
    private void notifyBatteryLevel() {
        currentBL += (int) - 1;
        final byte[] value = new byte[]{(byte) ((byte) currentBL & 0xFF)};
        notifyCharacteristicChanged(value, characteristic);
        sendBatteryLevelToUi(value);
        // stop the countdown on 0, in a real device this is not necessary as the device stops when battery is empty
        if (currentBL < 1) {
            handler.removeCallbacks(notifyRunnable);
        } else {
            handler.postDelayed(notifyRunnable, 1000);
        }
        Timber.i("new BL: %d", currentBL);
    }
*/

    private void sendBatteryLevelToUi(byte[] value) {
        BluetoothBytesParser parser = new BluetoothBytesParser(value);
        String valueString = parser.getIntValue(FORMAT_UINT8).toString();
        Intent intent = new Intent(BLUETOOTH_SERVER_BATTERY_LEVEL);
        intent.putExtra(BLUETOOTH_SERVER_BATTERY_LEVEL_EXTRA, valueString);
        mContext.sendBroadcast(intent);
    }

    private void stopNotifying() {
        handler.removeCallbacks(notifyRunnable);
    }

    @Override
    public @NotNull BluetoothGattService getService() {
        return service;
    }

    @Override
    public String getServiceName() {
        return "Chat Service";
    }
}
