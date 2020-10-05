package com.example.a4wbb0app;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import Connection.BluetoothA2DPRequester;
import Connection.BluetoothBroadcastReceiver;
import Connection.BluetoothService;
import Connection.ConnectThread;
import potentiallyUseless.Bluetooth;

public class MainActivityBackUp extends AppCompatActivity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    private static final String TAG = "MainActivity";
    // REQUEST_ENABLE_BT constant passed to startActivityForResult() is a locally defined integer
    // (which must be greater than 0), that the system passes back to you in your onActivityResult()
    // implementation as the requestCode parameter
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_DISCOVER_BT = 2;
    private static final int REQUEST_CONNECT_BT = 3;

    public static final String PREFERENCES = "preferences";
    //key for the default device to connect to
    public static final String defaultDevice = "device";
    public static final String newDevice = "newDevice";

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    protected static TextView statusText;
    protected TextView pairedText;
    AlertDialog pairedPreferenceChoice;
    public static final UUID MY_UUID = UUID.fromString("2c38fe90-116c-4645-921e-b4d8ca85449f");

    //protected Button onBtn, offBtn, discBtn;
    protected Button pairedBtn, scanBtn;

    Set<BluetoothDevice> pairedDevices;
    Set<BluetoothDevice> discoveredDevices;

    Intent intentOff;

    BluetoothDevice device;

    HashMap<String, BluetoothDevice> currentDevicesMap;
    /** Called when the activity is first created. */

    static BluetoothAdapter bluetoothAdapter;
    BluetoothService bluetoothService;

    ConnectThread connectThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sets the appropriate layout file to be the layout file for this activity
        setContentView(R.layout.activity_main);
        //finds the views
        statusText = (TextView)findViewById(R.id.BluetoothStatus);
        pairedText = (TextView)findViewById(R.id.BluetoothPairedStatus);
        pairedBtn = (Button)findViewById(R.id.BTPairedButton);
        scanBtn = (Button)findViewById(R.id.BTSearchButton);

        intentOff = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);

        //gets the bluetooth adapter - The BluetoothAdapter lets you perform fundamental Bluetooth tasks,
        // such as initiate device discovery, query a list of bonded (paired) devices,
        // instantiate a BluetoothDevice using a known MAC address, and create a BluetoothServerSocket
        // to listen for connection requests from other devices, and start a scan for Bluetooth LE devices
        //might need to call .getAdapter() - not static so that's a problem, but works for later android versions
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        Log.println(Log.INFO, TAG, "1");

        //initialises the hashmap that stores the BluetoothDevices that are currently paired to the device
        //the keys are their names as strings
        currentDevicesMap = new HashMap<>();
        //gets the shared preferences
        sharedPreferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);

        Log.println(Log.INFO, TAG, "2");


        //this still needs to be here despite the intent filter
        CheckBlueToothState();
        Log.println(Log.INFO, TAG, "3");

        buttonListeners();
        Log.println(Log.INFO, TAG, "4");
        // Register for broadcasts when a device is discovered
        IntentFilter filterDiscovered = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filterDiscovered);
        Log.println(Log.INFO, TAG, "5");
        //Register for broadcasts when bluetooth is turned off by user and make sure its turned back on
        IntentFilter filterBTOff = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiver2, filterBTOff);

        Log.println(Log.INFO, TAG, "6");
        setDevice();
        //discoverDevices();
        Log.println(Log.INFO, TAG, "7");
        CheckBlueToothState();

        Log.println(Log.INFO, TAG, "8");
//        while (!bluetoothAdapter.isEnabled()) {
//            continue;
//        }

