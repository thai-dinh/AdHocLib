package com.montefiore.gaulthiergain.adhoclibrary.datalink.service;

import com.montefiore.gaulthiergain.adhoclibrary.network.datalinkmanager.DataLinkManager;

public class AdHocDevice {

    protected final String deviceAddress;
    protected final String deviceName;
    protected int type;

    public AdHocDevice(String deviceAddress, String deviceName, int type) {
        this.deviceAddress = deviceAddress;
        this.deviceName = deviceName;
        this.type = type;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public int getType() {
        return type;
    }

    @Override
    public String toString() {
        return "AdHocDevice{" +
                "deviceAddress='" + deviceAddress + '\'' +
                ", deviceName='" + deviceName + '\'' +
                ", type=" + display(type) +
                '}';
    }

    private String display(int type) {
        if (type == DataLinkManager.BLUETOOTH) {
            return "Bluetooth";
        }

        return "Wifi";
    }
}
