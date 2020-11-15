package org.jp.illg.util.thread;

public interface ThreadUncaughtExceptionListener {
	/**
	 * Thread uncaught exception event
	 *
	 * called when exception is not catched by thread.
	 *
	 * @param ex exception
	 * @param thread for occurred exception
	 */
	void threadUncaughtExceptionEvent(Exception ex, Thread thread);

	/**
	 * Thread fatal application error event
	 *
	 * called when application fatal error by thread.
	 *
	 * @param message error message by application
	 * @param ex exception when available
	 * @param thread thread for occurred thread
	 */
	void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread);
}
