package com.example.a4wbb0app;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.UUID;

import Connection.BluetoothA2DPRequester;
import Connection.BluetoothBroadcastReceiver;
import Connection.BluetoothLeService;
import Connection.ConnectThread;
import Connection.LeDeviceListAdapter;

import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.GATT;
import static android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY;

public class MainActivity extends AppCompatActivity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    //final variables
    private static final String TAG = "MainActivity";
    public static final UUID MY_UUID = UUID.fromString("2c38fe90-116c-4645-921e-b4d8ca85449f");
    public final String arduinoName = "VibrationMotor";
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_GO_TO_WF_SETTINGS = 2;
    private static final int REQUEST_RECORD_AUDIO = 3;
    private static final int REQUEST_ACCESS_LOCATION = 4;

    //the name of the SharedPreferences xml file
    public static final String PREFERENCES = "preferences";
    //key in the SharedPreferences for storing the keyword that is to be recognized
    public static final String keywordPref = "keyword";

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    //variables for the views
    protected TextView bluetoothStatusText;
    protected Button scanAgainBT;
    protected TextView networkStatusText;
    protected Button networkSettingsBtn;
    protected EditText setKeywordEditText;

    //variables needed for the bluetooth connection
    static BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothDevice device;
    //boolean that indicates if the bracelet is connected
    boolean bracelet;

    //variables needed for the internet network connection
    ConnectivityManager connectivityManager;
    NetworkInfo networkInfo;

    //needed for the BLE connection
    BluetoothLeService bluetoothLeService;
    BluetoothGatt bluetoothGatt;
    BluetoothGattCallback gattCallback;
    BluetoothGattService bluetoothGattService;
    BluetoothGattCharacteristic triggerVibration;

    //variables needed for the BLE scanning
    BluetoothLeScanner bluetoothLeScanner;
    boolean scanning;
    Handler handler;
    static final long SCAN_PERIOD = 10000;
    LeDeviceListAdapter leDeviceListAdapter;

    //variables needed for the speech recognition
    SpeechRecognizer speechRecognizer;
    boolean isRecognizing;
    String keyword;
    Intent recognizerIntent;
    boolean keywordRegistered;

    //needed to turn down the volume and preven the SpeechRecognizer beeping
    AudioManager audioManager;
    int initialVolume;

    //needed for the Bluetooth classic, not used in the end
    ConnectThread connectThread;

    //needed for the A2DP, not used in the end
    BluetoothA2dp bluetoothA2dp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //sets the appropriate layout file to be the layout file for this activity
        setContentView(R.layout.activity_main);
        //finds the views
        bluetoothStatusText = (TextView)findViewById(R.id.BluetoothStatus);
        scanAgainBT = (Button)findViewById(R.id.BTSettings);
        networkStatusText = (TextView) findViewById(R.id.WiFiStatus);
        networkSettingsBtn = (Button) findViewById(R.id.WiFiSettings);
        setKeywordEditText = (EditText) findViewById(R.id.wordInput);

        //disables the "SCAN AGAIN" button, since scanning will start automatically
        scanAgainBT.setEnabled(false);

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();

        //gets the bluetooth adapter - The BluetoothAdapter lets you perform fundamental Bluetooth tasks,
        //such as initiate device discovery, query a list of bonded (paired) devices,
        //instantiate a BluetoothDevice using a known MAC address, and create a BluetoothServerSocket
        //to listen for connection requests from other devices, and start a scan for Bluetooth LE devices
        bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        //gets rid of the SpeechRecognizer beeping; the initial volume level is restored as soon as the app is shut down
        audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        audioManager.setMicrophoneMute(false);
        initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
        audioManager.setStreamVolume(AudioManager.STREAM_RING, 0, AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE);

        bluetoothLeService = new BluetoothLeService(this);
        handler = new Handler();
        leDeviceListAdapter = new LeDeviceListAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        gattCallback = bluetoothLeService.getGattCallback();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(recognitionListener);
        isRecognizing = false;
        keywordRegistered = false;

        sharedPreferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        keyword = sharedPreferences.getString(keywordPref, "cat");
        setKeywordEditText.setText(keyword);

        bracelet = false;

        //checks for the bluetooth, internet states and permissions
        CheckBluetoothState();
        CheckNetworkState();
        CheckAudioPermission();
        CheckLocationPermission();
        setListeners();

        //listens for changes in the Bluetooth state
        IntentFilter filterBTchange = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(receiverBTchange, filterBTchange);

        //this registers the receiver for the Classic Bluetooth; is not used in the end
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

        //sets up the speech recognition
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        //listens for changes in the internet connectivity
        registerReceiver(networkUpdateReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Method that checks the state of the bluetooth
     * There are four cases: the android device does not support bluetooth, the bluetooth is on and the bracelet is connected,
     * the bluetooth is on and the bracelet is not connected or the bluetooth is off
     *
     * It ensures that the user is notified in which state they are so that they can ensure they are
     * in the target state, namely bluetooth is enabled and the bracelet is connected
     */
    private void CheckBluetoothState(){
        //If BLE is not supported
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            bluetoothStatusText.setText("Bluetooth Low Energy is not supported on this Android device.");
            showToast("BLE is not supported on this device");
        } else {
            //if bluetooth is on ...
            if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                //...and the bracelet is connected
                if (bracelet) {
                    //disable the "SCAN AGAIN" buttin
                    scanAgainBT.setEnabled(false);
                    bluetoothStatusText.setText("Bluetooth is enabled and the bracelet is connected to this device.");
                //...and the bracelet is not connected...
                } else {
                    bluetoothStatusText.setText("Bluetooth is enabled but the bracelet is not connected to this device, try again.");
                    if (!scanning) {
                        //scan for LE devices
                        scanLeDevice();
                    }
                    //...if the scan resulted in the bracelet being connected
                    if (bracelet) {
                        scanAgainBT.setEnabled(false);
                        bluetoothStatusText.setText("Bluetooth is enabled and the bracelet is connected to this device.");
                    }
                }
            //if bluetooth is off ...
            } else {
                bracelet = false;
                bluetoothStatusText.setText("Make sure your Bluetooth is on");
                //ask the user for permission to thurn on the Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    /**
     * BroadcastReceiver that listens for bluetooth changes and ensures that user is aware when the
     * bluetooth is off, or device is not connected by calling CheckBluetoothState()
     */
    private final BroadcastReceiver receiverBTchange = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                CheckBluetoothState();
            }
        }
    };

    /**
     * Scans for BLE devices, enables/disables the scan again button accordingly
     */
    private void scanLeDevice() {
        //makes sure the scanner is initialized - sometimes it doesn't initialise on time, this is why this is needed
        if (bluetoothLeScanner == null) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        //if it isn't scanning already
        if (!scanning) {
            //makes sure the scan is stopped after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //if it is time to stop the scan, scanning should reflect that
                    scanning = false;
                    if (bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
                        //stop the scan
                        bluetoothLeScanner.stopScan(leScanCallback);
                        //if the bracelet didn't connect during this scan enable the "SCAN AGAIN" button
                        if (!bracelet) {
                            scanAgainBT.setEnabled(true);
                        }
                    }
                }
            }, SCAN_PERIOD);
            //start the scan after disabling the button and making sure scanning reflects that
            scanAgainBT.setEnabled(false);
            scanning = true;
            bluetoothAdapter.startDiscovery();
            bluetoothLeScanner.startScan(leScanCallback);
        }
    }

    /**
     * Device scan callback
     * If a device is found add it to the found devices data structure and call connectToBracelet()
     */
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            leDeviceListAdapter.addDevice(result.getDevice().getName(), result.getDevice());
            leDeviceListAdapter.notifyDataSetChanged();
            bracelet = connectToBracelet();
        }
    };

    /**
     * Method that checks if the bracelet has been found and if so connects to it
     * @return if the bracelet was connected
     */
    public boolean connectToBracelet() {
        //if the bracelet was added to the list, that means it was found
        if (leDeviceListAdapter.containsKey(arduinoName)) {
            //so stop the scan, connect to the bracelet and return true to set the value of bracelet in the calling method
            bluetoothLeScanner.stopScan(leScanCallback);
            bluetoothGatt = leDeviceListAdapter.getDevice(arduinoName).connectGatt(this, true, gattCallback);
            return true;
        } else {
            //if it isn't in the list it hasn't been found yet, continue searching
            return false;
        }
    }

    /**
     * BroadcastReceiver that handles actions of the BLE device (events fired by the BluetoothLeService)
     * Events are :
     * ACTION_GATT_CONNECTED: connected to a GATT server.
     * ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
     * ACTION_GATT_SERVICES_: discovered GATT services.
     * ACTION_DATA_AVAILABLE: received data from the device - This can be used to implement using
     *                        the device's microphone for the speech recognition
     */
    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        BluetoothDevice temp;
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            temp = bluetoothGatt.getDevice();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action) && temp.getName().equals(arduinoName)) {
                //if the device connected is the device we want set bracelet to true and call CheckBluetoothState()
                bracelet = true;
                device = temp;
                CheckBluetoothState();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                //if the bracelet was disconnected, make sure the user knows it
                bracelet = false;
                CheckBluetoothState();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                //if a service was discovered, set it
                if (bluetoothGatt.getService(MY_UUID) != null) {
                    bluetoothGattService = bluetoothGatt.getService(MY_UUID);
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                //data recorded by the external mic, sent to the device
                //here we can send the audio from the external microphone to the speech recognition could be
                //implemented if the speech recognition allows it
            }
        }
    };

    /**
     * Method thtat checks the state of the network connectivity
     * There are 2 cases: either connected or not
     * Uses the isConnected() method
     */
    private void CheckNetworkState() {
        if (isConnected()) {
            networkStatusText.setText("You are connected to the internet.");
            if (speechRecognizer != null && recognizerIntent != null) {
                //starts the speech recognition
                speechRecognizer.startListening(recognizerIntent);
            }
        } else {
            networkStatusText.setText("Go back to your device's Network Settings and make sure you are connected to the internet.");
        }
    }

    /**
     * Broadcast receiver for network connectivity changes
     * Since checking for connectivity actions only appears to be too time consuming, it will run CheckNetworkState for any broadcast
     */
    private final BroadcastReceiver networkUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CheckNetworkState();
        }
    };

    /**
     * Method that returns whether or not the android device is connected to the internet
     * @return whether or not the android device is connected to the internet
     */
    public  boolean isConnected() {
        networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected();
    }

    /**
     * RecognitionListener that soecifies what should be done when there is words recognized
     */
    private RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {}

        @Override
        public void onBeginningOfSpeech() {}

        @Override
        public void onRmsChanged(float rmsdB) {}

        @Override
        public void onBufferReceived(byte[] buffer) {}

        @Override
        public void onEndOfSpeech() {}

        @Override
        public void onError(int error) {
            //if there is an error that isn't that the recognizer is called too many times, start the recognition again
            if (error != ERROR_RECOGNIZER_BUSY) {
                speechRecognizer.startListening(recognizerIntent);
            }
        }

        @Override
        public void onResults(Bundle results) {
            //an array list of all words that were recognized by SpeechRecognizer
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            //if we have at least one match
            if (matches != null && matches.size() > 0) {
                String sentence = matches.get(0);
                //split the match into seperate words
                String[] split = sentence.split(" ");
                //and for each of these words
                for (String word : split) {
                    //check if it is equal to the keyword
                    if (word.equals(keyword)) {
                        //if it is, send the signal to the bracelet
                        sendToBracelet();
                    }
                }
            }
            //restart the recognition, since it stops when a match is found
            speechRecognizer.startListening(recognizerIntent);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {}

        @Override
        public void onEvent(int eventType, Bundle params) {}
    };

    /**
     * Method that given the appropriate connection sends the signal to the bracelet
     */
    public void sendToBracelet() {
        if (bluetoothGatt != null) {
            //get the service
            bluetoothGattService = bluetoothGatt.getService(MY_UUID);
        }
        //if it exists
        if (bluetoothGattService != null) {
            //get the characteristic from the service and set its value
            triggerVibration = bluetoothGattService.getCharacteristic(MY_UUID);
            triggerVibration.setValue(1, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
            //send the signal by writing the characteristic and check if it was successful
            if (bluetoothGatt.writeCharacteristic(triggerVibration)) {
                showToast("Signal was sent to the bracelet");
            } else {
                showToast("Something went wrong with sending the signal");
            }
            //if the service doesn't exist then either a different device is connected or no device is connected
        } else {
            showToast("Signal cannot be sent, there is no bluetooth connection of the required type");
        }
    }

    /**
     * Method that ensures that the permission to record audio is granted
     */
    private void CheckAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    /**
     * Method that ensures that the permission to use the devices location is granted
     */
    private void CheckLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_ACCESS_LOCATION);
        }
    }

    /**
     * Method that sets the button listeners for the buttons and the listener for changes in
     * the text input field
     */
    private void setListeners() {
        //if "SCAN AGAIN" is pressed, call CheckBluetoothState(), since it takes care of it
        scanAgainBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CheckBluetoothState();
            }
        });
        //if the button for the network settings is clicked, open the phones internet settings
        networkSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goToNwSettingsIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivityForResult(goToNwSettingsIntent, REQUEST_GO_TO_WF_SETTINGS);
            }
        });
        //if the text is changed
        setKeywordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            //after the text is changed, set the new keyword
            @Override
            public void afterTextChanged(Editable s) {
                keyword = setKeywordEditText.getText().toString();
                //save the new keyword in the SharedPreferences
                editor.putString(keywordPref, keyword);
                editor.commit();
                //let the user know the keyword is updated; needed since it is slow,
                //so the user knows when the app will start recognizing the new keyword
                showToast("Keyword is updated to " + keyword);
            }
        });
    }

    /**
     * Method that handles all request results
     * @param requestCode
     * @param resultCode
     * @param data
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //it the request is turning Bluetooth on
        if (requestCode == REQUEST_ENABLE_BT) {
            CheckBluetoothState();
        //if the request was going to the network settings
        } else if (requestCode == REQUEST_GO_TO_WF_SETTINGS) {
            CheckNetworkState();
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
     * Method that cleans up after app is closed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //restore the volume level
        audioManager.setStreamVolume(AudioManager.STREAM_RING, initialVolume, AudioManager.FLAG_VIBRATE);
        //clean up
        bluetoothAdapter.closeProfileProxy(GATT, bluetoothGatt);
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
        unregisterReceiver(receiverBTchange);
        unregisterReceiver(gattUpdateReceiver);
        speechRecognizer.stopListening();
        speechRecognizer.destroy();
    }

    /**
     * Not needed since we use BLE always
     */
    private BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == A2DP) {
                bluetoothA2dp = (BluetoothA2dp) proxy;
            } else if (profile == GATT) {
                bluetoothGatt = (BluetoothGatt) proxy;
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
     * Method that sets up a socket;
     * This functionality is part of the Bluetooth Classic
     * Does not work for the A2DP profile
     * Is not used, since BLE is used instead
     * @param device
     */
    private void connectThread(BluetoothDevice device) {
        connectThread = new ConnectThread(device);
        connectThread.run();
    }

    /**
     * Broadcast receiver for a Bluetooth Classic device connecting or disconnecting
     * Is not used, since BLE is used
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
            CheckBluetoothState();
        }
    };

    /**
     * Part of the A2DP implementation
     * Is not used since BLE is used
     */
    @Override
    public void onBluetoothConnected() {
        new BluetoothA2DPRequester((BluetoothA2DPRequester.Callback) this).request(this, bluetoothAdapter);
    }

    /**
     * Part of the A2DP implementation
     * Is not used since BLE is used
     */
    @Override
    public void onBluetoothError() {}

    /**
     * Part of the A2DP implementation
     * Is not used since BLE is used
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
     * Part of the A2DP implementation
     * Is not used since BLE is used
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