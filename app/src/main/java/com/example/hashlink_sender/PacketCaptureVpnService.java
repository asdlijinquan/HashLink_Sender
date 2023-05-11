package com.example.hashlink_sender;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;


import java.io.FileInputStream;
import java.io.IOException;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.net.UnknownHostException;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import java.util.Properties;



public class PacketCaptureVpnService extends VpnService {

    // 设置默认接收设备的 IP 和端口号
    public String receivingDeviceIp;

    private int destinationPort;

    public byte[][]hashChain = new byte[100][32];
    public int packetNumber=0;
    //读取文件部分
    private ParcelFileDescriptor vpnInterface;

    //加载资源配置文件
    private void loadConfig(Context context) {
        try {
            Properties props = new Properties();
            props.load(context.getAssets().open("config.properties"));
            receivingDeviceIp = props.getProperty("receiving_device_ip");

            destinationPort = Integer.parseInt(props.getProperty("destination_port", "2377"));

        } catch (IOException e) {
            e.printStackTrace();
            // Failed to load config, use default values
            receivingDeviceIp = "192.168.0.1";
            destinationPort = 2377;
        }
    }


    //启动命令集合
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        establishVpn();
        startPacketCapture();
        return START_STICKY;
    }

    private void establishVpn() {
        //    establishVpn(): 该方法负责创建一个VPN连接。它首先从config.properties文件中加载VPN连接的配置信息，
        //    然后通过Builder对象设置VPN的MTU和虚拟地址，以及要捕获数据包的应用程序的包名。最后，它通过调用establish()方法来建立VPN连接。
        loadConfig(this);

        Properties props = new Properties();
        try (InputStream inputStream = getAssets().open("config.properties")) {
            props.load(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Builder builder = new Builder();
        builder.setMtu(1500);
        //设置VPN的虚拟地址
        builder.addAddress("192.168.1.148", 24);
        builder.addRoute("0.0.0.0", 0);



        // 添加要捕获数据包的应用程序
        String packageNamesStr = props.getProperty("app.packageNames");
        if (packageNamesStr != null && !packageNamesStr.isEmpty()) {
            String[] packageNames = packageNamesStr.split(",");
            for (String packageName : packageNames) {
                try {
                    builder.addAllowedApplication(packageName);
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }

        //builder.allowBypass();
        vpnInterface = builder.establish();
    }

    private void startPacketCapture() {
        //        startPacketCapture(): 该方法在一个新的线程中运行，它从VPN接口中读取数据包，并将其传递给
        //        processAndReassemblePacket()方法进行处理和重新组装。如果处理后的数据包不为空，则将其发送到目标地址。
        //        这个方法通过一个死循环来持续捕获数据包。
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                    DatagramSocket outSocket = new DatagramSocket();
                    // vpnChannel.connect(null, 0);


                    byte[] buffer = new byte[32767];

                    while (true) {
                        // Capture packets and process them
                        int length = in.read(buffer);
                        if (length > 0) {
                            // Process the packet, hash IP header and data, and retransmit
                            byte[] originalPacket = Arrays.copyOf(buffer, length);
                            //处理0.0.0.0
                            InetAddress source_Address = extractSourceAddress(originalPacket);
                            InetAddress destination_Address = extractDestinationAddress(originalPacket);
                            int sourcePort = extractSourcePort(originalPacket);

                            if (!destination_Address.getHostAddress().equals(receivingDeviceIp) ||source_Address.getHostAddress().equals("0.0.0.0")) {
//                                || !source_Address.getHostAddress().equals("192.168.1.148")
                                continue;
                            }
//                            byte[] originalPacket = Arrays.copyOf(buffer, length);
                            byte[] modifiedPacket = processAndReassemblePacket(buffer, length);


                            if (modifiedPacket != null) {
                                // Extract destination IP and port from the original packet
//                                InetAddress destinationAddress = extractDestinationAddress(originalPacket);
                                int destinationPort = extractDestinationPort(originalPacket);
                                InetAddress sourceAddress = extractSourceAddress(originalPacket);


                                //！！！！！！！！！！！！！记得修改这里，VPN地址
                                // Check if the destination address is the specified address
//                                if (!destinationAddress.getHostAddress().equals(receivingDeviceIp) || !sourceAddress.getHostAddress().equals("192.168.1.148")||"0.0.0.0".equals(source_Address)) {
//                                    continue;
//                                }
                                // Send the modified packet
                                DatagramPacket outPacket = new DatagramPacket(modifiedPacket, modifiedPacket.length, destination_Address, destinationPort);
                                outSocket.send(outPacket);
                            }
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }



    @Override
    public void onDestroy() {
        //        onDestroy(): 该方法在VPN连接被销毁时被调用，它通过调用close()方法来关闭VPN连接。
        super.onDestroy();
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private byte[] hashPacketData(byte[] data) {
        //hashPacketData(): 该方法用于对数据包进行哈希处理。它使用SHA-256算法实现，对输入的数据进行哈希处理，并返回哈希后的结果。
        // Implement your hashing function here
        // For example, you can use Java's MessageDigest with the SHA-256 algorithm
        MessageDigest md;
        byte[] hashedData;
        try {
            md = MessageDigest.getInstance("SHA-256");
            hashedData = md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return null;
        }
        return hashedData;
    }


    private byte[] processAndReassemblePacket(byte[] originalPacket, int packetLength) {
        //        processAndReassemblePacket(): 该方法将原始数据包的IP头和数据部分分离出来，并将它们连接起来。
        //        接着，它使用hashPacketData()方法对连接后的数据进行哈希处理，并将哈希结果添加到原始数据包的末尾。
        //        最后，它返回一个已处理的数据包。
        // Parse and hash the IP header and data part of the packet
        byte[] ipHeader = Arrays.copyOfRange(originalPacket, 0, 20);
        byte[] dataPart = Arrays.copyOfRange(originalPacket, 20, packetLength);

        // Concatenate the IP header and data part
        byte[] concatenatedPacket = new byte[ipHeader.length + dataPart.length];
        System.arraycopy(ipHeader, 0, concatenatedPacket, 0, ipHeader.length);
        System.arraycopy(dataPart, 0, concatenatedPacket, ipHeader.length, dataPart.length);

        //A+concatenate
        // Hash the concatenated IP header and data part
        byte[] hashedConcatenatedPacket = hashPacketData(concatenatedPacket);

        if (hashedConcatenatedPacket == null) {
            return null;
        }


        if (packetNumber==0) {
            // if this is the first input, just calculate its hash and use it as the initial value of the hash chain
            hashChain[packetNumber++] = hashedConcatenatedPacket;  //
        } else {
            // concatenate the previous hash with the current input and calculate the hash of the result
            byte[] concatenated = new byte[hashChain[packetNumber].length + hashedConcatenatedPacket.length];
            System.arraycopy(hashChain[packetNumber], 0, concatenated, 0, hashChain[packetNumber].length);
            System.arraycopy(hashedConcatenatedPacket, 0, concatenated, hashChain[packetNumber].length, hashedConcatenatedPacket.length);
            hashChain[packetNumber++] = hashPacketData(concatenated);
        }

        // Concatenate the original packet with the hashChain
        byte[] modifiedPacket = new byte[packetLength + hashChain[packetNumber].length];
        System.arraycopy(originalPacket, 0, modifiedPacket, 0, packetLength);
        System.arraycopy(hashChain[packetNumber], 0, modifiedPacket, packetLength, hashChain[packetNumber].length);

        return modifiedPacket;
    }

    private InetAddress extractDestinationAddress(byte[] packet) throws UnknownHostException {
        //        extractDestinationAddress(): 该方法从数据包中提取目标IP地址。
        byte[] addressBytes = Arrays.copyOfRange(packet, 16, 20);

        InetAddress destinationAddress = InetAddress.getByAddress(addressBytes);

        return destinationAddress;
    }

    private int extractDestinationPort(byte[] packet) {
        //        extractDestinationPort(): 该方法从数据包中提取目标端口号。
        int ipHeaderLength = (packet[0] & 0x0F) * 4;
        return ((packet[ipHeaderLength + 2] & 0xFF) << 8) | (packet[ipHeaderLength + 3] & 0xFF);
    }

    private InetAddress extractSourceAddress(byte[] packet) throws UnknownHostException {
        //        extractSourceAddress(): 该方法从数据包中提取源IP地址。
        byte[] addressBytes = new byte[0];
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.GINGERBREAD) {
            addressBytes = Arrays.copyOfRange(packet, 12, 16);
        }
        InetAddress sourceAddress = InetAddress.getByAddress(addressBytes);

        return sourceAddress;
    }


    private int extractSourcePort(byte[] packet) {
        ByteBuffer buffer = ByteBuffer.wrap(packet);
        // Skip IP header
        buffer.position(buffer.get() & 0x0F << 2);
        // Get source port (the first 2 bytes in the transport header)
        return buffer.getShort() & 0xFFFF;
    }

}


