package org.jp.illg.util.android.pttutil;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.view.KeyEvent;


import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


public class PTTDetectService extends Service {
	
	// Binder given to clients
	private final IBinder mBinder = new LocalBinder();
	
	private PTTDetector pttDetector;
	
	public PTTDetectService() {
		super();
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		pttDetector = new PTTDetector(getApplicationContext());
		pttDetector.start();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		super.onStartCommand(intent, flags, startId);
		return START_STICKY;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		pttDetector.stop();
	}
	
	public class LocalBinder extends Binder {
		public PTTDetectService getService() {
			// Return this instance of LocalService so clients can call public methods
			return PTTDetectService.this;
		}
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}
	
	public void resetState() {
		if(pttDetector != null){
			pttDetector.resetState();
		}
	}
	
	public void receiveKeyEvent(@NonNull final KeyEvent keyEvent) {
		if(pttDetector != null){
			pttDetector.receiveKeyEvent(keyEvent);
		}
	}
	
	public void setPTTType(final PTTType pttType){
		if(pttDetector != null){
			pttDetector.setPttType(pttType);
		}
	}
	
	public void setPTTType(final PTTType pttType, final int keyCode){
		if(pttDetector != null){
			pttDetector.setPttType(pttType);
			pttDetector.setPttKeyCode(keyCode);
		}
	}
	
	public void setPTTToggleMode(final boolean pttToggleMode){
		if(pttDetector != null){
			pttDetector.setToggleMode(pttToggleMode);
		}
	}
	
	private void sendMessageToActivity(String key, int value) {
		Intent intent = new Intent("intentKey");
		
		intent.putExtra(key, value);
		LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
	}
}
