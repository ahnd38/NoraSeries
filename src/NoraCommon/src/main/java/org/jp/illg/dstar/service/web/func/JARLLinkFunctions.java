package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class JARLLinkFunctions extends ReflectorFunctions{

	@SuppressWarnings("unused")
	private static final String logTag = JARLLinkFunctions.class.getSimpleName() + " : ";

	private JARLLinkFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		return setup(exceptionListener, JARLLinkFunctions.class, server, handler);
	}
}
