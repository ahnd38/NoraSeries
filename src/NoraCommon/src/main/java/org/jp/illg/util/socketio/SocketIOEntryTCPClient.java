package org.jp.illg.util.socketio;

import java.nio.channels.SocketChannel;

import org.jp.illg.util.socketio.SocketIO.ChannelDirection;
import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;

public class SocketIOEntryTCPClient extends SocketIOEntryTCP<SocketChannel>{
	public SocketIOEntryTCPClient() {
		super();
		super.setDirection(ChannelDirection.OUT);
		super.setChannelType(ChannelType.TCPClient);
	}

	public SocketIOEntryTCPClient(SocketChannel channel, SocketIOProcessingHandlerInterface handler) {
		this();
		super.setChannel(channel);
		super.setHandler(handler);
	}
}
