package com.montefiore.gaulthiergain.adhoclibrary.network.datalinkmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.montefiore.gaulthiergain.adhoclibrary.appframework.Config;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerAction;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerAdapter;
import com.montefiore.gaulthiergain.adhoclibrary.appframework.ListenerApp;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.DeviceException;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.exceptions.GroupOwnerBadValue;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.AdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.DiscoveryListener;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.Service;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.ServiceMessageListener;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.udpwifi.UdpPDU;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.udpwifi.UdpPeers;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.wifi.ConnectionWifiListener;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.wifi.WifiAdHocDevice;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.wifi.WifiAdHocManager;
import com.montefiore.gaulthiergain.adhoclibrary.network.aodv.Constants;
import com.montefiore.gaulthiergain.adhoclibrary.network.aodv.TypeAodv;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.util.Header;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.util.MessageAdHoc;

import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class WrapperWifiUdp extends AbstractWrapper implements IWrapperWifi {

    private static final String TAG = "[AdHoc][WrapperWifiUdp]";
    private static final int TIMER_ACK = 2000;

    private int serverPort;
    private int remotePort;
    private UdpPeers udpPeers;
    private String ownIpAddress;
    private boolean isGroupOwner;
    private HashSet<String> ackSet;
    private WifiAdHocManager wifiAdHocManager;
    private HashMap<String, Long> helloMessages;
    private HashMap<String, AdHocDevice> neighbors;

    WrapperWifiUdp(boolean verbose, Context context, Config config,
                   HashMap<String, AdHocDevice> mapAddressDevice,
                   final ListenerApp listenerApp, final ListenerDataLink listenerDataLink) {
        super(verbose, config, mapAddressDevice, listenerApp, listenerDataLink);

        this.type = Service.WIFI;
        if (WifiAdHocManager.isWifiEnabled(context)) {
            this.wifiAdHocManager = new WifiAdHocManager(v, context, config.getServerPort(), initConnectionListener(),
                    new WifiAdHocManager.WifiDeviceInfosListener() {
                        @Override
                        public void getDeviceInfos(String name, String mac) {
                            ownName = name;
                            ownMac = mac;
                            listenerDataLink.initInfos(ownMac, ownName);
                        }
                    });
            this.init(config, context);
        } else {
            this.enabled = false;
        }
    }

    /*-------------------------------------Override methods---------------------------------------*/

    @Override
    void connect(short attempts, AdHocDevice device) throws DeviceException {

        String label = getLabelByMac(device.getMacAddress());
        if (label == null) {
            wifiAdHocManager.connect(device.getMacAddress());
        } else {
            if (!neighbors.containsKey(label)) {
                wifiAdHocManager.connect(device.getMacAddress());
            } else {
                throw new DeviceException(device.getDeviceName()
                        + "(" + device.getMacAddress() + ") is already connected");
            }
        }
    }

    @Override
    void stopListening() {
        udpPeers.stopServer();
    }

    @Override
    void discovery(final DiscoveryListener discoveryListener) {
        wifiAdHocManager.discovery(new DiscoveryListener() {
            @Override
            public void onDiscoveryStarted() {
                discoveryListener.onDiscoveryStarted();
            }

            @Override
            public void onDiscoveryFailed(Exception exception) {
                discoveryListener.onDiscoveryFailed(exception);
            }

            @Override
            public void onDeviceDiscovered(AdHocDevice device) {

                //todo refactor this

                if (!mapMacDevices.containsKey(device.getMacAddress())) {
                    if (v)
                        Log.d(TAG, "Add " + device.getMacAddress() + " into mapMacDevices");
                    mapMacDevices.put(device.getMacAddress(), device);
                }

                discoveryListener.onDeviceDiscovered(device);
            }

            @Override
            public void onDiscoveryCompleted(HashMap<String, AdHocDevice> mapNameDevice) {

                if (wifiAdHocManager == null) {
                    discoveryListener.onDiscoveryFailed(
                            new DeviceException("Unable to complete the discovery due to wifi connectivity"));
                } else {
                    // Add device into mapMacDevices
                    for (AdHocDevice device : mapNameDevice.values()) {
                        if (!mapMacDevices.containsKey(device.getMacAddress())) {
                            if (v)
                                Log.d(TAG, "Add " + device.getMacAddress() + " into mapMacDevices");
                            mapMacDevices.put(device.getMacAddress(), device);
                        }
                    }

                    if (listenerBothDiscovery != null) {
                        discoveryListener.onDiscoveryCompleted(mapMacDevices);
                    }

                    discoveryCompleted = true;

                    wifiAdHocManager.unregisterDiscovery();
                }
            }
        });
    }

    @Override
    HashMap<String, AdHocDevice> getPaired() {
        // Not used in wifi context
        return null;
    }

    @Override
    void enable(Context context, int duration, ListenerAdapter listenerAdapter) {
        this.wifiAdHocManager = new WifiAdHocManager(v, context, serverPort, initConnectionListener(),
                new WifiAdHocManager.WifiDeviceInfosListener() {
                    @Override
                    public void getDeviceInfos(String name, String mac) {
                        ownName = name;
                        ownMac = mac;
                        listenerDataLink.initInfos(ownMac, ownName);
                    }
                });
        wifiAdHocManager.enable();
        wifiAdHocManager.onEnableWifi(listenerAdapter);
        enabled = true;
    }

    @Override
    void disable() {
        // Clear data structure if adapter is disabled
        neighbors.clear();
        ackSet.clear();
        helloMessages.clear();

        wifiAdHocManager.disable();
        wifiAdHocManager = null;
        enabled = false;
    }

    @Override
    void updateContext(Context context) {
        wifiAdHocManager.updateContext(context);
    }

    @Override
    void unregisterConnection() {
        wifiAdHocManager.unregisterConnection();
    }

    @Override
    void init(Config config, Context context) {
        this.isGroupOwner = false;
        this.neighbors = new HashMap<>();
        this.helloMessages = new HashMap<>();
        this.ackSet = new HashSet<>();
        this.serverPort = config.getServerPort();
        this.listenServer();
    }

    @Override
    void unregisterAdapter() {
        // Not used in wifi context
    }

    @Override
    void resetDeviceName() {
        wifiAdHocManager.resetDeviceName();
    }

    @Override
    boolean updateDeviceName(String name) {
        return wifiAdHocManager.updateDeviceName(name);
    }

    @Override
    String getAdapterName() {
        return wifiAdHocManager.getAdapterName();
    }

    @Override
    void sendMessage(MessageAdHoc msg, String label) {

        WifiAdHocDevice wifiDevice = (WifiAdHocDevice) neighbors.get(label);
        if (wifiDevice != null) {
            _sendMessage(msg, wifiDevice.getIpAddress());
        }
    }

    @Override
    boolean isDirectNeighbors(String address) {
        return neighbors.containsKey(address);
    }

    @Override
    boolean broadcast(MessageAdHoc message) {
        if (neighbors.size() > 0) {
            _sendMessage(message, "192.168.49.255");
            return true;
        }

        return false;
    }

    @Override
    void disconnectAll() {
        // Not used in this context
    }

    @Override
    void disconnect(String remoteDest) {
        // Not used in this context
    }

    @Override
    boolean broadcastExcept(MessageAdHoc message, String excludedAddress) {
        if (neighbors.size() > 0) {
            for (Map.Entry<String, AdHocDevice> entry : neighbors.entrySet()) {
                if (!entry.getKey().equals(excludedAddress)) {
                    WifiAdHocDevice wifiAdHocDevice = (WifiAdHocDevice) entry.getValue();
                    _sendMessage(message, wifiAdHocDevice.getIpAddress());
                }
            }
            return true;
        }
        return false;
    }

    public ArrayList<AdHocDevice> getDirectNeighbors() {
        return new ArrayList<>(neighbors.values());
    }

    /*--------------------------------------IWifi methods----------------------------------------*/

    @Override
    public void setGroupOwnerValue(int valueGroupOwner) throws GroupOwnerBadValue {

        if (valueGroupOwner < 0 || valueGroupOwner > 15) {
            throw new GroupOwnerBadValue("GroupOwner value must be between 0 and 15");
        }

        wifiAdHocManager.setValueGroupOwner(valueGroupOwner);
    }

    @Override
    public void removeGroup(ListenerAction listenerAction) {
        wifiAdHocManager.removeGroup(listenerAction);
    }

    @Override
    public void cancelConnect(ListenerAction listenerAction) {
        wifiAdHocManager.cancelConnection(listenerAction);
    }

    @Override
    public boolean isWifiGroupOwner() {
        return isGroupOwner;
    }
    /*--------------------------------------Private methods---------------------------------------*/

    private void listenServer() {
        udpPeers = new UdpPeers(v, serverPort, json, label, new ServiceMessageListener() {
            @Override
            public void onMessageReceived(MessageAdHoc message) {
                try {
                    processMsgReceived(message);
                } catch (IOException e) {
                    listenerApp.processMsgException(e);
                }
            }

            @Override
            public void onConnectionClosed(String remoteAddress) {
                // If remote node has disabled wifi
                String label = getLabelByIP(remoteAddress);
                AdHocDevice adHocDevice = null;

                if (v) Log.w(TAG, label + " has wifi doisconnected");
                if (neighbors.containsKey(label)) {
                    adHocDevice = neighbors.get(label);
                    neighbors.remove(label);
                }

                if (helloMessages.containsKey(label)) {
                    helloMessages.remove(label);
                }

                if (adHocDevice != null) {
                    listenerApp.onConnectionClosed(adHocDevice);
                }
            }

            @Override
            public void onConnection(String remoteAddress) {
                // Ignored in udp context
            }

            @Override
            public void onConnectionFailed(Exception e) {
                // Ignored in udp context
            }

            @Override
            public void onMsgException(Exception e) {
                listenerApp.processMsgException(e);
            }
        });

        //Run timers for HELLO messages
        timerHello(Constants.HELLO_PACKET_INTERVAL);
        timerHelloCheck(Constants.HELLO_PACKET_INTERVAL_SND);
    }

    private void timerConnectMessage(final MessageAdHoc message, final String dest, final int time) {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

                if (ackSet.contains(dest)) {
                    _sendMessage(message, dest);
                    // Restart timer if no ACK is received
                    timerConnectMessage(message, dest, time);
                }
            }
        }, time);
    }

    private void _sendMessage(final MessageAdHoc msg, final String address) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                InetAddress inetAddress;
                try {
                    inetAddress = InetAddress.getByName(address);
                    udpPeers.sendMessageTo(msg, inetAddress, remotePort);
                } catch (UnknownHostException e) {
                    listenerApp.processMsgException(e);
                }
            }
        }).start();
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        // Used to avoid updating views in other threads than the main thread
        public void handleMessage(Message msg) {
            // Used handler to avoid updating views in other threads than the main thread
            AdHocDevice adHocDevice = (AdHocDevice) msg.obj;

            listenerApp.onConnectionClosed(adHocDevice);

            // If connectionFlooding option is enable, flood disconnect events
            if (connectionFlooding) {
                String id = adHocDevice.getLabel() + System.currentTimeMillis();
                setFloodEvents.add(id);
                Header header = new Header(AbstractWrapper.DISCONNECT_BROADCAST,
                        adHocDevice.getMacAddress(), adHocDevice.getLabel(), adHocDevice.getDeviceName(),
                        adHocDevice.getType());
                broadcastExcept(new MessageAdHoc(header, id), adHocDevice.getLabel());
            }
        }
    };

    /**
     * Method allowing to launch the timer to send HELLO messages between peers every TIME (ms).
     *
     * @param time an integer value which represents the period of the timer.
     */
    private void timerHello(final int time) {
        Timer timerHelloPackets = new Timer();
        timerHelloPackets.schedule(new TimerTask() {
            @Override
            public void run() {
                broadcastExcept(new MessageAdHoc(new Header(TypeAodv.HELLO.getType(), label, ownName)), label);
                timerHello(time);
            }
        }, time);
    }

    /**
     * Method allowing to launch the timer to check every TIME (ms) if a neighbor is broken.
     *
     * @param time an integer value which represents the period of the timer.
     */
    private void timerHelloCheck(final int time) {
        Timer timerHelloPackets = new Timer();
        timerHelloPackets.schedule(new TimerTask() {
            @Override
            public void run() {

                // Check peers
                Iterator<Map.Entry<String, Long>> iter = helloMessages.entrySet().iterator();
                while (iter.hasNext()) {

                    Map.Entry<String, Long> entry = iter.next();
                    long upTime = (System.currentTimeMillis() - entry.getValue());
                    Log.d(TAG, "UpTime: " + upTime);
                    if (upTime > Constants.HELLO_PACKET_INTERVAL_SND) {

                        if (v)
                            Log.d(TAG, "Neighbor " + entry.getKey() + " is down for " + upTime);

                        // Remove the hello message
                        iter.remove();
                        try {
                            leftPeer(entry.getKey());
                        } catch (IOException e) {
                            listenerApp.processMsgException(e);
                        }
                    }
                }
                timerHelloCheck(time);
            }
        }, time);
    }

    private void leftPeer(String label) throws IOException {

        // Process broken link in protocol
        listenerDataLink.brokenLink(label);

        // Callback via handler
        WifiAdHocDevice wifiDevice = (WifiAdHocDevice) neighbors.get(label);
        if (wifiDevice != null) {
            // Used handler to avoid using runOnUiThread in main app
            mHandler.obtainMessage(1, wifiDevice).sendToTarget();

            // Remove the remote device from a neighbors
            neighbors.remove(label);
        }
    }

    private void processMsgReceived(final MessageAdHoc message) throws IOException {

        switch (message.getHeader().getType()) {
            case CONNECT_SERVER: {

                Log.d(TAG, "Receive " + message);

                boolean event = false;

                // Receive UDP header from remote host
                Header header = message.getHeader();

                // Extract PDU
                UdpPDU udpPDU = (UdpPDU) message.getPdu();

                String destAddress = udpPDU.getHostAddress();
                remotePort = udpPDU.getPort();

                // If ownIpAddress is unknown, init the field
                if (ownIpAddress == null) {
                    ownIpAddress = destAddress;
                }

                // Send message to remote host with own info
                _sendMessage(new MessageAdHoc(new Header(CONNECT_CLIENT, ownIpAddress,
                        ownMac, label, ownName)), header.getAddress());

                WifiAdHocDevice device = new WifiAdHocDevice(header.getLabel(), header.getMac(),
                        header.getName(), type, header.getAddress());

                if (!neighbors.containsKey(header.getLabel())) {
                    event = true;
                }

                if (event) {
                    // Add remote host to neighbors
                    neighbors.put(header.getLabel(), device);

                    // Callback connection
                    listenerApp.onConnection(device);

                    // If connectionFlooding option is enable, flood connect events
                    if (connectionFlooding) {
                        String id = header.getLabel() + System.currentTimeMillis();
                        setFloodEvents.add(id);
                        header.setType(AbstractWrapper.CONNECT_BROADCAST);
                        broadcastExcept(new MessageAdHoc(header, id), header.getLabel());
                    }
                }

                break;
            }
            case CONNECT_CLIENT: {

                Log.d(TAG, "Receive " + message);

                boolean event = false;

                // Receive UDP header from remote host
                Header header = message.getHeader();

                // Update the ackSet for reliable transmission
                if (ackSet.contains(header.getAddress())) {
                    ackSet.remove(header.getAddress());
                } else {
                    break;
                }

                WifiAdHocDevice device = new WifiAdHocDevice(header.getLabel(), header.getMac(),
                        header.getName(), type, header.getAddress());

                if (!neighbors.containsKey(header.getLabel())) {
                    event = true;
                }

                if (event) {
                    // Add remote host to neighbors
                    neighbors.put(header.getLabel(), device);

                    // Callback connection
                    listenerApp.onConnection(device);

                    // If connectionFlooding option is enable, flood connect events
                    if (connectionFlooding) {
                        String id = header.getLabel() + System.currentTimeMillis();
                        setFloodEvents.add(id);
                        header.setType(AbstractWrapper.CONNECT_BROADCAST);
                        broadcastExcept(new MessageAdHoc(header, id), header.getLabel());
                    }
                }

                break;
            }
            case CONNECT_BROADCAST: {
                if (checkFloodEvent(message)) {

                    // Get Messsage Header
                    Header header = message.getHeader();

                    // Remote connection happens in other node
                    listenerApp.onConnection(new AdHocDevice(header.getLabel(), header.getMac(),
                            header.getName(), type, false));
                }

                break;
            }
            case DISCONNECT_BROADCAST: {
                if (checkFloodEvent(message)) {

                    // Get Messsage Header
                    Header header = message.getHeader();

                    // Remote connection is closed in other node
                    listenerApp.onConnectionClosed(new AdHocDevice(header.getLabel(), header.getMac(),
                            header.getName(), type, false));
                }
                break;
            }
            case BROADCAST: {
                // Get Messsage Header
                Header header = message.getHeader();

                listenerApp.onReceivedData(new AdHocDevice(header.getLabel(), header.getMac(),
                        header.getName(), type), message.getPdu());
                break;
            }
            case Constants.HELLO: {

                if (v) Log.d(TAG, "Received Hello message from " + message.getHeader().getName());

                // Add helloMessages messages to hashmap
                helloMessages.put(message.getHeader().getLabel(), System.currentTimeMillis());
                break;
            }
            default:
                // Handle messages in protocol scope
                listenerDataLink.processMsgReceived(message);
        }
    }

    private ConnectionWifiListener initConnectionListener() {
        return new ConnectionWifiListener() {
            @Override
            public void onConnectionStarted() {
                if (v) Log.d(TAG, "Connection Started");
            }

            @Override
            public void onConnectionFailed(Exception e) {
                listenerApp.onConnectionFailed(e);
            }

            @Override
            public void onGroupOwner(InetAddress groupOwnerAddress) {
                ownIpAddress = groupOwnerAddress.getHostAddress();
                isGroupOwner = true;
                if (v) Log.d(TAG, "onGroupOwner-> own IP: " + ownIpAddress);
                wifiAdHocManager.startRegistration();
            }

            @Override
            public void onClient(final InetAddress groupOwnerAddress, final InetAddress address) {

                ownIpAddress = address.getHostAddress();
                isGroupOwner = false;
                if (v)
                    Log.d(TAG, "onClient-> GroupOwner IP: " + groupOwnerAddress.getHostAddress());
                if (v) Log.d(TAG, "onClient-> own IP: " + ownIpAddress);
                ackSet.add(groupOwnerAddress.getHostAddress());

                wifiAdHocManager.discoverService(new WifiAdHocManager.ServiceDiscoverListener() {
                    @Override
                    public void onServiceCompleted(int port) {
                        remotePort = port;
                        Log.d(TAG, "Remote port is " + remotePort);
                        timerConnectMessage(new MessageAdHoc(
                                        new Header(CONNECT_SERVER, ownIpAddress, ownMac, label, ownName),
                                        new UdpPDU(serverPort, groupOwnerAddress.getHostAddress())),
                                groupOwnerAddress.getHostAddress(), TIMER_ACK);
                    }
                });
            }
        };
    }



    private String getLabelByMac(String mac) {
        for (Map.Entry<String, AdHocDevice> entry : neighbors.entrySet()) {
            if (mac.equals(entry.getValue().getMacAddress())) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String getLabelByIP(String ip) {
        for (Map.Entry<String, AdHocDevice> entry : neighbors.entrySet()) {
            WifiAdHocDevice wifiDevice = (WifiAdHocDevice) entry.getValue();
            if (ip.equals(wifiDevice.getIpAddress())) {
                return entry.getKey();
            }
        }
        return null;
    }
}
