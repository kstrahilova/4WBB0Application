package Connection;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.util.Log;

import com.example.a4wbb0app.MainActivity;

import java.io.IOException;
import java.util.UUID;

/**
 * Is not used, since it is part of the Bluetooth Classic Implementation, and the app uses BLE
 * Facilitates connecting to a remote device that accepts connections on an open server socket
 * the device is found and then passed as a parameter to the constructor
 */
public class ConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final String TAG = "ConnectThread";
    //object that represents the remote device we want to connect to
    private final BluetoothDevice mmDevice;
    BluetoothAdapter bluetoothAdapter;
    public final UUID MY_UUID;

    /**
     * Connect the device by making a socket
     * @param device
     */
    @SuppressLint("LongLogTag")
    public ConnectThread(BluetoothDevice device) {
        MY_UUID = MainActivity.MY_UUID;
        // Use a temporary object that is later assigned to mmSocket because it is final.
        BluetoothSocket tmp = null;
        mmDevice = device;
        bluetoothAdapter = MainActivity.getBluetoothAdapter();
        if (device == null) {
            Log.println(Log.INFO, TAG, "device is null in constructor");
        }
        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mmSocket = tmp;
    }

    /**
     * Method that attempts to connect to the socket
     */
    public void run() {
        //Cancel discovery because it otherwise slows down the connection.
        bluetoothAdapter.cancelDiscovery();
        try {
            // Connect to the remote device through the socket. This call blocks until it succeeds or throws an exception.
            mmSocket.connect();
        } catch (IOException connectException) {
            //Unable to connect; close the socket and return.
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
            return;
        }

        //The connection attempt succeeded. Perform work associated with the connection in a separate thread.
        manageMyConnectedSocket(mmSocket);
    }

    /*
     * Method that manages the socket
     */
    public void manageMyConnectedSocket(BluetoothSocket socket) {
        new BluetoothService().new ConnectedThread(mmSocket).run();
        //TODO: implement this method
    }

    /**
     * Closes the client socket and causes the thread to finish.
     */
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}
