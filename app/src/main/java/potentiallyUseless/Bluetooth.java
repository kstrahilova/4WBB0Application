package potentiallyUseless;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;

import androidx.core.app.ActivityCompat;

import static android.app.Activity.RESULT_OK;
import static androidx.core.app.ActivityCompat.startActivityForResult;

public class Bluetooth {

    BluetoothAdapter bluetoothAdapter;
    // REQUEST_ENABLE_BT constant passed to startActivityForResult() is a locally defined integer
    // (which must be greater than 0), that the system passes back to you in your onActivityResult()
    // implementation as the requestCode parameter
    private final static int REQUEST_ENABLE_BT = 1;

    public Bluetooth() {

    }

    public BluetoothAdapter getBluetoothAdapter() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            // TODO: display some error message
            //status.setText("Your device does not support Bluetooth");
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                //startActivityForResult(enableBtIntent, RESULT_OK);//REQUEST_ENABLE_BT);
            }
            //If enabling Bluetooth succeeds, your activity receives the RESULT_OK result code in
            // the onActivityResult() callback. If Bluetooth was not enabled due to an error (or the
            // user responded "No") then the result code is RESULT_CANCELED

        }
        return bluetoothAdapter;
    }
}
