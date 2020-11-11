package org.jp.illg.noravrclient;

import android.content.Context;
import android.content.Intent;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import lombok.NonNull;

public class NoraVRClientUtil {
	
	private NoraVRClientUtil(){}
	
	public static LocalBroadcastManager getLBM(@NonNull final Context applicationContext){
		return LocalBroadcastManager.getInstance(applicationContext);
	}
	
	public static boolean sendMessageToHost(
			@NonNull final Context applicationContext,
			@NonNull final Intent msg
	){
		return sendMessageToHost(applicationContext, msg, false);
	}
	
	public static boolean sendMessageToHost(
			@NonNull final Context applicationContext,
			@NonNull final Intent msg,
			final boolean sync
	){
		if(sync)
			getLBM(applicationContext).sendBroadcastSync(msg);
		else
			getLBM(applicationContext).sendBroadcast(msg);
		
		return true;
	}
	
	public static String formatCallsignFullLength(final String callsign){
		return formatCallsignLength(callsign, 8);
	}
	
	public static String formatCallsignShortLength(final String callsign){
		return formatCallsignLength(callsign, 4);
	}
	
	public static String formatCallsignLength(final String callsign, final int length) {
		final String call = callsign != null ? callsign : "";
		
		return String.format("%-" + length + "S", call);
	}
}
