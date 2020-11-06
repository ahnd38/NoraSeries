package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class IRCDDBAnnounceTask {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String callsign;

	@Getter
	@Setter
	private String message;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long announceTime;

	public IRCDDBAnnounceTask(String callsign, String message, long announceTimeAfterSeconds) {
		super();

		setCreatedTime(System.currentTimeMillis());
		setCallsign(callsign);
		setMessage(message);
		setAnnounceTime(
				System.currentTimeMillis() +
				(announceTimeAfterSeconds< 0 ? 0 : TimeUnit.MILLISECONDS.convert(announceTimeAfterSeconds, TimeUnit.SECONDS))
		);
	}

	public boolean isAnnounceTime() {
		return System.currentTimeMillis() >= getAnnounceTime();
	}
}
