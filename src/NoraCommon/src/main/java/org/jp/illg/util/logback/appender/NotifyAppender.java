package org.jp.illg.util.logback.appender;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.event.Level;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

public class NotifyAppender extends AppenderBase<ILoggingEvent> {

	private PatternLayoutEncoder encoder = null;

	private static final List<NotifyAppenderListener> listeners;

	static{
		listeners = new ArrayList<>();
	}

	public PatternLayoutEncoder getEncoder() {
		return this.encoder;
	}

	public void setEncoder(PatternLayoutEncoder encoder) {
		this.encoder = encoder;
	}

	public static boolean isListenerRegisterd(NotifyAppenderListener listener){
		if(listener == null){return false;}

		boolean found = false;
		synchronized (listeners){
			for(Iterator<NotifyAppenderListener> it = listeners.iterator(); it.hasNext();) {
				NotifyAppenderListener rlistener = it.next();
				if(rlistener == null) {
					it.remove();
				}else if(rlistener == listener) {
					found = true;
				}
			}
		}

		return found;
	}

	public static boolean addListener(NotifyAppenderListener listener){
		if(listener == null){return false;}

		synchronized (listeners){
			return listeners.add(listener);
		}
	}

	public static boolean removeListener(NotifyAppenderListener listener){
		boolean remove = false;
		synchronized (listeners){
			for(Iterator<NotifyAppenderListener> it = listeners.iterator(); it.hasNext();) {
				NotifyAppenderListener target = it.next();
				if(target == null) {
					it.remove();
				}else if(target == listener) {
					it.remove();
					remove = true;
				}
			}
		}

		return remove;
	}

	public static void removeListeners() {
		synchronized(listeners) {
			listeners.clear();
		}
	}

	@Override
	protected void append(ILoggingEvent event){

		final Level eventLevel = convertLevel(event.getLevel());
		if(eventLevel == null) {
			System.err.println("Logger event level is unknown.");
			return;
		}
		final NotifyLogEvent eventObj =
			new NotifyLogEvent(
				event.getTimeStamp(),
				event.getThreadName(),
				eventLevel,
				event.getMessage(),
				event.getFormattedMessage(),
				(
					encoder != null ?
					encoder.getLayout().doLayout(event) : event.getFormattedMessage()
				),
				event.getCallerData()
			);

		synchronized (listeners){
			for(Iterator<NotifyAppenderListener> it = listeners.iterator(); it.hasNext();) {
				NotifyAppenderListener listener = it.next();

				if(listener == null) {
					it.remove();
				}else {
					listener.notifyLog(eventObj.getLayouttedMessage());
					listener.notifyLogEvent(eventObj);
				}
			}
		}
	}

	private Level convertLevel(final ch.qos.logback.classic.Level level) {
		if(level == null)
			return null;
		else if(ch.qos.logback.classic.Level.ALL == level)
			return Level.TRACE;
		else {
			for(final Level l : Level.values()) {
				if(l.toString().equalsIgnoreCase(level.toString()))
					return l;
			}
		}

		return null;
	}
}
