package com.kx3panadapterfft;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TwoLineListItem;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.HexDump;

import java.util.ArrayList;
import java.util.List;

public class DeviceListActivity extends Activity {

    private final static String TAG = DeviceListActivity.class.getSimpleName();

    private static UsbManager mUsbManager;
    private ListView mListView;
    private static TextView mProgressBarTitle;
    private TextView mTitle;
    private static ProgressBar mProgressBar;

    private static final int MESSAGE_REFRESH = 101;
    private static final long REFRESH_TIMEOUT_MILLIS = 5000;

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication 
                            Log.i("usb", "permission granted for device " + device);
                        }
                    } 
                    else {
                        Log.i("usb", "permission denied for device " + device);
                    }
                }
            }
        }
    };   
    
    private final static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_REFRESH:
                    refreshDeviceList();
                    mHandler.sendEmptyMessageDelayed(MESSAGE_REFRESH, REFRESH_TIMEOUT_MILLIS);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }

    };

    /** Simple container for a UsbDevice and its driver. */
    private static class DeviceEntry {
        public UsbDevice device;
        public UsbSerialDriver driver;

        DeviceEntry(UsbDevice device, UsbSerialDriver driver) {
            this.device = device;
            this.driver = driver;
        }
    }

    private static List<DeviceEntry> mEntries = new ArrayList<DeviceEntry>();
    private static ArrayAdapter<DeviceEntry> mAdapter;
    private static PendingIntent mPermissionIntent;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.devicelist);

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(mUsbReceiver, filter);
        
        mTitle = (TextView) findViewById(R.id.demoTitle);
        
        mUsbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mListView = (ListView) findViewById(R.id.deviceList);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mProgressBarTitle = (TextView) findViewById(R.id.progressBarTitle);

        Intent myIntent = getIntent();
        mTitle.setText(myIntent.getStringExtra("title"));
        
        mAdapter = new ArrayAdapter<DeviceEntry>(this, android.R.layout.simple_expandable_list_item_2, mEntries) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                final TwoLineListItem row;
                if (convertView == null){
                    final LayoutInflater inflater =
                            (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                    row = (TwoLineListItem) inflater.inflate(android.R.layout.simple_list_item_2, null);
                } else {
                    row = (TwoLineListItem) convertView;
                }

                final DeviceEntry entry = mEntries.get(position);
                final String title;
                if(entry.device!=null){
                	title = String.format("Vendor %s Product %s",
                        HexDump.toHexString((short) entry.device.getVendorId()),
                        HexDump.toHexString((short) entry.device.getProductId()));
                }else{
                	title=getString(R.string.noconnectivity);
                }
                row.getText1().setText(title);

                final String subtitle = entry.driver != null ?
                        entry.driver.getClass().getSimpleName() : "No Driver";
                row.getText2().setText(subtitle);

                return row;
            }

        };
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "Pressed item " + position);
                if (position >= mEntries.size()) {
                    Log.w(TAG, "Illegal position.");
                    return;
                }

                final DeviceEntry entry = mEntries.get(position);
                if(entry.device==null){
                	showConsoleActivity(null,null);
                }
                final UsbSerialDriver driver = entry.driver;
                if (driver == null) {
                    Log.d(TAG, "No driver.");
                    return;
                }

                showConsoleActivity(entry.device.getDeviceName(), driver );
            }
        });
    }
    @Override
    protected void onStop()
    {
        unregisterReceiver(mUsbReceiver);
        super.onStop();
    }
    @Override
    protected void onResume() {
        super.onResume();
        mHandler.sendEmptyMessage(MESSAGE_REFRESH);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeMessages(MESSAGE_REFRESH);
    }

    private static void refreshDeviceList() {
        showProgressBar();

        new AsyncTask<Void, Void, List<DeviceEntry>>() {
            @Override
            protected List<DeviceEntry> doInBackground(Void... params) {
                Log.d(TAG, "Refreshing device list ...");
                SystemClock.sleep(1000);
            
                
                final List<DeviceEntry> result = new ArrayList<DeviceEntry>();
                result.add(new DeviceEntry(null, null));//No Connectivity
                for (final UsbDevice device : mUsbManager.getDeviceList().values()) {
                	mUsbManager.requestPermission(device, mPermissionIntent);
                    final UsbSerialDriver driver = UsbSerialProber.acquire(mUsbManager, device);
                    Log.d(TAG, "Found usb device: " + device);
                    if (driver==null) {
                        Log.d(TAG, "  - No UsbSerialDriver available.");
                        result.add(new DeviceEntry(device, null));
                    } else {
                            result.add(new DeviceEntry(device, driver));
                    }
                }
                return result;
            }

            @Override
            protected void onPostExecute(List<DeviceEntry> result) {
                mEntries.clear();
                mEntries.addAll(result);
                mAdapter.notifyDataSetChanged();
                mProgressBarTitle.setText(
                        String.format("%s device(s) found",Integer.valueOf(mEntries.size()-1)));
                hideProgressBar();
                Log.d(TAG, "Done refreshing, " + (mEntries.size()-1) + " entries found.");
            }

        }.execute((Void) null);
    }

    private static void showProgressBar() {
        mProgressBar.setVisibility(View.VISIBLE);
        mProgressBarTitle.setText(R.string.refreshing);
    }

    private static void hideProgressBar() {
        mProgressBar.setVisibility(View.INVISIBLE);
    }

    private void showConsoleActivity(String usbDevice, UsbSerialDriver driver) {
    	if(SoundRecordAndAnalysisActivity.serialUsbDevice==null){
    	SoundRecordAndAnalysisActivity.serialUsbDevice=usbDevice==null?"":usbDevice;
    	SoundRecordAndAnalysisActivity.serialDriver=driver;
    	}else{
    		SoundRecordAndAnalysisActivity.audioUsbDevice=usbDevice==null?"":usbDevice;
        	SoundRecordAndAnalysisActivity.audioDriver=driver;
    	}
    	 Intent intent = new Intent(this, SoundRecordAndAnalysisActivity.class);
         startActivity(intent);
         finish();
    }
   
	

}
