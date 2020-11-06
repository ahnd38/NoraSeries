package org.jp.illg.dstar.util.dvpacket2;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.util.PerformanceTimer;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RateAdjuster<T extends TransmitterPacket> {

	private static final long maxTransportPacket = Long.MAX_VALUE;

	private final String logHeader;

	public interface RateAdjusterCacheMemorySortFunction<T>{
		public boolean sort(List<T> packets);
	}

	/**
	 * 初回に転送するパケット数
	 */
	@Getter
	private long initialTransportPackets;
	private static final long initialTransportPacketsDefault = 1;

	@Getter
	private boolean autoStart;
	private static final boolean autoStartDefault = true;


	private final Lock locker;

	private final Deque<T> cacheMemory;

	private final PerformanceTimer timer;

	private long outputPacketCount;
	private long outputHeaderCount;

	public RateAdjuster() {
		this(initialTransportPacketsDefault);
	}

	public RateAdjuster(final long initialTransportPackets, final boolean autoStart) {
		super();

		if(initialTransportPackets >= 1)
			this.initialTransportPackets = initialTransportPackets;
		else
			this.initialTransportPackets = initialTransportPacketsDefault;

		this.autoStart = autoStart;

		logHeader = RateAdjuster.class.getSimpleName() + " : ";

		locker = new ReentrantLock();

		cacheMemory = new LinkedList<>();

		timer = new PerformanceTimer();

		reset();
	}

	public RateAdjuster(final boolean autoStart) {
		this(initialTransportPacketsDefault, autoStart);
	}

	public RateAdjuster(final long initialTransportPackets) {
		this(initialTransportPackets, autoStartDefault);
	}

	public void reset(boolean memoryReset) {
		locker.lock();
		try {
			if(memoryReset)
				cacheMemory.clear();

			outputPacketCount = 0;
			outputHeaderCount = 0;

			timer.reset();

		}finally {
			locker.unlock();
		}
	}

	public void reset() {reset(true);}

	public boolean writePacket(Queue<T> packets) {
		if(packets == null || packets.size() <= 0)
			return false;

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!addSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public boolean writePacket(T packets[]) {
		if(packets == null || packets.length <= 0)
			return false;

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!addSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public boolean writePacket(T packet) {
		if(packet == null)
			return false;

		return addSourceDvPacketInt(packet);
	}

	public boolean writePacketPushBack(T packet) {
		if(packet == null){return false;}


		return pushBackSourceDvPacketInt(packet);
	}

	public boolean writePacketPushBack(Queue<T> packets) {
		if(packets == null){return false;}

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!pushBackSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public boolean isRunning() {
		return timer.isRunning();
	}

	public void start() {
		reset(false);
		timer.start();
	}

	/**
	 * 読み込めるパケットが存在するかチェックする
	 * @return 読み込めるパケットが存在する場合にはtrue
	 */
	public boolean hasReadableDvPacket() {
		locker.lock();
		try {
			return !cacheMemory.isEmpty() && calcTransportPackets() >= 1;
		}finally {
			locker.unlock();
		}
	}

	private T readCacheMemory(boolean isPeek) {
		T packet = null;
		while(!cacheMemory.isEmpty()) {
			if(isPeek) {
				if((packet = cacheMemory.peek()) != null) {break;}
			}
			else {
				if((packet = cacheMemory.poll()) != null) {break;}
			}
		}
		return packet;
	}

	public T peekDvPacket() {
		return readCacheMemory(true);
	}

	public Optional<T> readDvPacket() {
		locker.lock();
		try {
			if(!hasReadableDvPacket())
				return Optional.empty();

			if(!isAutoStart() && !timer.isRunning())
				return Optional.empty();

			T packet = readCacheMemory(false);
			if(packet == null)
				return Optional.empty();

			if(packet.getFrameSequenceType() == FrameSequenceType.Start) {
				reset(false);
				timer.start();

				if(log.isDebugEnabled())
					log.debug(logHeader + "Start of voice transmit.");
			}
			else if(packet.getFrameSequenceType() == FrameSequenceType.End) {
				reset(false);

				if(log.isDebugEnabled())
					log.debug(logHeader + "End of voice transmit.");
			}

			if(packet.getFrameSequenceType() == FrameSequenceType.None) {
				if(outputHeaderCount < maxTransportPacket) {
					if(packet.getPacket() != null && packet.getPacketType() == PacketType.Header) {
						outputHeaderCount++;
					}
				}
				else {
					reset(false);
					return Optional.of(packet);
				}

				if(outputPacketCount < maxTransportPacket) {
					outputPacketCount++;
				}
				else {
					reset(false);
					return Optional.of(packet);
				}
			}

			return Optional.of(packet);

		}finally {
			locker.unlock();
		}
	}


	/**
	 * このアジャスタに保存されているパケット数を取得する
	 * @return 保存されているパケット数
	 */
	public int getCachePacketSize() {
		locker.lock();
		try {
			return cacheMemory.size();
		}finally {
			locker.unlock();
		}
	}

	public boolean isCachePacketEmpty() {
		locker.lock();
		try {
			return cacheMemory.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	public boolean sortCache(@NonNull final RateAdjusterCacheMemorySortFunction<T> func) {
		locker.lock();
		try {
			final List<T> copyCacheMemory = new ArrayList<>(cacheMemory);
			final boolean sortResult = func.sort(copyCacheMemory);
			if(sortResult) {
				cacheMemory.clear();
				cacheMemory.addAll(copyCacheMemory);
			}

			return sortResult;
		}finally {
			locker.unlock();
		}
	}

	public List<T> getCache(){
		locker.lock();
		try {
			return new LinkedList<>(cacheMemory);
		}finally {
			locker.unlock();
		}
	}

	/**
	 * 何パケット転送するべきか計算する
	 * @return
	 */
	private long calcTransportPackets() {
		long result = 0;

		locker.lock();
		try {
			if(outputPacketCount < initialTransportPackets) {
				//初回転送が終わっていない
				result = initialTransportPackets - (outputPacketCount - outputHeaderCount);
			}
			else {
				long processTimeNanos = timer.getTimeFromTimerStart(TimeUnit.NANOSECONDS);
				int correctPackets = 0;
				if(processTimeNanos > 0) {
					correctPackets =
						(int) (
							processTimeNanos /
							TimeUnit.NANOSECONDS.convert(DSTARDefines.DvFrameIntervalTimeMillis, TimeUnit.MILLISECONDS)
						);
				}

				final long transportedPacketCount = outputPacketCount - initialTransportPackets - outputHeaderCount;

				if(correctPackets > transportedPacketCount)
					result = correctPackets - transportedPacketCount;
				else
					result = 0;
			}
		}finally {
			locker.unlock();
		}

		return result;
	}

	private boolean addSourceDvPacketInt(T packet) {
		if(packet == null) {return false;}

		locker.lock();
		try {
			return cacheMemory.add(packet);
		}finally {
			locker.unlock();
		}
	}

	private boolean pushBackSourceDvPacketInt(T packet) {
		assert packet != null;

		locker.lock();
		try {
			cacheMemory.addFirst(packet);

			if(outputPacketCount > 0) {outputPacketCount--;}

			if(
				packet != null && packet.getPacket() != null &&
				packet.getPacketType() == PacketType.Header &&
				outputHeaderCount > 0
			) {outputHeaderCount--;}

		}finally {
			locker.unlock();
		}

		return true;
	}

}
