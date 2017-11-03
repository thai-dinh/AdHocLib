package com.montefiore.gaulthiergain.adhoclib.fragment;


import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import com.montefiore.gaulthiergain.adhoclib.R;
import com.montefiore.gaulthiergain.adhoclib.wifi.WifiP2P;

import java.net.Socket;


public class TabFragment3 extends Fragment {

    private View fragmentView;
    private WifiP2P wifiP2P;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        fragmentView = inflater.inflate(R.layout.fragment_tab_fragment3, container, false);

        Handler mHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case 1:
                        Log.d("[AdHoc]", "String rcv: " + message.obj);
                        Toast.makeText(getContext(), (String) message.obj, Toast.LENGTH_LONG);
                        break;
                    default:
                        Log.d("[AdHoc]", "error");
                }

            }
        };

        wifiP2P = new WifiP2P(getContext(), mHandler);

        Button button = fragmentView.findViewById(R.id.buttonDiscoveryWifi);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                wifiP2P.discover();

            }
        });


        return fragmentView;
    }
}