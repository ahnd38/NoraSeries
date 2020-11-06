package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class DummyFunctions extends RepeaterFunctions {

	public DummyFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlRepeaterHandler repeaterHandler
	) {
		return
			setupStatusFunction(
				exceptionListener, DummyFunctions.class, server, repeaterHandler
			) &&
			setupReflectorControlFunction(
				exceptionListener, DummyFunctions.class, server, gatewayHandler, repeaterHandler
			) &&
			setupReflectorHostsFunction(
				exceptionListener, DummyFunctions.class, server, gatewayHandler, repeaterHandler
			);
	}
}
