package org.jp.illg.util.logback.appender;

import org.slf4j.event.Level;

import lombok.Getter;

public class NotifyLogEvent {

	@Getter
	private long timestamp;

	@Getter
	private String threadName;

	@Getter
	private Level level;

	@Getter
	private String message;

	@Getter
	private String formattedMessage;

	@Getter
	private String layouttedMessage;

	@Getter
	private StackTraceElement[] callerData;

	public NotifyLogEvent(
		final long timestamp,
		final String threadName,
		final Level level,
		final String message,
		final String formattedMessage,
		final String layoutedMessage,
		final StackTraceElement[] callerData
	) {
		super();

		this.timestamp = timestamp;
		this.threadName = threadName;
		this.level = level;
		this.message = message;
		this.formattedMessage = formattedMessage;
		this.layouttedMessage = layoutedMessage;
		this.callerData = callerData;
	}

	public boolean hasCallerData() {
		return callerData != null;
	}
}
