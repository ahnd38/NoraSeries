package org.jp.illg.util.socketio.napi.define;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.util.object.FunctionTriple;

public interface PacketTracerFunction
extends FunctionTriple<ByteBuffer, InetSocketAddress, InetSocketAddress, Boolean>{
	@Override
	Boolean apply(
		ByteBuffer buffer,
		InetSocketAddress remoteAddress,
		InetSocketAddress localAddress
	);
}
