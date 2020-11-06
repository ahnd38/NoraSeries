package org.jp.illg.dstar.service.web.func;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRepeaterHandler;
import org.jp.illg.dstar.service.web.model.ReflectorLinkControl;
import org.jp.illg.dstar.service.web.model.RepeaterStatusData;
import org.jp.illg.dstar.service.web.model.WebRemoteControlErrorCode;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.google.gson.Gson;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RepeaterFunctions {

	protected RepeaterFunctions() {}

	public static void notifyStatusChanged(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRepeaterHandler handler
	) {
		sendUpdateStatusData(server, handler);
	}

	protected static boolean setupReflectorHostsFunction(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final Class<?> functionClass,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlRepeaterHandler repeaterHandler
	) {
		final String logTag =
			RepeaterFunctions.class.getSimpleName() +
			"(" + functionClass.getSimpleName() + ") : ";

		final String requestReflectorLinkEventName =
			"request_reflectorhosts_" + repeaterHandler.getWebSocketRoomId();
		final String responseReflectorLinkEventName =
			"response_reflectorhosts_" + repeaterHandler.getWebSocketRoomId();
		server.addEventListener(
			requestReflectorLinkEventName,
			Object.class,
			new DashboardEventListenerBuilder<>(
				functionClass,
				requestReflectorLinkEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(
						SocketIOClient client, Object data, AckRequest ackSender
					) {
						final List<ReflectorHostInfo> hosts = gatewayHandler.getReflectorHosts();
						if(hosts == null) {return;}

						if(log.isTraceEnabled()) {
							log.trace(
								logTag +
								"Send response " + responseReflectorLinkEventName + "/" + hosts.size() + " hosts."
							);
						}

						client.sendEvent(
							responseReflectorLinkEventName,
							hosts
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	protected static boolean setupReflectorControlFunction(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final Class<?> functionClass,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlRepeaterHandler repeaterHandler
	) {
		final String logTag =
			RepeaterFunctions.class.getSimpleName() +
			"(" + functionClass.getSimpleName() + ") : ";

		return
			setupReflectorControlLinkFunction(
				exceptionListener, functionClass, server, gatewayHandler, repeaterHandler, logTag
			) &&
			setupReflectorControlUnlinkFunction(
				exceptionListener, functionClass, server, gatewayHandler, repeaterHandler, logTag
			) &&
			setupEventRequestQueryReflector(
				exceptionListener, functionClass, server, gatewayHandler, repeaterHandler
			);
	}

	protected static boolean setupReflectorControlLinkFunction(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler gatewayHandler,
		final WebRemoteControlRepeaterHandler repeaterHandler,
		final String logTag
	) {
		final String requestReflectorLinkEventName =
			"request_reflector_link_" + repeaterHandler.getWebSocketRoomId();
		final String responseReflectorLinkEventName =
			"response_reflector_link_" + repeaterHandler.getWebSocketRoomId();
		server.addEventListener(
			requestReflectorLinkEventName,
			ReflectorLinkControl.class,
			new DashboardEventListenerBuilder<>(functionClass, requestReflectorLinkEventName,
				new DashboardEventListener<ReflectorLinkControl>() {
					@Override
					public void onEvent(
						SocketIOClient client, ReflectorLinkControl data, AckRequest ackSender
					) {
						final String reflectorCallsign =
							data != null ? data.getReflectorCallsign() : DSTARDefines.EmptyLongCallsign;

						final String repeaterCallsign =
							data != null ? data.getRepeaterCallsign() : DSTARDefines.EmptyLongCallsign;

						final DSTARRepeater targetRepeater = gatewayHandler.getRepeater(repeaterCallsign);
						if(targetRepeater == null) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not link to reflector, Illegal repeater callsign = " + targetRepeater + "."
								);
							}

							client.sendEvent(
								responseReflectorLinkEventName,
								new ReflectorLinkControl(reflectorCallsign, repeaterCallsign, false)
							);

							return;
						}

						final ReflectorLinkManager manager = gatewayHandler.getReflectorLinkManager();
						if(manager == null) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not link to reflector, Relector link manager is must not null."
								);
							}

							client.sendEvent(
								responseReflectorLinkEventName,
								new ReflectorLinkControl(reflectorCallsign, repeaterCallsign, false)
							);

							return;
						}

						final Optional<ReflectorHostInfo> reflectorInfo =
							gatewayHandler.findReflectorByCallsign(reflectorCallsign);
						if(!reflectorInfo.isPresent()) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not link to reflector, Relector " + reflectorCallsign + " is not found."
								);
							}

							client.sendEvent(
								responseReflectorLinkEventName,
								new ReflectorLinkControl(reflectorCallsign, repeaterCallsign, false)
							);

							return;
						}

						if(
							manager.linkReflector(
								targetRepeater, reflectorCallsign, reflectorInfo.get()
							)
						) {
							client.sendEvent(
								responseReflectorLinkEventName,
								new ReflectorLinkControl(reflectorCallsign, repeaterCallsign, true)
							);
						}
						else {
							if(log.isErrorEnabled())
								log.error(logTag + "Failed to link reflector.");

							client.sendEvent(
								responseReflectorLinkEventName,
								new ReflectorLinkControl(reflectorCallsign, repeaterCallsign, false)
							);
						}
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	protected static boolean setupReflectorControlUnlinkFunction(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler gatewayHandler,
		final WebRemoteControlRepeaterHandler repeaterHandler,
		final String logTag
	) {
		final String requestReflectorUnlinkEventName =
			"request_reflector_unlink_" + repeaterHandler.getWebSocketRoomId();
		final String responseReflectorUnlinkEventName =
			"response_reflector_unlink_" + repeaterHandler.getWebSocketRoomId();
		server.addEventListener(
			requestReflectorUnlinkEventName,
			ReflectorLinkControl.class,
			new DashboardEventListenerBuilder<>(
				functionClass,
				requestReflectorUnlinkEventName,
				new DashboardEventListener<ReflectorLinkControl>() {
					@Override
					public void onEvent(
						SocketIOClient client, ReflectorLinkControl data, AckRequest ackSender
					) {
						final String repeaterCallsign =
							data != null ? data.getRepeaterCallsign() : DSTARDefines.EmptyLongCallsign;

						final DSTARRepeater targetRepeater = gatewayHandler.getRepeater(repeaterCallsign);
						if(targetRepeater == null) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not unlink from reflector, Illegal repeater callsign = " + targetRepeater + "."
								);
							}

							client.sendEvent(
								responseReflectorUnlinkEventName,
								new ReflectorLinkControl(DSTARDefines.EmptyLongCallsign, repeaterCallsign, false)
							);

							return;
						}

						final ReflectorLinkManager manager = gatewayHandler.getReflectorLinkManager();
						if(manager == null) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not unlink from reflector, Relector link manager is must not null."
								);
							}

							client.sendEvent(
								responseReflectorUnlinkEventName,
								new ReflectorLinkControl(DSTARDefines.EmptyLongCallsign, repeaterCallsign, false)
							);

							return;
						}

						final String linkedReflectorCallsign = targetRepeater.getLinkedReflectorCallsign();
						if(
							linkedReflectorCallsign == null || "".equals(linkedReflectorCallsign) ||
							DSTARDefines.EmptyLongCallsign.equals(linkedReflectorCallsign) ||
							!manager.isReflectorLinked(targetRepeater, ConnectionDirectionType.OUTGOING)
						) {
							if(log.isErrorEnabled()) {
								log.error(
									logTag +
									"Could not unlink from reflector, Repeater " + targetRepeater.getRepeaterCallsign() + " is not linked to reflector."
								);
							}

							client.sendEvent(
								responseReflectorUnlinkEventName,
								new ReflectorLinkControl(DSTARDefines.EmptyLongCallsign, repeaterCallsign, false)
							);

							return;
						}

						if(manager.unlinkReflector(targetRepeater)) {
							client.sendEvent(
								responseReflectorUnlinkEventName,
								new ReflectorLinkControl(DSTARDefines.EmptyLongCallsign, repeaterCallsign, true)
							);
						}
						else {
							if(log.isErrorEnabled())
								log.error(logTag + "Failed to unlink from reflector.");

							client.sendEvent(
								responseReflectorUnlinkEventName,
								new ReflectorLinkControl(DSTARDefines.EmptyLongCallsign, repeaterCallsign, false)
							);
						}
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static boolean setupEventRequestQueryReflector(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler gatewayHandler,
		final WebRemoteControlRepeaterHandler handler
	) {
		final String requestEventName =
			"request_query_reflector_" + handler.getWebSocketRoomId();
		final String responseEventName =
			"response_query_reflector_" + handler.getWebSocketRoomId();

		server.addEventListener(
			requestEventName,
			Properties.class,
			new DashboardEventListenerBuilder<>(functionClass, requestEventName,
				new DashboardEventListener<Properties>() {
					@Override
					public void onEvent(
						final SocketIOClient client, final Properties data, final AckRequest ackSender
					) {
						if(data == null) {return;}

						final String queryText = data.getProperty("query_text");
						final List<ReflectorHostInfo> result =
							queryText != null ? gatewayHandler.findReflectorByFullText(queryText, 50) : Collections.emptyList();

						final Properties prop = new Properties();
						prop.setProperty("result", String.valueOf(true));
						prop.setProperty("error_code", String.valueOf(WebRemoteControlErrorCode.NoError.getErrorCode()));
						prop.setProperty("error_message", WebRemoteControlErrorCode.NoError.getMessage());
						prop.setProperty("query_result", new Gson().toJson(result, List.class));

						client.sendEvent(responseEventName, prop);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	protected static boolean setupStatusFunction(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final Class<?> functionClass,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRepeaterHandler handler
	) {
		return setupEventRequestStatus(exceptionListener, functionClass, server, handler);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRepeaterHandler handler
	) {
		return sendUpdateStatusData(
			server,
			handler,
			server.getRoomOperations(handler.getWebSocketRoomId()).getClients()
		);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRepeaterHandler handler,
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
		@NonNull final WebRemoteControlRepeaterHandler handler,
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
		@NonNull final WebRemoteControlRepeaterHandler handler,
		@NonNull final SocketIOClient client,
		final RepeaterStatusData status
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
		final WebRemoteControlRepeaterHandler handler
	) {
		final String requestStatusEventName =
			"request_status_" + handler.getWebSocketRoomId();
		server.addEventListener(
			requestStatusEventName,
			Object.class,
			new DashboardEventListenerBuilder<>(
				functionClass, requestStatusEventName,
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
		final WebRemoteControlRepeaterHandler handler,
		final Collection<SocketIOClient> clients
	) {
		final RepeaterStatusData status = handler.createStatusData();
		boolean success = true;
		for(final SocketIOClient client : clients) {
			if(!sendUpdateStatusData(server, handler, client, status))
				success = false;
		}

		return success;
	}

	private static boolean sendResponseStatusData(
		final SocketIOServer server,
		final WebRemoteControlRepeaterHandler handler,
		final SocketIOClient client,
		final RepeaterStatusData status
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
		final WebRemoteControlRepeaterHandler handler,
		final SocketIOClient client,
		final String header,
		final RepeaterStatusData status
	) {
		client.sendEvent(
			header + "_status_" + handler.getWebSocketRoomId(),
			status != null ? status : handler.createStatusData()
		);

		return true;
	}
}
