package org.jp.illg.dstar.reflector;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorCommunicationServiceFactory {

	private static final String logHeader;

	static {
		logHeader = ReflectorCommunicationServiceFactory.class.getSimpleName() + " : ";
	}

	public static ReflectorCommunicationService createService(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final DSTARGateway gateway,
		@NonNull final ReflectorProtocolProcessorTypes reflectorProtocolType,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketIO,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		ReflectorCommunicationService service = null;

		service = createServiceInstance(
			systemID, applicationInformation,
			gateway, reflectorProtocolType, workerExecutor, socketIO, reflectorLinkManager, eventListener
		);
		if(service == null) {
			service = createServiceInstance(
				systemID, applicationInformation,
				gateway, reflectorProtocolType, workerExecutor, reflectorLinkManager, eventListener
			);
		}

		if(service == null) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not create instance for reflector communication service.");

			return null;
		}

		return service;
	}

	private static ReflectorCommunicationService createServiceInstance(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull DSTARGateway gateway,
		@NonNull ReflectorProtocolProcessorTypes type,
		@NonNull final ExecutorService workerExecutor,
		@NonNull ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		return createServiceInstance(systemID, applicationInformation, gateway, type, null, reflectorLinkManager, eventListener);
	}

	@SuppressWarnings("unchecked")
	private static ReflectorCommunicationService createServiceInstance(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final DSTARGateway gateway,
		@NonNull final ReflectorProtocolProcessorTypes type,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketIO,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		assert gateway != null && type != null;

		ReflectorCommunicationService instance = null;

		try {
			final Class<? extends ReflectorCommunicationService> reflectorClass =
				(Class<? extends ReflectorCommunicationService>)Class.forName(type.getClassName());

			final Constructor<? extends ReflectorCommunicationService> constructor = socketIO != null ?
				reflectorClass.getConstructor(
					UUID.class,
					ApplicationInformation.class,
					ThreadUncaughtExceptionListener.class, DSTARGateway.class,
					ExecutorService.class, SocketIO.class, ReflectorLinkManager.class,
					EventListener.class
				):
				reflectorClass.getConstructor(
					UUID.class,
					ApplicationInformation.class,
					ThreadUncaughtExceptionListener.class, DSTARGateway.class,
					ExecutorService.class, ReflectorLinkManager.class,
					EventListener.class
				);

			instance = socketIO != null ?
				constructor.newInstance(
					systemID, applicationInformation, gateway, gateway, workerExecutor, socketIO, reflectorLinkManager, eventListener
				):
				constructor.newInstance(
					systemID, applicationInformation, gateway, gateway, workerExecutor, reflectorLinkManager, eventListener
				);

		}catch(
			ClassNotFoundException |
			ClassCastException |
			NoSuchMethodException |
			SecurityException |
			InstantiationException |
			IllegalAccessException |
			IllegalArgumentException |
			InvocationTargetException ex
		) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not load reflector link protocol " + type.getProtocol().toString() + ".", ex);
		}

		return instance;
	}
}
