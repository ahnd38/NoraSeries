package org.jp.illg.util.android.pttutil.detector;

import android.view.KeyEvent;

import com.itsmartreach.libzm.ZmCmdLink;

import org.jp.illg.util.android.pttutil.PTTDetectorInterface;
import org.jp.illg.util.android.pttutil.PTTResult;
import org.jp.illg.util.android.pttutil.PTTState;

import lombok.NonNull;

/**
 * PTT Detector
 *  for Dellking H3-B
 */
public class HandMicTypeA extends DetectorBase implements PTTDetectorInterface {
	
	public HandMicTypeA() {
		super();
	}
	
	public PTTResult isPTTDetect(@NonNull final Object[] vendorSpecificEventArg) {
		final String type =
				vendorSpecificEventArg.length >= 1 ? String.valueOf(vendorSpecificEventArg[0]) : "";
		int state = 0;
		try{
			state =
					vendorSpecificEventArg.length >= 2 ?
							Integer.valueOf(String.valueOf(vendorSpecificEventArg[1])) : -1;
		}catch(NumberFormatException ex) {
			state = -1;
		}
		
		if(
				vendorSpecificEventArg.length < 2 ||
				!"TALK".equals(type) ||
				(
					state != 0 && state != 1
				)
		){return new PTTResult(getPTTType(), false, null);}
		
		return new PTTResult(
				getPTTType(),
				true,
				state == 1 ? PTTState.DOWN : PTTState.UP
		);
	}
	
	public PTTResult isPTTDetect(@NonNull final KeyEvent keyEvent, final int keyCode) {
		return new PTTResult(getPTTType(), false, null);
	}
	
	public PTTResult isPTTDetect(final ZmCmdLink.ZmUserEvent event){
		return new PTTResult(getPTTType(), false, null);
	}
}
