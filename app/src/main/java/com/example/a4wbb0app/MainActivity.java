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
import java.util.HashMap;
import java.util.UUID;

import Connection.BluetoothA2DPRequester;
import Connection.BluetoothBroadcastReceiver;
import Connection.BluetoothLeService;
import Connection.ConnectThread;

import static android.bluetooth.BluetoothProfile.A2DP;
import static android.bluetooth.BluetoothProfile.GATT;

public class MainActivity extends AppCompatActivity implements BluetoothBroadcastReceiver.Callback, BluetoothA2DPRequester.Callback {

    //final variables
    private static final String TAG = "MainActivity";
    public static final UUID MY_UUID = UUID.fromString("2c38fe90-116c-4645-921e-b4d8ca85449f");
    //TODO: change this once you know the actual name of the arduino
    public final String arduinoName = "JBL REFLECT FLOW";
    private static final int REQUEST_GO_TO_BT_SETTINGS = 1;
    private static final int REQUEST_GO_TO_WF_SETTINGS = 2;
    private static final int REQUEST_SPEECH_RECOGNITION = 3;
    private static final int REQUEST_RECORD_AUDIO = 4;

    public static final String PREFERENCES = "preferences";
    //key for the default device to connect to
    public static final String keywordPref = "keyword";

    SharedPreferences sharedPreferences;
    SharedPreferences.Editor editor;

    //variables for the views
    protected static TextView bluetoothStatusText;
    protected Button bluetoothSettingsBtn;
    protected TextView networkStatusText;
    protected Button networkSettingsBtn;
    //protected TextView currentKeywordText;
    protected Button recognitionBtn;
    protected EditText setKeywordEditText;

    //variables needed for the connection always
    static BluetoothAdapter bluetoothAdapter;
    BluetoothManager bluetoothManager;
    BluetoothDevice device;
    boolean bracelet;

    //variables needed for network connection
    ConnectivityManager connectivityManager;
    NetworkInfo networkInfo;

    //needed for the BLE connection
    BluetoothLeService bluetoothLeService;
    BluetoothGatt bluetoothGatt;
    BluetoothGattCallback gattCallback;
    BluetoothGattCharacteristic triggerVibration;

    //variables needed for the sppech recognition
    SpeechRecognizer speechRecognizer;
    boolean isRecognizing;
    String keyword;
    Intent recognizerIntent;
    boolean keywordRegistered;

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
        bluetoothStatusText = (TextView)findViewById(R.id.BluetoothStatus);
        bluetoothSettingsBtn = (Button)findViewById(R.id.BTSettings);
        networkStatusText = (TextView) findViewById(R.id.WiFiStatus);
        networkSettingsBtn = (Button) findViewById(R.id.WiFiSettings);
        //currentKeywordText = (TextView) findViewById(R.id.current);
        recognitionBtn = (Button) findViewById(R.id.BTRecognise);
        setKeywordEditText = (EditText) findViewById(R.id.wordInput);

        discoveredDevices = new HashMap<>();

        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkInfo = connectivityManager.getActiveNetworkInfo();

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
        triggerVibration = new BluetoothGattCharacteristic(MY_UUID, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT, BluetoothGattCharacteristic.PERMISSION_WRITE);
        triggerVibration.setValue("0");

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(recognitionListener);
        isRecognizing = false;
        keywordRegistered = false;

        sharedPreferences = getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
        keyword = sharedPreferences.getString(keywordPref, "cat");
        setKeywordEditText.setText(keyword);


        //needed because otherwise we can't know if the device is connected or not, we need to listen for it getting connected
        bluetoothAdapter.disable();

        bracelet = false;

