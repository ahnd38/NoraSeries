package org.jp.illg.util.socketio.model;

import java.nio.channels.SelectionKey;

import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;

public abstract class SocketIOTask extends RunnableTask {

	@Getter
	private final SelectionKey key;

	public SocketIOTask(
		@NonNull SelectionKey key,
		ThreadUncaughtExceptionListener exceptionListener
	) {
		super(exceptionListener);

		this.key = key;
	}

	public SocketIOTask(@NonNull SelectionKey key) {
		this(key, null);
	}
}
