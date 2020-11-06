package org.jp.illg.dstar.repeater.homeblew.protocol;

import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.repeater.homeblew.model.HRPPacket;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HomebrewRepeaterProtocolFrameEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long activityTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private HRPPacket headerPacket;

	private HomebrewRepeaterProtocolFrameEntry() {
		super();
	}

	public HomebrewRepeaterProtocolFrameEntry(int frameID, HRPPacket header) {
		this();

		setCreatedTimestamp(System.currentTimeMillis());
		updateActivityTimestamp();
		setHeaderPacket(header);
	}

	public void updateActivityTimestamp() {
		setActivityTimestamp(System.currentTimeMillis());
	}

	public boolean isTimeoutActivityTimestamp(long timeoutMillis) {
		return System.currentTimeMillis() > (getActivityTimestamp() + TimeUnit.MILLISECONDS.toMillis(timeoutMillis));
	}

}
