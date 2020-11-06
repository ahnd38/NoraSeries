package org.jp.illg.dstar.util.dvpacket2;

import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster.RateAdjusterCacheMemorySortFunction;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepairCacheTransporter<T extends TransmitterPacket> {

	private static final int initialCacheSizeDefault = 5;

	private final String logHeader;

	@Getter
	private int currentSequence;

	@Getter
	private int insertedNullPacketCount;

	@Getter
	@Setter
	private int cacheLimit;
	private static final int cacheLimitDefault = 100;

	@Getter
	@Setter
	private boolean cacheLimitEnable;
	private static final boolean cacheLimitEnableDefault = false;

	@Getter
	@Setter
	private boolean repairEnable;
	private static final boolean repairEnableDefault = true;

	private final CacheTransmitter<T> cacheTransmitter;

	private final RateAdjusterCacheMemorySortFunction<T> packetSorter =
		new RateAdjusterCacheMemorySortFunction<T>() {

			@Override
			public boolean sort(List<T> packets) {
				if(isNeedSwapSequence(packets)) {
					String beforeSortSeqs = "";
					if(log.isDebugEnabled()) {beforeSortSeqs = showPacketSeq(packets);}

					boolean swapped = sortBySequence(packets);

					if(log.isDebugEnabled() && swapped) {
						log.debug(
							logHeader + "Corrected the sequence of packets.\n" +
							"    Before : " + beforeSortSeqs + "\n" +
							"    After  : " + showPacketSeq(packets)
						);
					}
				}

				return false;
			}
		};

	public RepairCacheTransporter() {
		this(initialCacheSizeDefault);
	}

	public RepairCacheTransporter(final int cacheSize) {
		this(cacheSize, 1);
	}

	public RepairCacheTransporter(final int cacheSize, final int initialTransportPacketSize) {
		super();

		logHeader = RepairCacheTransporter.class.getSimpleName() + " : ";

		cacheTransmitter = new CacheTransmitter<>(cacheSize, initialTransportPacketSize);

		currentSequence = 0x0;
		insertedNullPacketCount = 0;

		cacheLimit = cacheLimitDefault;
		cacheLimitEnable = cacheLimitEnableDefault;
		repairEnable = repairEnableDefault;
	}

	public synchronized void reset() {
		cacheTransmitter.reset();

		currentSequence = 0x0;

		insertedNullPacketCount = 0;
	}

	public synchronized Optional<T> readPacket(){

		if(isRepairEnable())
			cacheTransmitter.sortCacheMemory(packetSorter);

		Optional<T> packet = cacheTransmitter.outputRead();
		if(packet.isPresent()) {
			if(isRepairEnable() && !packet.get().getPacket().isLastFrame())
				return Optional.of(processNextPacket(packet.get()));
			else
				return Optional.of(packet.get());
		} else
			return Optional.empty();
	}

	public synchronized boolean hasReadablePacket(){
		return cacheTransmitter.hasOutputRead();
	}

	public synchronized boolean writePacket(T packet) {
		if(packet == null) {return false;}

		if(
			(isCacheLimitEnable() && hasWriteSpace()) ||
			!isCacheLimitEnable()
		){
			return cacheTransmitter.inputWrite(packet);
		}else{return false;}
	}

	public synchronized boolean hasWriteSpace(){
		return getCachePacketSize() < getCacheLimit();
	}

	public synchronized int getCachePacketSize(){
		return cacheTransmitter.getCachePacketSize();
	}

	public synchronized boolean isCachePacketEmpty() {
		return cacheTransmitter.isCachePacketEmpty();
	}

	private T processNextPacket(final T packet) {
		assert packet != null;

		if(packet.getPacketType() != PacketType.Voice) {
			currentSequence = 0x0;

			return packet;
		}

//		cacheTransmitter.sortCache(packetComparator);

		T result = null;

		Optional<Deque<T>> silincePacket = generateSilencePacket(packet);
		if(silincePacket.isPresent()) {
			String before = "";
			if(log.isDebugEnabled()) {
				final List<T> cache = cacheTransmitter.getCacheMemory();
				before = showPacketSeqB(cache);
			}

			for(Iterator<T> it = silincePacket.get().descendingIterator(); it.hasNext();) {
				T p = it.next();

				if(it.hasNext())
					cacheTransmitter.inputWritePushBack(p);
				else
					result = p;
			}

			String after = "";
			if(log.isDebugEnabled()) {
				List<T> cache = cacheTransmitter.getCacheMemory();
				after = showPacketSeqB(cache);
			}

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader + "Inserted " + (silincePacket.get().size() - 1) + " null packets.\n" +
					"    Before : " + before + "\n" +
					"    After  : " + after
				);
			}

			if((silincePacket.get().size() - 1) >= 1) {
				if(insertedNullPacketCount < Integer.MAX_VALUE) {insertedNullPacketCount++;}
			}

			if(
				log.isDebugEnabled() &&
				result.getPacket().getBackBoneHeader().getSequenceNumber() != getCurrentSequence()
			) {log.debug(logHeader + "Not matched input != output sequence.");}
		}
		else {
			result = packet;
		}

		if(getCurrentSequence() == 0x0)
			ArrayUtil.copyOf(packet.getPacket().getDVData().getDataSegment(), DSTARDefines.SlowdataSyncBytes);

		if(result.getPacket().isLastFrame()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "A total of " + getInsertedNullPacketCount() + " null packets were inserted.");

			insertedNullPacketCount = 0;
		}

		nextSequence();

		return result;
	}

	private Optional<Deque<T>> generateSilencePacket(final T packet){
		assert packet != null;

		int calcSeq = getCurrentSequence();
		final int receiveSeq = packet.getPacket().getSequenceNumber();
//			packet.getPacket().isLastFrame() ?
//				((packet.getPacket().getBackBoneHeader().getManagementInformation() & ~0x40) & 0x1F) :
//				packet.getPacket().getBackBoneHeader().getManagementInformation();

		if(calcSeq == receiveSeq)
			return Optional.empty();

		if(log.isDebugEnabled()) {
			log.debug(
				logHeader +
				"Receive packet sequence unmatched(CurrentSequence=" +
				String.format("%02X", calcSeq) + "/ReceiveSequence=" + String.format("%02X", receiveSeq) + ")"
			);
		}

		int silenceCount = 0;
		if(receiveSeq > calcSeq)
			silenceCount = receiveSeq - calcSeq;
		else
			silenceCount = (receiveSeq + 0x15) - calcSeq;

		if(silenceCount > 10) {
			currentSequence = receiveSeq;

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Could not repair dv packet sequence, too lost packet(" + silenceCount + "packet lost).\n" +
					"    CacheData : " + showPacketSeqB(cacheTransmitter.getCacheMemory())
				);
			}

			return Optional.empty();
		}

		final Deque<T> silencePackets = new LinkedList<>();
