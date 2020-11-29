package org.jp.illg.dstar.gateway;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.routing.RoutingServiceManager;
import org.jp.illg.dstar.service.reflectorname.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARGatewayManager {

	private static final String logHeader;

	private static final Lock locker;

	private static Map<UUID, DSTARGateway> systemGateways;
	private static final Condition gatewayRemoveComplete;
	private static boolean isGatewayRemoving;


	static {
		logHeader = RoutingServiceManager.class.getSimpleName() + " : ";

		locker = new ReentrantLock();

		systemGateways = new HashMap<>();
		gatewayRemoveComplete = locker.newCondition();
		isGatewayRemoving = false;
	}

	private DSTARGatewayManager() {}

	public static DSTARGateway createGateway(
		final UUID systemID,
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final String gatewayCallsign,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) {
		return createGateway(
			systemID,
			exceptionListener, gatewayCallsign,
			workerExecutor,
			null,
			applicationVersion,
			reflectorNameService,
			repeaterNameService
		);
	}

	public static DSTARGateway createGateway(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final String gatewayCallsign,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		final WebRemoteControlService webRemoteControlService,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) {
		if (!DSTARUtils.isValidCallsignFullLength(gatewayCallsign)) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Illegal gateway callsign = " + gatewayCallsign);

			return null;
		}

		locker.lock();
		try {
			DSTARGateway gateway = getGateway(systemID);
			if (gateway != null) {return null;}

			try {
				gateway = new DSTARGatewayImpl(
					systemID,
					exceptionListener, gatewayCallsign,
					workerExecutor,
					socketio,
					applicationVersion,
					reflectorNameService,
					repeaterNameService
				);
			} catch (IllegalStateException ex) {
				return null;
			}

			gateway.setWebRemoteControlService(webRemoteControlService);

			systemGateways.put(systemID, gateway);

			return gateway;
		} finally {
			locker.unlock();
		}
	}

	public static DSTARGateway getGateway(
		@NonNull final UUID systemID
	) {
		locker.lock();
		try {
			return systemGateways.get(systemID);
		}finally {
			locker.unlock();
		}
	}

	public static boolean removeGateway(
		@NonNull final UUID systemID,
		final boolean isStopRequest
	) {
		DSTARGateway gateway;

		locker.lock();
		try {
			if(isGatewayRemoving) {
				try {
					if(!gatewayRemoveComplete.await(10, TimeUnit.SECONDS)) {
						if(log.isErrorEnabled())
							log.error(logHeader + "Could not remove gateway, timeout.");

						return false;
					}
				}catch(InterruptedException ex) {
					return false;
				}
			}

			gateway = getGateway(systemID);
			if(gateway == null) {
				isGatewayRemoving = false;
				return false;
			}

			isGatewayRemoving = true;
		} finally {
			locker.unlock();
		}

		try {
			if(isGatewayRemoving) {
				gateway.stop();

				locker.lock();
				try {
					systemGateways.remove(systemID);
				}finally {
					locker.unlock();
				}
			}
		}
		finally {
			locker.lock();
			try {
				isGatewayRemoving = false;
				gatewayRemoveComplete.signal();
			}finally {
				locker.unlock();
			}
		}

		return true;
	}

	public static void finalizeSystem(final @NonNull UUID systemID) {
		removeGateway(systemID, true);
	}
}
