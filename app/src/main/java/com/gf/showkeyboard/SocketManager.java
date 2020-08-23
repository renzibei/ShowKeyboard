package com.gf.showkeyboard;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;



class Msg {
    public int length;
    public int type;
    public byte[] data;

    public Msg() {
        length = 0;
        type = 0;
        data = new byte[Constants.MSG_DATA_LEN];
    }

};

enum SocketManager {
    INSTANCE;
    private BluetoothSocket mBluetoothSocket;
    private BluetoothAdapter mBluetoothAdapter;
    private InputStream inputStream;
    private OutputStream outputStream;


    public static SocketManager getInstance() {
        return INSTANCE;
    }

    private void testRead() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                                    byte[] b = new byte[1024];
//                                    int n;
//                                    while ((n = inputStream.read(b)) != -1) {
//
//                                        String s = new String(b,0 , n ,"UTF-8");
//                                        Log.i(Constants.infoTag , "客户端收到服务器的数据了" + s);
//                                    }
                    while(true) {
//                                        byte[] b = readBytes();
                        Msg msg = readMsg();
                        if(msg.type == 3) {
                            String s = new String(msg.data, 0, msg.length, "UTF-8");
                            Log.i(Constants.infoTag, "receive: " + s);
                        }
                        else if(msg.type == 2) {
                            double x = getDoubleFrom8Byte(Arrays.copyOfRange(msg.data, 0, 8));
                            double y = getDoubleFrom8Byte(Arrays.copyOfRange(msg.data, 8, 16));
                            Log.i(Constants.infoTag, "receive x: " + x + " y: " + y);
                        }

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();

    }

    public int connect() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(Constants.serverAddr);
        final UUID uuid = UUID.fromString(Constants.uuidString);
        try {
            int sdk = Build.VERSION.SDK_INT;
//            UUID uuid = device.getUuids()[0].getUuid();
            Log.i(Constants.infoTag, "UUID: " + uuid.toString());
            if (sdk >= 10) {
                mBluetoothSocket = device.createInsecureRfcommSocketToServiceRecord(uuid);
            } else {
                mBluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            }
            if (mBluetoothSocket != null) {
                if (mBluetoothAdapter.isDiscovering()) {
                    mBluetoothAdapter.cancelDiscovery();
                }
                if (!mBluetoothSocket.isConnected()) {
                    try {
                        Method m = device.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                        mBluetoothSocket = (BluetoothSocket) m.invoke(device, 1);
                        Log.i(Constants.infoTag, "before connect");
                        mBluetoothSocket.connect();
                        Log.i(Constants.infoTag, "after connect");
                        outputStream = mBluetoothSocket.getOutputStream();
                        inputStream = mBluetoothSocket.getInputStream();

                        requestConnect();


//                        sendData();
                    } catch(Exception e) {
                        Log.e(Constants.errorTag, "error2" + e.getMessage());
                        return -2;
                    }
                }
                //EventBus.getDefault().post(new BluRxBean(connectsuccess, device));
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(Constants.errorTag, "connect error");
            return -1;
        }

        return 0;
    }

    private int readBeginLength() {
        byte[] dataByte = new byte[4];
        try {
            int realLen = inputStream.read(dataByte, 0, 4);
            if(realLen < 4) {
                Log.e(Constants.errorTag, "read realLen < 4, realLen = " + realLen);
            }
        } catch(IOException e) {
            Log.e(Constants.errorTag,"Fail to read begin length");
            Log.e(Constants.errorTag, e.getMessage(), e);
            return 0;
        }
//        ByteBuffer byteBuffer = ByteBuffer.wrap(dataByte);
//        byteBuffer.order(ByteOrder.nativeOrder());
//        return byteBuffer.getInt();
        return getIntFrom4Byte(dataByte);
    }

    public byte[] readBytes() {
        int dataLen = readBeginLength();
        byte[] bytes = new byte[dataLen];
        try {
            int realLen = inputStream.read(bytes, 0, dataLen);
            if(realLen < dataLen) {
                Log.e(Constants.errorTag,"read realLen < targetLen ,  realLen = " + realLen + " targetLen = " +dataLen);
            }
        } catch(IOException e) {
            Log.e(Constants.errorTag, "Fail to readBytes");
            Log.e(Constants.errorTag,e.toString(), e);
            bytes = null;
        }
        return bytes;
    }

