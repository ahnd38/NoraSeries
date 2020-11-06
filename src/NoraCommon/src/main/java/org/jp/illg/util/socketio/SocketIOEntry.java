package org.jp.illg.util.socketio;

import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;

import org.jp.illg.util.socketio.SocketIO.ChannelType;
import org.jp.illg.util.socketio.SocketIO.SocketIOProcessingHandlerInterface;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class SocketIOEntry<T>{

	@Getter
	@Setter(AccessLevel.PROTECTED)
	private ChannelType channelType;

	@Getter
	@Setter
	private SelectionKey key;

	@Getter
	@Setter
	private T channel;

	@Getter
	@Setter
	private SocketIOProcessingHandlerInterface handler;

	@Getter
	@Setter
	private InetSocketAddress localAddress;

	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	protected SocketIOEntry() {
		super();
		this.channelType = ChannelType.Unknown;
	}

	@Override
	public String toString() {
		return this.toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder(indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]");

		sb.append("ChannelType:");
		sb.append(getChannelType());

		sb.append("/");

		sb.append("LocalAddress:");
		sb.append(getLocalAddress());

		sb.append("/");

		sb.append("RemoteAddress:");
		sb.append(getRemoteAddress());

		return sb.toString();
	}
}
