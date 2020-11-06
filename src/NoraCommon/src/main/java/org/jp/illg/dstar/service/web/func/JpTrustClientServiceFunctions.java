package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlJpTrustClientHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class JpTrustClientServiceFunctions extends RoutingServiceFunctions {

	private JpTrustClientServiceFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlJpTrustClientHandler handler
	) {
		return setup(exceptionListener, JpTrustClientServiceFunctions.class, server, gatewayHandler, handler);
	}
}
