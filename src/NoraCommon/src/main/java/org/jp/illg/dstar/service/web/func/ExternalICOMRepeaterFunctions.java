package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlExternalICOMRepeaterHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class ExternalICOMRepeaterFunctions extends RepeaterFunctions {

	public ExternalICOMRepeaterFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlExternalICOMRepeaterHandler repeaterHandler
	) {
		return setupStatusFunction(
			exceptionListener, ExternalICOMRepeaterFunctions.class, server, repeaterHandler
		) &&
		setupReflectorControlFunction(
			exceptionListener, InternalFunctions.class, server, gatewayHandler, repeaterHandler
		) &&
		setupReflectorHostsFunction(
			exceptionListener, InternalFunctions.class, server, gatewayHandler, repeaterHandler
		);
	}
}
