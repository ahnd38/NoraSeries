package org.jp.illg.dstar.util.dvpacket;

import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.dvpacket.DvPacketRateAdjuster.DvPacketSortFunction;
import org.jp.illg.util.ArrayUtil;

import com.annimon.stream.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DvPacketRepairTransporter {

	private final String logHeader;

	private final DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData> cacheTransmitter;

	@Getter
	@Setter
	private int currentSequence;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int insertedNullPacketCount;

	@Getter
	@Setter
	private int cacheLimit;
	private static final int cacheLimitDefault = 100;

	@Getter
	@Setter
	private boolean cacheLimitEnable;
	private static final boolean cacheLimitEnableDefault = false;

	private final DvPacketSortFunction<
		DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket
	> packetComparator;

	public DvPacketRepairTransporter(int initialCacheSize) {
		this();

		if(initialCacheSize < 5) {initialCacheSize = 5;}

		cacheTransmitter.setCacheSize(initialCacheSize);
	}

	public DvPacketRepairTransporter(int initialCacheSize, int cacheLimit) {
		this(initialCacheSize);

		setCacheLimitEnable(true);
		setCacheLimit(cacheLimit);
	}

	public DvPacketRepairTransporter() {
		super();

		logHeader = this.getClass().getSimpleName() + " : ";

		cacheTransmitter = new DvPacketCacheTransmitter<>(5);

		setCurrentSequence(0);

		setInsertedNullPacketCount(0);

		setCacheLimit(cacheLimitDefault);
		setCacheLimitEnable(cacheLimitEnableDefault);

		packetComparator =
			new DvPacketSortFunction<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket>() {

				@Override
				public List<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket> sort(
						List<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket> packets
				) {

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

					return packets;
				}
			};
	}

	public synchronized void reset() {
		cacheTransmitter.reset();

		setCurrentSequence(0x0);

		setInsertedNullPacketCount(0);
	}

	public synchronized Optional<DVPacket> readPacket(){

		cacheTransmitter.sortCache(packetComparator);

		Optional<DvPacketRepairTransporterPacketData> packet = cacheTransmitter.outputRead();
		if(packet.isPresent())
			return Optional.of(processNextPacket(packet.get().getPacketType(), packet.get().getPacket()));
		else
			return Optional.empty();
	}

	public synchronized boolean hasReadablePacket(){
		return cacheTransmitter.hasOutputRead();
	}

	public synchronized boolean writePacket(
		@NonNull final PacketType packetType, @NonNull final DVPacket packet
	) {
		if(
			(isCacheLimitEnable() && hasWriteSpace()) ||
			packetType == PacketType.Header
		){
			return cacheTransmitter.inputWrite(packetType, new DvPacketRepairTransporterPacketData(packetType, packet));
		}else{return false;}
	}

	public synchronized boolean hasWriteSpace(){
		return getCachePacketSize() < getCacheLimit();
	}

	public synchronized int getCachePacketSize(){
		return cacheTransmitter.getCacheSize();
	}

	private DVPacket processNextPacket(
		@NonNull final PacketType packetType, @NonNull final DVPacket packet
	) {
		assert packet != null;

		if(packetType != PacketType.Voice) {
			setCurrentSequence(0x0);
			return packet;
		}

//		cacheTransmitter.sortCache(packetComparator);

		DVPacket result = null;

		Optional<Deque<DVPacket>> silincePacket = generateSilencePacket(packet);
		if(silincePacket.isPresent()) {
			String before = "";
			if(log.isDebugEnabled()) {
				List<DvPacketRepairTransporterPacketData> cache = cacheTransmitter.getCache();
				before = showPacketSeqB(cache);
			}

			for(Iterator<DVPacket> it = silincePacket.get().descendingIterator(); it.hasNext();) {
				DVPacket p = it.next();

				if(it.hasNext()) {
					cacheTransmitter.pushBackCache(
						PacketType.Voice, new DvPacketRepairTransporterPacketData(PacketType.Voice, p)
					);
				}
				else
					result = p;
			}

			String after = "";
			if(log.isDebugEnabled()) {
				List<DvPacketRepairTransporterPacketData> cache = cacheTransmitter.getCache();
				after = showPacketSeqB(cache);
			}

			log.debug(
				logHeader + "Inserted " + (silincePacket.get().size() - 1) + " null packets.\n" +
				"    Before : " + before + "\n" +
				"    After  : " + after
			);

			if((silincePacket.get().size() - 1) >= 1)
				setInsertedNullPacketCount(getInsertedNullPacketCount() + 1);

			if(result.getBackBone().getSequenceNumber() != getCurrentSequence())
				log.debug(logHeader + "Not matched input != output sequence.");
		}
		else {
			result = packet;

		}

		if(getCurrentSequence() == 0x0)
			ArrayUtil.copyOf(packet.getVoiceData().getDataSegment(), DSTARDefines.SlowdataSyncBytes);

		if(result.isEndVoicePacket()) {
			log.debug(logHeader + "A total of " + getInsertedNullPacketCount() + " null packets were inserted.");
			setInsertedNullPacketCount(0);
		}

		nextSequence();

		return result;
	}

	private Optional<Deque<DVPacket>> generateSilencePacket(final DVPacket packet){
		assert packet != null;

		int calcSeq = getCurrentSequence();
		int receiveSeq = packet.getBackBone().getSequenceNumber();

		if(calcSeq == receiveSeq)
			return Optional.empty();

		log.debug(
				logHeader +
				"Receive packet sequence unmatched(CurrentSequence=" +
				String.format("%02X", calcSeq) + "/ReceiveSequence=" + String.format("%02X", receiveSeq) + ")"
		);

		int silenceCount = 0;
		if(receiveSeq > calcSeq)
			silenceCount = receiveSeq - calcSeq;
		else
			silenceCount = (receiveSeq + 0x15) - calcSeq;

		if(silenceCount > 10) {
			setCurrentSequence(receiveSeq);

			log.debug(
					logHeader +
					"Could not repair dv packet sequence, too lost packet(" + silenceCount + "packet lost).\n" +
					"    CacheData : " + showPacketSeqB(cacheTransmitter.getCache())
			);

			return Optional.empty();
		}

		Deque<DVPacket> silencePackets = new LinkedList<>();
//		silencePackets.add(packet);

		for(int c = 0; c < silenceCount; c++) {
			DVPacket p = packet.clone();

			if(calcSeq == 0x0) {
				ArrayUtil.copyOf(p.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);
				ArrayUtil.copyOf(p.getVoiceData().getDataSegment(), DSTARDefines.SlowdataSyncBytes);
			}
			else {
				ArrayUtil.copyOf(p.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);
				ArrayUtil.copyOf(p.getVoiceData().getDataSegment(), DSTARDefines.SlowdataNullBytes);
			}

			p.getBackBone().setSequenceNumber((byte)calcSeq);

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
		for(int c = 0; c < addSeq; c++) {curSeq = (curSeq + 1) % 0x15;}

		setCurrentSequence(curSeq);

		return curSeq;
	}

	private static boolean sortBySequence(
			List<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket> packets
	) {
		assert packets != null;

		if(packets.size() < 2) {return true;}

		boolean swaped = false;

		for(int i = 0; i < (packets.size() - 1); i++) {
			DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket first =
				packets.get(i);
			int firstSeq = first.getPacket().getBackBone().getSequenceNumber();

			DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket second =
				packets.get(i + 1);
			int secondSeq = second.getPacket().getBackBone().getSequenceNumber();

			if(first.getPacketType() != PacketType.Voice || second.getPacketType() != PacketType.Voice)
				continue;

			// need swap?
			if(
				(firstSeq == 0x00 && secondSeq == 0x14) ||
				(
					firstSeq != 0x00 && firstSeq != 0x14 &&
					secondSeq != 0x00 && secondSeq != 0x14 &&
					firstSeq > secondSeq
				)
			) {
				Collections.swap(packets, i, i + 1);

				swaped = true;
			}
		}


		return swaped;
	}

	private static boolean isNeedSwapSequence(
			List<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket> packets
	) {
		assert packets != null;

		if(packets.size() < 1) {return false;}

		int curSeq = 0;

		boolean curSeqFound = false;
		boolean needSwap = false;
		for(DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket p : packets) {
			if(
				p.getPacketType() != PacketType.Voice
			) {
				continue;	// Skip
			}
			else if(!curSeqFound) {
				curSeq = p.getData().getPacket().getBackBone().getSequenceNumber();
				curSeqFound = true;
				curSeq = (curSeq + 1) % 0x15;
				continue;
			}
			else if(curSeq != p.getPacket().getBackBone().getSequenceNumber()) {
				needSwap = true;
				break;
			}
			else {
				curSeq = (curSeq + 1) % 0x15;
			}
		}

		return needSwap;
	}

	private static String showPacketSeq(
			List<DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket> packets
	) {
		assert packets != null;

		StringBuffer sb = new StringBuffer("");

		sb.append("[");
		for(DvPacketCacheTransmitter<DvPacketRepairTransporterPacketData>.DvPacketCacheTransmitterPacket p : packets) {
			sb.append(String.format("%02X ", p.getPacket().getBackBone().getSequenceNumber()));
		}
		sb.append("]");

		return sb.toString();
	}

	private static String showPacketSeqB(List<DvPacketRepairTransporterPacketData> packets) {
		assert packets != null;

		StringBuffer sb = new StringBuffer("");

		sb.append("[");
		for(DvPacketRepairTransporterPacketData p : packets) {
			sb.append(String.format("%02X ", p.getPacket().getBackBone().getSequenceNumber()));
		}
		sb.append("]");

		return sb.toString();
	}

}
