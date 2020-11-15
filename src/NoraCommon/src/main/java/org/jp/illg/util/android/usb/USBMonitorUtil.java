package org.jp.illg.util.android.usb;

import android.app.PendingIntent;
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
public class USBMonitorUtil {
	private static final String ACTION_USB_PERMISSION = "org.jp.illg.util.android.usb.USB_PERMISSION";

	public interface OnDeviceConnectListener {
		public void onAttach(UsbDevice device);

		public void onDettach(UsbDevice device);

		public void onConnect(UsbDevice device, UsbControlBlock ctrlBlock, boolean createNew);

		public void onDisconnect(UsbDevice device, UsbControlBlock ctrlBlock);
	}

	public final class UsbControlBlock {
		private final UsbDevice usbDevice;
		private UsbDeviceConnection usbConnection;
		private final SparseArray<UsbInterface> usbInterfaces = new SparseArray<UsbInterface>();

		public UsbControlBlock(UsbDevice device) {
			usbDevice = device;
			usbConnection = usbManager.openDevice(device);
		}

		public UsbDeviceConnection getUsbDeviceConnection() {
			return usbConnection;
		}

		public int getFileDescriptor() {
			return usbConnection != null ? usbConnection.getFileDescriptor() : 0;
		}

		public byte[] getRawDescriptors() {
			return usbConnection != null ? usbConnection.getRawDescriptors() : null;
		}

		public int getVenderId() {
			return usbDevice.getVendorId();
		}

		public int getProductId() {
			return usbDevice.getProductId();
		}

		public UsbInterface open(int interfaceIndex) {
			UsbInterface intf = null;
			synchronized (usbInterfaces) {
				intf = usbInterfaces.get(interfaceIndex);
			}
			if (intf == null) {
				intf = usbDevice.getInterface(interfaceIndex);
				if (intf != null) {
					synchronized (usbInterfaces) {
						usbInterfaces.append(interfaceIndex, intf);
					}
				}
			}
			return intf;
		}

		public void close(int interfaceIndex) {
			UsbInterface intf = null;
			synchronized (usbInterfaces) {
				intf = usbInterfaces.get(interfaceIndex);
				if (intf != null) {
					usbInterfaces.delete(interfaceIndex);
					usbConnection.releaseInterface(intf);
				}
			}
		}

		public void close() {
			if (usbConnection != null) {
				if (onDeviceConnectListener != null) {
					onDeviceConnectListener.onDisconnect(usbDevice, this);
				}
				synchronized (usbInterfaces) {
					final int n = usbInterfaces.size();
					int key;
					UsbInterface intf;
					for (int i = 0; i < n; i++) {
						key = usbInterfaces.keyAt(i);
						intf = usbInterfaces.get(key);
						usbConnection.releaseInterface(intf);
					}
				}
				usbConnection.close();
				usbConnection = null;
			}
		}

		@Override
		protected void finalize() throws Throwable {
			close();
			super.finalize();
		}
	}

	private final Map<UsbDevice, UsbControlBlock> controlBlocks = new HashMap<UsbDevice, UsbControlBlock>();
	private final Context context;
	private final UsbManager usbManager;
	private PendingIntent permissionIntent;
	private final OnDeviceConnectListener onDeviceConnectListener;

	private final BroadcastReceiver usbBroadcastReceiver = new BroadcastReceiver() {

		public void onReceive(Context context, Intent intent) {
			final String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
					if (device != null) {
						processConnect(device);
					}
				}
			}
			else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
				if (onDeviceConnectListener != null) {
					final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
					onDeviceConnectListener.onAttach(device);
				}
			}
			else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
				final UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
				if (device != null) {
					UsbControlBlock ctrlBlock = null;
					synchronized (controlBlocks) {
						ctrlBlock = controlBlocks.remove(device);
					}
					if (ctrlBlock != null) {
						ctrlBlock.close();
					}
					if (onDeviceConnectListener != null) {
						onDeviceConnectListener.onDettach(device);
					}
				}
			}
		}
	};

	public USBMonitorUtil(final Context context, final OnDeviceConnectListener listener) {
		this.context = context;
		usbManager = (UsbManager)context.getSystemService(Context.USB_SERVICE);
		onDeviceConnectListener = listener;
	}

	public void destroy() {
		unregister();
		synchronized (controlBlocks) {
			Set<UsbDevice> keys = controlBlocks.keySet();
			if (keys != null) {
				UsbControlBlock ctrlBlock;
				for (UsbDevice key: keys) {
					ctrlBlock = controlBlocks.remove(key);
					ctrlBlock.close();
				}
			}
		}
	}

	public void register() {
		unregister();
		permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
		final IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
		context.registerReceiver(usbBroadcastReceiver, filter);
	}

	public void unregister() {
		if (permissionIntent != null) {
			context.unregisterReceiver(usbBroadcastReceiver);
			permissionIntent = null;
		}
	}

	public List<UsbDevice>getDeviceList() {
		final HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
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

	public Iterator<UsbDevice> getDevices() {
		Iterator<UsbDevice> iterator = null;
		final HashMap<String, UsbDevice> list = usbManager.getDeviceList();
		if (list != null)
			iterator = list.values().iterator();
		return iterator;
	}


	public String dumpDevices() {
		final StringBuilder sb = new StringBuilder();
		final HashMap<String, UsbDevice> list = usbManager.getDeviceList();
		if (list == null) {
			sb.append("no device");
			return sb.toString();
		}

		final Set<String> keys = list.keySet();
		if (keys != null && keys.size() > 0) {
			for (final String key: keys) {
				sb.append("key=");
				sb.append(key);
				sb.append(":");
				sb.append(list.get(key));
			}
		}
		else
			sb.append("no device");

		return sb.toString();
	}

	public boolean hasPermission(UsbDevice device) {
		return usbManager.hasPermission(device);
	}

	public void requestPermission(UsbDevice device) {
		if (device != null) {
			if ((permissionIntent != null)
					&& !usbManager.hasPermission(device)) {

				usbManager.requestPermission(device, permissionIntent);
			} else if (usbManager.hasPermission(device)) {
				processConnect(device);
			}
		}
	}

	private final void processConnect(UsbDevice device) {
		boolean createNew = false;
		UsbControlBlock ctrlBlock;
		synchronized (controlBlocks) {
			ctrlBlock = controlBlocks.get(device);
			if (ctrlBlock == null) {
				ctrlBlock = new UsbControlBlock(device);
				controlBlocks.put(device, ctrlBlock);
			}
		}
		if (onDeviceConnectListener != null) {
			onDeviceConnectListener.onConnect(device, ctrlBlock, createNew);
		}
	}
}
