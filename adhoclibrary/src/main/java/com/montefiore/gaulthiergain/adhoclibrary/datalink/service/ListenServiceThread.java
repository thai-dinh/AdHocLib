package com.montefiore.gaulthiergain.adhoclibrary.datalink.service;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.datalink.network.AdHocSocketWifi;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.network.NetworkObject;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;

/**
 * <p>This class allows to receive messages in background from the remote connected host.</p>
 *
 * @author Gaulthier Gain
 * @version 1.0
 */
class ListenServiceThread extends Thread {
    private static final String TAG = "[AdHoc][ListenService]";
    private final boolean v;
    private final NetworkObject network;
    private final Handler handler;

    /**
     * @param verbose a boolean value to set the debug/verbose mode.
     * @param network a Network object which manages the connection with the remote host.
     * @param handler a Handler object which allows to send and process {@link android.os.Message}
     *                and Runnable objects associated with a thread's.
     */
    ListenServiceThread(boolean verbose, NetworkObject network, Handler handler) {
        this.v = verbose;
        this.network = network;
        this.handler = handler;
    }

    /**
     * Method allowing to receive messages in background from the remote connected host.
     */
    @Override
    public void run() {
        if (v) Log.d(TAG, "start Listening ...");

        MessageAdHoc message;
        while (true) {
            try {
                if (v) Log.d(TAG, "Waiting response from server ...");

                // Get response MessageAdHoc
                message = (MessageAdHoc) network.receiveObjectStream();
                if (v) Log.d(TAG, "Response: " + message);
                handler.obtainMessage(Service.MESSAGE_READ, message).sendToTarget();
            } catch (IOException e) {
                if (network.getISocket() != null) {

                    if (network.getISocket() instanceof AdHocSocketWifi) {
                        // Notify handler
                        String handleConnectionAborted[] = new String[2];
                        // Get remote device name
                        handleConnectionAborted[0] = "name"; //TODO
                        // Get remote device address
                        handleConnectionAborted[1] = network.getISocket().getRemoteSocketAddress();
                        handler.obtainMessage(Service.CONNECTION_ABORTED, handleConnectionAborted).sendToTarget();
                    } else {
                        // Get Socket
                        BluetoothSocket socket = (BluetoothSocket) network.getISocket().getSocket();
                        String handleConnectionAborted[] = new String[2];
                        // Get remote device name
                        handleConnectionAborted[0] = socket.getRemoteDevice().getName();
                        // Get remote device address
                        handleConnectionAborted[1] = socket.getRemoteDevice().getAddress();
                        // Notify handler
                        handler.obtainMessage(Service.CONNECTION_ABORTED, handleConnectionAborted).sendToTarget();
                    }

                    network.closeConnection();
                }
                break;
            } catch (ClassNotFoundException e) {
                e.printStackTrace(); //TODO update
            }
        }
    }

    /**
     * Method allowing to close the remote connection.
     */
    void cancel() {
        if (network.getISocket() != null) { //TODO !network.getSocket().isConnected()
            network.closeConnection();
        }
    }
}