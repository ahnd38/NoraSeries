package org.jp.illg.util.android.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OnClearFromRecentService extends Service {

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		log.trace(this.getClass().getSimpleName() + ".onStartCommand()");


		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		log.trace(this.getClass().getSimpleName() + ".onDestroy()");
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {
		log.trace(this.getClass().getSimpleName() + ".onTaskRemoved()");
		//Code here
		stopSelf();
	}
}
