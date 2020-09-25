package edu.osu.cse.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

public class BLEManager {

    private BLEManager() {
    }

    static BLEManager manager = new BLEManager();

    public static BLEManager getManager() {
        return manager;
    }

    BluetoothLeAdvertiser advertiser = null;
    AdvertiseCallback advertisingCallback = null;

    private void enableBLE(Context context) {
        BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.enable();
        }
        mBluetoothAdapter.setName(BLEManager.class.getName());
    }

    public void startBroadcast(Context context, int advertiseMode, int txPowerLevel) throws Exception {
        enableBLE(context);

        //if( !BluetoothAdapter.getDefaultAdapter().isMultipleAdvertisementSupported() ) {
        //    Toast.makeText( context, "Multiple advertisement not supported", Toast.LENGTH_SHORT ).show();
        //    throw new Exception("ultiple advertisement not supported");
        //}

        advertiser = BluetoothAdapter.getDefaultAdapter().getBluetoothLeAdvertiser();
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(advertiseMode)
                .setTxPowerLevel(txPowerLevel)
                .setConnectable(false)
                .build();
        ParcelUuid pUuid = new ParcelUuid(UUID.fromString("00000000-1111-2222-3333-444444444444"));

        byte[] bdata = new byte[16];
        for (int i = 0; i < bdata.length; i++) {
            bdata[i] = (byte) i;
        }
        AdvertiseData data = new AdvertiseData.Builder()
                //.setIncludeDeviceName( true )
                //.addServiceUuid( pUuid )
                .addManufacturerData(0, bdata)
                .build();

        advertisingCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                Log.e("BLEManager", "Advertising onStartSuccess!!!");
                super.onStartSuccess(settingsInEffect);
            }

            @Override
            public void onStartFailure(int errorCode) {
                Log.e("BLEManager", "Advertising onStartFailure: " + errorCode);
                super.onStartFailure(errorCode);
            }
        };

        advertiser.startAdvertising(settings, data, advertisingCallback);
    }

    public void stop() {
        if (advertiser != null && advertisingCallback != null)
            advertiser.stopAdvertising(advertisingCallback);
    }
}
