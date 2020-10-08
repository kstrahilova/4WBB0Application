package com.example.a4wbb0app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.UUID;

import Connection.BluetoothA2DPRequester;
import Connection.BluetoothBroadcastReceiver;
import Connection.BluetoothLeService;
import Connection.ConnectThread;

import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.GATT;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.telecom.Connection.STATE_DISCONNECTED;

public class MainActivity extends AppCompatActivity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    //final variables
    private static final String TAG = "MainActivity";
    public static final UUID MY_UUID = UUID.fromString("2c38fe90-116c-4645-921e-b4d8ca85449f");
    //TODO: change this once you know the actual name of the arduino
    public final String arduinoName = "JBL REFLECT FLOW";
    private static final int REQUEST_GO_TO_BT_SETTINGS = 1;

    //variables for the views
    protected static TextView statusText;
    protected Button settingsBtn;

    //variables needed for the connection always
    static BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothDevice device;
    boolean bracelet;

    //needed for the BLE connection
    BluetoothLeService bluetoothLeService;
    BluetoothGatt bluetoothGatt;
    BluetoothGattCallback gattCallback;

    //variables needed for the BLE scanning, will mostl likey not be used
    BluetoothLeScanner bluetoothLeScanner;
    boolean scanning;
    Handler handler;
    static final long SCAN_PERIOD = 10000;
    HashMap<String, BluetoothDevice> discoveredDevices;

    //needed for the Bluetooth classic, will most likely not be used
    ConnectThread connectThread;

    //needed for the A2DP, will most likely not be used
    BluetoothA2dp bluetoothA2dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sets the appropriate layout file to be the layout file for this activity
        setContentView(R.layout.activity_main);
        //finds the views
        statusText = (TextView)findViewById(R.id.BluetoothStatus);
        settingsBtn = (Button)findViewById(R.id.BTSettings);

        discoveredDevices = new HashMap<>();

        //gets the bluetooth adapter - The BluetoothAdapter lets you perform fundamental Bluetooth tasks,
        // such as initiate device discovery, query a list of bonded (paired) devices,
        // instantiate a BluetoothDevice using a known MAC address, and create a BluetoothServerSocket
        // to listen for connection requests from other devices, and start a scan for Bluetooth LE devices
        //might need to call .getAdapter() - not static so that's a problem, but works for later android versions
        //bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        bluetoothLeService = new BluetoothLeService();

        gattCallback = bluetoothLeService.getGattCallback();

        //needed because otherwise we can't know if the device is connected or not, we need to listen for it getting connected
        bluetoothAdapter.disable();

        bracelet = false;

        //this still needs to be here despite the intent filter
        CheckBlueToothState();
        buttonListeners();

        IntentFilter filterBTchange = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiverBTchange, filterBTchange);

        //this registers the receiver for the Classic Bluetooth
        IntentFilter filterConnected = new IntentFilter();
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filterConnected.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        //this.registerReceiver(receiverDevice, filterConnected);

        //resisters the BLE receiver
        IntentFilter filterGatt = new IntentFilter();
        filterGatt.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        filterGatt.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        filterGatt.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        filterGatt.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        this.registerReceiver(gattUpdateReceiver, filterGatt);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Method that sets the button listener for the button that opens the bluetooth settings
     * any other button onClickListeners should be created here
     */
    private void buttonListeners() {
        settingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotToBtSettingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivityForResult(gotToBtSettingsIntent, REQUEST_GO_TO_BT_SETTINGS);
            }
        });
    }

    /**
     * Method that checks the state of the bluetooth
     * There are four cases: the android device does not support bluetooth, the bluetooth is on and the bracelet is connected,
     * the bluetooth is on and the bracelet is not connected or the bluetooth is off
     *
     * It ensures that the user is notified in which state they are so that they can ensure they are
     * in the target state, namely bluetooth is enabled and the bracelet is connected
     */
    private void CheckBlueToothState(){
        //If bluetooth is not supported
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
                    //not so sure about this - check the profileListener?
                    bluetoothGatt = device.connectGatt(this, true, gattCallback);
                    //connectThread(device);
                    //onA2DPProxyReceived(bluetoothA2dp);
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

    /**
     * Broadcast receiver
     * Handles actions of the BLE device (events fired by the Service)
     * Events are :
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services. - I am not sure we need this
     * ACTION_DATA_AVAILABLE: received data from the device - This can be a result of read or notification operations.
     */
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        BluetoothDevice temp;
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            temp = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action) && temp.getName().equals(arduinoName)) {
                bracelet = true;
//                updateConnectionState(R.string.connected);
//                invalidateOptionsMenu();
                device = temp;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
//                connected = false;
//                updateConnectionState(R.string.disconnected);
//                invalidateOptionsMenu();
//                clearUI();
                bracelet = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the
                // user interface.
                //displayGattServices(bluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                //TODO; HERE WE WILL MOST LIKELY HAVE TO SEND THE DATA TO THE SPEECH RECOGNITION
            }
        }
    };

    /**
     * Service listener that checks the profile of a newly connected device and sets the corresponding
     * profile object
     */
    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == A2DP) {
                bluetoothA2dp = (BluetoothA2dp) proxy;
            } else if (profile == GATT) {
                //not so sure about this, check the CheckBluetoothConnection
                bluetoothGatt = (BluetoothGatt) proxy;
                //bluetoothGatt = device.connectGatt(this, true, gattCallback);
            }
        }
        //clean up
        public void onServiceDisconnected(int profile) {
            if (profile == A2DP) {
                bluetoothA2dp = null;
            } else if (profile == GATT) {
                bluetoothGatt = null;
            }
        }
    };

    /**
     * Method that handles all request results
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
        // the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
        // user responded "No") then the result code is RESULT_CANCELED
        super.onActivityResult(requestCode, resultCode, data);

        //How do i make it so that the app is open again after settings?
        if (requestCode == REQUEST_GO_TO_BT_SETTINGS) {
            CheckBlueToothState();
        }
    }

    /**
     * Toasts are the little temporary messages in the bottom of the screen
     * This method automated their firing
     * @param message
     */
    public void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Getter method for the bluetooth adapter
     * @return
     */
    public static BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    /**
     * Getter method for the status text
     * I am not sure why this is used, since it is used in this method and here we can just type
     * statusText, but oh well
     * @return
     */
    public static TextView getStatusText() {
        return statusText;
    }

    /**
     * Method that cleans up after app is closed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //bluetoothAdapter.closeProfileProxy(A2DP, bluetoothA2dp);
        bluetoothAdapter.closeProfileProxy(GATT, bluetoothGatt);
        unregisterReceiver(receiverBTchange);
        unregisterReceiver(gattUpdateReceiver);
        connectThread.cancel();

        if (bluetoothGatt == null) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    /**
     * Device scan callback
     * Part of the BLE implementation, however will not be used due to our precondition that the
     * user needs to make sure the android device is connected to the bracelet themselves
     */
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            discoveredDevices.put(result.getDevice().getName(), result.getDevice());
        }
    };

    /**
     * Scans for BLE devices
     * Part of the BLE implementation, however will not be used due to our precondition that the
     * user needs to make sure the android device is connected to the bracelet themselves
     */
    private void scanLeDevice() {
        if (!scanning) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    /**
     * Method that sets up a socket;
     * This functionality is part of the Bluetooth Classic, but does not work for the A2DP profile
     * So this will probably never be used anyway
     * @param device
     */
    private void connectThread(BluetoothDevice device) {
        connectThread = new ConnectThread(device);
        connectThread.run();
    }

    /**
     * Broadcast receiver for a Bluetooth Classic device connecting or disconnecting
     * Most likely will not be used
     */
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

    /**
     * A2DP code that will most likely not be used
     */
    @Override
    public void onBluetoothConnected() {
        new BluetoothA2DPRequester((BluetoothA2DPRequester.Callback) this).request(this, bluetoothAdapter);
    }

    /**
     * A2DP code that will most likely not be used
     */
    @Override
    public void onBluetoothError() {}

    /**
     * Some weird A2DP method that I didn't really get.
     * Most likely will not be used
     * @param proxy
     */
    @Override
    public void onA2DPProxyReceived(BluetoothA2dp proxy) {
        Method connect = getConnectMethod();

        //If either is null, just return. The errors have already been logged
        if (connect == null || device == null) {
            return;
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
     * Most likely will not be used
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

}