package com.montefiore.gaulthiergain.adhoclib.bluetooth;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclib.bluetoothListener.ConnectionListener;
import com.montefiore.gaulthiergain.adhoclib.exceptions.BluetoothBadDuration;
import com.montefiore.gaulthiergain.adhoclib.exceptions.BluetoothDeviceException;

import java.util.HashMap;
import java.util.Set;

/**
 * Created by gaulthiergain on 25/10/17.
 */

public class BluetoothManager {

    private final boolean v;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private final String TAG = "[AdHoc][" + getClass().getName() + "]";

    private HashMap<String, BluetoothAdHocDevice> hashMapBluetoothDevice;

    private ConnectionListener connectionListener;

    public BluetoothManager(Context context, boolean verbose)
            throws BluetoothDeviceException {
        this.context = context;
        this.v = verbose;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device does not support Bluetooth
            throw new BluetoothDeviceException("Error device does not support Bluetooth");
        } else {
            // Device supports Bluetooth
            hashMapBluetoothDevice = new HashMap<>();
        }
    }

    public boolean isEnabled() {
        return bluetoothAdapter.isEnabled();
    }

    public boolean enable() {
        return bluetoothAdapter.enable();
    }

    public boolean disable() {
        return bluetoothAdapter.disable();
    }

    public HashMap<String, BluetoothAdHocDevice> getPairedDevices() {
        if(v) Log.d(TAG, "getPairedDevices()");

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
        HashMap<String, BluetoothAdHocDevice> hashMapBluetoothPairedDevice = new HashMap<>();

        if (pairedDevices.size() > 0) {
            // Add each paired device into a hashMap
            for (BluetoothDevice device : pairedDevices) {
                if(v) Log.d(TAG, "DeviceName: " + device.getName() +
                        " - DeviceHardwareAddress: " + device.getAddress());
                hashMapBluetoothPairedDevice.put(device.getAddress(),
                        new BluetoothAdHocDevice(device));
            }
        }
        return hashMapBluetoothPairedDevice;
    }

    public void discovery(ConnectionListener listener) {
        if(v) Log.d(TAG, "discovery()");

        // Check if the device is already "discovering". If it is, then cancel discovery.
        cancelDiscovery();

        this.connectionListener = listener;

        // Start Discovery
        bluetoothAdapter.startDiscovery();

        // Register for broadcasts when a device is discovered.
        context.getApplicationContext().registerReceiver(mReceiver, new IntentFilter(
                BluetoothDevice.ACTION_FOUND));
        context.getApplicationContext().registerReceiver(mReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_DISCOVERY_STARTED));
        context.getApplicationContext().registerReceiver(mReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

        // Change scan mode
        context.getApplicationContext().registerReceiver(mReceiver, new IntentFilter(
                BluetoothAdapter.ACTION_SCAN_MODE_CHANGED));
    }

    public void cancelDiscovery() {
        if(v) Log.d(TAG, "cancelDiscovery()");

        // Check if the device is already "discovering". If it is, then cancel discovery.
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // Get the BluetoothDevice object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                connectionListener.onDeviceFound(device);

                // Add into the hashMap
                if (!hashMapBluetoothDevice.containsKey(device.getAddress())) {
                    if(v) Log.d(TAG, "DeviceName: " + device.getName() +
                            " - DeviceHardwareAddress: " + device.getAddress());
                    hashMapBluetoothDevice.put(device.getAddress(), new BluetoothAdHocDevice(device,
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)));
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                if(v) Log.d(TAG, "ACTION_DISCOVERY_STARTED");
                // Clear the hashMap
                hashMapBluetoothDevice.clear();
                // Listener onDiscoveryStarted
                connectionListener.onDiscoveryStarted();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                if(v) Log.d(TAG, "ACTION_DISCOVERY_FINISHED");
                // Listener onDiscoveryFinished
                connectionListener.onDiscoveryFinished(hashMapBluetoothDevice);
            }else if(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED.equals(action)){
                if(v) Log.d(TAG, "ACTION_SCAN_MODE_CHANGED");
                // Listener onScanModeChange
                int currentMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, 0);
                int oldMode = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_SCAN_MODE, 0);
                connectionListener.onScanModeChange(currentMode, oldMode);
            }
        }
    };

    public void unregisterDiscovery() throws IllegalArgumentException {
        if(v) Log.d(TAG, "unregisterDiscovery()");
        context.getApplicationContext().unregisterReceiver(mReceiver);
    }

    public void enableDiscovery(int duration) throws BluetoothBadDuration {
        if(duration < 0 || duration > 3600){
            throw new BluetoothBadDuration("Duration must be between 0 and 3600 second(s)");
        }

        Intent discoverableIntent =
                new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
       context.startActivity(discoverableIntent);
    }

    public HashMap<String, BluetoothAdHocDevice> getHashMapBluetoothDevice() {
        return hashMapBluetoothDevice;
    }
}
