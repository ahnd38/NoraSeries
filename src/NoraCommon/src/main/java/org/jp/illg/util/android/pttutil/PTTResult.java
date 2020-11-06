package org.jp.illg.util.android.pttutil;

import org.jp.illg.util.ambe.dv3k.packet.control.PRODID;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class PTTResult {
	
	@Getter
	private boolean detected;
	
	@Getter
	private PTTState state;
	
	@Getter
	@Setter(AccessLevel.PACKAGE)
	private PTTType type;
	
	public PTTResult(
			final boolean detected,
			final PTTState state
	) {
		this(null, detected, state);
	}
	
	public PTTResult(
			final PTTType type,
			final boolean detected,
			final PTTState state
	){
		super();
		
		this.type = type;
		this.detected = detected;
		this.state = state;
	}
}
