package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlIrcDDBRoutingHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class IrcDDBRoutingServiceFunctions extends RoutingServiceFunctions {

	private IrcDDBRoutingServiceFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlIrcDDBRoutingHandler handler
	) {
		return setup(exceptionListener, IrcDDBRoutingServiceFunctions.class, server, gatewayHandler, handler);
	}
}
