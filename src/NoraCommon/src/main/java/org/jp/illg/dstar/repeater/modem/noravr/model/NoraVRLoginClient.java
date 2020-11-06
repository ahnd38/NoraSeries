package org.jp.illg.dstar.repeater.modem.noravr.model;

import lombok.Data;
import lombok.NonNull;

@Data
public class NoraVRLoginClient {
	
	private long loginId;
	
	private String loginCallsign;
	
	private long loginTime;
	
	private String codecType;
	
	public NoraVRLoginClient(
		final long loginId,
		@NonNull final String loginCallsign,
		final long loginTime,
		@NonNull final String codecType
	) {
		super();
		
		this.loginId = loginId;
		this.loginCallsign = loginCallsign;
		this.loginTime = loginTime;
		this.codecType = codecType;
	}
	
}
