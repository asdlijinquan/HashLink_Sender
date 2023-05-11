package com.example.hashlink_sender;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class MainActivity extends AppCompatActivity  {

    private static final int VPN_REQUEST_CODE = 0;
    private Button mVpnStartButton;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mVpnStartButton = findViewById(R.id.btn_vpn_start);

        final Properties config = loadConfig();
        final String destinationIp = config.getProperty("receiving_device_ip");
        final int destinationPort = Integer.parseInt(config.getProperty("destination_port"));


        mVpnStartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startVpnService();
            }
        });

    }

    //启动VPN
    private void startVpnService() {
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, PacketCaptureVpnService.class);
            startService(intent);
        }
    }


    //加载assets资源文件 获取对端ip以及端口
    private Properties loadConfig() {
        Properties config = new Properties();

        try {
            InputStream input = getAssets().open("config.properties");
            config.load(input);
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return config;
    }


}