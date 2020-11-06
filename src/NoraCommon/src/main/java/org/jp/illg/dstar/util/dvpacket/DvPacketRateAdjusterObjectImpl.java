package org.jp.illg.dstar.util.dvpacket;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DvPacketRateAdjusterObjectImpl implements DvPacketRateAdjusterObject {

	@Getter
	@Setter
	private DVPacket packet;

	@Setter
	private PacketType packetType;

	public DvPacketRateAdjusterObjectImpl(
		@NonNull final PacketType packetType,
		@NonNull final DVPacket packet
	) {
		super();

		if(!packet.hasPacketType(packetType))
			throw new IllegalArgumentException("Packet is not have type = " + packetType);

		setPacketType(packetType);
		setPacket(packet);
	}

	@Override
	public PacketType getPacketType() {
		return packetType;
	}

	@Override
	public boolean isEndVoicePacket() {
		if(getPacket() != null)
			return getPacket().isEndVoicePacket();
		else
			return false;
	}

}
