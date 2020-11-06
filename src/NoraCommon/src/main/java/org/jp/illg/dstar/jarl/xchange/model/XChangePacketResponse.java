package org.jp.illg.dstar.jarl.xchange.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.DVPacket;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class XChangePacketResponse extends XChangePacketBase {

	@Getter
	@Setter
	private XChangePacketType type;

	public XChangePacketResponse(@NonNull XChangePacket receivePacket) {
		super();

		setPacketNo(receivePacket.getPacketNo());
		setDirection(XChangePacketDirection.ToGateway);
		setType(receivePacket.getType());
		setRouteFlags(receivePacket.getRouteFlags());
	}

	@Override
	protected int getDataFieldTransmitDataLength() {
		return 0;
	}

	@Override
	protected int getDataFieldReceiveDataLength() {
		return 0;
	}

	@Override
	protected boolean parseDataField(ByteBuffer buffer) {
		return true;
	}

	@Override
	protected boolean assembleDataField(ByteBuffer buffer) {
		return buffer.remaining() == 0;
	}

	@Override
	public DVPacket getDvPacket() {
		return null;
	}
}
