package org.jp.illg.noravrclient;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.multidex.MultiDex;

import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ApplicationInformationGradleMaven;
import org.jp.illg.util.android.pttutil.PTTDetectService;
import org.jp.illg.util.android.pttutil.PTTDetector;
import org.parceler.Parcels;

import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static org.jp.illg.noravrclient.NoraVRClientDefine.*;
import static org.jp.illg.noravrclient.NoraVRClientUtil.*;


@Slf4j
public class NoraVRClientApplication extends Application implements LifecycleObserver {

	private final Deque<HeardEntry> lastHeardList;
	
	@Getter
	@Setter
	private static PTTDetectService pttDetectService;
	
	public NoraVRClientApplication(){
		super();
		
		lastHeardList = new LinkedList<>();
	}
	
	@OnLifecycleEvent(Lifecycle.Event.ON_START)
	public void onApplicationForegrounded(){
		log.trace(NoraVRClientApplication.class.getSimpleName() + ".onApplicationForegrounded()");
	}
	
	@OnLifecycleEvent(Lifecycle.Event.ON_STOP)
	public void onApplicationBackgrounded() {
		log.trace(NoraVRClientApplication.class.getSimpleName() + ".onApplicationBackgrounded()");
	}
	
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		
		MultiDex.install(this);
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		log.trace(NoraVRClientApplication.class.getSimpleName() + ".onCreate()");
		
		ProcessLifecycleOwner.get().getLifecycle().addObserver(this);
		
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(String.valueOf(MSG_REQUEST_LASTHEARDLIST));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_ADDHEARD));
		
		getLBM(getApplicationContext()).registerReceiver(broadcastReceiver, intentFilter);
	}
	
	@Override
	public void onTerminate(){
		log.trace(NoraVRClientApplication.class.getSimpleName() + ".onTerminate()");
		
		getLBM(getApplicationContext()).unregisterReceiver(broadcastReceiver);
		
		super.onTerminate();
	}
	
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			switch(Integer.valueOf(intent.getAction())){
				case MSG_REQUEST_ADDHEARD:
					onReceiveMsgRequestAddHeard(intent);
					break;
					
				case MSG_REQUEST_LASTHEARDLIST:
					onReceiveMsgRequestLastHeardList(intent);
					break;
					
				default:
					break;
			}
		}
	};
	
	private void onReceiveMsgRequestAddHeard(final Intent intent){
		while(lastHeardList.size() >= 20){lastHeardList.pollLast();}
		
		final HeardEntry entry = Parcels.unwrap(intent.getParcelableExtra(ID_HEARDENTRY));
		if(entry == null){return;}
		
		for(Iterator<HeardEntry> it = lastHeardList.iterator(); it.hasNext();){
			final HeardEntry e = it.next();
			
			if(e.myCallsign.equals(entry.myCallsign)){it.remove();}
		}
		
		lastHeardList.addFirst(entry);
		
		final Intent response = new Intent(String.valueOf(MSG_RESPONSE_ADDHEARD));
		sendMessageToHost(getApplicationContext(), response);
		
		final Intent notify = new Intent(String.valueOf(MSG_NOTIFY_LASTHEARDLIST_CHANGE));
		notify.putExtra(ID_HEARDENTRYLIST, lastHeardList.toArray(new HeardEntry[0]));
		sendMessageToHost(getApplicationContext(), notify);
	}
	
	private void onReceiveMsgRequestLastHeardList(final Intent intent){
		final Intent response = new Intent(String.valueOf(MSG_RESPONSE_LASTHEARDLIST));
		response.putExtra(ID_HEARDENTRYLIST, lastHeardList.toArray(new HeardEntry[0]));
		sendMessageToHost(getApplicationContext(), response);
	}
}
