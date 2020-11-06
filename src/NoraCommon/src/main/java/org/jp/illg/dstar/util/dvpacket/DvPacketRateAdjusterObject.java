package org.jp.illg.dstar.util.dvpacket;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.PacketType;

public interface DvPacketRateAdjusterObject {

	public PacketType getPacketType();

	public boolean isEndVoicePacket();

	public DVPacket getPacket();
}
