package org.jp.illg.dstar.jarl.xchange.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.DVPacket;

public class XChangePacketKeepalive extends XChangePacketBase {

	public XChangePacketKeepalive() {
		super();
	}

	@Override
	public DVPacket getDvPacket() {
		return null;
	}

	@Override
	public XChangePacketType getType() {
		return XChangePacketType.Voice;
	}

	@Override
	protected int getDataFieldTransmitDataLength() {
		return 0;
	}

	@Override
	protected boolean parseDataField(ByteBuffer buffer) {
		return buffer.remaining() == 0;
	}

	@Override
	protected int getDataFieldReceiveDataLength() {
		return 0;
	}

	@Override
	protected boolean assembleDataField(ByteBuffer buffer) {
		return buffer.remaining() == 0;
	}

}
