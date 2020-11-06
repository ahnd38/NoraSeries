package org.jp.illg.dstar.util.dvpacket2;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.Getter;
import lombok.NonNull;

public class TransmitterPacketImpl implements TransmitterPacket, Cloneable {

	@Getter
	private DSTARPacket packet;

	@Getter
	private PacketType packetType;

	@Getter
	private FrameSequenceType frameSequenceType;


	public TransmitterPacketImpl(
		@NonNull final PacketType packetType,
		@NonNull DSTARPacket packet,
		@NonNull FrameSequenceType frameSequenceType
	) {
		super();

		this.packetType = packetType;
		this.packet = packet;
		this.frameSequenceType = frameSequenceType;
	}

	@Override
	public TransmitterPacketImpl clone() {
		TransmitterPacketImpl copy = null;
		try {
			copy = (TransmitterPacketImpl)super.clone();

			copy.packetType = packetType;
			copy.packet = packet.clone();
			copy.frameSequenceType = frameSequenceType;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

}
