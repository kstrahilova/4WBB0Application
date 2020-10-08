package Connection;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.util.Log;
import android.widget.Toast;

import com.example.a4wbb0app.MainActivity;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;

import potentiallyUseless.AndroidBluetooth;

import static android.content.ContentValues.TAG;

/**
 * I THINK WE WILL ONLY NEED THIS ONE AND NOT ACCEPTTHREAD
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
    BroadcastReceiver receiver;
    //static BluetoothProfile profile;
    //static BluetoothA2dp A2dpinstance;

    //We need a device that we have found
    @SuppressLint("LongLogTag")
    public ConnectThread(BluetoothDevice device) {
        MY_UUID = MainActivity.MY_UUID;
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        BluetoothSocket tmp = null;
        mmDevice = device;
        bluetoothAdapter = MainActivity.getBluetoothAdapter();

        Log.println(Log.INFO, "ConnectThread", "we are in the constructor");


        //bluetoothAdapter.getProfileProxy(MainActivity.getContext(), listener, BluetoothProfile.A2DP);

        if (device == null) {
            Log.println(Log.INFO, TAG, "device is null in constructor");
        }
        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            //get a BluetoothSocket that allows the client to connect to a Bluetooth device
            tmp = device.createRfcommSocketToServiceRecord(MY_UUID);//createInsecureRfcommSocketToServiceRecord(MY_UUID);//createRfcommSocketToServiceRecord(MY_UUID);
            //tmp =(BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
            //tmp =(BluetoothSocket) device.getClass().getMethod("createRfcommSocketToServiceRecord", new Class[] {int.class, UUID.class}).invoke(device,1, MY_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
//        } catch (NoSuchMethodException e) {
//            e.printStackTrace();
//        } catch (IllegalAccessException e) {
//            e.printStackTrace();
//        } catch (InvocationTargetException e) {
//            e.printStackTrace();
        }
        mmSocket = tmp;
    }


    public void run() {
        Log.println(Log.INFO, "ConnectThread", "beginning of run()");
        // Cancel discovery because it otherwise slows down the connection.
        bluetoothAdapter.cancelDiscovery();

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocket.connect();
            Log.println(Log.INFO, "ConnectThread", "after mmSocket.connect()");
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
//            try {
//                mmSocket.close();
//            } catch (IOException closeException) {
//                Log.e(TAG, "Could not close the client socket", closeException);
//            }
            Log.e(TAG, "Connect didn't work", connectException);
            return;
        }

        // The connection attempt succeeded. Perform work associated with
        // the connection in a separate thread.
        //TODO:
        manageMyConnectedSocket(mmSocket);

    }

    /*
    TODO:
     */
    public void manageMyConnectedSocket(BluetoothSocket socket) {
        new BluetoothService().new ConnectedThread(mmSocket).run();
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            Log.println(Log.INFO, "ConnectThread", "we are closing the socket.");
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}
