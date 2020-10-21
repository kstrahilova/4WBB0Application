package Connection;

import android.bluetooth.BluetoothDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import java.util.HashMap;

// Adapter for holding devices found through scanning.
public class LeDeviceListAdapter extends BaseAdapter {
    private HashMap<String, BluetoothDevice> mLeDevices;

    public LeDeviceListAdapter() {
        super();
        mLeDevices = new HashMap<>();
        //mInflator = DeviceScanActivity.this.getLayoutInflater();
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
//        RecyclerView.ViewHolder viewHolder;
//        // General ListView optimization code.
//        if (view == null) {
//            view = mInflator.inflate(R.layout.listitem_device, null);
//            viewHolder = new ViewHolder();
//            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
//            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
//            view.setTag(viewHolder);
//        } else {
//            viewHolder = (ViewHolder) view.getTag();
//        }
//
//        BluetoothDevice device = mLeDevices.get(i);
//        final String deviceName = device.getName();
//        if (deviceName != null && deviceName.length() > 0)
//            viewHolder.deviceName.setText(deviceName);
//        else
//            viewHolder.deviceName.setText(R.string.unknown_device);
//        viewHolder.deviceAddress.setText(device.getAddress());

        return view;
    }
}