package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

public class HeartbeatCommand extends AccessPointCommandBase {

	public HeartbeatCommand() {
		super();
	}

	@Override
	public HeartbeatCommand clone() {
		final HeartbeatCommand cloneInstance = (HeartbeatCommand)super.clone();

		return cloneInstance;
	}

	@Override
	public byte[] assembleCommandData() {
		return new byte[] {
			(byte)0x02,
			(byte)0x02,
			(byte)0xff
		};
	}

	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.limit() >= 4 &&
			buffer.get() == (byte)0x03 &&
			buffer.get() == (byte)0x03 &&
			(buffer.get() & 0x0) == 0x0 &&
			buffer.get() == (byte)0xff
		){

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}else {
			buffer.rewind();
			return null;
		}
	}

}
