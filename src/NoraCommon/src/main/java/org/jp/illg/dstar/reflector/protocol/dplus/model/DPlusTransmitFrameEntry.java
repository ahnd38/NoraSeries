package org.jp.illg.dstar.reflector.protocol.dplus.model;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.Setter;

public class DPlusTransmitFrameEntry {

	@Getter
	private final int frameID;

	@Getter
	@Setter
	private byte frameSequence;

	@Getter
	private final DSTARPacket header;

	@Getter
	private final Timer activityTimestamp;

	@Getter
	private final CacheTransmitter<DPlusTransmitPacketEntry> cacheTransmitter;

	@Getter
	@Setter
	private long packetCount;

	public DPlusTransmitFrameEntry(
		final int frameID,
		final DSTARPacket header
	) {
		super();

		this.frameID = frameID;
		frameSequence = (byte)0x0;
		this.header = header;
		activityTimestamp = new Timer();
		activityTimestamp.updateTimestamp();

		cacheTransmitter = new CacheTransmitter<>(10);
		packetCount = 0L;
	}


}
