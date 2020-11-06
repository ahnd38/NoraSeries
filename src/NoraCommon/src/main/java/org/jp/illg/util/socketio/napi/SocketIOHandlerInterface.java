package org.jp.illg.util.socketio.napi;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;

public interface SocketIOHandlerInterface {
	OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	);
	OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	);
	OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	);
	void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	);
	void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	);

	void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes);
}
