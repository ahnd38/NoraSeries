package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlNewAccessPointHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class NewAccessPointFunctions extends ModemFunctions {

	private NewAccessPointFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlNewAccessPointHandler handler
	) {
		return setup(exceptionListener, NewAccessPointFunctions.class, server, handler);
	}
}
