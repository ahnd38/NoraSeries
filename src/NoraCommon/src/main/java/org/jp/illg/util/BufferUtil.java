package org.jp.illg.util;

import java.nio.ByteBuffer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BufferUtil {

	public enum BufferProcessResult{
		Unknown,
		Success,
		Failed,
		Overflow,
		;
	}

	private BufferUtil() {
		super();
	}

	public static BufferUtilObject putBuffer(
			String logHeader,
			ByteBuffer dstBuffer, BufferState dstBufferState, Timer dstBuffertimer,
			byte[] srcBuffer
	) {
		return putBuffer(logHeader, dstBuffer, dstBufferState, dstBuffertimer, ByteBuffer.wrap(srcBuffer), false);
	}

	public static BufferUtilObject putBuffer(
			String logHeader,
			ByteBuffer dstBuffer, BufferState dstBufferState, Timer dstBuffertimer,
			byte[] srcBuffer, boolean allowOverflow
	) {
		return putBuffer(logHeader, dstBuffer, dstBufferState, dstBuffertimer, ByteBuffer.wrap(srcBuffer), allowOverflow);
	}

	public static BufferUtilObject putBuffer(
			String logHeader,
			ByteBuffer dstBuffer, BufferState dstBufferState, Timer dstBuffertimer,
			ByteBuffer srcBuffer
	) {
		return putBuffer(logHeader, dstBuffer, dstBufferState, dstBuffertimer, srcBuffer, false);
	}

	public static BufferUtilObject putBuffer(
			String logHeader,
			ByteBuffer dstBuffer, BufferState dstBufferState, Timer dstBuffertimer,
			ByteBuffer srcBuffer, boolean allowOverflow
	) {
		if(dstBuffer == null || dstBufferState == null || dstBuffertimer == null || srcBuffer == null)
			return new BufferUtilObject(dstBuffer, dstBufferState, BufferProcessResult.Failed);

		BufferProcessResult result = BufferProcessResult.Success;

		int savedSrcBufferPos = srcBuffer.position();

		//受信バッファが古ければ捨てる
		if(dstBuffertimer.isTimeout()) {
			if(dstBufferState == BufferState.WRITE) {
				dstBuffer.flip();
				dstBufferState = BufferState.READ;
			}
			if(log.isDebugEnabled() && dstBuffer.remaining() >= 1) {
				dstBuffer.rewind();
				log.debug(
					logHeader +
					"Purged receive cache data..." +
					FormatUtil.byteBufferToHex(dstBuffer)
				);
			}
			dstBuffer.clear();
			dstBufferState = BufferState.INITIALIZE;
		}
		dstBuffertimer.updateTimestamp();

		if(dstBufferState == BufferState.READ) {
			dstBuffer.compact();
			dstBufferState = BufferState.WRITE;
		}

		if(srcBuffer.remaining() > 0) {
			//バッファをオーバーランするか？
			if(dstBuffer.remaining() < srcBuffer.remaining()) {
				int overflowBytes = srcBuffer.remaining() - dstBuffer.remaining();

				if(!allowOverflow && log.isWarnEnabled()){
					log.warn(
						logHeader +
						"Buffer overflow detected, " +
						overflowBytes + "bytes overflow."
					);
				}

				//超える分は破棄して入れられるだけ入れる
				srcBuffer.position(srcBuffer.position() + overflowBytes);
				dstBuffer.put(srcBuffer);
				dstBufferState = BufferState.WRITE;

				result = BufferProcessResult.Overflow;
			}else {
				//バッファに余裕があるので、書き込む
				dstBuffer.put(srcBuffer);	//受信バッファにコピー
				dstBufferState = BufferState.WRITE;
			}

			if(log.isTraceEnabled()) {
				final int savedPos = srcBuffer.position();
				srcBuffer.position(savedSrcBufferPos);
				log.trace(logHeader + "received data..." + FormatUtil.byteBufferToHex(srcBuffer));
				srcBuffer.position(savedPos);

				dstBuffer.flip();
				log.trace(logHeader + "receive buffer updated..." + FormatUtil.byteBufferToHex(dstBuffer));
				dstBufferState = BufferState.READ;
			}
		}

		if(dstBufferState == BufferState.WRITE) {
			dstBuffer.flip();
			dstBufferState = BufferState.READ;
		}

		dstBuffer.rewind();

		return new BufferUtilObject(dstBuffer, dstBufferState, result);
	}
}
