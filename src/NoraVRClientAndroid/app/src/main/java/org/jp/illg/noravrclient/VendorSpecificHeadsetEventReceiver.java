package org.jp.illg.noravrclient;

import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;


import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VendorSpecificHeadsetEventReceiver extends BroadcastReceiver {
	
	public interface Callback {
		void onEventInvoked(final String cmd, final String cmdType, final String cmdArgs);
	}
	
	
	public VendorSpecificHeadsetEventReceiver(){
		super();
	}
	
	
	@Override
	public void onReceive(@NotNull Context context, @NotNull Intent intent) {
		if(log.isTraceEnabled())
			log.trace("onReceive() = " + intent.getDataString());
		
		if(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT.equals(intent.getAction())) {
			final String cmd =
					intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
			final int cmdType =
					intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);
			final Object[] cmdArgs =
					(Object[])intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
			
			if(log.isTraceEnabled()) {
				log.trace(
						"event invoked(cmd=" + cmd + "/cmd type=" + cmdType + "/cmdArgs=" + Arrays.toString(cmdArgs) + ")"
				);
			}
			
			//callback.onEventInvoked(cmd, cmdType, cmdArgs);
		}
	}
}
