package com.montefiore.gaulthiergain.adhoclibrary.datalink.udpwifi;

import android.os.Handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.Service;
import com.montefiore.gaulthiergain.adhoclibrary.util.MessageAdHoc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

class UdpClient extends Thread {

    private final int serverPort;
    private final Handler handler;
    private final MessageAdHoc msg;
    private final InetAddress serverAddr;

    UdpClient(Handler handler, MessageAdHoc msg, InetAddress serverAddr, int serverPort) {
        this.handler = handler;
        this.msg = msg;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
    }

    @Override
    public void run() {

        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] msgBytes = mapper.writeValueAsString(msg).getBytes();

            DatagramSocket datagramSocket = null;
            try {
                datagramSocket = new DatagramSocket();
                DatagramPacket datagramPacket = new DatagramPacket(msgBytes, msgBytes.length, serverAddr, serverPort);
                datagramSocket.send(datagramPacket);
            } catch (IOException e) {
                handler.obtainMessage(Service.MESSAGE_EXCEPTION, e).sendToTarget();
            } finally {
                if (datagramSocket != null) {
                    datagramSocket.close();
                }
            }

        } catch (IOException e) {
            handler.obtainMessage(Service.MESSAGE_EXCEPTION, e).sendToTarget();
        }
    }
}


