package org.jp.illg.util.android.pttutil.detector;

import org.jp.illg.util.android.pttutil.PTTDetectorInterface;
import org.jp.illg.util.android.pttutil.PTTType;

import lombok.Getter;

public abstract class DetectorBase implements PTTDetectorInterface {
	
	@Getter
	private PTTType PTTType;

	public DetectorBase() {
		super();
	}
	
	public DetectorBase(final PTTType PTTType) {
		this();
		
		this.PTTType = PTTType;
	}
}
