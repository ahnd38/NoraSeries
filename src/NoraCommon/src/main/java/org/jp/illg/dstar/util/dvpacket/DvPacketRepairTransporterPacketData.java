package org.jp.illg.dstar.util.dvpacket;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.Getter;
import lombok.Setter;

public class DvPacketRepairTransporterPacketData implements DvPacketCacheTransmitterFunc {

	@Getter
	@Setter
	private DVPacket packet;

	@Getter
	@Setter
	private PacketType packetType;

	public DvPacketRepairTransporterPacketData(final PacketType packetType, final DVPacket packet) {
		super();

		setPacketType(packetType);
		setPacket(packet);
	}

}
