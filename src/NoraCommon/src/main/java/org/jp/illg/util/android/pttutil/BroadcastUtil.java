package org.jp.illg.util.android.pttutil;

import android.content.Context;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import lombok.NonNull;

public class BroadcastUtil {
	
	private BroadcastUtil(){
		super();
	}
	
	public static LocalBroadcastManager getLBM(@NonNull final Context applicationContext){
		return LocalBroadcastManager.getInstance(applicationContext);
	}
}
