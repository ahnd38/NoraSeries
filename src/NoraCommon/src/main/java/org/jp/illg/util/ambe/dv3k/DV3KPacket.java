package org.jp.illg.util.ambe.dv3k;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;
import org.jp.illg.util.ambe.dv3k.packet.channel.DV3KChannelPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.DV3KControlPacketType;
import org.jp.illg.util.ambe.dv3k.packet.speech.DV3KSpeechPacketType;

public interface DV3KPacket {

	public DV3KPacketType getPacketType();

	public DV3KControlPacketType getControlPacketType();
	public DV3KSpeechPacketType getSpeechPacketType();
	public DV3KChannelPacketType getChannelPacketType();

	public ByteBuffer assemblePacket();
	public DV3KPacket parsePacket(ByteBuffer buffer);

	public DV3KPacket clone();

	public InetSocketAddress getRemoteAddress();
	public void setRemoteAddress(final InetSocketAddress remoteAddress);
}
