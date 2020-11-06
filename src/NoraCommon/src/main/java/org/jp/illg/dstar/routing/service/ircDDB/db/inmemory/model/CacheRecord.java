package org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model;

import java.util.Date;

import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;

public class CacheRecord extends Record {

	@Getter
	private final Timer inactivityTimer;

	public CacheRecord(
		@NonNull Date timestamp,
		@NonNull String key, @NonNull String value
	) {
		super(timestamp, key, value);

		inactivityTimer = new Timer();
		inactivityTimer.updateTimestamp();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuffer sb = new StringBuffer();

		sb.append("InactivityTimer:");
		sb.append(new Date(inactivityTimer.getTimestampMilis()));
		sb.append("/");
		sb.append(super.toString(0));

		return sb.toString();
	}
}
