package org.jp.illg.util;

import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class Timer implements Cloneable{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long timestampMilis;

	private long timeSnapshot;

	@Getter
	private long timeoutMillis;

	@Getter
	@Setter
	private boolean autoUpdate;
	private static final boolean autoUodateDefault = true;


	public Timer() {
		super();

		setTimeoutMillis(0);

		timeSnapshot = 0;

		setAutoUpdate(autoUodateDefault);
	}

	public Timer(long timeoutMillis) {
		this();

		if(timeoutMillis > 0)
			setTimeoutMillis(timeoutMillis);
	}

	public Timer(long timeout, TimeUnit timeUnit) {
		this();

		if(timeout > 0 && timeUnit != null)
			setTimeoutTime(timeout, timeUnit);
	}

	public Timer(boolean autoUpdate) {
		this();

		setAutoUpdate(autoUpdate);
	}

	public Timer(long timeoutMillis, boolean autoUpdate) {
		this(timeoutMillis);

		setAutoUpdate(autoUpdate);
	}

	@Override
	public Timer clone() {
		Timer copy = null;
		try {
			copy = (Timer)super.clone();

			copy.timestampMilis = this.timestampMilis;
			copy.timeoutMillis = this.timeoutMillis;
			copy.autoUpdate = this.autoUpdate;
			copy.timeSnapshot = this.timeSnapshot;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	public void updateTimestamp() {
		setTimestampMilis(SystemUtil.getCurrentTimeMillis());

		timeSnapshot = SystemUtil.getNanoTimeCounterValue();
	}

	public boolean isTimeout() {
		return isTimeout(getTimeoutMillis(), TimeUnit.MILLISECONDS);
	}

	public boolean isTimeout(final long duration, @NonNull final TimeUnit timeUnit) {
		return SystemUtil.getNanoTimeCounterValue() > (timeSnapshot + timeUnit.toNanos(duration));
	}

	public boolean setTimeoutTime(long time, TimeUnit timeUnit) {
		if(time < 0 || timeUnit == null) {return false;}

		setTimeoutMillis(timeUnit.toMillis(time));

		return true;
	}

	public void setTimeoutMillis(long timeoutMillis) {
		this.timeoutMillis = timeoutMillis;

		if(isAutoUpdate()) {updateTimestamp();}
	}

	public long getTimeFromUpdate(@NonNull final TimeUnit timeUnit) {
		return timeUnit.convert(SystemUtil.getNanoTimeCounterValue() - timeSnapshot, TimeUnit.NANOSECONDS);
	}

}
