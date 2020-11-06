package org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model;

import java.util.Date;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Data;
import lombok.NonNull;

@Data
public class Record {

	private final Lock locker;

	private Date timestamp;

	private String key;

	private String value;

	public Record(
		@NonNull final Date timestamp,
		@NonNull final String key, @NonNull final String value
	) {
		super();

		locker = new ReentrantLock();

		this.timestamp = timestamp;
		this.key = key;
		this.value = value;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuffer sb = new StringBuffer();

		sb.append("Key:");
		sb.append(getKey());
		sb.append("/");
		sb.append("Value:");
		sb.append(getValue());
		sb.append("/");
		sb.append("Timestamp:");
		sb.append(getTimestamp());

		return sb.toString();
	}
}
