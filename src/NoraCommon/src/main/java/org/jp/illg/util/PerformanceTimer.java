package org.jp.illg.util;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.NonNull;

public class PerformanceTimer {

	@Getter
	private long startTime;

	private long startTimeNanos, stopTimeNanos;

	private static Method elapsedRealtimeNanos;


	static {
		elapsedRealtimeNanos = null;
		if(SystemUtil.IS_Android) {
			try {
				Class<?> systemClockClass = Class.forName("android.os.SystemClock");
				elapsedRealtimeNanos = systemClockClass.getMethod("elapsedRealtimeNanos");
			}catch(ClassNotFoundException|NoSuchMethodException ex){
				throw new RuntimeException();
			}
		}
	}

	public PerformanceTimer() {
		super();

		reset();
	}

	public boolean isRunning() {
		return startTime > 0 && stopTimeNanos == 0;
	}

	public void start() {
		startTimeNanos = getNanotime();
		stopTimeNanos = 0;
		startTime = System.currentTimeMillis();
	}

	public void stop() {
		if(isRunning())
			stopTimeNanos = getNanotime();
	}

	public void reset() {
		startTimeNanos = 0;
		stopTimeNanos = 0;
		startTime = 0;
	}

	public boolean isTimeout(long time, @NonNull TimeUnit timeUnit) {
		if(time < 0)
			throw new IllegalArgumentException();

		return getTimeFromTimerStart(TimeUnit.NANOSECONDS) > timeUnit.toNanos(time);
	}

	public long getTimeFromTimerStart(@NonNull TimeUnit timeUnit) {
		if(startTime <= 0)
			return 0;

		final long now = getNanotime();

		return timeUnit.convert(now - startTimeNanos - (stopTimeNanos != 0 ? now - stopTimeNanos : 0), TimeUnit.NANOSECONDS);
	}

	private long getNanotime(){
		try {
			if (elapsedRealtimeNanos != null)
				return (Long)elapsedRealtimeNanos.invoke(null);
		}catch(ReflectiveOperationException ex) {
			throw new RuntimeException();
		}

		return System.nanoTime();
	}

}
