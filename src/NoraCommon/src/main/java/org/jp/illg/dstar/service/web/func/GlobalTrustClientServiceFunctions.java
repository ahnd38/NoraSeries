package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGlobalTrustClientHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class GlobalTrustClientServiceFunctions extends RoutingServiceFunctions {

	private GlobalTrustClientServiceFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlGlobalTrustClientHandler handler
	) {
		return setup(exceptionListener, GlobalTrustClientServiceFunctions.class, server, gatewayHandler, handler);
	}
}
