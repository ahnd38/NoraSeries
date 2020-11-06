package org.jp.illg.util.android.pttutil;

import android.view.KeyEvent;

import com.itsmartreach.libzm.ZmCmdLink;

import org.jp.illg.util.android.pttutil.detector.HandMicTypeA;
import org.jp.illg.util.android.pttutil.detector.HandMicTypeB;
import org.jp.illg.util.android.pttutil.detector.KeyboardDetector;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;

public enum PTTType {
	
	KeyBoard(new KeyboardDetector()),
	HandMicTypeA(new HandMicTypeA()),
	HandMicTypeB(new HandMicTypeB())
	;
	

	@Getter(AccessLevel.PRIVATE)
	private final PTTDetectorInterface detector;
	
	PTTType(@NonNull final PTTDetectorInterface detector){
		this.detector = detector;
	}
	
	public String getTypeName(){
		return this.toString();
	}
	
	public static PTTType getTypeByName(final String typeName) {
		for(final PTTType state : values()){
			if(state.getTypeName().equals(typeName))
				return state;
		}
		
		return null;
	}
	
	public PTTResult isPTTDetect(final Object[] vendorSpecificEventArg) {
		final PTTResult result = getDetector().isPTTDetect(vendorSpecificEventArg);
		if(result != null) {
			result.setType(this);
			return result;
		}
		else{
			return new PTTResult(this, false, null);
		}
	}
	
	public PTTResult isPTTDetect(final KeyEvent keyEvent, final int keyCode) {
		final PTTResult result = getDetector().isPTTDetect(keyEvent, keyCode);
		if(result != null) {
			result.setType(this);
			return result;
		}
		else{
			return new PTTResult(this, false, null);
		}
	}
	
	public PTTResult isPTTDetect(final ZmCmdLink.ZmUserEvent event) {
		final PTTResult result = getDetector().isPTTDetect(event);
		if(result != null) {
			result.setType(this);
			return result;
		}
		else{
			return new PTTResult(this, false, null);
		}
	}
	
	public static PTTType[] getAllType(){
		return values();
	}
}
