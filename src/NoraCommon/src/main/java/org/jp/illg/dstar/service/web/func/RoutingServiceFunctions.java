package org.jp.illg.dstar.service.web.func;

import java.util.Collection;
import java.util.UUID;

import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceResult;
import org.jp.illg.dstar.routing.model.RepeaterRoutingInfo;
import org.jp.illg.dstar.routing.model.RoutingInfo;
import org.jp.illg.dstar.routing.model.UserRoutingInfo;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlGatewayHandler;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlRoutingServiceHandler;
import org.jp.illg.dstar.service.web.model.RequestQuery;
import org.jp.illg.dstar.service.web.model.ResultQuery;
import org.jp.illg.dstar.service.web.model.RoutingServiceStatusData;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.thread.Callback;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.google.common.util.concurrent.RateLimiter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingServiceFunctions {

	private static final String logTag =
		RoutingServiceFunctions.class.getSimpleName() + " : ";

	private static RateLimiter queryRateLimitter = RateLimiter.create(0.1);

	protected RoutingServiceFunctions() {}

	public static void notifyStatusChanged(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRoutingServiceHandler handler
	) {
		sendUpdateStatusData(server, handler);
	}

	protected static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull Class<?> functionClass,
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlGatewayHandler gatewayHandler,
		@NonNull final WebRemoteControlRoutingServiceHandler handler
	) {
		return
			setupEventRequestStatus(exceptionListener, functionClass, server, handler) &&
			setupEventRequestQueryUser(exceptionListener, functionClass, server, gatewayHandler, handler) &&
			setupEventRequestQueryRepeater(exceptionListener, functionClass, server, gatewayHandler, handler);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRoutingServiceHandler handler
	) {
		return sendUpdateStatusData(
			server,
			handler,
			server.getRoomOperations(handler.getWebSocketRoomId()).getClients()
		);
	}

	protected static boolean sendUpdateStatusData(
		@NonNull final SocketIOServer server,
		@NonNull final WebRemoteControlRoutingServiceHandler handler,
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
		@NonNull final WebRemoteControlRoutingServiceHandler handler,
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
		@NonNull final WebRemoteControlRoutingServiceHandler handler,
		@NonNull final SocketIOClient client,
		final RoutingServiceStatusData status
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
		final WebRemoteControlRoutingServiceHandler handler
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

	private static boolean setupEventRequestQueryUser(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler gatewayHandler,
		final WebRemoteControlRoutingServiceHandler handler
	) {
		final String requestUserQueryEventName =
			"request_query_user_" + handler.getWebSocketRoomId();
		final String responseUserQueryEventName =
			"response_query_user_" + handler.getWebSocketRoomId();

		server.addEventListener(
			requestUserQueryEventName,
			RequestQuery.class,
			new DashboardEventListenerBuilder<>(functionClass, requestUserQueryEventName,
				new DashboardEventListener<RequestQuery>() {
					@Override
					public void onEvent(
						final SocketIOClient client, final RequestQuery data, final AckRequest ackSender
					) {

						data.setQueryCallsign(DSTARUtils.formatFullLengthCallsign(data.getQueryCallsign()));

						final RoutingServiceTypes routingServiceType =
							RoutingServiceTypes.getTypeByTypeNameIgnoreCase(data.getRoutingServiceType());

						if(!CallSignValidator.isValidUserCallsign(data.getQueryCallsign())) {
							sendResultQueryFailed(
								client,
								responseUserQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Illegal user callsign " + (data.getQueryCallsign() != null ? data.getQueryCallsign() : "")
							);

							return;
						}
						else if(routingServiceType == null || routingServiceType == RoutingServiceTypes.Unknown) {
							sendResultQueryFailed(
								client,
								responseUserQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Routing service " + routingServiceType + " is unavailable."
							);

							return;
						}
						else if(!queryRateLimitter.tryAcquire()) {
							sendResultQueryFailed(
								client,
								responseUserQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Too many executing query, please wait a while and try again."
							);

							return;
						}

						final UUID queryId =
							gatewayHandler.findUser(
								routingServiceType,
								new Callback<RoutingInfo>() {
									@Override
									public void call(RoutingInfo attachData) {
										if(!(attachData instanceof UserRoutingInfo)) {
											sendResultQueryFailed(
												client,
												responseUserQueryEventName,
												data.getQueryCallsign(),
												routingServiceType,
												"System error, result is not user routing info."
											);

											throw new RuntimeException();
										}

										sendResultQuery(
											client,
											responseUserQueryEventName,
											routingServiceType,
											data.getQueryCallsign(),
											(UserRoutingInfo)attachData,
											gatewayHandler
										);
									}
								},
								data.getQueryCallsign()
							);

						if(queryId == null) {
							sendResultQueryFailed(
								client,
								responseUserQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Routing service " + routingServiceType + " did not return query id."
							);

							return;
						}

						if(log.isInfoEnabled()) {
							log.info(
								logTag +
								"Execute query user callsign " + data.getQueryCallsign() +
								" from request by dashboard user " + client.getSessionId()
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

	private static boolean setupEventRequestQueryRepeater(
		final ThreadUncaughtExceptionListener exceptionListener,
		final Class<?> functionClass,
		final SocketIOServer server,
		final WebRemoteControlGatewayHandler gatewayHandler,
		final WebRemoteControlRoutingServiceHandler handler
	) {
		final String requestRepeaterQueryEventName =
			"request_query_repeater_" + handler.getWebSocketRoomId();
		final String responseRepeaterQueryEventName =
			"response_query_repeater_" + handler.getWebSocketRoomId();

		server.addEventListener(
			requestRepeaterQueryEventName,
			RequestQuery.class,
			new DashboardEventListenerBuilder<>(functionClass, requestRepeaterQueryEventName,
				new DashboardEventListener<RequestQuery>() {
					@Override
					public void onEvent(
						final SocketIOClient client, final RequestQuery data, final AckRequest ackSender
					) {

						data.setQueryCallsign(DSTARUtils.formatFullLengthCallsign(data.getQueryCallsign()));

						final RoutingServiceTypes routingServiceType =
							RoutingServiceTypes.getTypeByTypeNameIgnoreCase(data.getRoutingServiceType());

						if(!CallSignValidator.isValidRepeaterCallsign(data.getQueryCallsign())) {
							sendResultQueryFailed(
								client,
								responseRepeaterQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Illegal repeater callsign " + (data.getQueryCallsign() != null ? data.getQueryCallsign() : "")
							);

							return;
						}
						else if(routingServiceType == null || routingServiceType == RoutingServiceTypes.Unknown) {
							sendResultQueryFailed(
								client,
								responseRepeaterQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Routing service " + routingServiceType + " is unavailable."
							);

							return;
						}
						else if(!queryRateLimitter.tryAcquire()) {
							sendResultQueryFailed(
								client,
								responseRepeaterQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Too many executing query, please wait a while and try again."
							);

							return;
						}

						final UUID queryId =
							gatewayHandler.findRepeater(
								routingServiceType,
								new Callback<RoutingInfo>() {
									@Override
									public void call(RoutingInfo attachData) {
										if(!(attachData instanceof RepeaterRoutingInfo)) {
											sendResultQueryFailed(
												client,
												responseRepeaterQueryEventName,
												data.getQueryCallsign(),
												routingServiceType,
												"System error, result is not user routing info."
											);

											throw new RuntimeException();
										}

										sendResultQuery(
											client,
											responseRepeaterQueryEventName,
											routingServiceType,
											data.getQueryCallsign(),
											(RepeaterRoutingInfo)attachData,
											gatewayHandler
										);
									}
								},
								data.getQueryCallsign()
							);

						if(queryId == null) {
							sendResultQueryFailed(
								client,
								responseRepeaterQueryEventName,
								data.getQueryCallsign(),
								routingServiceType,
								"Routing service " + routingServiceType + " did not return query id."
							);

							return;
						}

						if(log.isInfoEnabled()) {
							log.info(
								logTag +
								"Execute query repeater callsign " + data.getQueryCallsign() +
								" from request by dashboard user " + client.getSessionId()
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

	private static void sendResultQuery(
		final SocketIOClient client,
		final String responseEventName,
		final RoutingServiceTypes routingServiceType,
		final String queryCallsign,
		final UserRoutingInfo queryResult,
		final WebRemoteControlGatewayHandler gatewayHandler
	) {
		final ResultQuery result = new ResultQuery();
		result.setResult(
			queryResult.getRoutingResult() != null ?
				queryResult.getRoutingResult().toString() : RoutingServiceResult.Failed.toString()
		);
		result.setRoutingServiceType(routingServiceType.toString());
		result.setQueryCallsign(queryCallsign);

		if(queryResult.getRoutingResult() == RoutingServiceResult.Success) {
			result.setAreaRepeaterCallsign(queryResult.getRepeaterCallsign());
			result.setZoneRepeaterCallsign(queryResult.getGatewayCallsign());
			result.setGatewayIpAddress(queryResult.getGatewayAddress().getHostAddress());
			result.setGatewayHostName(queryResult.getGatewayAddress().getHostName());
			result.setTimestamp(queryResult.getTimestamp() > 0 ? queryResult.getTimestamp() / 1000 : 0);

			result.setRepeaterName(gatewayHandler.getRepeaterNameService().findRepeaterName(
				queryResult.getRepeaterCallsign())
			);
		}
		else if(queryResult.getRoutingResult() == RoutingServiceResult.NotFound) {
			result.setMessage("User " + queryCallsign + " is not found.");
		}
		else {
			result.setMessage("Routing service " + routingServiceType + " return query failed.");
		}

		client.sendEvent(responseEventName, result);
	}

	private static void sendResultQuery(
		final SocketIOClient client,
		final String responseEventName,
		final RoutingServiceTypes routingServiceType,
		final String queryCallsign,
		final RepeaterRoutingInfo queryResult,
		final WebRemoteControlGatewayHandler gatewayHandler
	) {
		final ResultQuery result = new ResultQuery();
		result.setResult(
			queryResult.getRoutingResult() != null ?
				queryResult.getRoutingResult().toString() : RoutingServiceResult.Failed.toString()
		);
		result.setRoutingServiceType(routingServiceType.toString());
		result.setQueryCallsign(queryCallsign);

		if(queryResult.getRoutingResult() == RoutingServiceResult.Success) {
			result.setAreaRepeaterCallsign(queryResult.getRepeaterCallsign());
			result.setZoneRepeaterCallsign(queryResult.getGatewayCallsign());
			result.setGatewayIpAddress(queryResult.getGatewayAddress().getHostAddress());
			result.setGatewayHostName(queryResult.getGatewayAddress().getHostName());
			result.setTimestamp(queryResult.getTimestamp() > 0 ? queryResult.getTimestamp() / 1000 : 0);

			result.setRepeaterName(gatewayHandler.getRepeaterNameService().findRepeaterName(
				queryResult.getRepeaterCallsign())
			);
		}
		else if(queryResult.getRoutingResult() == RoutingServiceResult.NotFound) {
			result.setMessage("Repeater " + queryCallsign + " is not found.");
		}
		else {
			result.setMessage("Routing service " + routingServiceType + " return query failed.");
		}

		client.sendEvent(responseEventName, result);
	}

	private static void sendResultQueryFailed(
		final SocketIOClient client,
		final String responseEventName,
		final String queryCallsign,
		final RoutingServiceTypes serviceType,
		final String message
	) {
		final ResultQuery result = new ResultQuery();
		result.setResult(RoutingServiceResult.Failed.toString());
		result.setRoutingServiceType(serviceType.toString());
		result.setQueryCallsign(queryCallsign);
		result.setMessage(message);

		client.sendEvent(responseEventName, result);
	}

	private static boolean sendUpdateStatusData(
		final SocketIOServer server,
		final WebRemoteControlRoutingServiceHandler handler,
		final Collection<SocketIOClient> clients
	) {
		final RoutingServiceStatusData status = handler.createStatusData();
		boolean success = true;
		for(final SocketIOClient client : clients) {
			if(!sendUpdateStatusData(server, handler, client, status))
				success = false;
		}

		return success;
	}

	private static boolean sendResponseStatusData(
		final SocketIOServer server,
		final WebRemoteControlRoutingServiceHandler handler,
		final SocketIOClient client,
		final RoutingServiceStatusData status
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
		final WebRemoteControlRoutingServiceHandler handler,
		final SocketIOClient client,
		final String header,
		final RoutingServiceStatusData status
	) {
		client.sendEvent(
			header + "_status_" + handler.getWebSocketRoomId(),
			status != null ? status : handler.createStatusData()
		);

		return true;
	}
}
