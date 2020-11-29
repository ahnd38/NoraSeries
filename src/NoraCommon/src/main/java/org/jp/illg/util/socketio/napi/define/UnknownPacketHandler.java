package org.jp.illg.util.socketio.napi.define;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.util.object.ConsumerTriple;

public interface UnknownPacketHandler
extends ConsumerTriple<ByteBuffer, InetSocketAddress, InetSocketAddress>{
	@Override
	void accept(
		ByteBuffer buffer,
		InetSocketAddress remoteAddress,
		InetSocketAddress localAddress
	);
}
