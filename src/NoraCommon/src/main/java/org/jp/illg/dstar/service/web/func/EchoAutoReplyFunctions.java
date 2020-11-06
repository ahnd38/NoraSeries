package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlEchoAutoReplyHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class EchoAutoReplyFunctions extends RepeaterFunctions {

	private EchoAutoReplyFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlEchoAutoReplyHandler handler
	) {
		return setupStatusFunction(exceptionListener, EchoAutoReplyFunctions.class, server, handler);
	}
}
