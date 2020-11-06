package org.jp.illg.dstar.util.dvpacket2;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster.RateAdjusterCacheMemorySortFunction;
import org.jp.illg.util.PerformanceTimer;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CacheTransmitter<T extends TransmitterPacket> {

	private enum CacheState {
		Accumulate,
		Output,
		;
	}

	private enum Direction {
		Input,
		Output,
		;
	}

	@Getter
	private int cacheSize;

	private final String logHeader;

	private final Lock locker;

	private CacheState state;
	private final RateAdjuster<T> rateAdjuster;

	private final PerformanceTimer timer;

	private long timeoutMillis;

	public CacheTransmitter(final int cacheSize, final int initialTransportPacketSize) {
		super();

		this.cacheSize = cacheSize;

		locker = new ReentrantLock();

		logHeader = CacheTransmitter.class.getSimpleName() + " : ";

		state = CacheState.Accumulate;
		rateAdjuster = new RateAdjuster<>(initialTransportPacketSize);

		timer = new PerformanceTimer();

		calcTimeoutMillis();
	}

	public CacheTransmitter(final int cacheSize) {
		this(cacheSize, 1);
	}

	public CacheTransmitter() {
		this(10);
	}

	private void calcTimeoutMillis() {
		timeoutMillis = getCacheSize() * DSTARDefines.DvFrameIntervalTimeMillis * 2;
	}

	public void reset() {
		locker.lock();
		try {
			state = CacheState.Accumulate;
			rateAdjuster.reset();
		}finally {
			locker.unlock();
		}
	}

	public int getCachePacketSize() {
		locker.lock();
		try {
			return rateAdjuster.getCachePacketSize();
		}finally {
			locker.unlock();
		}
	}

	public boolean isCachePacketEmpty() {
		locker.lock();
		try {
			return rateAdjuster.isCachePacketEmpty();
		}finally {
			locker.unlock();
		}
	}

	public List<T> getCacheMemory() {
		locker.lock();
		try {
			return rateAdjuster.getCache();
		}finally {
			locker.unlock();
		}
	}

	public boolean sortCacheMemory(@NonNull final RateAdjusterCacheMemorySortFunction<T> func) {
		locker.lock();
		try {
			return rateAdjuster.sortCache(func);
		}finally {
			locker.unlock();
		}
	}

	public boolean inputWrite(@NonNull T packet) {
		locker.lock();
		try {
			if(rateAdjuster.writePacket(packet)) {
				process(packet, Direction.Input);

				return true;
			}
			else {
				return false;
			}
		}finally {
			locker.unlock();
		}
	}

	public boolean inputWritePushBack(@NonNull T packet) {
		locker.lock();
		try {
			return rateAdjuster.writePacketPushBack(packet);
		}finally {
			locker.unlock();
		}
	}

	public boolean hasOutputRead() {
		locker.lock();
		try {
			process(null, Direction.Input);

			return state == CacheState.Output && rateAdjuster.hasReadableDvPacket();
		}finally {
			locker.unlock();
		}
	}

	public boolean isUnderflow() {
		locker.lock();
		try {
			return state == CacheState.Output && rateAdjuster.getCachePacketSize() <= 0;
		}finally {
			locker.unlock();
		}
	}

	public Optional<T> outputRead() {
		locker.lock();
		try {
			Optional<T> opPacket = Optional.empty();

			if(state == CacheState.Output) {
				opPacket = rateAdjuster.readDvPacket();

				process(opPacket.isPresent() ? opPacket.get() : null, Direction.Output);
			}

			return opPacket;
		}finally {
			locker.unlock();
		}
	}

	private void process(final T packet, final Direction dir) {

		locker.lock();
		try {
			boolean reProcess;
			do {
				reProcess = false;
				CacheState nextState = null;
				boolean isTimeout = false;

				switch(state) {
				case Accumulate:
					if(
						(dir == Direction.Input && rateAdjuster.getCachePacketSize() > getCacheSize()) ||
						(isTimeout = timer.isRunning() && timer.isTimeout(timeoutMillis, TimeUnit.MILLISECONDS))
					) {
						nextState = CacheState.Output;

						timer.reset();
						timer.start();

						rateAdjuster.start();

						reProcess = true;

						if(log.isTraceEnabled())
							log.trace(logHeader + "Start transmit" + (isTimeout ? "(TIMEOUT)":""));
					}
					else if(
						dir == Direction.Input &&
						packet != null && packet.getFrameSequenceType() == FrameSequenceType.Start
					) {
						timer.start();
					}
					break;

				case Output:
					/*
					if(
						rateAdjuster.getCachePacketSize() < getCacheSize() &&
						packet != null &&
						dir == Direction.Output && packet.getFrameSequenceType() == FrameSequenceType.Start
					) {
						nextState = CacheState.Accumulate;

						reProcess = true;
					}
					*/
					if(
						packet != null &&
						dir == Direction.Output && packet.getFrameSequenceType() == FrameSequenceType.End
					) {
						nextState = CacheState.Accumulate;

						reProcess = true;

						timer.reset();

						if(log.isTraceEnabled())
							log.trace(logHeader + "End transmit");
					}
					else if(
						timer.isTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
//						(
//							dir == Direction.Output &&
//							packet != null && packet.getFrameSequenceType() == FrameSequenceType.Start
//						)
					) {
						nextState = CacheState.Accumulate;

						reProcess = true;

						timer.reset();

						if(log.isTraceEnabled())
							log.trace(logHeader + "Transmit timeout");
					}
					else if(packet != null) {
						timer.reset();
						timer.start();
					}

					break;

				default:
					break;
				}

				if(nextState != null) {
					if(log.isTraceEnabled())
						log.trace(logHeader + "State changed " + state + " -> " + nextState);

					state = nextState;
				}
			}while(reProcess);
		}finally {
			locker.unlock();
		}
	}

}
