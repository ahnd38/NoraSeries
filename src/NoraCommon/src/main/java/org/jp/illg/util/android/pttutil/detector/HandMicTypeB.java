package org.jp.illg.util.android.pttutil.detector;

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import com.itsmartreach.libzm.ZmCmdLink;

import org.jp.illg.util.android.pttutil.PTTDetectorInterface;
import org.jp.illg.util.android.pttutil.PTTResult;
import org.jp.illg.util.android.pttutil.PTTState;
import org.jp.illg.util.android.pttutil.PTTType;

public class HandMicTypeB extends DetectorBase implements PTTDetectorInterface {
	
	public HandMicTypeB(){
		super();
	}
	
	public PTTType getPTTType() {
		return PTTType.HandMicTypeB;
	}
	
	public PTTResult isPTTDetect(final Object[] vendorSpecificEventArg) {
		return new PTTResult(getPTTType(), false, null);
	}
	
	public PTTResult isPTTDetect(final KeyEvent keyEvent, final int keyCode){
		return new PTTResult(getPTTType(), false, null);
	}
	
	public PTTResult isPTTDetect(@NonNull final ZmCmdLink.ZmUserEvent event){
		if(
				event == ZmCmdLink.ZmUserEvent.zmEventPttPressed ||
				event == ZmCmdLink.ZmUserEvent.zmEventPttReleased
		){
			return new PTTResult(getPTTType(), true,
					event == ZmCmdLink.ZmUserEvent.zmEventPttPressed ?
					PTTState.DOWN : PTTState.UP
			);
		}
		else{
			return new PTTResult(getPTTType(), false, null);
		}
	}
}
