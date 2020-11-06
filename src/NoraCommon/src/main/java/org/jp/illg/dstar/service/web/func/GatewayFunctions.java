package org.jp.illg.dstar.service.web.func;

import java.util.Collection;
import java.util.List;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.model.GatewayStatusData;
import org.jp.illg.dstar.service.web.model.HeardEntry;
import org.jp.illg.dstar.service.web.model.ResponseHeardLog;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;

import lombok.NonNull;

public class GatewayFunctions {

	@SuppressWarnings("unused")
	private static final String logTag = GatewayFunctions.class.getSimpleName() + " : ";

	public GatewayFunctions() {}


	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler
	) {
		if(!setupEventRequestStatus(exceptionListener, GatewayFunctions.class, server, handler))
			return false;

		final String requestHeardlogEventName =
			"request_heardlog_" + handler.getWebSocketRoomId();
		server.addEventListener(
			requestHeardlogEventName,
			Object.class,
			new DashboardEventListenerBuilder<>(
				GatewayFunctions.class,
				requestHeardlogEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						final ResponseHeardLog heardLog = createHeardLog(handler);

						sendResponseHeardLog(client, handler, heardLog);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		final String requestReflectorHostsEventName =
			"request_reflectorhosts_" + handler.getWebSocketRoomId();
		server.addEventListener(
			requestReflectorHostsEventName,
			Object.class,
			new DashboardEventListenerBuilder<>(
				GatewayFunctions.class,
				requestReflectorHostsEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						final List<ReflectorHostInfo> hosts = handler.getReflectorHosts();
						if(hosts == null) {return;}

						sendResponseReflectorHosts(client, handler, hosts);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	public static boolean notifyStatusChanged(
		@NonNull SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler
	) {
		return sendUpdateStatusData(server, handler);
	}

	public static boolean notifyUpdateHeard(
		@NonNull SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler,
		@NonNull final org.jp.illg.dstar.model.HeardEntry e
	) {
		final HeardEntry entry = convertHeardEntry(e);

		final String eventName = "notify_updateheard_" + WebSocketTool.formatRoomId(handler.getGatewayCallsign());

		for(
			final SocketIOClient client :
			server.getRoomOperations(
				WebSocketTool.formatRoomId(handler.getGatewayCallsign())
			).getClients()
		) {
			client.sendEvent(eventName, entry);
		}

		return true;
	}

	public static boolean notifyReflectorHostsUpdated(
		@NonNull SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler,
		@NonNull List<ReflectorHostInfo> updateHosts
	){
		final String eventName =
			"notify_update_reflectorhosts_" + WebSocketTool.formatRoomId(handler.getGatewayCallsign());

		for(
			final SocketIOClient client :
			server.getRoomOperations(
				WebSocketTool.formatRoomId(handler.getGatewayCallsign())
			).getClients()
		) {
			client.sendEvent(eventName, updateHosts);
		}

		return true;
	}

	private static void sendResponseReflectorHosts(
		final SocketIOClient client,
		final WebRemoteControlGatewayHandler handler,
		final List<ReflectorHostInfo> hosts
	) {
		client.sendEvent(
			"response_reflectorhosts_" + handler.getWebSocketRoomId(),
			hosts
		);
	}

	private static void sendResponseHeardLog(
		final SocketIOClient client, final WebRemoteControlGatewayHandler handler, final ResponseHeardLog heardLog
	) {
		client.sendEvent(
			"response_heardlog_" + handler.getWebSocketRoomId(),
			heardLog
		);
	}

	private static ResponseHeardLog createHeardLog(final WebRemoteControlGatewayHandler handler) {
		final List<org.jp.illg.dstar.model.HeardEntry> logs = handler.requestHeardLog();
		if(logs == null) {return null;}

		final HeardEntry[] heardLogs = new HeardEntry[logs.size()];
		int index = 0;
		for(final org.jp.illg.dstar.model.HeardEntry e : logs) {
			heardLogs[index++] = convertHeardEntry(e);
		}

		return new ResponseHeardLog(heardLogs);
	}

	private static HeardEntry convertHeardEntry(final org.jp.illg.dstar.model.HeardEntry e) {
		final HeardEntry entry = new HeardEntry();

		entry.setProtocol(e.getProtocol() != null ? e.getProtocol().toString() : "");
		entry.setDirection(e.getDirection() != null ? e.getDirection().toString() : "");
		entry.setState(e.getState() != null ? e.getState().toString() : "");
		entry.setHeardTime(e.getHeardTime() != 0 ? e.getHeardTime() / 1000 : 0);
		entry.setRepeater1Callsign(
			e.getRepeater1Callsign() != null ? e.getRepeater1Callsign() : DSTARDefines.EmptyLongCallsign
		);
		entry.setRepeater2Callsign(
			e.getRepeater2Callsign() != null ? e.getRepeater2Callsign() : DSTARDefines.EmptyLongCallsign
		);
		entry.setYourCallsign(
			e.getYourCallsign() != null ? e.getYourCallsign() : DSTARDefines.EmptyLongCallsign
		);
		entry.setMyCallsignLong(
			e.getMyCallsignLong() != null ? e.getMyCallsignLong() : DSTARDefines.EmptyLongCallsign
		);
		entry.setMyCallsignShort(
			e.getMyCallsignShort() != null ? e.getMyCallsignShort() : DSTARDefines.EmptyShortCallsign
		);
		entry.setShortMessage(
			e.getShortMessage() != null ? e.getShortMessage() : DSTARDefines.EmptyDvShortMessage
		);
		entry.setLocationAvailable(e.isLocationAvailable());
		entry.setLatitude(e.getLatitude());
		entry.setLongitude(e.getLongitude());
		entry.setDestination(
			e.getDestination() != null ? e.getDestination() : DSTARDefines.EmptyLongCallsign
		);
		entry.setFrom(
			e.getFrom() != null ? e.getFrom() : DSTARDefines.EmptyLongCallsign
		);

		entry.setPacketCount(e.getPacketCount());
		entry.setPacketDropRate(e.getPacketDropRate());
		entry.setBitErrorRate(e.getBitErrorRate());

		return entry;
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler
	) {
		return sendUpdateStatusData(
			server,
			handler,
			server.getRoomOperations(handler.getWebSocketRoomId()).getClients()
		);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler handler,
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
		@NonNull final WebRemoteControlGatewayHandler handler,
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
		@NonNull final WebRemoteControlGatewayHandler handler,
		@NonNull final SocketIOClient client,
		final GatewayStatusData status
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
		final WebRemoteControlGatewayHandler handler
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
		final WebRemoteControlGatewayHandler handler,
		final Collection<SocketIOClient> clients
	) {
		final GatewayStatusData status = handler.createStatusData();
		boolean success = true;
		for(final SocketIOClient client : clients) {
			if(!sendUpdateStatusData(server, handler, client, status))
				success = false;
		}

		return success;
	}

	private static boolean sendResponseStatusData(
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler handler,
		final SocketIOClient client,
		final GatewayStatusData status
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
		final WebRemoteControlGatewayHandler handler,
		final SocketIOClient client,
		final String header,
		final GatewayStatusData status
	) {
		client.sendEvent(
			header + "_status_" + handler.getWebSocketRoomId(),
			status != null ? status : handler.createStatusData()
		);

		return true;
	}
}
