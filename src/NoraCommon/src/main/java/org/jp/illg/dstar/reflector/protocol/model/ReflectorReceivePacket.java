package org.jp.illg.dstar.reflector.protocol.model;

import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.DSTARPacket;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ReflectorReceivePacket {

	private static final int timeoutSeconds = 10;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String repeaterCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARPacket packet;


	public ReflectorReceivePacket(String repeaterCallsign, DSTARPacket packet) {
		setCreatedTimestamp(System.currentTimeMillis());
		setRepeaterCallsign(repeaterCallsign);
		setPacket(packet);
	}

	public boolean isTimeout() {
		return isTimeout(timeoutSeconds);
	}

	public boolean isTimeout(long timeoutSeconds) {
		if(timeoutSeconds < 0) {timeoutSeconds = 0;}

		return System.currentTimeMillis() > (getCreatedTimestamp() + TimeUnit.SECONDS.toMillis(timeoutSeconds));
	}

}