    private static double getDoubleFrom8Byte(byte[] dataByte) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(dataByte);
        byteBuffer.order(ByteOrder.nativeOrder());
        return byteBuffer.getDouble();
    }

    private static int getIntFrom4Byte(byte[] dataByte) {
        ByteBuffer byteBuffer = ByteBuffer.wrap(dataByte);
        byteBuffer.order(ByteOrder.nativeOrder());
        return byteBuffer.getInt();
    }

    public Msg readMsg() {
        Msg msg = new Msg();
        Log.i(Constants.infoTag, "before readBytes");
        byte[] bytes = readBytes();
        Log.i(Constants.infoTag, "after readBytes");
        msg.length = getIntFrom4Byte(Arrays.copyOfRange(bytes, 0, 4));
        msg.type = getIntFrom4Byte(Arrays.copyOfRange(bytes, 4, 8));
        msg.data = Arrays.copyOfRange(bytes, 8, bytes.length);
        Log.i(Constants.infoTag, "type:" + msg.type + " len: " + msg.length);
        return msg;
    }

    public byte[] get4ByteFromInt(int x) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.putInt(x);
        return byteBuffer.array();
    }

    public void writeMsg(Msg msg) {
        byte[] bytes = new byte[4 + 4 + Constants.MSG_DATA_LEN];
        byte[] lenBytes = get4ByteFromInt(msg.length);
        byte[] typeBytes = get4ByteFromInt(msg.type);
        System.arraycopy(lenBytes, 0, bytes, 0, lenBytes.length);
        System.arraycopy(typeBytes, 0, bytes, lenBytes.length, typeBytes.length);
        System.arraycopy(msg.data, 0, bytes, lenBytes.length + typeBytes.length, msg.data.length);
        this.writeBytes(bytes);
    }

    // request, type = 5
    public void sendConnectRequest() {
        Log.i(Constants.infoTag, "before send request");
        Msg msg = new Msg();
        msg.type = 5;
        msg.length = 0;
        writeMsg(msg);
        Log.i(Constants.infoTag, "after send request");
    }

    // confirm for connect request, type = 6
    public int waitConnectConfirm() {
        Msg msg = readMsg();
        if(msg.type == 6) {
            return 0;
        }
        return -1;
    }

    public void requestConnect() {
        new Thread(new Runnable() {
            @Override
            public void run() {

                sendConnectRequest();
                int ret = waitConnectConfirm();
                if(ret == 0) {
                    displayDialog("Connected", "Established bluetooth connection");
                }
                else {
                    displayDialog("Failed to connect", "Failed to establish bluetooth connection");
                }
            }
        }).start();

    }

    private void writeBeginLength(int len) {
        ByteBuffer byteBuffer = ByteBuffer.allocate(4);
        byteBuffer.order(ByteOrder.nativeOrder());
        byteBuffer.putInt(len);
        try {
            outputStream.write(byteBuffer.array());
            outputStream.flush();
        } catch (IOException e) {
            Log.e(Constants.errorTag,"Fail to write begin length");
            Log.e(Constants.errorTag, e.getMessage(), e);
        }
    }

    public void writeBytes(byte[] bytes) {
        int dataLen = bytes.length;
        writeBeginLength(dataLen);
        try {
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            Log.e(Constants.errorTag, "Fail to write bytes through pipe");
            Log.e(Constants.errorTag, e.getMessage(), e);
        }
    }

//    public int readMessage() {
//
//        return 0;
//    }
//
//    public int writeMessage() {
//
//        return 0;
//    }

    private void sendData(){
        if (outputStream != null) {
            try {
//                outputStream.write("from client Hello!".getBytes("UTF-8"));
//                outputStream.flush();
                writeBytes("from client Hello!".getBytes("UTF-8"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void displayDialog(String title, String s){
        final String text = s, titleText = title;
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(MainActivity.getInstance())
                        .setTitle(titleText)
                        .setMessage(text)
                        .setPositiveButton("Ok", null)
                        .show();
            }
        });

    }

}
