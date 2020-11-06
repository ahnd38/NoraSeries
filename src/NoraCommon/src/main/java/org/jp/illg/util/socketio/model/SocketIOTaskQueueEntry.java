package org.jp.illg.util.socketio.model;

import java.nio.channels.SelectionKey;
import java.util.LinkedList;
import java.util.Queue;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class SocketIOTaskQueueEntry {

	@Getter
	private final SelectionKey key;

	@Getter
	private final Queue<Runnable> queue;

	@Getter
	@Setter
	private boolean processing;

	public SocketIOTaskQueueEntry(
		@NonNull SelectionKey key
	) {
		super();

		this.key = key;

		queue = new LinkedList<>();

		processing = false;
	}

}