//		silencePackets.add(packet);

		for(int c = 0; c < silenceCount; c++) {
			@SuppressWarnings("unchecked")
			T p = (T)packet.clone();

			if(calcSeq == 0x0) {
				ArrayUtil.copyOf(p.getPacket().getDVData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);
				ArrayUtil.copyOf(p.getPacket().getDVData().getDataSegment(), DSTARDefines.SlowdataSyncBytes);
			}
			else {
				ArrayUtil.copyOf(p.getPacket().getDVData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);
				ArrayUtil.copyOf(p.getPacket().getDVData().getDataSegment(), DSTARDefines.SlowdataNullBytes);
			}

			p.getPacket().getBackBoneHeader().setSequenceNumber((byte)calcSeq);

			silencePackets.add(p);

			calcSeq = (calcSeq + 1) % 0x15;
		}
		silencePackets.add(packet);

		return Optional.of(silencePackets);
	}

	private int nextSequence() {
		return nextSequence(1);
	}

	private int nextSequence(int addSeq) {
		int curSeq = getCurrentSequence();
		for(int c = 0; c < addSeq; c++) {curSeq = (curSeq + 1) % (DSTARDefines.MaxSequenceNumber + 1);}

		currentSequence = curSeq;

		return curSeq;
	}

	private boolean sortBySequence(final List<T> packets) {
		assert packets != null;

		if(packets.size() < 2) {return true;}

		boolean isSwaped = false;

		for(int i = 0; i < (packets.size() - 1); i++) {
			final T first = packets.get(i);
			int firstSeq = first.getPacket().getBackBoneHeader().getSequenceNumber();

			final T second = packets.get(i + 1);
			int secondSeq = second.getPacket().getBackBoneHeader().getSequenceNumber();

			if(
				!first.getPacket().getDVPacket().hasPacketType(PacketType.Voice) ||
				!second.getPacket().getDVPacket().hasPacketType(PacketType.Voice)
			) {continue;}

			// need swap?
			if(
				(firstSeq == DSTARDefines.MinSequenceNumber && secondSeq == DSTARDefines.MaxSequenceNumber) ||
				(
					firstSeq != DSTARDefines.MinSequenceNumber && firstSeq != DSTARDefines.MaxSequenceNumber &&
					secondSeq != DSTARDefines.MinSequenceNumber && secondSeq != DSTARDefines.MaxSequenceNumber &&
					firstSeq > secondSeq
				)
			) {
				Collections.swap(packets, i, i + 1);

				isSwaped = true;
			}
		}

		return isSwaped;
	}

	private boolean isNeedSwapSequence(
			Collection<T> packets
	) {
		assert packets != null;

		if(packets.size() < 1) {return false;}

		int curSeq = 0;

		boolean isCurrentSequenceFound = false;
		boolean isNeedSwap = false;
		for(T p : packets) {
			if(!p.getPacket().getDVPacket().hasPacketType(PacketType.Voice)) {
				continue;	// Skip
			}
			else if(!isCurrentSequenceFound) {
				curSeq = p.getPacket().getBackBoneHeader().getSequenceNumber();
				isCurrentSequenceFound = true;
				curSeq = (curSeq + 1) % (DSTARDefines.MaxSequenceNumber + 1);
				continue;
			}
			else if(
				(
					!p.getPacket().isLastFrame() &&
					curSeq != p.getPacket().getBackBoneHeader().getSequenceNumber()
				) ||
				(
					p.getPacket().isLastFrame() &&
					curSeq != p.getPacket().getBackBoneHeader().getSequenceNumber()
				)
			) {
				isNeedSwap = true;
				break;
			}
			else {
				curSeq = (curSeq + 1) % (DSTARDefines.MaxSequenceNumber + 1);
			}
		}

		return isNeedSwap;
	}

	private String showPacketSeq(
			Collection<T> packets
	) {
		assert packets != null;

		final StringBuilder sb = new StringBuilder("");

		sb.append("[");
		for(T p : packets) {
			sb.append(String.format("%02X ", p.getPacket().getBackBoneHeader().getSequenceNumber()));
		}
		sb.append("]");

		return sb.toString();
	}

	private String showPacketSeqB(List<T> packets) {
		assert packets != null;

		final StringBuilder sb = new StringBuilder("");

		sb.append("[");
		for(T p : packets) {
			sb.append(String.format("%02X ", p.getPacket().getBackBoneHeader().getSequenceNumber()));
		}
		sb.append("]");

		return sb.toString();
	}

}
