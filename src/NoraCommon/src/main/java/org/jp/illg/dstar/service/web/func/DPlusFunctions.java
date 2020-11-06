package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class DPlusFunctions extends ReflectorFunctions{

	@SuppressWarnings("unused")
	private static final String logTag = DPlusFunctions.class.getSimpleName() + " : ";

	private DPlusFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		return setup(exceptionListener, DPlusFunctions.class, server, handler);
	}
}
