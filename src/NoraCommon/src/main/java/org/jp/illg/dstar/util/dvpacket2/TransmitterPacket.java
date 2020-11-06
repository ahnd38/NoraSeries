package org.jp.illg.dstar.util.dvpacket2;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.PacketType;

public interface TransmitterPacket {
	public DSTARPacket getPacket();
	public PacketType getPacketType();
	public FrameSequenceType getFrameSequenceType();
	public TransmitterPacket clone();
}
