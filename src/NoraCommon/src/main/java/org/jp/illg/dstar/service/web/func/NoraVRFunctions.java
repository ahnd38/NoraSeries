package org.jp.illg.dstar.service.web.func;

import org.jp.illg.dstar.repeater.modem.noravr.model.NoraVRLoginClient;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNoraVRHandler;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class NoraVRFunctions extends ModemFunctions{

	@SuppressWarnings("unused")
	private static final String logTag = NoraVRFunctions.class.getSimpleName() + " : ";

	private NoraVRFunctions() {}

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlNoraVRHandler handler
	) {
		return setup(exceptionListener, NoraVRFunctions.class, server, handler);
	}

	public static void notifyNoraVRClientLogin(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlNoraVRHandler handler,
		@NonNull final NoraVRLoginClient client
	) {
		notifyStatusChanged(server, handler);
	}

	public static void notifyNoraVRClientLogout(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlNoraVRHandler handler,
		@NonNull final NoraVRLoginClient client
	) {
		notifyStatusChanged(server, handler);
	}
}