//        if (bluetoothAdapter.isEnabled()) {
//            connectThread = new ConnectThread(device);
//            connectThread.run();
//        }
        //startActivityForResult(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), REQUEST_CONNECT_BT);
    }

    @Override
    protected void onStart() {
        super.onStart();
        discoverDevices();

    }

    private void buttonListeners() {
        pairedBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (bluetoothAdapter.isEnabled()){
                    queryPairedDevices();
                    createAlertDialogDeviceChoice("Select a paired device as a default", pairedDevices, true);
                }
                else {
                    //bluetooth is off so can't get paired devices
                    showToast("Turn on bluetooth to get paired devices");
                }
            }
        });

        scanBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                if (bluetoothAdapter.isEnabled()){
//                    discoverDevices();
//                    //TODO: figure out why this makes it crash, probably because of next TODO
//                    //createAlertDialogDeviceChoice("Select a device to pair to", discoveredDevices, false);
//                }
//                else {
//                    //bluetooth is off so can't discover devices
//                    showToast("Turn on bluetooth to discover devices");
//                }
            connectThread = new ConnectThread(device);
            connectThread.run();
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

    public void createAlertDialogDeviceChoice(String message, Set<BluetoothDevice> devices, final boolean paired){
        //showToast("Dialog");
        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivityBackUp.this);

        builder.setTitle(message);//"Select a paired device as a default");

        editor = sharedPreferences.edit();
        final String[] items = new String[devices.size()];
        int i = 0;
        for (BluetoothDevice device : devices) {
            currentDevicesMap.put(device.getName(), device);
            items[i++] = device.getName();
        }

        builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int item) {
                //it doesn't matter if two devices were previously paired
                device = currentDevicesMap.get(items[item]);
                putDeviceinPref(device);
                //TODO: THIS DOESN"T WORK
                //connectThread = new ConnectThread(currentDevicesMap.get(items[item]));
                //connectThread.run();
                showToast(sharedPreferences.getString(defaultDevice, ""));

                pairedPreferenceChoice.dismiss();

            }
        });

        pairedPreferenceChoice = builder.create();
        pairedPreferenceChoice.show();

    }

    private void putDeviceinPref(BluetoothDevice device) {
        Gson gson = new Gson();
        String json = gson.toJson(device);
        editor.putString(defaultDevice, json);
        editor.commit();
    }

    private BluetoothDevice readDeviceFromPref() {
        Gson gson = new Gson();
        String json = sharedPreferences.getString(defaultDevice, "");
        return gson.fromJson(json, BluetoothDevice.class);
    }

    //TODO: change
    public void setDevice() {
        if (device == null && !readDeviceFromPref().equals("")) {
            device = readDeviceFromPref();
        } else {
            while (readDeviceFromPref().equals("")) {
                pairedText.setText("Click on SHOW PAIRED DEVICES and select a device!");
            }
            pairedText.setText("Default device was selected");
        }
    }

    private void CheckBlueToothState(){
        if (bluetoothAdapter == null){
            statusText.setText("Bluetooth is not supported on this Android device");
            showToast("Bluetooth is not supported on this device");
        } else {
            if (bluetoothAdapter.isEnabled()){
                if (bluetoothAdapter.isDiscovering()){
                    statusText.setText("Bluetooth is currently in device discovery process.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapte.isEnabled() && bluetoothAdapter.isDiscovering()");

                } else {
                    statusText.setText("Bluetooth is enabled.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapte.isEnabled() && !bluetoothAdapter.isDiscovering()");

                    connectThread = new ConnectThread(device);
                    connectThread.run();
                }
            } else {
                statusText.setText("Bluetooth is not enabled!");
                Log.println(Log.INFO, TAG, "CheckBluetoothState, !bluetoothAdapte.isEnabled()");

                //Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                //startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                if (bluetoothAdapter.enable()) {
                    BluetoothBroadcastReceiver.register(this, this);
                }
                statusText.setText("Bluetooth is enabled.");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
        // the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
        // user responded "No") then the result code is RESULT_CANCELED
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_CANCELED) {
                showToast("Something went wrong");
            }
            //calls the function again to set the status of bluetooth
            CheckBlueToothState();

            if (resultCode == RESULT_OK) {
                showToast("Bluetooth was enabled");
            }
        }

//        if (requestCode == REQUEST_CONNECT_BT) {
//            if (bluetoothAdapter. getState() == BluetoothAdapter.STATE_CONNECTED)
//            if (resultCode == RESULT_OK) {
//                showToast("Device was connected to " + device.getName());
//            }
//        }
    }

    public static BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public void queryPairedDevices(){
        pairedDevices = bluetoothAdapter.getBondedDevices();
    }

    //TODO: IS THIS ENOUGH
    public void discoverDevices() {
        pairedText.setText("discoverDevices");
        bluetoothAdapter.startDiscovery();
        //int i = 1;
        while (!bluetoothAdapter.isDiscovering()) {
            pairedText.setText("is not discovering");
        }
        if (bluetoothAdapter.isDiscovering()) {
            pairedText.setText("discovering");
        }

    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            pairedText.setText("receiver");
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Discovery has found a device; get it and its info from Intent
                BluetoothDevice deviceTemp = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                discoveredDevices.add(deviceTemp);
                String deviceName = deviceTemp.getName();
                String deviceHardwareAddress = deviceTemp.getAddress();
                pairedText.setText(deviceName);
                if (deviceName.equals(readDeviceFromPref().getName())) {
                    device = deviceTemp;
                    connectThread = new ConnectThread(device);
                }
            }
        }
    };

    /**
     * Method that censures that bluetooth is turned back on if the user turns it off
     */
    private final BroadcastReceiver receiver2 = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_OFF)  {
                    CheckBlueToothState();
                }
            }
        }
    };

    //this method would work with the AcceptThread
    //this displayes a dialog that asks the user if they want to set their device to discoverable for 5 min
    //If i am initiating the connection I don't need this. Well i am, so I don't need this...
    public void enableDiscoverability() {
        Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        //sets the device to be discoverable for 5 minutes (300 sec)
        discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverable);
    }



    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public static TextView getStatusText() {
        return statusText;
    }

    public TextView getPairedText() {
        return pairedText;
    }

    public Context getContext() {
        return this;
    }

    @Override
    public void onA2DPProxyReceived(BluetoothA2dp proxy) {
        Method connect = getConnectMethod();
        //not needed because device is set when it is picked
        //BluetoothDevice device = findBondedDeviceByName(mAdapter, HTC_MEDIA);

        //If either is null, just return. The errors have already been logged
        if (connect == null || device == null) {
            //return;
            setDevice();
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

        unregisterReceiver(receiver);
        unregisterReceiver(receiver2);
        connectThread.cancel();
    }


}