package org.jp.illg.dstar.util.dvpacket;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;

public interface DvPacketCacheTransmitterFunc {
	public DVPacket getPacket();
	public PacketType getPacketType();
}
