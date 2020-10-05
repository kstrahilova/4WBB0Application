package com.example.a4wbb0app;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import Connection.BluetoothA2DPRequester;
import Connection.BluetoothBroadcastReceiver;
import Connection.ConnectThread;

import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;

public class MainActivity extends AppCompatActivity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    private static final String TAG = "MainActivity";
    public static final UUID MY_UUID = UUID.fromString("2c38fe90-116c-4645-921e-b4d8ca85449f");
    //TODO: change this once you know the actual name of the arduino
    public final String arduinoName = "JBL REFLECT FLOW";
    // REQUEST_GO_TO_BT_SETTINGS constant passed to startActivityForResult() is a locally defined integer
    // (which must be greater than 0), that the system passes back to you in your onActivityResult()
    // implementation as the requestCode parameter
    private static final int REQUEST_GO_TO_BT_SETTINGS = 1;

    protected static TextView statusText;
    protected Button settingsBtn;

    BluetoothDevice device;
    static BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;

    BluetoothA2dp bluetoothA2dp;

    ConnectThread connectThread;

    boolean bracelet;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sets the appropriate layout file to be the layout file for this activity
        setContentView(R.layout.activity_main);
        //finds the views
        statusText = (TextView)findViewById(R.id.BluetoothStatus);
        settingsBtn = (Button)findViewById(R.id.BTSettings);

        //gets the bluetooth adapter - The BluetoothAdapter lets you perform fundamental Bluetooth tasks,
        // such as initiate device discovery, query a list of bonded (paired) devices,
        // instantiate a BluetoothDevice using a known MAC address, and create a BluetoothServerSocket
        // to listen for connection requests from other devices, and start a scan for Bluetooth LE devices
        //might need to call .getAdapter() - not static so that's a problem, but works for later android versions
        //bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothManager = (BluetoothManager)this.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //needed because otherwise we can't know if the device is connected or not, we need to listen for it getting connected
        bluetoothAdapter.disable();

        bracelet = false;
        //this still needs to be here despite the intent filter
        CheckBlueToothState();
        buttonListeners();

        IntentFilter filterBTchange = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiverBTchange, filterBTchange);

        IntentFilter filterConnected = new IntentFilter();
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        this.registerReceiver(receiverDevice, filterConnected);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void buttonListeners() {
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotToBtSettingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivityForResult(gotToBtSettingsIntent, REQUEST_GO_TO_BT_SETTINGS);
            }
        });
    }

    @Override
    public void onBluetoothConnected() {
        new BluetoothA2DPRequester((BluetoothA2DPRequester.Callback) this).request(this, bluetoothAdapter);
    }

    @Override
    public void onBluetoothError() {

    }

    private void CheckBlueToothState(){
        if (bluetoothAdapter == null){
            statusText.setText("Bluetooth is not supported on this Android device");
            showToast("Bluetooth is not supported on this device");
        } else {
            //if bluetooth is on ...
            if (bluetoothAdapter.isEnabled()){
                bluetoothAdapter.getProfileProxy(this, profileListener, A2DP);
                if (bracelet) {
                    statusText.setText("Bluetooth is enabled and the bracelet is connected to this device.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapter.isEnabled() && bluetoothAdapter.getBondedDevices() contains bracelet");
                    //connectThread(device);
                    onA2DPProxyReceived(bluetoothA2dp);
                //...and the bracelet is not connected
                } else {
                    statusText.setText("Go back to your device's Bluetooth settings and make sure you are connected to the bracelet.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapter.isEnabled() && bluetoothAdapter.getBondedDevices() does NOT contain bracelet");
                }
            //if bluetooth is off
            } else {
                bracelet = false;
                statusText.setText("Go back to your device's Bluetooth settings and make sure you are connected to the bracelet.");
                Log.println(Log.INFO, TAG, "CheckBluetoothState, !bluetoothAdapter.isEnabled()");
            }
        }
    }

    private void connectThread(BluetoothDevice device) {
        connectThread = new ConnectThread(device);
        connectThread.run();
    }

    /**
     * Method that ensures that user is aware that bluetooth is off, or device is not connected
     */
    private final BroadcastReceiver receiverBTchange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                CheckBlueToothState();

            }
        }
    };

    private final BroadcastReceiver receiverDevice = new BroadcastReceiver() {
        BluetoothDevice temp;
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            temp = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) && temp.getName().equals(arduinoName)) {
                bracelet = true;
                device = temp;
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                bracelet = false;
            }
            CheckBlueToothState();
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
        // the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
        // user responded "No") then the result code is RESULT_CANCELED
        super.onActivityResult(requestCode, resultCode, data);

        /*
        How do i make it so that the app is open again after settings?
         */
        if (requestCode == REQUEST_GO_TO_BT_SETTINGS) {
            CheckBlueToothState();
        }
    }

    public static BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static TextView getStatusText() {
        return statusText;
    }

    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == A2DP) {
                bluetoothA2dp = (BluetoothA2dp) proxy;
            }
        }
        public void onServiceDisconnected(int profile) {
            if (profile == A2DP) {
                bluetoothA2dp = null;
            }
        }
    };


    @Override
    public void onA2DPProxyReceived(BluetoothA2dp proxy) {
        Method connect = getConnectMethod();
        //not needed because device is set when it is picked
        //BluetoothDevice device = findBondedDeviceByName(mAdapter, HTC_MEDIA);

        //If either is null, just return. The errors have already been logged
        if (connect == null || device == null) {
            return;
            //setDevice();
        }

        try {
            connect.setAccessible(true);
            connect.invoke(proxy, device);
        } catch (InvocationTargetException ex) {
            Log.e(TAG, "Unable to invoke connect(BluetoothDevice) method on proxy. " + ex.toString());
        } catch (IllegalAccessException ex) {
            Log.e(TAG, "Illegal Access! " + ex.toString());
        }
    }

    /**
     * Wrapper around some reflection code to get the hidden 'connect()' method
     * @return the connect(BluetoothDevice) method, or null if it could not be found
     */
    private Method getConnectMethod () {
        try {
            return BluetoothA2dp.class.getDeclaredMethod("connect", BluetoothDevice.class);
        } catch (NoSuchMethodException ex) {
            Log.e(TAG, "Unable to find connect(BluetoothDevice) method in BluetoothA2dp proxy.");
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bluetoothAdapter.closeProfileProxy(A2DP, bluetoothA2dp);
        unregisterReceiver(receiverBTchange);
        connectThread.cancel();
    }


}