package org.jp.illg.noravrclient.util;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.CountDownTimer;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BluetoothScoController {
	
	public interface BluetoothEventListener {
		void onHeadsetDisconnected();
		
		void onHeadsetConnected();
		
		void onScoAudioDisconnected();
		
		void onScoAudioConnected();
	}
	
	private BluetoothEventListener eventListener;
	
	private Context context;
	
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothHeadset bluetoothHeadset;
	private BluetoothDevice connectedHeadset;
	
	private AudioManager audioManager;
	
	private boolean isCountDownOn;
	private boolean isStarting;
	private boolean isOnHeadsetSco;
	private boolean isStarted;
	
	public BluetoothScoController(Context context, BluetoothEventListener eventListener) {
		this.context = context;
		this.eventListener = eventListener;
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
	}
	
	public boolean start() {
		if (!isStarted) {
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				isStarted = startBluetooth();
			}
			else
			{
				isStarted = startBluetooth11();
			}
		}
		
		return isStarted;
	}
	
	public void stop() {
		if (isStarted) {
			isStarted = false;
			
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB)
			{
				stopBluetooth();
			}
			else
			{
				stopBluetooth11();
			}
		}
	}
	
	public boolean isOnHeadsetSco() {
		return isOnHeadsetSco;
	}
	
	
	@SuppressWarnings("deprecation")
	private boolean startBluetooth() {
		if (bluetoothAdapter != null) {
			if (audioManager.isBluetoothScoAvailableOffCall()) {
				context.registerReceiver(headsetBroadcastReceiver,
						new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED));
				context.registerReceiver(headsetBroadcastReceiver,
						new IntentFilter(BluetoothDevice.ACTION_ACL_DISCONNECTED));
				context.registerReceiver(headsetBroadcastReceiver,
						new IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_CHANGED));
				
				audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
				
				isCountDownOn = true;
				mCountDown.start();
				
				isStarting = true;
				
				return true;
			}
		}
		
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private boolean startBluetooth11()
	{
		if (bluetoothAdapter != null)
		{
			if (audioManager.isBluetoothScoAvailableOffCall())
			{
				if (
						bluetoothAdapter.getProfileProxy(
							context,
							headsetProfileListener,
							BluetoothProfile.HEADSET
						)
				) {
					return true;
				}
			}
		}
		
		return false;
	}
	
	private void stopBluetooth() {
		if (isCountDownOn) {
			isCountDownOn = false;
			mCountDown.cancel();
		}
		
		context.unregisterReceiver(headsetBroadcastReceiver);
		audioManager.stopBluetoothSco();
		audioManager.setMode(AudioManager.MODE_NORMAL);
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void stopBluetooth11()
	{
		if (isCountDownOn)
		{
			isCountDownOn = false;
			countDown11.cancel();
		}
		
		if (bluetoothHeadset != null)
		{
			bluetoothHeadset.stopVoiceRecognition(connectedHeadset);
			context.unregisterReceiver(headsetBroadcastReceiver);
			bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset);
			bluetoothHeadset = null;
		}
	}
	
	private BluetoothProfile.ServiceListener headsetProfileListener =
			new BluetoothProfile.ServiceListener()
	{
		@Override
		public void onServiceDisconnected(int profile)
		{
			stopBluetooth11();
		}
		
		@SuppressWarnings("synthetic-access")
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		public void onServiceConnected(int profile, BluetoothProfile proxy)
		{
			bluetoothHeadset = (BluetoothHeadset) proxy;
			
			List<BluetoothDevice> devices = bluetoothHeadset.getConnectedDevices();
			if (devices.size() > 0)
			{
				connectedHeadset = devices.get(0);
				
				if(eventListener != null){eventListener.onHeadsetConnected();}
				
				isCountDownOn = true;
				countDown11.start();
			}
			
			context.registerReceiver(headsetBroadcastReceiver,
					new IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED));

			context.registerReceiver(headsetBroadcastReceiver,
					new IntentFilter(BluetoothHeadset.ACTION_AUDIO_STATE_CHANGED));
		}
	};
	
	private BroadcastReceiver headsetBroadcastReceiver = new BroadcastReceiver() {
		@SuppressWarnings({"deprecation", "synthetic-access"})
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			int state;
			if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
			{
				state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
						BluetoothHeadset.STATE_DISCONNECTED);

				if(log.isDebugEnabled())
					log.debug("Bluetooth broadcast event received.(Action=" + action + "/State=" + state + ")");
				
				if (state == BluetoothHeadset.STATE_CONNECTED)
				{
					connectedHeadset = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					
					isCountDownOn = true;
					countDown11.start();
					
					if(eventListener != null){eventListener.onHeadsetConnected();}
				}
				else if (state == BluetoothHeadset.STATE_DISCONNECTED)
				{
					if (isCountDownOn)
					{
						isCountDownOn = false;
						countDown11.cancel();
					}
					connectedHeadset = null;
					
					if(eventListener != null){eventListener.onHeadsetDisconnected();}
				}
			}
			else // audio
			{
				state =
					intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_AUDIO_DISCONNECTED);
				
				if(log.isDebugEnabled())
					log.debug("Bluetooth broadcast event received.(Action=" + action + "/State=" + state + ")");
				
				if (state == BluetoothHeadset.STATE_AUDIO_CONNECTED)
				{
					isOnHeadsetSco = true;
					
					if (isCountDownOn)
					{
						isCountDownOn = false;
						countDown11.cancel();
					}
					
					if(eventListener != null){eventListener.onScoAudioConnected();}
				}
				else if (state == BluetoothHeadset.STATE_AUDIO_DISCONNECTED)
				{
					isOnHeadsetSco = false;
					
					bluetoothHeadset.stopVoiceRecognition(connectedHeadset);
					
					if(eventListener != null){eventListener.onScoAudioDisconnected();}
				}
			}
		}
	};
	
	private CountDownTimer mCountDown =
			new CountDownTimer(10000, 1000) {
		
		@SuppressWarnings("synthetic-access")
		@Override
		public void onTick(long millisUntilFinished) {
			try {
				audioManager.startBluetoothSco();
			} catch (Exception ex) {}
		}
		
		@SuppressWarnings("synthetic-access")
		@Override
		public void onFinish() {
			isCountDownOn = false;
			audioManager.setMode(AudioManager.MODE_NORMAL);
		}
	};
	
	private CountDownTimer countDown11 =
			new CountDownTimer(10000, 1000)
	{
		@TargetApi(Build.VERSION_CODES.HONEYCOMB)
		@SuppressWarnings("synthetic-access")
		@Override
		public void onTick(long millisUntilFinished)
		{
			bluetoothHeadset.startVoiceRecognition(connectedHeadset);
		}
		
		@SuppressWarnings("synthetic-access")
		@Override
		public void onFinish()
		{
			isCountDownOn = false;
		}
	};
}