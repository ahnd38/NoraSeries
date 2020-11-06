package org.jp.illg.dstar.service.web.func;

import java.util.Collection;

import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public abstract class ReflectorFunctions {

	protected ReflectorFunctions() {}

	public static void notifyStatusChanged(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		sendUpdateStatusData(server, handler);
	}

	protected static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull Class<?> functionClass,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		return setupEventRequestStatus(exceptionListener, functionClass, server, handler);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler
	) {
		return sendUpdateStatusData(
			server,
			handler,
			server.getRoomOperations(handler.getWebSocketRoomId()).getClients()
		);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler,
		@NonNull final String roomName
	) {
		return sendUpdateStatusData(
			server,
			handler,
			server.getRoomOperations(roomName).getClients()
		);
	}

	protected static boolean sendResponseStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler,
		@NonNull final SocketIOClient client
	) {
		return sendResponseStatusData(
			server,
			handler,
			client,
			handler.createStatusData()
		);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlReflectorHandler handler,
		@NonNull final SocketIOClient client,
		final ReflectorStatusData status
	) {
		return sendStatusData(
			server,
			handler,
			client,
			"update",
			status != null ? status : handler.createStatusData()
		);
	}

	private static boolean setupEventRequestStatus(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlReflectorHandler handler
	) {
		final String requestStatusEventName =
			"request_status_" + handler.getWebSocketRoomId();
		server.addEventListener(
			requestStatusEventName,
			Object.class,
			new DashboardEventListenerBuilder<>(functionClass, requestStatusEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						sendResponseStatusData(server, handler, client);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static boolean sendUpdateStatusData(
		final SocketIOServer server,
		final WebRemoteControlReflectorHandler handler,
		final Collection<SocketIOClient> clients
	) {
		final ReflectorStatusData status = handler.createStatusData();
		boolean success = true;
		for(final SocketIOClient client : clients) {
			if(!sendUpdateStatusData(server, handler, client, status))
				success = false;
		}

		return success;
	}

	private static boolean sendResponseStatusData(
		final SocketIOServer server,
		final WebRemoteControlReflectorHandler handler,
		final SocketIOClient client,
		final ReflectorStatusData status
	) {
		return sendStatusData(
			server,
			handler,
			client,
			"response",
			status != null ? status : handler.createStatusData()
		);
	}

	private static boolean sendStatusData(
		final SocketIOServer server,
		final WebRemoteControlReflectorHandler handler,
		final SocketIOClient client,
		final String header,
		final ReflectorStatusData status
	) {
		client.sendEvent(
			header + "_status_" + handler.getWebSocketRoomId(),
			status != null ? status : handler.createStatusData()
		);

		return true;
	}
}
