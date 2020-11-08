package org.jp.illg.util.android.usb;

import android.app.PendingIntent;
import android.companion.DeviceFilter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class USBMonitor {
	private static final String ACTION_USB_PERMISSION = "org.jp.illg.util.android.usb.USB_PERMISSION";

	private final Map<UsbDevice, UsbControlBlock> mCtrlBlocks = new HashMap<UsbDevice, UsbControlBlock>();

	private final Context mContext;
	private final UsbManager mUsbManager;	// API >= 12
	private PendingIntent mPermissionIntent;
	private final OnDeviceConnectListener mOnDeviceConnectListener;

	public interface OnDeviceConnectListener {
		/**
		 * called when device attached
		 * @param device
		 */
		public void onAttach(UsbDevice device);
		/**
		 * called when device dettach(after onDisconnect)
		 * @param device
		 */
		public void onDettach(UsbDevice device);
		/**
		 * called after device opened
		 * @param device
		 * @param ctrlBlock
		 * @param createNew
		 */
		public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew);
		/**
		 * called when USB device removed or its power off (this callback is called before device closing)
		 * @param device
		 * @param ctrlBlock
		 */
		public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock);
	}

	public USBMonitor(Context context, OnDeviceConnectListener listener) {
		mContext = context;
		mUsbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
		mOnDeviceConnectListener = listener;
	}

	public void destroy() {
		unregister();
		synchronized (mCtrlBlocks) {
			Set<UsbDevice> keys = mCtrlBlocks.keySet();
			if (keys != null) {
				UsbControlBlock ctrlBlock;
				for (UsbDevice key: keys) {
					ctrlBlock = mCtrlBlocks.remove(key);
					ctrlBlock.close();
				}
			}
		}
	}

	/**
	 * register BroadcastReceiver to monitor USB events
	 */
	public void register() {
		unregister();
		mPermissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
		final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		mContext.registerReceiver(mUsbReceiver, filter);
	}

	/**
	 * unregister BroadcastReceiver
	 */
	public void unregister() {
		if (mPermissionIntent != null) {
			mContext.unregisterReceiver(mUsbReceiver);
			mPermissionIntent = null;
		}
	}

	/**
	 * return specified USB device list
	 * @return
	 */
	public List<UsbDevice>getDeviceList() {
		final HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();
		List<UsbDevice> result = null;
		if (deviceList != null) {
			result = new ArrayList<UsbDevice>();
			final Iterator<UsbDevice> iterator = deviceList.values().iterator();
			UsbDevice device;
			while(iterator.hasNext()){
				device = iterator.next();

				result.add(device);
			}
		}
		return result;
	}
	/**
	 * get USB device list
	 * @return
	 */
	public Iterator<UsbDevice> getDevices() {
		Iterator<UsbDevice> iterator = null;
		final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
		if (list != null)
			iterator = list.values().iterator();
		return iterator;
	}

	/**
	 * output device list to Slf4j
	 */
	public void dumpDevices() {
		final HashMap<String, UsbDevice> list = mUsbManager.getDeviceList();
		if (list != null) {
			final Set<String> keys = list.keySet();
			if (keys != null && keys.size() > 0) {
				for (String key: keys) {
					log.info("key=" + key + ":" + list.get(key));
				}
			} else {
				log.info("no device");
			}
		} else {
			log.info("no device");
		}
	}

	public boolean hasPermission(UsbDevice device) {
		return mUsbManager.hasPermission(device);
	}

	/**
	 * request permission to access to USB device
	 * @param device
	 */
	public void requestPermission(UsbDevice device) {
		if (device != null) {
			if ((mPermissionIntent != null)
					&& !mUsbManager.hasPermission(device)) {

				mUsbManager.requestPermission(device, mPermissionIntent);
			} else if (mUsbManager.hasPermission(device)) {
				processConnect(device);
			}
		}
	}

	/**
	 * BroadcastReceiver for USB permission
	 */
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					if (device != null) {
						processConnect(device);
					}
				} else {
					// failed to get permission
				}
			} else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				// when USB device attached to Android device
				if (mOnDeviceConnectListener != null) {
					final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					mOnDeviceConnectListener.onAttach(device);
				}
			} else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				// when device removed or power off
				final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (device != null) {
					UsbControlBlock ctrlBlock = null;
					synchronized (mCtrlBlocks) {
						ctrlBlock = mCtrlBlocks.remove(device);
					}
					if (ctrlBlock != null) {
						ctrlBlock.close();
					}
					if (mOnDeviceConnectListener != null) {
						mOnDeviceConnectListener.onDettach(device);
					}
				}
			}
		}
	};

	private final void processConnect(UsbDevice device) {
		boolean createNew = false;
		UsbControlBlock ctrlBlock;
		synchronized (mCtrlBlocks) {
			ctrlBlock = mCtrlBlocks.get(device);
			if (ctrlBlock == null) {
				ctrlBlock = new UsbControlBlock(device);
				mCtrlBlocks.put(device, ctrlBlock);
			}
		}
		if (mOnDeviceConnectListener != null) {
			mOnDeviceConnectListener.onConnect(device, ctrlBlock, createNew);
		}
	}

	public final class UsbControlBlock {
		private final UsbDevice mDevice;
		private UsbDeviceConnection mConnection;
		private final SparseArray<UsbInterface> mInterfaces = new SparseArray<UsbInterface>();

		/**
		 * this class needs permission to access USB device before constructing
		 * @param device
		 */
		public UsbControlBlock(UsbDevice device) {
			mDevice = device;
			mConnection = mUsbManager.openDevice(device);
		}

		public UsbDeviceConnection getUsbDeviceConnection() {
			return mConnection;
		}

		public int getFileDescriptor() {
			return mConnection != null ? mConnection.getFileDescriptor() : 0;
		}

		public byte[] getRawDescriptors() {
			return mConnection != null ? mConnection.getRawDescriptors() : null;
		}

		public int getVenderId() {
			return mDevice.getVendorId();
		}

		public int getProductId() {
			return mDevice.getProductId();
		}

		public UsbInterface open(int interfaceIndex) {
			UsbInterface intf = null;
			synchronized (mInterfaces) {
				intf = mInterfaces.get(interfaceIndex);
			}
			if (intf == null) {
				intf = mDevice.getInterface(interfaceIndex);
				if (intf != null) {
					synchronized (mInterfaces) {
						mInterfaces.append(interfaceIndex, intf);
					}
				}
			}
			return intf;
		}

		public void close(int interfaceIndex) {
			UsbInterface intf = null;
			synchronized (mInterfaces) {
				intf = mInterfaces.get(interfaceIndex);
				if (intf != null) {
					mInterfaces.delete(interfaceIndex);
					mConnection.releaseInterface(intf);
				}
			}
		}

		public void close() {
			if (mConnection != null) {
				if (mOnDeviceConnectListener != null) {
					mOnDeviceConnectListener.onDisconnect(mDevice, this);
				}
				synchronized (mInterfaces) {
					final int n = mInterfaces.size();
					int key;
					UsbInterface intf;
					for (int i = 0; i < n; i++) {
						key = mInterfaces.keyAt(i);
						intf = mInterfaces.get(key);
						mConnection.releaseInterface(intf);
					}
				}
				mConnection.close();
				mConnection = null;
			}
		}

		@Override
		protected void finalize() throws Throwable {
			close();
			super.finalize();
		}
	}

}
