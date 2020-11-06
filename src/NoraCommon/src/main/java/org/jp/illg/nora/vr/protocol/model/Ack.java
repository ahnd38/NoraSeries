package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.NonNull;

public class Ack extends NoraVRPacketBase {

	public Ack() {
		super(NoraVRCommandType.ACK);
	}

	@Override
	public Ack clone() {
		Ack copy = null;

		copy = (Ack)super.clone();

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		return buffer.remaining() == 0;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 0;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		return buffer.remaining() == 0;
	}

}
