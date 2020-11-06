package org.jp.illg.util.logback.appender;

public interface NotifyAppenderListener {
	public void notifyLog(String msg);
	public void notifyLogEvent(NotifyLogEvent event);
}
