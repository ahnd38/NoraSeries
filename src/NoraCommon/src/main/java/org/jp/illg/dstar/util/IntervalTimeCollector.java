package org.jp.illg.dstar.util;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.PerformanceTimer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IntervalTimeCollector {

	private static final int averageTableSize = 100;

	private final Lock locker;

	private final PerformanceTimer timer;

	private long minTimeNanos, maxTimeNanos;

	private long dataCount;
	private final long[] averageTable;

	public IntervalTimeCollector() {
		super();

		locker = new ReentrantLock();

		timer = new PerformanceTimer();

		averageTable = new long[averageTableSize];

		minTimeNanos = 0;
		maxTimeNanos = 0;
		dataCount = 0;
		Arrays.fill(averageTable, 0L);
	}

	public long getMaxTime(@NonNull final TimeUnit timeunit) {
		return timeunit.convert(maxTimeNanos, TimeUnit.NANOSECONDS);
	}

	public long getMinTime(@NonNull final TimeUnit timeunit) {
		return timeunit.convert(minTimeNanos, TimeUnit.NANOSECONDS);
	}

	public long getAverageTime(@NonNull final TimeUnit timeunit) {
		locker.lock();
		try {
			final int maxIndex = (int)(dataCount >= averageTableSize ? averageTableSize : dataCount);
			long sumNanos = 0L;
			for(int i = 0; i < maxIndex; i++) {sumNanos += averageTable[i];}
			sumNanos /= maxIndex;

			return timeunit.convert(sumNanos, TimeUnit.NANOSECONDS);
		}finally {
			locker.unlock();
		}
	}

	public long getDataCount() {
		return dataCount;
	}

	public void reset() {
		locker.lock();
		try {
			clearTime();

			timer.stop();
			timer.reset();
		}finally {
			locker.unlock();
		}
	}

	public void tickHeader() {
		locker.lock();
		try {
			reset();
		}finally {
			locker.unlock();
		}
	}

	public void tickData(final boolean isEnd) {
		locker.lock();
		try {
			if(!timer.isRunning()) {
				timer.reset();
				timer.start();

				return;
			}

			timer.stop();
			final long timeNanos = timer.getTimeFromTimerStart(TimeUnit.NANOSECONDS);
			timer.reset();
			if(!isEnd) {timer.start();}


			if(minTimeNanos > timeNanos || dataCount <= 0) {minTimeNanos = timeNanos;}
			if(maxTimeNanos < timeNanos || dataCount <= 0) {maxTimeNanos = timeNanos;}

			if(dataCount < Long.MAX_VALUE) {dataCount++;}

			averageTable[(int)(dataCount > 0 ? dataCount % averageTableSize : 0)] = timeNanos;

			if(log.isTraceEnabled()) {
				log.trace(
					"Tick No." + dataCount + " ... Interval = " +
					TimeUnit.MILLISECONDS.convert(timeNanos, TimeUnit.NANOSECONDS) + "(ms)"
				);
			}

		}finally {
			locker.unlock();
		}
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("DataCount = ");
		sb.append(getDataCount());
		sb.append(",");

		sb.append("AverageTime(ms) = ");
		sb.append(String.format("%.3f", (float)getAverageTime(TimeUnit.MICROSECONDS) / 1000F));
		sb.append(" , ");

		sb.append("MaxTime(ms) = ");
		sb.append(String.format("%.3f", (float)getMaxTime(TimeUnit.MICROSECONDS) / 1000F));
		sb.append(" , ");

		sb.append("MinTime(ms) = ");
		sb.append(String.format("%.3f", (float)getMinTime(TimeUnit.MICROSECONDS) / 1000F));

		return sb.toString();
	}

	private void clearTime() {
		locker.lock();
		try {
			minTimeNanos = 0;
			maxTimeNanos = 0;
			Arrays.fill(averageTable, 0L);

			dataCount = 0;
		}finally {
			locker.unlock();
		}
	}
}
