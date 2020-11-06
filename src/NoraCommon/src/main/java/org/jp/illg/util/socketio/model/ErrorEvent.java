package org.jp.illg.util.socketio.model;

import java.nio.channels.SelectableChannel;

import org.jp.illg.util.socketio.SocketIOEntry;

import lombok.Getter;
import lombok.NonNull;

public class ErrorEvent {
	@Getter
	private SocketIOEntry<? extends SelectableChannel> entry;

	@Getter
	private Exception exception;

	public ErrorEvent(
		@NonNull SocketIOEntry<? extends SelectableChannel> entry, @NonNull Exception exception
	) {
		super();

		this.entry = entry;
		this.exception = exception;
	}
}
