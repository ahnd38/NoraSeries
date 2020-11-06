package org.jp.illg.util.socketio;

import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

import org.jp.illg.util.socketio.SocketIO.ChannelDirection;
import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class SocketIOEntryTCPServerClient extends SocketIOEntryTCP<SocketChannel>{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private ServerSocketChannel serverChannel;

	public SocketIOEntryTCPServerClient() {
		super();
		super.setDirection(ChannelDirection.IN);
		super.setChannelType(ChannelType.TCPServerClient);
	}

	public SocketIOEntryTCPServerClient(
		ServerSocketChannel serverChannel, SocketChannel channel, SocketIOProcessingHandlerInterface handler
	) {
		this();
		super.setChannel(channel);
		super.setHandler(handler);
		setServerChannel(serverChannel);
	}
}
