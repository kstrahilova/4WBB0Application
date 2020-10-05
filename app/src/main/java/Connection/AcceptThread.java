package Connection;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.example.a4wbb0app.MainActivity;

import java.io.IOException;
import java.util.UUID;

import potentiallyUseless.AndroidBluetooth;

import static android.content.ContentValues.TAG;
import static android.provider.CalendarContract.Calendars.NAME;


/**
 * Connecting two devices requires that one acts as a server by holding an open BluetoothServerSocket
 * It listens for incoming connection requests and provides a connected BluetoothSocket after a request
 * is accepted.Once we get the BluetoothSocket discard the BluetoothServerSocket unless the device
 * should accept more connections
 */
public class AcceptThread extends Thread {
    //is the final server socket
    private final BluetoothServerSocket mmServerSocket;
    BluetoothAdapter bluetoothAdapter;
    //the UUID is the Universally Unique Identifier of the service
    public final UUID MY_UUID;

    public AcceptThread() {
        MY_UUID = MainActivity.MY_UUID;
        // Use a temporary object that is later assigned to mmServerSocket
        // because mmServerSocket is final.
        BluetoothServerSocket tmp = null;
        bluetoothAdapter = MainActivity.getBluetoothAdapter();
        try {
            // MY_UUID is the app's UUID string, also used by the client code.
            //get the BluetoothServerSocket
            tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, MY_UUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's listen() method failed", e);
        }
        mmServerSocket = tmp;
    }

    public void run() {
        BluetoothSocket socket = null;
        // Keep listening until exception occurs or a socket is returned.
        while (true) {
            try {
                //start listening for connection requests on the server socket
                socket = mmServerSocket.accept();
            } catch (IOException e) {
                Log.e(TAG, "Socket's accept() method failed", e);
                break;
            }

            if (socket != null) {
                // A connection was accepted. Perform work associated with the connection in a separate thread.
                // TODO:
                manageMyConnectedSocket(socket);
                try {
                    //closes the server socket once a socket is acquired
                    mmServerSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
    }

    //TODO:
    public void manageMyConnectedSocket(BluetoothSocket socket) {}


    // Closes the connect socket and causes the thread to finish.
    public void cancel() {
        try {
            mmServerSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}
