package org.jp.illg.util.android.service;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;

public class ServiceUtil {

	public static boolean isServiceWorking(Context context, Class<? extends Service> serviceClass) {
		if(context == null)
			throw new IllegalArgumentException("Context must not null.");
		if(serviceClass == null)
			throw new IllegalArgumentException("ServiceClass must not null");

		ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

		boolean found = false;
		for (ActivityManager.RunningServiceInfo serviceInfo : manager.getRunningServices(Integer.MAX_VALUE)) {
			if (serviceClass.getName().equals(serviceInfo.service.getClassName())) {
				found = true;
				break;
			}
		}

		return found;
	}
}
