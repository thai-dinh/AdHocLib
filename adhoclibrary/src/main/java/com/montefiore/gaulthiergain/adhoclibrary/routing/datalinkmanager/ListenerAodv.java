package com.montefiore.gaulthiergain.adhoclibrary.routing.datalinkmanager;

import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.RemoteConnection;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.util.HashMap;

public interface ListenerAodv {

    /**
     * Callback when the discovery is completed.
     * @param mapAddressDevice
     */
    void onDiscoveryCompleted(HashMap<String, DiscoveredDevice> mapAddressDevice);

    /**
     * Callback when the getPairedDevices is completed.
     */
    void onPairedCompleted();

    /**
     * Callback when a RREQ message is received.
     *
     * @param message a MessageAdHoc object which represents a message exchanged between nodes.
     */
    void receivedRREQ(MessageAdHoc message);

    /**
     * Callback when a RREQ message is received.
     *
     * @param message a MessageAdHoc object which represents a message exchanged between nodes.
     */
    void receivedRREP(MessageAdHoc message);

    /**
     * Callback when a RREP GRATUITOUS message is received.
     *
     * @param message a MessageAdHoc object which represents a message exchanged between nodes.
     */
    void receivedRREP_GRAT(MessageAdHoc message);

    /**
     * Callback when a RREQ message is received.
     *
     * @param message a MessageAdHoc object which represents a message exchanged between nodes.
     */
    void receivedRERR(MessageAdHoc message);

    /**
     * Callback when a RREQ message is received.
     *
     * @param message a MessageAdHoc object which represents a message exchanged between nodes.
     */
    void receivedDATA(MessageAdHoc message);

    /**
     * Callback when the RREQ timer for routing table is called.
     *
     * @param destAddr a String value which represents the destination address.
     * @param retry    an integer value which represents the retries of the RREQ Timer.
     */
    void timerExpiredRREQ(String destAddr, int retry);

    /**
     * Callback when exception occurs.
     *
     * @param e an Exception object which represents the exception.
     */
    void catchException(Exception e);

    void onConnectionClosed(RemoteConnection remoteDevice);

    void onConnection(RemoteConnection remoteDevice);
}