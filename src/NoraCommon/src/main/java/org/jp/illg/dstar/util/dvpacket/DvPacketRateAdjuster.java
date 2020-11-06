package org.jp.illg.dstar.util.dvpacket;

import java.util.Deque;
import java.util.Iterator;
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
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DvPacketRateAdjuster<T extends DvPacketRateAdjusterObject> {

	private final String logHeader;

	public interface DvPacketSortFunction<T>{
		public List<T> sort(List<T> packets);
	}

	/**
	 * 初回に転送するパケット数
	 */
	@Getter
	private int initialTransportPackets;
	private static final int initialTransportPacketsDefault = 20;	// 20 = 400ms

	@Getter
	@Setter
	private boolean autoStart;
	private static final boolean autoStartDefault = true;

	@Getter
	@Setter
	private boolean autoReset;
	private static final boolean autoResetDefault = true;


	private final Lock locker;

	private final Deque<T> src;
	private final Deque<T> dst;

	private final PerformanceTimer timer;

	private int packetCount;
	private int transportPacketCount;
	private int srcPacketCount;
	private int headerCount;

	public DvPacketRateAdjuster() {
		super();

		logHeader = this.getClass().getSimpleName() + " : ";

		locker = new ReentrantLock();

		src = new LinkedList<>();
		dst = new LinkedList<>();

		this.initialTransportPackets = initialTransportPacketsDefault;

		timer = new PerformanceTimer();

		setAutoStart(autoStartDefault);
		setAutoReset(autoResetDefault);

		reset();
	}

	public DvPacketRateAdjuster(int initialTransportPackets) {
		this();

		if(initialTransportPackets > 0)
			this.initialTransportPackets = initialTransportPackets;
		else
			this.initialTransportPackets = initialTransportPacketsDefault;

	}

	public DvPacketRateAdjuster(int initialTransportPackets, boolean autoStart) {
		this(initialTransportPackets);

		setAutoStart(autoStart);
	}

	public void reset(boolean memoryReset) {
		locker.lock();
		try {
			if(memoryReset) {
				src.clear();
				dst.clear();
			}

			packetCount = 0;
			transportPacketCount = 0;
			srcPacketCount = 0;
			headerCount = 0;

			timer.reset();

		}finally {
			locker.unlock();
		}
	}

	public void reset() {reset(true);}

	public boolean debugSwapCacheDvPacket() {
		locker.lock();
		try {
			if(src.size() >= 2) {
				T p1 = src.poll();
				T p2 = src.poll();

				src.addFirst(p1);
				src.addFirst(p2);

				return true;
			}else {return false;}
		}finally {
			locker.unlock();
		}
	}

	public boolean addSourceDvPacket(Queue<T> packets) {
		if(packets == null || packets.size() <= 0)
			return false;

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!addSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public boolean addSourceDvPacket(T packets[]) {
		if(packets == null || packets.length <= 0)
			return false;

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!addSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public boolean addSourceDvPacket(T packet) {
		if(packet == null)
			return false;

		return addSourceDvPacketInt(packet);
	}

	public boolean pushBackSourceDvPacket(T packet) {
		if(packet == null){return false;}


		return pushBackSourceDvPacketInt(packet);
	}

	public boolean pushBackSourceDvPacket(Queue<T> packets) {
		if(packets == null){return false;}

		boolean result = true;

		for(T packet : packets) {
			if(packet == null) {continue;}
			if(!pushBackSourceDvPacketInt(packet)) {result = false;}
		}

		return result;
	}

	public void start() {
		if(timer.isRunning()) {return;}

		timer.start();
	}

	public boolean isStart() {
		return timer.isRunning();
	}

	/**
	 * 読み込めるパケットが存在するかチェックする
	 * @return 読み込めるパケットが存在する場合にはtrue
	 */
	public boolean hasReadableDvPacket() {
		if(!isStart()) {
			if(isAutoStart())
				start();
			else
				return false;
		}

		processTransportPackets();

		locker.lock();
		try {
			return !dst.isEmpty();
		}finally {
			locker.unlock();
		}
	}

	public Optional<T> readDvPacket() {
		if(!isStart()) {
			if(isAutoStart())
				start();
			else
				return Optional.empty();
		}

		processTransportPackets();

		locker.lock();
		try {
			if(!dst.isEmpty()) {
				T packet = dst.poll();
				if(packet != null) {
					if(packet.getPacketType() == PacketType.Header && isAutoReset()) {
						reset(false);
						start();

						log.debug(logHeader + "Start of voice transmit.");
					}
					else if(packet.getPacketType() == PacketType.Voice) {
						if(transportPacketCount < Integer.MAX_VALUE) {
							transportPacketCount++;
						}else {
							reset();
						}
						if(packet.isEndVoicePacket())
							log.debug(logHeader + "End of voice transmit.");
					}
					return Optional.of(packet);
				}
				else
					return Optional.empty();
			}else
				return Optional.empty();
		}finally {
			locker.unlock();
		}
	}

	public boolean isTransportCompleted() {
		locker.lock();
		try {
			boolean completed =
					(transportPacketCount >= srcPacketCount) &&
					src.isEmpty() && dst.isEmpty() &&
					timer.isRunning();

			return completed;
		}finally {
			locker.unlock();
		}
	}

	/**
	 * 不足しているパケット数を取得する
	 * @return 不足しているパケット数
	 */
	public int getLackPackets() {
		locker.lock();
		try {
			int srcPackets = src.size();
			int transportRequestPackets = calcTransportPackets();

			if(srcPackets >= transportRequestPackets)
				return 0;
			else
				return transportRequestPackets - srcPackets;
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
			return src.size() + dst.size();
		}finally {
			locker.unlock();
		}
	}

	public boolean sortCache(DvPacketSortFunction<T> func) {
		if(func == null) {return false;}

		locker.lock();
		try {
			int srcSize = src.size();
			int dstSize = dst.size();
			int cacheSize = srcSize + dstSize;

			if(cacheSize < 2) {return true;}

//			List<T> cache = new LinkedList<>();
//			cache.addAll(dst);
//			cache.addAll(src);

			List<T> sortedList = func.sort(getCache());

			assert sortedList.size() != cacheSize;

			int count = 0;

			dst.clear();
			src.clear();
			for(Iterator<T> it = sortedList.iterator(); it.hasNext();){
				T data = it.next();
				it.remove();

				if(count < dstSize)
					dst.add(data);
				else
					src.add(data);

				count++;
			}

			return true;
		}finally {
			locker.unlock();
		}
	}

	public List<T> getCache(){
		locker.lock();
		try {
			List<T> cache = new LinkedList<>();
			cache.addAll(dst);
			cache.addAll(src);

			return cache;
		}finally {
			locker.unlock();
		}
	}

	/**
	 * src->dstへパケットを転送する
	 */
	private void processTransportPackets() {
		int transportPackets = calcTransportPackets();

		locker.lock();
		try {
			for(int c = 0 ; c < transportPackets && !src.isEmpty(); c++) {
				T packet = src.poll();

				dst.add(packet);

				if(packet.getPacketType() == PacketType.Header) {
					if(
						headerCount < Integer.MAX_VALUE
					) {
						headerCount++;
					}
					else {
						reset();
						break;
					}
				}

				if(packetCount < Integer.MAX_VALUE) {
					packetCount++;
				}
				else {
					reset();
					break;
				}
			}
		}finally {
			locker.unlock();
		}
	}

	/**
	 * 何パケット転送するべきか計算する
	 * @return
	 */
	private int calcTransportPackets() {
		int result = 0;

		locker.lock();
		try {
			if(packetCount < initialTransportPackets) {
				//初回転送が終わっていない
				result = initialTransportPackets - packetCount;
			}
			else {
				long processTimeNanos = timer.getTimeFromTimerStart(TimeUnit.NANOSECONDS);
				int correctPackets = 0;
				if(processTimeNanos > 0) {
					correctPackets =
						(int) (
								processTimeNanos /
								TimeUnit.NANOSECONDS.convert(DSTARDefines.DvFrameIntervalTimeMillis, TimeUnit.MILLISECONDS)
						) + 1;
				}

				int transportedPacketCount = packetCount - initialTransportPackets - headerCount;
//				int transportedPacketCount = packetCount - initialTransportPackets;
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
			//ヘッダであればバイパスする
//			if(packet.getPacketType() == PacketType.Header) {
//				if(!dst.add(packet)) {return false;}

//				log.debug(logHeader + "Start of transport packet.");
//			}
//			else {

			if(
				packet.getPacketType() == PacketType.Header
			) {
				if(isAutoStart()) {
					if(src.isEmpty()) {reset(false);}
//					if(isAutoStart()) {start();}

					if(getCachePacketSize() != 0)
						log.trace(logHeader + "Cache memory has remain " + getCachePacketSize() + "packets.");
				}
			}

			if(!src.add(packet)) {return false;}

			if(srcPacketCount < Integer.MAX_VALUE)
				srcPacketCount++;
			else {
				reset();
				return false;
			}
//			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	private boolean pushBackSourceDvPacketInt(T packet) {
		assert packet != null;

		locker.lock();
		try {
			dst.addFirst(packet);
			src.addFirst(dst.pollLast());
		}finally {
			locker.unlock();
		}

		return true;
	}
}
