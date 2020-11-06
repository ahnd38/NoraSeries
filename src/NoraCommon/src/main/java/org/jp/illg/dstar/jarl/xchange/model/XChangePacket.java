package org.jp.illg.dstar.jarl.xchange.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;

public interface XChangePacket extends DSTARPacket, Cloneable {

	byte[] getHeader();

	int getPacketNo();
	void setPacketNo(int packetNo);

	XChangePacketDirection getDirection();

	XChangePacketType getType();

	XChangeRouteFlagData getRouteFlags();

	int getLength();

	DVPacket getDvPacket();

	void setRemoteAddress(InetSocketAddress remoteAddress);
	InetSocketAddress getRemoteAddress();

	XChangePacket parsePacket(ByteBuffer buffer);
	ByteBuffer assemblePacket();

	XChangePacket clone();

	String toString(int indentLevel);
}
