package org.jp.illg.dstar.service.web.model;

import org.jp.illg.util.logback.appender.NotifyLogEvent;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LogInfo {

	private String message;

	private long timestamp;

	private String threadName;

	private String level;

	private String formattedMessage;

	private String layouttedMessage;

	public LogInfo(final String message) {
		this.message = message;
	}

	public LogInfo(final NotifyLogEvent event) {
		this.message = event.getMessage();
		this.timestamp = event.getTimestamp() != 0 ? event.getTimestamp() / 1000 : 0;
		this.threadName = event.getThreadName();
		this.level = event.getLevel().toString();
		this.formattedMessage = event.getFormattedMessage();
		this.layouttedMessage = event.getLayouttedMessage();
	}

}
