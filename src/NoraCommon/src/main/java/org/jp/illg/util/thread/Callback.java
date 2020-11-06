package org.jp.illg.util.thread;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class Callback<T> implements Runnable {

	@Getter(AccessLevel.PRIVATE)
	@Setter
	private T attachData;

	private final ThreadUncaughtExceptionListener exceptionListener;


	public Callback() {
		this(null);
	}

	public Callback(final ThreadUncaughtExceptionListener exceptionListener) {
		super();

		this.exceptionListener = exceptionListener;
	}

	@Override
	public void run() {
		try {
			call(getAttachData());
		}catch(Exception ex) {
			if(log.isErrorEnabled()) {
				log.error("Exception occurred while executing task", ex);
			}
			if(exceptionListener != null) {
				exceptionListener.threadUncaughtExceptionEvent(ex, null);
			}
		}
	}

	public abstract void call(T attachData);
}
