package org.jp.illg.util.thread;

import org.jp.illg.util.ArrayUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RunnableTask implements Runnable {

	private final ThreadUncaughtExceptionListener exceptionListener;
	private final StackTraceElement[] stackTrace;

	public RunnableTask(
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		super();

		this.exceptionListener = exceptionListener;

		final StackTraceElement[] ste = Thread.currentThread().getStackTrace();
		if(ste.length > 2) {
			stackTrace = new StackTraceElement[ste.length - 2];
			for(int i = 2; i < ste.length; i++) {stackTrace[i - 2] = ste[i];}
		}
		else {
			stackTrace = ste;
		}
	}

	public RunnableTask() {
		this(null);
	}

	@Override
	public final void run() {
		try {
			task();
		}catch(Exception ex) {
			final StackTraceElement[] ste = ex.getStackTrace();
			ex.setStackTrace(
				ste != null ? ArrayUtil.concat(ste, stackTrace) : stackTrace
			);

			if(exceptionListener != null)
				exceptionListener.threadUncaughtExceptionEvent(ex, Thread.currentThread());

			if(log.isErrorEnabled())
				log.error("Exception occurred while executing task", ex);
		}
	}

	public abstract void task();
}
