package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

public class READY extends DV3KControlPacketBase {

	public READY() {
		super(DV3KControlPacketType.READY);
	}

	@Override
	public READY clone() {
		READY copy = (READY)super.clone();

		return copy;
	}

	@Override
	protected int getRequestControlFieldDataLength() {
		return 0;
	}

	@Override
	protected boolean assembleControlFieldData(ByteBuffer buffer) {
		return true;
	}

	@Override
	protected boolean parseControlFieldData(ByteBuffer buffer, int fieldLength) {
		return true;
	}

}
