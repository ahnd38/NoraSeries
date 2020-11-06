package org.jp.illg.util.socketio;

import org.jp.illg.util.socketio.SocketIO.ChannelDirection;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class SocketIOEntryTCP<T> extends SocketIOEntry<T>{

	@Getter
	@Setter(AccessLevel.PROTECTED)
	private ChannelDirection direction;

	public SocketIOEntryTCP() {
		super();
		this.direction = ChannelDirection.Unknown;
	}


	@Override
	public String toString() {
		return this.toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb =
			new StringBuilder(super.toString(indentLevel));

		sb.append("/");
		sb.append("Direction:");
		sb.append(getDirection());

		return sb.toString();
	}
}
