package org.jp.illg.util.android.pttutil.detector;

import android.view.KeyEvent;

import com.itsmartreach.libzm.ZmCmdLink;

import org.jp.illg.util.android.pttutil.PTTDetectorInterface;
import org.jp.illg.util.android.pttutil.PTTResult;
import org.jp.illg.util.android.pttutil.PTTState;

import lombok.NonNull;

/**
 * PTT Detector
 *  for Keyboard
 */
public class KeyboardDetector extends DetectorBase implements PTTDetectorInterface {
	
	public KeyboardDetector() {
		super();
	}
	
	public PTTResult isPTTDetect(@NonNull final Object[] vendorSpecificEventArg){
		return new PTTResult(getPTTType(), false, null);
	}
	
	public PTTResult isPTTDetect(@NonNull final KeyEvent keyEvent, final int keyCode) {
		return new PTTResult(
				getPTTType(),
				keyEvent.getKeyCode() == keyCode &&
						(
							keyEvent.getAction() == KeyEvent.ACTION_UP ||
							(keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getRepeatCount() == 0)
						),
				keyEvent.getAction() == KeyEvent.ACTION_DOWN ? PTTState.DOWN : PTTState.UP
		);
	}
	
	public PTTResult isPTTDetect(final ZmCmdLink.ZmUserEvent event){
		return new PTTResult(getPTTType(), false, null);
	}
}
