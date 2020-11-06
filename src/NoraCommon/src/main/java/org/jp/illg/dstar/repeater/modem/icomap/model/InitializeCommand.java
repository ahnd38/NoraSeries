package org.jp.illg.dstar.repeater.modem.icomap.model;

import java.nio.ByteBuffer;

public class InitializeCommand extends AccessPointCommandBase {

	public InitializeCommand() {
		super();
	}

	@Override
	public InitializeCommand clone() {
		final InitializeCommand cloneInstance = (InitializeCommand)super.clone();

		return cloneInstance;
	}

	@Override
	public byte[] assembleCommandData() {
		return new byte[]{
			(byte)0xff,
			(byte)0xff,
			(byte)0xff
		};
	}

	@Override
	public AccessPointCommand analyzeCommandData(ByteBuffer buffer) {
		buffer.rewind();
		if(
			buffer.limit() >= 3 &&
			buffer.get() == (byte)0xff &&
			buffer.get() == (byte)0xff &&
			buffer.get() == (byte)0xff
		){

			buffer.compact();
			buffer.limit(buffer.position());
			buffer.rewind();

			return this;
		}
		else {
			buffer.rewind();
			return null;
		}
	}

}
