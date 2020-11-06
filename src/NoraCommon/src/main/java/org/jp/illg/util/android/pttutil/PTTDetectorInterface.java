package org.jp.illg.util.android.pttutil;

import android.view.KeyEvent;

import com.itsmartreach.libzm.ZmCmdLink;

public interface PTTDetectorInterface {
	
	PTTType getPTTType();

	PTTResult isPTTDetect(final Object[] vendorSpecificEventArg);
	
	PTTResult isPTTDetect(final KeyEvent keyEvent, final int keyCode);
	
	PTTResult isPTTDetect(final ZmCmdLink.ZmUserEvent event);
}
