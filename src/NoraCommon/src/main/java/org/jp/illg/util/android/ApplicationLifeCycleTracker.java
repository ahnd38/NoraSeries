package org.jp.illg.util.android;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApplicationLifeCycleTracker implements Application.ActivityLifecycleCallbacks {




	private int startCount;

	private int createCount;

	{
		startCount = 0;
		createCount = 0;
	}



	public void onActivityCreated(Activity activity, Bundle savedInstanceState){
		log.trace(this.getClass().getSimpleName() + ".onActivityCreated()");
	}

	public void onActivityStarted(Activity activity){
		log.trace(this.getClass().getSimpleName() + ".onActivityStarted()");
	}

	public void onActivityResumed(Activity activity){
		log.trace(this.getClass().getSimpleName() + ".onActivityResumed()");
	}

	public void onActivityPaused(Activity activity){
		log.trace(this.getClass().getSimpleName() + ".onActivityPaused()");
	}

	public void onActivityStopped(Activity activity){
		log.trace(this.getClass().getSimpleName() + ".onActivityStopped()");
	}

	public void onActivitySaveInstanceState(Activity activity, Bundle outState){
		log.trace(this.getClass().getSimpleName() + ".onActivitySaveInstanceState()");
	}

	public void onActivityDestroyed(Activity activity){
		log.trace(this.getClass().getSimpleName() + ".onActivityDestroyed()");
	}
}
