package org.jp.illg.util.socketio;

import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;

import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;

public class SocketIOEntryUDP extends SocketIOEntry<DatagramChannel>{

	public SocketIOEntryUDP() {
		super();
		super.setChannelType(ChannelType.UDP);
	}

	public SocketIOEntryUDP(DatagramChannel channel, SocketIOProcessingHandlerInterface handler) {
		this();
		super.setChannel(channel);
		super.setHandler(handler);
	}

	@Override
	public InetSocketAddress getRemoteAddress() {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setRemoteAddress(InetSocketAddress remoteAddress) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString() {
		return this.toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		return super.toString(indentLevel);
	}
}
