package local.john.mouseclient;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class MouseService extends Service {
    private final IBinder binder = new mBinder();
    private MainActivity.mHandler uiHandler;
    private static Thread connectionThread;

    private static Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private static String mServerIP;
    private static String mServerPort;

    public boolean isConnected = false;
    private boolean mGraceful = false;
    private boolean mTerminated = false;
    private int mErr = 0;

    public void connect() {
        if (connectionThread == null) {
            connectionThread = new Thread(new Runnable() {
                @Override
                    public void run() {
                    try {
                        socket = new Socket();
                        socket.connect(new InetSocketAddress(mServerIP, Integer.parseInt(mServerPort)), 1000);
                        out = new PrintWriter(socket.getOutputStream());
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        isConnected = true;
                        uiHandler.sendEmptyMessage(MainActivity.mHandler.MESSAGE_CONNECTED);

                        startListener();
                    } catch (IOException e) {
                        e.printStackTrace();
                        if (!socket.isConnected())
                            mErr = MainActivity.mHandler.MESSAGE_CONN_TIMEOUT;
                    } finally {
                        close();
                        stopSelf();
                    }
                }
            });

            connectionThread.start();
        }
    }

    public void write(String msg) {
        if (isConnected) {
            out.write(msg);
            out.flush();
        }
    }

    private void startListener() throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        char[] buff = new char[256];
        int read;
        while ((read = in.read(buff)) > 0 && !mGraceful) {
            sb.setLength(0);
            line = sb.append(buff, 0, read).toString();

            if (line.compareTo("110") != 0) {
                int serverCode = Integer.parseInt(line.substring(0, 1));

                if (serverCode == 1) {
                    Log.v("DEBUGGING_TAG", "Error: " + line.substring(1));
                } else if (serverCode == 2) {
                    mGraceful = true;
                    break;
                }
            }
        }

        if (!mGraceful)
            mErr = MainActivity.mHandler.MESSAGE_CONN_LOST;
        stopSelf();
    }

    public void close() {
        if (mTerminated)
            return;

        mTerminated = true;
        isConnected = false;
        connectionThread = null;

        try {
            if (socket != null)
                socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        uiHandler.sendEmptyMessage((mGraceful) ? MainActivity.mHandler.MESSAGE_GRACEFUL : mErr);
    }

    public void close(boolean userReq) {
        mGraceful = userReq;
        close();
    }

    public void setHandler(MainActivity.mHandler handler) {
        uiHandler = handler;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Bundle bundle = intent.getExtras();

        mServerIP = (bundle.getString("server_ip") != null) ? bundle.getString("server_ip") : MainActivity.DEFAULT_SERVER_IP;
        mServerPort = (bundle.getString("server_port") != null) ? bundle.getString("server_port") : MainActivity.DEFAULT_SERVER_PORT;

        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    class mBinder extends Binder {
        MouseService getService() {
            return MouseService.this;
        }
    }
}
