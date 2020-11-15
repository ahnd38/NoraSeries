package org.jp.illg.dstar.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.routing.define.RoutingServiceEvent;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RoutingServiceManager {

	private static final String logHeader;

	private static Map<UUID, Map<RoutingServiceTypes, RoutingService>> systemServices;
	private static final Lock servicesLocker;
	private static final Condition serviceRemoveComplete;
	private static boolean isServiceRemoving;
	private static final Condition allServiceRemoveComplete;
	private static boolean isAllServiceRemoving;

	static {
		logHeader = RoutingServiceManager.class.getSimpleName() + " : ";

		systemServices = new HashMap<>();
		servicesLocker = new ReentrantLock();
		serviceRemoveComplete = servicesLocker.newCondition();
		isServiceRemoving = false;
		allServiceRemoveComplete = servicesLocker.newCondition();
		isAllServiceRemoving = false;
	}

	private RoutingServiceManager() {}

	public static RoutingService getService(
		@NonNull final UUID systemID,
		@NonNull final RoutingServiceTypes routingServiceTypes
	) {
		servicesLocker.lock();
		try {
			final Map<RoutingServiceTypes, RoutingService> services = systemServices.get(systemID);

			return services != null ? services.get(routingServiceTypes) : null;
		}finally {servicesLocker.unlock();}
	}

	public static List<RoutingService> getServices(
		@NonNull final UUID systemID
	) {
		servicesLocker.lock();
		try {
			final Map<RoutingServiceTypes, RoutingService> services = systemServices.get(systemID);

			return services != null ? new ArrayList<>(services.values()) : Collections.emptyList();
		}finally {servicesLocker.unlock();}
	}

	public static boolean isEnableService(
		@NonNull final UUID systemID,
		@NonNull final RoutingServiceTypes routingServiceType
	) {
		servicesLocker.lock();
		try {
			return getService(systemID, routingServiceType) != null;
		}finally {servicesLocker.unlock();}
	}

	public static RoutingService createService(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final RoutingServiceProperties routingServiceProperties,
		@NonNull final ApplicationInformation<?> applicationVersion,
		@NonNull final EventListener<RoutingServiceEvent> eventListener,
		@NonNull final SocketIO socketIO
	) {
		RoutingServiceTypes serviceType =
			RoutingServiceTypes.getTypeByTypeName(routingServiceProperties.getType());
		if(serviceType == null || serviceType == RoutingServiceTypes.Unknown) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Unknown routing service type " + routingServiceProperties.getType() + ".");

			return null;
		}

		servicesLocker.lock();
		try {
			Map<RoutingServiceTypes, RoutingService> services = systemServices.get(systemID);
			if(services == null) {
				services = new HashMap<>();

				systemServices.put(systemID, services);
			}
			else if(services.containsKey(serviceType)) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not create duplicate routing service, " + serviceType.getTypeName() + ".");

				return null;
			}

			final RoutingService routingService =
				RoutingServiceFactory.createRoutingService(
					systemID,
					gateway, workerExecutor, serviceType,
					applicationVersion,
					eventListener,
					socketIO
				);

			if(routingService == null) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not create instance for reflector communication service.");

				return null;
			}

			routingService.setGatewayCallsign(gateway.getGatewayCallsign());

			if(!routingService.setProperties(routingServiceProperties)) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Failed set configuration to routing service " + routingService.getServiceType() + ".");

				return null;
			}

			services.put(serviceType, routingService);

			return routingService;
		}finally {servicesLocker.unlock();}
	}

	public static boolean removeService(
		@NonNull final UUID systemID,
		@NonNull final RoutingServiceTypes routingServiceType,
		final boolean stopRoutingService
	) {
		boolean isRemoveSuccess;
		Map<RoutingServiceTypes, RoutingService> services;
		RoutingService targetRoutingService;

		servicesLocker.lock();
		try {
			if(isServiceRemoving) {
				try {
					if(!serviceRemoveComplete.await(10, TimeUnit.SECONDS)) {
						if(log.isErrorEnabled())
							log.error(logHeader + "Could not remove service = " + routingServiceType + ", Remove timeout.");

						return false;
					}
				}catch(InterruptedException ex) {
					return false;
				}
			}

			try {
				services = systemServices.get(systemID);
				if(services == null) {
					isServiceRemoving = false;
					return false;
				}

				targetRoutingService = services.get(routingServiceType);
				if(targetRoutingService == null) {
					isServiceRemoving = false;
					return false;
				}
			}catch(Exception ex) {
				isServiceRemoving = false;

				return false;
			}

			isServiceRemoving = true;
		}
		finally {servicesLocker.unlock();}

		try {
			if(stopRoutingService) {targetRoutingService.stop();}

			servicesLocker.lock();
			try {
				isRemoveSuccess = services.remove(routingServiceType) != null;
			}
			finally {
				servicesLocker.unlock();
			}
		}
		finally {
			servicesLocker.lock();
			try {
				isServiceRemoving = false;
				serviceRemoveComplete.signal();
			}
			finally {
				servicesLocker.unlock();
			}
		}

		return isRemoveSuccess;
	}

	public static boolean removeServices(
		@NonNull final UUID systemID,
		final boolean stopRoutingService
	) {
		servicesLocker.lock();
		try {
			if(isAllServiceRemoving) {
				try {
					if(!allServiceRemoveComplete.await(60, TimeUnit.SECONDS)) {
						if(log.isErrorEnabled())
							log.error(logHeader + "Could not remove service, Remove process timeout.");

						return false;
					}
				}catch(InterruptedException ex) {
					return false;
				}
			}

			isAllServiceRemoving = true;
			try {
				final Map<RoutingServiceTypes, RoutingService> services = systemServices.get(systemID);
				if(services == null) {return false;}

				final List<RoutingServiceTypes> removeServices = new ArrayList<>(services.keySet());

				boolean isRemoveSuccess = true;
				for(Iterator<RoutingServiceTypes> it = removeServices.iterator(); it.hasNext();) {
					RoutingServiceTypes removeType = it.next();
					it.remove();

					isRemoveSuccess &= removeService(systemID, removeType, stopRoutingService);
				}

				return systemServices.remove(systemID) != null & isRemoveSuccess;
			}finally {
				isAllServiceRemoving = false;
				allServiceRemoveComplete.signal();
			}
		}finally {servicesLocker.unlock();}
	}

	public static void finalizeSystem(final @NonNull UUID systemID) {
		removeServices(systemID, true);
	}

	public static void finalizeManager() {
		servicesLocker.lock();
		try {
			final List<UUID> removeSystems = new ArrayList<>(systemServices.keySet());

			for(final UUID systemID : removeSystems)
				removeServices(systemID, true);
		}finally {
			servicesLocker.unlock();
		}
	}

	/**
	 * 指定レピータのルーティングサービスを変更する
	 *
	 * @param repeater 対象レピータ
	 * @param routingService 変更したいルーティングサービス
	 * @return 正常に変更が完了した場合にはtrue
	 */
	public static boolean changeRoutingService(
		DSTARRepeater repeater,
		RoutingService routingService
	) {

		final boolean validConfig =
			!repeater.isRoutingServiceFixed() ||
			(repeater.isRoutingServiceFixed() && repeater.getRoutingService() == null);

		final boolean validModule = checkRoutingServiceModuleBlacklist(repeater, routingService);

		if(validConfig && validModule) {
			repeater.setRoutingService(routingService);

			return true;
		}
		else if(!validConfig){
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					"Failed to change routing service, disabled by config. [Repeater=" + repeater.getRepeaterCallsign() + "]"
				);
			}
		}
		else if(!validModule) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Failed change routing service, Illegal repeater module " + repeater.getRepeaterCallsign() + "." +
					"Routing service " + routingService.getServiceType().getTypeName() + " is not accept module " +
					routingService.getServiceType().getModuleBlacklist()
				);
			}
		}

		return false;
	}

	/**
	 * 指定されたルーティングサービスに変更可能であるか、
	 * モジュールブラックリストから照合する
	 *
	 * @param repeater レピータ
	 * @param service ルーティングサービス
	 * @return 指定されたルーティングサービスに変更可能であればtrue
	 */
	public static boolean checkRoutingServiceModuleBlacklist(
		@NonNull final DSTARRepeater repeater,
		@NonNull final RoutingService service
	) {
		final String repeaterCallsign = repeater.getRepeaterCallsign();
		if(
			!CallSignValidator.isValidRepeaterCallsign(repeaterCallsign) ||
			repeaterCallsign.length() < DSTARDefines.CallsignFullLength
		)
			return false;

		final char repeaterModule = repeaterCallsign.charAt(DSTARDefines.CallsignFullLength - 1);

		return !service.getServiceType().getModuleBlacklist().contains(String.valueOf(repeaterModule));
	}
}
