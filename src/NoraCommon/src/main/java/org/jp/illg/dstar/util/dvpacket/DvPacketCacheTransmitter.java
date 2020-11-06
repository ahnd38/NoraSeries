package org.jp.illg.dstar.util.dvpacket;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket.DvPacketRateAdjuster.DvPacketSortFunction;
import org.jp.illg.util.Timer;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DvPacketCacheTransmitter<T extends DvPacketCacheTransmitterFunc> {

	public class DvPacketCacheTransmitterPacket
	implements DvPacketCacheTransmitterFunc, DvPacketRateAdjusterObject{

		@Getter
		@Setter
		private T data;

		@Getter
		@Setter
		private PacketType packetType;

		public DvPacketCacheTransmitterPacket(final PacketType packetType, final T data) {
			super();

			setPacketType(packetType);
			setData(data);
		}

		@Override
		public boolean isEndVoicePacket() {
			if(getData() != null)
				return getData().getPacket().isEndVoicePacket();
			else
				return false;
		}

		@Override
		public DVPacket getPacket() {
			if(getData() != null)
				return getData().getPacket();
			else
				return null;
		}

	}

	private enum State{
		Accumulate,
		Output,
		Terminate,
		;
	}
	private State state;
	private final Lock stateLock;

	private final DvPacketRateAdjuster<DvPacketCacheTransmitterPacket> rateAdjustor;
	private final Lock cacheLock;

	@Getter
	@Setter
	private int cacheSize;
	private final int cacheSizeDefault = 5;

	@Setter
	@Getter
	private boolean debugSwapPacket;
	private static final boolean debugSwapPacketDefault = false;

	@Getter
	private long debugSwapPacketPeriodMillis;
	private static final long debugSwapPacketPeriodMillisDefault = 1000L;

	@Getter
	private boolean autoReset;
	private static final boolean autoResetDefault = true;

	private final Timer debugSwapPacketTimer;


	public DvPacketCacheTransmitter() {
		super();

		debugSwapPacketTimer = new Timer(getDebugSwapPacketPeriodMillis());

		rateAdjustor = new DvPacketRateAdjuster<DvPacketCacheTransmitterPacket>(1);
		cacheLock = new ReentrantLock();

		state = State.Accumulate;
		stateLock = new ReentrantLock();

		setCacheSize(cacheSizeDefault);

		setDebugSwapPacket(debugSwapPacketDefault);
		setDebugSwapPacketPeriodMillis(debugSwapPacketPeriodMillisDefault);

		setAutoReset(autoResetDefault);
	}

	public DvPacketCacheTransmitter(int cacheSize) {
		this();

		setCacheSize(cacheSize);
	}

	public DvPacketCacheTransmitter(int cacheSize, boolean autoReset) {
		this(cacheSize);

		setAutoReset(autoReset);
	}

	public void setDebugSwapPacketPeriodMillis(long debugSwapPacketPeriodMillis) {
		this.debugSwapPacketPeriodMillis = debugSwapPacketPeriodMillis;

		debugSwapPacketTimer.setTimeoutTime(debugSwapPacketPeriodMillis, TimeUnit.MILLISECONDS);
	}

	public void setAutoReset(boolean autoReset) {
		this.autoReset = autoReset;

		rateAdjustor.setAutoReset(autoReset);
	}

	public void reset() {
		stateLock.lock();
		try {
			cacheLock.lock();
			try {

				state = State.Accumulate;
				rateAdjustor.reset();

				debugSwapPacketTimer.updateTimestamp();

			}finally {
				cacheLock.unlock();
			}
		}finally {
			stateLock.unlock();
		}
	}

	public boolean inputWrite(final PacketType packetType, @NonNull final T packet) {
		stateLock.lock();
		try {
			cacheLock.lock();
			try {
				if(
					rateAdjustor.addSourceDvPacket(
						new DvPacketCacheTransmitterPacket(packetType, packet)
					)
				) {
					if(packetType == PacketType.Header && isAutoReset())
						state = State.Accumulate;

				}else {return false;}
			}finally {
				cacheLock.unlock();
			}
		}finally {
			stateLock.unlock();
		}

		process();

		return true;
	}

	public Optional<T> outputRead(){
		Optional<T> packet;

		cacheLock.lock();
		try {
			//スワップパケット有効時には指定間隔でパケットを入れ替える
			if(isDebugSwapPacket() && debugSwapPacketTimer.isTimeout()) {
				debugSwapPacketTimer.updateTimestamp();

				rateAdjustor.debugSwapCacheDvPacket();
			}

			Optional<DvPacketCacheTransmitterPacket> p = Optional.empty();
			if(state == State.Output) {p = rateAdjustor.readDvPacket();}
			if(p.isPresent())
				packet = Optional.of(p.get().getData());
			else
				packet = Optional.empty();
		}finally {
			cacheLock.unlock();
		}

		packet.ifPresent(new Consumer<T>() {
			@Override
			public void accept(T t) {
				process(t);
			}
		});

		return packet;
	}

	public boolean hasOutputRead() {
		cacheLock.lock();
		try {
			return rateAdjustor.hasReadableDvPacket() && state == State.Output;
		}finally {
			cacheLock.unlock();
		}
	}

	public boolean isUnderflow() {
		stateLock.lock();
		try {
			cacheLock.lock();
			try {
				return state == State.Output && rateAdjustor.getCachePacketSize() <= 0;
			}finally {
				cacheLock.unlock();
			}
		}finally {
			stateLock.unlock();
		}
	}

	public boolean sortCache(DvPacketSortFunction<DvPacketCacheTransmitterPacket> func) {
		if(func == null) {return false;}

		cacheLock.lock();
		try {
			return rateAdjustor.sortCache(func);
		}finally {
			cacheLock.unlock();
		}
	}

	public List<T> getCache(){
		cacheLock.lock();
		try {
			List<DvPacketCacheTransmitterPacket> cacheSrc = rateAdjustor.getCache();

			List<T> cacheDst = new LinkedList<>();
			for(DvPacketCacheTransmitterPacket packet : cacheSrc)
				cacheDst.add(packet.getData());

			return cacheDst;
		}finally {
			cacheLock.unlock();
		}
	}

	public boolean pushBackCache(final PacketType packetType, @NonNull final T packet) {
		assert packet != null;

		stateLock.lock();
		try {
			cacheLock.lock();
			try {
				if(state == State.Terminate) {state = State.Output;}

				return rateAdjustor.pushBackSourceDvPacket(new DvPacketCacheTransmitterPacket(packetType, packet));
			}finally {
				cacheLock.unlock();
			}
		}finally {
			stateLock.unlock();
		}
	}

	private void process() {
		process(null);
	}

	private void process(T packet) {
		stateLock.lock();
		try {
			cacheLock.lock();
			try {
				boolean reProcess;
				do {
					reProcess = false;

					switch(state) {
					case Accumulate:
						if(rateAdjustor.getCachePacketSize() >= getCacheSize()) {
							state = State.Output;
							reProcess = true;
						}
						break;

					case Output:
						if(
							packet != null &&
							packet.getPacketType() == PacketType.Voice &&
							packet.getPacket().isEndVoicePacket() &&
							rateAdjustor.getCachePacketSize() == 0
						) {
							state = State.Terminate;
							reProcess = true;
						}
						break;

					case Terminate:
						if(rateAdjustor.getCachePacketSize() >= 1) {
							state = State.Accumulate;
							reProcess = true;
						}
						break;

					default:
						break;
					}
				}while(reProcess);
			}finally {
				cacheLock.unlock();
			}
		}finally {
			stateLock.unlock();
		}
	}



}
