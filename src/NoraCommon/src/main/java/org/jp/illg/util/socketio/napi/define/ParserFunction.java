package org.jp.illg.util.socketio.napi.define;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.util.object.FunctionQuadruple;

public interface ParserFunction
extends FunctionQuadruple<ByteBuffer, Integer, InetSocketAddress, InetSocketAddress, Boolean>{
	@Override
	Boolean apply(
		ByteBuffer buffer,
		Integer packetSize,
		InetSocketAddress remoteAddress,
		InetSocketAddress localAddress
	);
}
