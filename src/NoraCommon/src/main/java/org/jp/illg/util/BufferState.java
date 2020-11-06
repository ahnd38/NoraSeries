package org.jp.illg.util;

import java.nio.ByteBuffer;

public enum BufferState {
	INITIALIZE,
	READ,
	WRITE;

	public static BufferState toWRITE(ByteBuffer buffer, BufferState bufferState) {
		assert buffer != null && bufferState != null;

		if(bufferState == BufferState.READ) {buffer.compact();}

		return BufferState.WRITE;
	}

	public static BufferState toREAD(ByteBuffer buffer, BufferState bufferState) {
		assert buffer != null && bufferState != null;

		switch(bufferState) {
		case INITIALIZE:
			return BufferState.INITIALIZE;

		case WRITE:
			buffer.flip();
			return BufferState.READ;

		case READ:
			return BufferState.READ;

		default:
			throw new RuntimeException();
		}
	}

	public static BufferState toINITIALIZE(ByteBuffer buffer, BufferState bufferState) {
		assert buffer != null && bufferState != null;

		buffer.clear();

		return BufferState.INITIALIZE;
	}
}
