package org.jp.illg.dstar.routing;

import java.lang.reflect.Constructor;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingServiceFactory {

	private static final String logHeader;

	static {
		logHeader = RoutingServiceFactory.class.getSimpleName() + " : ";
	}

	public static RoutingService createRoutingService(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final RoutingServiceTypes routingServiceType,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		final SocketIO socketIO
	) {
		if(gateway == null || routingServiceType == null) {return null;}

		RoutingService routingService =
			createServiceInstance(
				systemID,
				gateway, workerExecutor, routingServiceType,
				applicationVersion,
				eventListener,
				socketIO
			);
		if(routingService == null) {
			routingService = createServiceInstance(
				systemID,
				gateway, workerExecutor, routingServiceType,
				applicationVersion,
				eventListener
			);
		}

		if(routingService == null) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not create instance for reflector communication service.");

			return null;
		}

		return routingService;

	}

	private static RoutingService createServiceInstance(
		final UUID systemID,
		final DSTARGateway gateway,
		final ExecutorService workerExecutor,
		final RoutingServiceTypes routingServiceType,
		final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener
	) {
		return createServiceInstance(
			systemID,
			gateway, workerExecutor, routingServiceType,
			applicationVersion,
			eventListener,
			null
		);
	}

	private static RoutingService createServiceInstance(
		@NonNull final UUID systemID,
		final DSTARGateway gateway,
		final ExecutorService workerExecutor,
		final RoutingServiceTypes routingServiceType,
		final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		final SocketIO socketIO
	) {
		RoutingService routingService = null;
		try {
			@SuppressWarnings("unchecked")
			final Class<? extends RoutingService> routingServiceClassObj =
				(Class<? extends RoutingService>) Class.forName(routingServiceType.getClassName());

			if(socketIO != null) {
				final Constructor<? extends RoutingService> constructor =
					routingServiceClassObj.getConstructor(
						UUID.class,
						ThreadUncaughtExceptionListener.class,
						DSTARGateway.class,
						ExecutorService.class,
						ApplicationInformation.class,
						EventListener.class,
						SocketIO.class
					);

				routingService =
					constructor.newInstance(
						systemID,
						gateway, gateway, workerExecutor,
						applicationVersion,
						eventListener,
						socketIO
					);
			}
			else {
				final Constructor<? extends RoutingService> constructor =
					routingServiceClassObj.getConstructor(
						UUID.class,
						ThreadUncaughtExceptionListener.class,
						DSTARGateway.class,
						ExecutorService.class,
						ApplicationInformation.class,
						EventListener.class
					);

				routingService = constructor.newInstance(
					systemID,
					gateway, gateway, workerExecutor,
					applicationVersion,
					eventListener
				);
			}

		}catch(Exception ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not load routing service class..." + routingServiceType.getClassName() + "]", ex);

			return null;
		}

		return routingService;
	}
}
