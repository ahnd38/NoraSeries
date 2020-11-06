package org.jp.illg.util;

import java.nio.ByteBuffer;

import org.jp.illg.util.BufferUtil.BufferProcessResult;

import lombok.Getter;
import lombok.Setter;

public class BufferUtilObject{
	@Getter
	@Setter
	private ByteBuffer buffer;

	@Getter
	@Setter
	private BufferState bufferState;

	@Getter
	@Setter
	private BufferProcessResult processResult;

	private BufferUtilObject() {
		super();

		setBuffer(null);
		setBufferState(BufferState.INITIALIZE);
		setProcessResult(BufferProcessResult.Unknown);
	}

	public BufferUtilObject(ByteBuffer buffer, BufferState bufferState) {
		this();

		setBuffer(buffer);
		setBufferState(bufferState);
	}

	public BufferUtilObject(ByteBuffer buffer, BufferState bufferState, BufferProcessResult processResult) {
		this(buffer, bufferState);

		setProcessResult(processResult);
	}
}

