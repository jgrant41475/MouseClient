package local.john.mouseclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnTouchListener, View.OnLongClickListener {

    // Debugging
    protected static final String DEFAULT_SERVER_IP = "192.168.0.5";
    protected static final String DEFAULT_SERVER_PORT = "5050";


    // Variables instantiated in onCreate
    private String mServerIP;
    private String mServerPort;
    private SharedPreferences sharedPref;

    //UI views
    protected TextView mConnectionStatus;
    protected GestureDetector mDetector;

    private static long lastSend = 0L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectionStatus = (TextView) findViewById(R.id.connection_status);
        RelativeLayout rootView = (RelativeLayout) findViewById(R.id.rootView);
        rootView.setOnTouchListener(this);

        getPreferences();

        // Collect all control buttons and assign a ClickListener event
        ImageButton volumeDown = (ImageButton) findViewById(R.id.button_volume_down);
        ImageButton volumeMute = (ImageButton) findViewById(R.id.button_volume_mute);
        ImageButton volumeUp = (ImageButton) findViewById(R.id.button_volume_up);
        ImageButton exitButton = (ImageButton) findViewById(R.id.button_exit);
        ImageButton powerButton = (ImageButton) findViewById(R.id.button_power);

        volumeDown.setOnClickListener(this);
        volumeMute.setOnClickListener(this);
        volumeUp.setOnClickListener(this);
        exitButton.setOnLongClickListener(this);
        powerButton.setOnLongClickListener(this);

        mDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private boolean isDown = false;

            long lastSent, gestCount = 0, curGest;
            float lastX, lastY;

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isDown) {
                    mService.write("UP");
                    isDown = false;
                } else
                    mService.write("CLICK");
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                mService.write("RCLICK");
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                isDown = true;
                mService.write("DOWN");
            }

            @Override
            public boolean onDown(MotionEvent e) {
                gestCount++;
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                long now = System.currentTimeMillis();

                // new scroll event, reset last X and Y coords
                if (gestCount != curGest) {
                    lastX = lastY = 0;
                    curGest = gestCount;
                }
                if (now - lastSent > 75) {
                    if (lastX != 0 && lastY != 0) {
                        float deltaX = e2.getX() - lastX;
                        float deltaY = e2.getY() - lastY;
                        mService.write("MOVE " + deltaX + "," + deltaY);
                        lastSent = now;
                    }
                    lastX = e2.getX();
                    lastY = e2.getY();
                }
                return true;
            }
        });

    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(!isBound || event.getAction() != KeyEvent.ACTION_DOWN)
            return super.dispatchKeyEvent(event);

        int number = (event.getAction() == KeyEvent.FLAG_LONG_PRESS) ? 5 : 1;

        long now = System.currentTimeMillis();
        boolean ok;

        if(lastSend == 0 || now - lastSend > 75) {
            lastSend = now;
            ok = true;
        }
        else { ok = false; }


        if(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if(ok) new sendAsync().execute("VDOWN " + number);
            return true;
        }
        else if(event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            if(ok) new sendAsync().execute("VUP " + number);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if(!isBound)
            return false;

        int kc = event.getUnicodeChar();
        boolean send;

        if(keyCode == 67) {
            kc = 8;
            send = true;
        }

        else if ((kc > 96 && kc < 123) || (kc > 64 && kc < 91) || (kc > 47 && kc < 58))
            send = true;
        else
            switch (kc) {
                case 8:
                case 10:
                case 44:
                case 32:
                case 46:
                case 47:
                case 64:
                case 35:
                case 36:
                case 37:
                case 38:
                case 45:
                case 43:
                case 40:
                case 41:
                case 42:
                case 34:
                case 39:
                case 58:
                case 59:
                case 33:
                case 63:
                case 95:
                case 126:
                case 96:
                case 124:
                case 94:
                case 61:
                case 123:
                case 125:
                case 92:
                case 91:
                case 93:
                case 62:
                case 60:
                    send = true;
                    break;
                default:
                    send = false;
            }

        if (send)
            mService.write("SEND " + kc);

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        getPreferences();

        if (sharedPref.getBoolean("connect_on_open", false))
            doBind();
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (isBound)
            doUnbind();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (v.getId() == R.id.rootView && isBound) {
            mDetector.onTouchEvent(event);
            return true;
        }

        return false;
    }

    private void getPreferences() {
        if(sharedPref == null)
            sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

        if (!sharedPref.contains("mouse_server_ip") || !sharedPref.contains("mouse_server_port")) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else {
            mServerIP = sharedPref.getString("mouse_server_ip", DEFAULT_SERVER_IP);
            mServerPort = sharedPref.getString("mouse_server_port", DEFAULT_SERVER_PORT);
        }
    }

    @Override
    public void onClick(View view) {
        if(isBound) {
            switch (view.getId()) {
                case R.id.button_volume_down:
                    new sendAsync().execute("VDOWN");
                    break;
                case R.id.button_volume_mute:
                    new sendAsync().execute("VMUTE");
                    break;
                case R.id.button_volume_up:
                    new sendAsync().execute("VUP");
                    break;
                default:
                    // Do nothing...
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if(isBound) {
            switch (view.getId()) {
                case R.id.button_exit:
                    new sendAsync().execute("EXIT");
                    break;
                case R.id.button_power:
                    new sendAsync().execute("SLEEP");
                    break;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_toggle_keyboard:
                InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                break;

            case R.id.action_connect:
                doBind();
                break;

            case R.id.action_disconnect:
                if (isBound)
                    mService.close(true);
                break;

            case R.id.action_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    // Service
    private mHandler handler = new mHandler(this);
    private MouseService mService;
    private boolean isBound = false;

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MouseService.mBinder binder = (MouseService.mBinder) service;
            mService = binder.getService();
            isBound = true;

            mService.setHandler(handler);
            mService.connect();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            isBound = false;
        }
    };

    void doBind() {
        if (!isBound) {
            Bundle bundle = new Bundle();
            bundle.putString("server_ip", mServerIP);
            bundle.putString("server_port", mServerPort);
            bindService((new Intent(this, MouseService.class)).putExtras(bundle), mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    void doUnbind() {
        if (isBound) {
            if (mService != null)
                mService.write("CLOSE");
            unbindService(mConnection);
            mService = null;
            isBound = false;

            mConnectionStatus.setText(R.string.disconnected);
            mConnectionStatus.setTextColor(Color.parseColor("#FF0000"));
        }
    }

    // Write to MouseService buffer on a separate thread.  returns Void
    private class sendAsync extends AsyncTask<String, Void, String> {

        @Override
        protected String doInBackground(String... strings) {

            mService.write(strings[0]);
            return null;
        }
    }

    static class mHandler extends Handler {

        static final int MESSAGE_CONNECTED = 1;
        static final int MESSAGE_CONN_LOST = 2;
        static final int MESSAGE_CONN_TIMEOUT = 3;
        static final int MESSAGE_GRACEFUL = 4;


        private WeakReference<MainActivity> mRef;

        mHandler(MainActivity activity) {
            mRef = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            MainActivity activity = mRef.get();

            switch (msg.what) {
                // Connection to server established
                case MESSAGE_CONNECTED:
                    activity.mConnectionStatus.setText(R.string.connected);
                    activity.mConnectionStatus.setTextColor(Color.parseColor("#00FF00"));
                    break;
                // Connection ended unexpectedly
                case MESSAGE_CONN_LOST:
                    Toast.makeText(activity.getApplicationContext(), "Lost the connection to the server.", Toast.LENGTH_SHORT).show();
                    activity.doUnbind();
                    break;
                // Connection failed
                case MESSAGE_CONN_TIMEOUT:
                    Toast.makeText(activity.getApplicationContext(), "Couldn't establish a connection to the server.", Toast.LENGTH_SHORT).show();
                    activity.doUnbind();
                    break;
                // Connection terminated by client
                case MESSAGE_GRACEFUL:
                    activity.doUnbind();
                    break;

            }
        }

    }

}
