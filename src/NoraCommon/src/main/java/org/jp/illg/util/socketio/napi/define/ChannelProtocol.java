package org.jp.illg.util.socketio.napi.define;

import org.jp.illg.util.socketio.SocketIO.ChannelType;

public enum ChannelProtocol {
	TCP,
	UDP;

	public static ChannelProtocol toChannelProtocol(ChannelType channelType) {
		switch(channelType) {
		case TCPClient:
		case TCPServerClient:
			return TCP;
		case UDP:
			return UDP;
		default:
			return null;
		}
	}
}
