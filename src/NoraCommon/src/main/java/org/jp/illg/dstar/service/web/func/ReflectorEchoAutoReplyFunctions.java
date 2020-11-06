package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorEchoAutoReplyHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class ReflectorEchoAutoReplyFunctions extends RepeaterFunctions {

	private ReflectorEchoAutoReplyFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorEchoAutoReplyHandler handler
	) {
		return setupStatusFunction(exceptionListener, ReflectorEchoAutoReplyFunctions.class, server, handler);
	}
}