        //this still needs to be here despite the intent filter
        CheckBlueToothState();
        CheckNetworkState();
        CheckAudioPermission();
        setListeners();

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

        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_SECURE, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);

        speechRecognizer.startListening(recognizerIntent);
        //speechRecognizer.startRecognition();

    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    /**
     * Method that sets the button listener for the button that opens the bluetooth settings
     * any other button onClickListeners should be created here
     */
    private void setListeners() {
        bluetoothSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent gotToBtSettingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                startActivityForResult(gotToBtSettingsIntent, REQUEST_GO_TO_BT_SETTINGS);
            }
        });
        networkSettingsBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent goToNwSettingsIntent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                startActivityForResult(goToNwSettingsIntent, REQUEST_GO_TO_WF_SETTINGS);
            }
        });
        recognitionBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivityForResult(recognizerIntent, REQUEST_SPEECH_RECOGNITION);
            }
        });
        setKeywordEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            //after the text is changed, set the new keyword
            @Override
            public void afterTextChanged(Editable s) {
                keyword = setKeywordEditText.getText().toString();
                editor.putString(keywordPref, keyword);
                editor.commit();
                showToast("Keyword is updated to " + keyword);
                //currentKeywordText.setText(keyword);
                //setKeywordEditText.setText("");
            }
        });
    }

    /**
     * Method that returns whether or not the android device is connected to the internet
     * @return
     */
    public  boolean isConnected() {
        if (networkInfo != null && networkInfo.isAvailable() && networkInfo.isConnected()) {
            return true;
        } else {
            return false;
        }
    }

    //TODO: Finish the documentation of this method
    /**
     * Method thtat checks the state of the network connectivity
     * There are ? cases:
     */
    private void CheckNetworkState() {
        if (isConnected()) {
            networkStatusText.setText("You are connected to the internet.");
        } else {
            networkStatusText.setText("Go back to your device's Network Settings and make sure you are connected to the internet.");
        }
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
            bluetoothStatusText.setText("Bluetooth is not supported on this Android device.");
            showToast("Bluetooth is not supported on this device");
        } else {
            //if bluetooth is on ...
            if (bluetoothAdapter.isEnabled()){
                bluetoothAdapter.getProfileProxy(this, profileListener, A2DP);
                if (bracelet) {
                    bluetoothStatusText.setText("Bluetooth is enabled and the bracelet is connected to this device.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapter.isEnabled() && bluetoothAdapter.getBondedDevices() contains bracelet");
                    //not so sure about this - check the profileListener?
                    bluetoothGatt = device.connectGatt(this, true, gattCallback);
                    //connectThread(device);
                    //onA2DPProxyReceived(bluetoothA2dp);
                //...and the bracelet is not connected
                } else {
                    bluetoothStatusText.setText("Go back to your device's Bluetooth settings and make sure you are connected to the bracelet.");
                    Log.println(Log.INFO, TAG, "CheckBluetoothState, bluetoothAdapter.isEnabled() && bluetoothAdapter.getBondedDevices() does NOT contain bracelet");
                }
            //if bluetooth is off
            } else {
                bracelet = false;
                bluetoothStatusText.setText("Go back to your device's Bluetooth settings and make sure you are connected to the bracelet.");
                Log.println(Log.INFO, TAG, "CheckBluetoothState, !bluetoothAdapter.isEnabled()");
            }
        }
    }

    private void CheckAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    private void SpeechRecognition(Intent data) {
        Log.println(Log.INFO, TAG, "SpeechRecognition method");
        Log.println(Log.INFO, TAG, data.getAction());
    }

    /**
     * Method that if all goes well should send a "1" to the arduino
     */
    public void sendToBracelet() {
        Log.println(Log.INFO, TAG, "sendToBracelet");
        triggerVibration.setValue("1");
        if (bluetoothGatt != null) {
            bluetoothGatt.writeCharacteristic(triggerVibration);
            showToast("Signal was sent to the bracelet");
        } else {
            showToast("Signal cannot be sent, there is no bluetooth connection of the required type");
        }
        triggerVibration.setValue("0");
    }

    private RecognitionListener recognitionListener = new RecognitionListener() {
        @Override
        public void onReadyForSpeech(Bundle params) {

        }

        @Override
        public void onBeginningOfSpeech() {
            Log.println(Log.INFO, TAG, "onBeginningOfSpeech");
        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int error) {

        }

        @Override
        public void onResults(Bundle results) {
            //This doesn't work
            Log.println(Log.INFO, TAG, "onResults()");
            ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            float[] scores = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
            if (matches != null) {
                for (String word : matches) {
                    if (word.equals(keyword)) {
                        //TODO: Do I need keywordRegistered?
                        keywordRegistered = true;
                        sendToBracelet();
                        Log.println(Log.INFO, TAG, "sent to bracelet from listener");
                        keywordRegistered = false;
                    }
                }
            }
        }

        @Override
        public void onPartialResults(Bundle partialResults) {

        }

        @Override
        public void onEvent(int eventType, Bundle params) {

        }
    };

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
     * Broadcast receiver for network connectivity changes
     * Since checking for connectivity actions only appears to be a bit more work, it will run CheckNetworkState for any broadcast
     */
    private final BroadcastReceiver networkUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            CheckNetworkState();
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
        } else if (requestCode == REQUEST_GO_TO_WF_SETTINGS) {
            CheckNetworkState();
        } else if (requestCode == REQUEST_SPEECH_RECOGNITION && resultCode == RESULT_OK) {
            //This works
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (matches != null && matches.size() > 0) {
                String sentence = matches.get(0);
                //for (String sentence : matches) {
                    //divide the string into words
                    Log.println(Log.INFO, TAG, "sentence #" + matches.size());
                    String[] split = sentence.split(" ");
                    for (String  word: split)
                    if (word.equals(keyword)) {
                        Log.println(Log.INFO, TAG, "word is " + word);
                        sendToBracelet();
                    }
                //}
            }
            startActivityForResult(recognizerIntent, REQUEST_SPEECH_RECOGNITION);
        }

//        if (requestCode == REQUEST_SPEECH_RECOGNITION) {
//            startActivityForResult(recognizerIntent, REQUEST_SPEECH_RECOGNITION);
//        }
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
    public static TextView getBluetoothStatusText() {
        return bluetoothStatusText;
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
        speechRecognizer.stopListening();
        speechRecognizer.destroy();
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