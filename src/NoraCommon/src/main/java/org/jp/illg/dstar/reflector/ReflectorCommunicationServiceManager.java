package org.jp.illg.dstar.reflector;

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

import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.util.ObjectWrapper;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorCommunicationServiceManager {

	private static final String logHeader;

	private static final Map<UUID, Map<DSTARProtocol, ReflectorCommunicationService>> systemServices;
	private static final Lock servicesLocker;
	private static final Condition serviceRemoveComplete;
	private static boolean isServiceRemoving;
	private static final Condition allServiceRemoveComplete;
	private static boolean isAllServiceRemoving;

	private static SocketIO localSocketIO;
	private static final Lock localSocketIOLocker;

	static {
		logHeader = ReflectorCommunicationServiceManager.class.getSimpleName() + " : ";

		systemServices = new HashMap<>();
		servicesLocker = new ReentrantLock();
		serviceRemoveComplete = servicesLocker.newCondition();
		isServiceRemoving = false;
		allServiceRemoveComplete = servicesLocker.newCondition();
		isAllServiceRemoving = false;

		localSocketIOLocker = new ReentrantLock();
	}

	private ReflectorCommunicationServiceManager() {}

	public static boolean isSupportedReflectorCallsign(
		@NonNull final UUID systemID,
		@NonNull final String reflectorCallsign,
		@NonNull final DSTARProtocol protocol
	) {
		if(!CallSignValidator.isValidReflectorCallsign(reflectorCallsign))
			return false;

		final Optional<ReflectorCommunicationService> opService = getService(systemID, protocol);

		return opService.isPresent() &&
			opService.get().isSupportedReflectorCallsign(reflectorCallsign);
	}

	public static boolean isLinked(
		@NonNull final UUID systemID,
		@NonNull final DSTARRepeater repeater,
		@NonNull final DSTARProtocol protocol,
		@NonNull final ConnectionDirectionType connectionDir
	) {
		if(repeater == null || protocol == null || connectionDir == null)
			return false;

		final ObjectWrapper<Boolean> linked = new ObjectWrapper<>(false);

		getService(systemID, protocol)
		.ifPresent(new Consumer<ReflectorCommunicationService>() {
			@Override
			public void accept(ReflectorCommunicationService service) {
				if(connectionDir == ConnectionDirectionType.OUTGOING) {
					linked.setObject(service.getLinkInformationOutgoing(repeater).isPresent());
				}
				else if(connectionDir == ConnectionDirectionType.INCOMING) {
					linked.setObject(!service.getLinkInformationIncoming(repeater).isEmpty());
				}
				else {
					if(log.isWarnEnabled())
						log.warn("Not supported connection direction type = " + connectionDir.toString());
				}
			}
		});

		return linked.getObject();
	}

	public static List<ReflectorLinkInformation> getLinkInformationOutgoing(
		@NonNull final UUID systemID,
		@NonNull final DSTARRepeater repeater
	){
		final List<ReflectorLinkInformation> result = new ArrayList<>();

		for(ReflectorCommunicationService service : getServices(systemID)) {
			service.getLinkInformationOutgoing(repeater).ifPresent(new Consumer<ReflectorLinkInformation>() {
				@Override
				public void accept(ReflectorLinkInformation t) {
					result.add(t);
				}
			});
		}

		return result;
	}

	public static List<ReflectorLinkInformation> getLinkInformationIncoming(
		@NonNull final UUID systemID,
		@NonNull final DSTARRepeater repeater
	){
		final List<ReflectorLinkInformation> result = new ArrayList<>();

		for(ReflectorCommunicationService service : getServices(systemID)) {
			final List<ReflectorLinkInformation> serviceLinkInfo = service.getLinkInformationIncoming(repeater);

			if(!serviceLinkInfo.isEmpty()) {result.addAll(serviceLinkInfo);}
		}

		return result;
	}

	public static List<ReflectorLinkInformation> getLinkInformation(
		@NonNull final UUID systemID,
		@NonNull final DSTARRepeater repeater
	){
		final List<ReflectorLinkInformation> result = new ArrayList<>();

		for(ReflectorCommunicationService service : getServices(systemID)) {
			final List<ReflectorLinkInformation> serviceLinkInfo = service.getLinkInformation(repeater);
			if(!serviceLinkInfo.isEmpty()) {result.addAll(serviceLinkInfo);}
		}

		return result;
	}

	public static List<ReflectorLinkInformation> getLinkInformation(
		@NonNull final UUID systemID
	){
		final List<ReflectorLinkInformation> result = new ArrayList<>();

		for(ReflectorCommunicationService service : getServices(systemID)) {
			final List<ReflectorLinkInformation> serviceLinkInfo = service.getLinkInformation();

			if(!serviceLinkInfo.isEmpty()) {result.addAll(serviceLinkInfo);}
		}

		return result;
	}

	public static Optional<ReflectorCommunicationService> getService(
		@NonNull final UUID systemID,
		@NonNull final DSTARProtocol reflectorProtocol
	) {
		servicesLocker.lock();
		try {
			final Map<DSTARProtocol, ReflectorCommunicationService> services = systemServices.get(systemID);

			return services != null ? Optional.ofNullable(services.get(reflectorProtocol)) : Optional.empty();
		}finally {servicesLocker.unlock();}
	}

	public static List<ReflectorCommunicationService> getServices(
		@NonNull final UUID systemID
	){
		servicesLocker.lock();
		try {
			final Map<DSTARProtocol, ReflectorCommunicationService> services = systemServices.get(systemID);

			return services != null ? new ArrayList<>(services.values()) : Collections.emptyList();
		}finally {servicesLocker.unlock();}
	}

	public static ReflectorCommunicationService createService(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		@NonNull final String reflectorProtocolType,
		@NonNull final ReflectorProperties reflectorProperties,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener,
		final String applicationName, final String applicationVersion
	) {
		return createService(
			systemID,
			gateway,
			reflectorProtocolType, reflectorProperties, workerExecutor, null, reflectorLinkManager,
			eventListener,
			applicationName, applicationVersion
		);
	}

	public static ReflectorCommunicationService createService(
		@NonNull final UUID systemID,
		@NonNull DSTARGateway gateway,
		@NonNull final String reflectorProtocolType,
		@NonNull final ReflectorProperties reflectorProperties,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketIO,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener,
		String applicationName, String applicationVersion
	) {
		if(
			gateway == null ||
			reflectorProtocolType == null || "".equals(reflectorProtocolType) ||
			reflectorProperties == null
		) {return null;}

		if(applicationName == null){applicationName = "";}
		if(applicationVersion == null){applicationVersion = "";}

		ReflectorProtocolProcessorTypes serviceType =
			ReflectorProtocolProcessorTypes.getTypeByTypeName(reflectorProtocolType);
		if(serviceType == null) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Illegal reflector protocol type " + reflectorProtocolType + ".");

			return null;
		}

		servicesLocker.lock();
		try {
			Map<DSTARProtocol, ReflectorCommunicationService> services = systemServices.get(systemID);
			if(services == null) {
				services = new HashMap<>();

				systemServices.put(systemID, services);
			}
			else if(services.containsKey(serviceType.getProtocol())) {
				if(log.isErrorEnabled()) {
					log.error(
						logHeader +
						"Could not create dupplicate reflector communication service, " + serviceType.getTypeName() + "."
					);
				}

				return null;
			}

			SocketIO useSocketIO = null;
			localSocketIOLocker.lock();
			try {
				if(socketIO != null)
					useSocketIO = socketIO;
				else if(localSocketIO == null) {
					localSocketIO = new SocketIO(gateway, workerExecutor);

					if(!localSocketIO.start() || !localSocketIO.waitThreadInitialize(TimeUnit.SECONDS.toMillis(10))) {
						log.error(logHeader + "Could not start SocketI/O thread.");
						stopLocalSocketIO();
						return null;
					}
					useSocketIO = localSocketIO;
				}
				else {useSocketIO = localSocketIO;}
			}finally {localSocketIOLocker.unlock();}


			final ReflectorCommunicationService service =
				ReflectorCommunicationServiceFactory.createService(
					systemID,
					gateway, serviceType,
					workerExecutor,
					useSocketIO,
					reflectorLinkManager,
					eventListener
				);
			if(service == null) {
				log.error(
					logHeader +
					"Could not create reflector link protocol " + serviceType.getProtocol().toString() + "."
				);
				return null;
			}

			service.setApplicationName(applicationName != null ? applicationName : "");
			service.setApplicationVersion(applicationVersion != null ? applicationVersion : "");

			//設定流し込み
			if(!service.setProperties(reflectorProperties)) {
				log.error(
					logHeader + "Failed configuration set to reflector communication service = " + service.getProcessorType() + "."
				);
				return null;
			}

			services.put(serviceType.getProtocol(), service);

			return service;

		}finally {servicesLocker.unlock();}
	}

	@Deprecated
	public static Optional<ReflectorCommunicationService> getService(
		@NonNull final UUID systemID,
		@NonNull final String reflectorCallsign
	) {
		ReflectorCommunicationService reflectorService = null;
		for(ReflectorCommunicationService s : getServices(systemID)) {
			if(s.isSupportedReflectorCallsign(reflectorCallsign)) {
				reflectorService = s;
				break;
			}
		}

		return Optional.ofNullable(reflectorService);
	}

	public static boolean removeService(
		@NonNull final UUID systemID,
		@NonNull final DSTARProtocol reflectorProtocol,
		final boolean stopService
	) {
		boolean isRemoveSuccess;
		Map<DSTARProtocol, ReflectorCommunicationService> services;
		ReflectorCommunicationService targetRoutingService;

		servicesLocker.lock();
		try {
			if(isServiceRemoving) {
				try {
					if(!serviceRemoveComplete.await(10, TimeUnit.SECONDS)) {
						if(log.isErrorEnabled())
							log.error(logHeader + "Could not remove service = " + reflectorProtocol + ", Remove timeout.");

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

				targetRoutingService = services.get(reflectorProtocol);
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
			if(stopService) {targetRoutingService.stop();}

			servicesLocker.lock();
			try {
				isRemoveSuccess = services.remove(reflectorProtocol) != null;
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
		final boolean stopService
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
				final Map<DSTARProtocol, ReflectorCommunicationService> services = systemServices.get(systemID);
				if(services == null) {return false;}

				final List<DSTARProtocol> removeServices = new ArrayList<>(services.keySet());

				boolean isRemoveSuccess = true;
				for(Iterator<DSTARProtocol> it = removeServices.iterator(); it.hasNext();) {
					DSTARProtocol removeType = it.next();
					it.remove();

					isRemoveSuccess &= removeService(systemID, removeType, stopService);
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

		boolean isNeedStopLocalSocketIO = false;
		servicesLocker.lock();
		try {
			isNeedStopLocalSocketIO = !systemServices.isEmpty();
		}finally {
			servicesLocker.unlock();
		}

		if(isNeedStopLocalSocketIO) {stopLocalSocketIO();}
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

		stopLocalSocketIO();
	}

	private static void stopLocalSocketIO() {
		servicesLocker.lock();
		try {
			localSocketIOLocker.lock();
			try {
				if(localSocketIO != null) {
					synchronized(localSocketIO) {
						if(localSocketIO.isRunning()) {localSocketIO.stop();}
					}
				}

				localSocketIO = null;

			}finally {localSocketIOLocker.unlock();}
		}finally {servicesLocker.unlock();}
	}
}
