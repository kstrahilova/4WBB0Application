package Connection;

import android.bluetooth.BluetoothDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.HashMap;

/**
 * Adapter for holding devices found through scanning.
 */
public class LeDeviceListAdapter extends BaseAdapter {
    private HashMap<String, BluetoothDevice> mLeDevices;

    public LeDeviceListAdapter() {
        super();
        mLeDevices = new HashMap<>();
    }

    public void addDevice(String name, BluetoothDevice device) {
        if(!mLeDevices.containsKey(name)) {
            mLeDevices.put(name, device);
        }
    }

    public boolean containsKey(String name) {
        return mLeDevices.containsKey(name);
    }

    private HashMap<String, BluetoothDevice> getLeDevices() {
        return mLeDevices;
    }

    public BluetoothDevice getDevice(String name) {
        return mLeDevices.get(name);
    }

    public void clear() {
        mLeDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeDevices.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeDevices.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        return view;
    }
}