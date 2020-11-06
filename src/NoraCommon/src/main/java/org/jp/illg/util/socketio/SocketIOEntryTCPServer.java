package org.jp.illg.util.socketio;

import java.nio.channels.ServerSocketChannel;

import org.jp.illg.util.socketio.SocketIO.ChannelDirection;
import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;

public class SocketIOEntryTCPServer extends SocketIOEntryTCP<ServerSocketChannel>{
	public SocketIOEntryTCPServer() {
		super();
		super.setDirection(ChannelDirection.IN);
		super.setChannelType(ChannelType.TCPServer);
	}

	public SocketIOEntryTCPServer(ServerSocketChannel channel, SocketIOProcessingHandlerInterface handler) {
		this();
		super.setChannel(channel);
		super.setHandler(handler);
	}
}
