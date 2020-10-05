package potentiallyUseless;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.a4wbb0app.MainActivity;
import com.example.a4wbb0app.R;

import java.util.Set;

public class AndroidBluetooth extends MainActivity {

    // REQUEST_ENABLE_BT constant passed to startActivityForResult() is a locally defined integer
    // (which must be greater than 0), that the system passes back to you in your onActivityResult()
    // implementation as the requestCode parameter
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_DISCOVER_BT = 2;

    Set<BluetoothDevice> pairedDevices;

    /** Called when the activity is first created. */

    static BluetoothAdapter bluetoothAdapter;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        CheckBlueToothState();

        // Register for broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

    }

    private void CheckBlueToothState(){
        if (bluetoothAdapter == null){
            MainActivity.getStatusText().setText("Bluetooth is not supported on this Android device");
            //MainActivity.showToast("Bluetooth is not supported on this device");
        } else {
            if (bluetoothAdapter.isEnabled()){
                if (bluetoothAdapter.isDiscovering()){
                    MainActivity.getStatusText().setText("Bluetooth is currently in device discovery process.");
                } else {
                    MainActivity.getStatusText().setText("Bluetooth is enabled.");
                }
            } else {
                MainActivity.getStatusText().setText("Bluetooth is not Enabled!");
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        //If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
        // the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
        // user responded "No") then the result code is RESULT_CANCELED
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            CheckBlueToothState();
            /*if (resultCode == RESULT_CANCELED) {
                //TODO: figure this out
                //MainActivity.showToast("Something went wrong");
            }
            //calls the function again to set the status of bluetooth
            CheckBlueToothState();
        }
        if (resultCode == RESULT_OK) {
            //TODO: figure this out
            //MainActivity.showToast("Bluetooth was enabled");*/
        }

    }

    public static BluetoothAdapter getBluetoothAdapter() {
        return bluetoothAdapter;
    }

    public void queryPairedDevices(){
        pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress(); // MAC address
            }
        }
    }

    public void discoverDevices() {
        bluetoothAdapter.startDiscovery();

        // NOtTODO because this is done in ConnectThread already: after discovering the device and BEFORE connecting, cancelDiscovery()!!!!
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                //Discovery has found a device; get it and its info from Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();
            }
        }
    };

    //this displayes a dialog that asks the user if they want to set their device to discoverable for 5 min
    //If i am initiating the connection I don't need this. Well i am, so I don't need this...
    public void enableDiscoverability() {
        Intent discoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        //sets the device to be discoverable for 5 minutes (300 sec)
        discoverable.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        startActivity(discoverable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(receiver);
    }


    //Performing device discovery consumes a lot of the Bluetooth adapter's resources. After you
    // have found a device to connect to, be certain that you stop discovery with cancelDiscovery()
    // before attempting a connection. Also, you shouldn't perform discovery while connected to a
    // device because the discovery process significantly reduces the bandwidth available for any
    // existing connections.

}