package org.jp.illg.dstar.service.icom;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ICOMRepeaterCommunicationServiceProperties;
import org.jp.illg.dstar.model.config.ICOMRepeaterProperties;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.repeater.icom.ExternalICOMRepeater;
import org.jp.illg.dstar.service.Service;
import org.jp.illg.dstar.service.icom.model.ICOMRepeaterType;
import org.jp.illg.dstar.service.icom.model.RepeaterCommunicationService;
import org.jp.illg.dstar.service.icom.repeaters.model.CommunicatorEvent;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IcomRepeaterCommunicationService extends ThreadBase implements Service{

	private static class CommunicatorEntry {
		private final RepeaterCommunicationService communicator;

		private final Timer processIntervalTimekeeper;

		public CommunicatorEntry(final RepeaterCommunicationService communicator) {
			this.communicator = communicator;

			processIntervalTimekeeper = new Timer();
		}
	}

	private static final String logTag =
		IcomRepeaterCommunicationService.class.getSimpleName() + " : ";

	private static final Lock servicesLocker;
	private static final Map<UUID, IcomRepeaterCommunicationService> services;

	private final Lock locker;

	@Getter
	private final UUID systemID;

	private final ExecutorService workerExecutor;

	private final SocketIO socketio;

	private final List<CommunicatorEntry> communicators;

	private ICOMRepeaterCommunicationServiceProperties properties;


	private EventListener<CommunicatorEvent> communicatorEventListener = new EventListener<CommunicatorEvent>() {
		@Override
		public void event(CommunicatorEvent event, Object attachment) {
			if(event == CommunicatorEvent.ReceivePacketFromController) {
				processCommunicatorsPacket();
			}
			else if(event == CommunicatorEvent.RequestRunProcess) {
				wakeupProcessThread();
			}
		}
	};

	static {
		servicesLocker = new ReentrantLock();
		services = new ConcurrentHashMap<>();
	}

	public IcomRepeaterCommunicationService(
		@NonNull final UUID systemID,
		@NonNull final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final SocketIO socketio,
		@NonNull final DSTARGateway gateway
	) {
		super(
			exceptionListener,
			IcomRepeaterCommunicationService.class.getSimpleName(),
			20, TimeUnit.MILLISECONDS
		);

		this.systemID = systemID;
		this.workerExecutor = workerExecutor;
		this.socketio = socketio;

		locker = new ReentrantLock();

		communicators = new ArrayList<>(8);

		properties = null;
	}

	public static IcomRepeaterCommunicationService createInstance(
		@NonNull final UUID systemID,
		@NonNull final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final SocketIO socketio,
		@NonNull final DSTARGateway gateway
	) {
		servicesLocker.lock();
		try {
			if(services.containsKey(systemID))
				return null;

			final IcomRepeaterCommunicationService instance =
				new IcomRepeaterCommunicationService(systemID, exceptionListener, workerExecutor, socketio, gateway);

			services.put(systemID, instance);

			return instance;
		}finally {
			servicesLocker.unlock();
		}
	}

	public static IcomRepeaterCommunicationService getInstance(@NonNull final UUID systemID) {
		servicesLocker.lock();
		try {
			return services.get(systemID);
		}finally {
			servicesLocker.unlock();
		}
	}

	public static boolean removeInstance(@NonNull final UUID systemID) {
		servicesLocker.lock();
		try {
			return services.remove(systemID) != null;
		}finally {
			servicesLocker.unlock();
		}
	}

	@Override
	public boolean start() {
		boolean isSuccess = true;

		locker.lock();
		try {
			for(final CommunicatorEntry entry : communicators) {
				if(!entry.communicator.start()) {
					isSuccess = false;

					if(log.isErrorEnabled()) {
						log.error(
							logTag + "Failed to start communicator type = " +
							entry.communicator.getRepeaterControllerType()
						);
					}
					break;
				}
			}

			if(!isSuccess || !super.start()) {
				stop();

				return false;
			}
		}finally {
			locker.unlock();
		}

		return isSuccess;
	}

	@Override
	public void stop() {
		super.stop();

		locker.lock();
		try {
			for(final CommunicatorEntry entry : communicators)
				entry.communicator.stop();
		}finally {
			locker.unlock();
		}
	}

	@Override
	protected void threadFinalize() {
		stop();
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult process() {
		processCommunicatorsProcess();

		processCommunicatorsPacket();

		return ThreadProcessResult.NoErrors;
	}

	@Override
	public ThreadProcessResult processService() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	public boolean initializeService() {
		return true;
	}

	@Override
	public void finalizeService() {
	}

	public ICOMRepeaterCommunicationServiceProperties getProperties() {
		return properties;
	}

	public boolean setProperties(
		@NonNull final ICOMRepeaterCommunicationServiceProperties properties
	) {
		this.properties = properties;

		locker.lock();
		try {
			for(final ICOMRepeaterProperties prop : properties.getRepeaters()) {
				if(!prop.isEnable()) {continue;}

				final RepeaterCommunicationService communicator = createCommunicator(
					getSystemID(),
					getExceptionListener(),
					workerExecutor,
					socketio,
					communicatorEventListener,
					prop
				);

				if(!communicator.setProperties(prop.getProperties())) {
					if(log.isErrorEnabled())
						log.error(logTag + "Failed configuration to comunicator type = " + prop.getRepeaterType());

					return false;
				}

				communicators.add(new CommunicatorEntry(communicator));
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	public boolean writePacket(@NonNull final DSTARPacket packet) {
		locker.lock();
		try {
			for(final CommunicatorEntry entry : communicators)
				entry.communicator.writePacket(packet.clone());
		}finally {
			locker.unlock();
		}

		return true;
	}

	private void processCommunicatorsProcess() {
		locker.lock();
		try {
			for(final CommunicatorEntry entry : communicators) {
				if(entry.processIntervalTimekeeper.isTimeout(
					entry.communicator.getProcessIntervalTimeMillis(), TimeUnit.MILLISECONDS
				)) {
					entry.communicator.serviceProcess();

					entry.processIntervalTimekeeper.updateTimestamp();
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private void processCommunicatorsPacket() {
		locker.lock();
		try {
			for(final CommunicatorEntry entry : communicators) {
				DSTARPacket packet = null;
				while(((packet = entry.communicator.readPacket())) != null) {
					if(
						packet.getPacketType() == DSTARPacketType.UpdateHeard &&
						packet.getHeardPacket() != null &&
						packet.getHeardPacket().getAreaRepeaterCallsign().length() == DSTARDefines.CallsignFullLength &&
						packet.getHeardPacket().getAreaRepeaterCallsign().endsWith("S")
					) {
						for(final String repeaterCallsign : entry.communicator.getManagementRepeaterCallsigns()) {
							final DSTARRepeater repeater =
								DSTARRepeaterManager.getRepeater(getSystemID(), repeaterCallsign);

							if(repeater == null || repeater.getRepeaterType() != RepeaterTypes.ExternalICOMRepeater)
								continue;

							workerExecutor.submit(new RunnableTask(getExceptionListener()) {
								@Override
								public void task() {
									((ExternalICOMRepeater)repeater).keepAliveFromRepeater(
										repeater.getRepeaterType().getTypeName()
									);
								}
							});
						}
					}
					else {
						sendToRepeater(packet);
					}
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private boolean sendToRepeater(final DSTARPacket packet) {
		if(packet.getRFHeader() == null) {return false;}

		final String destinationRepeater = packet.getRFHeader().getRepeater1CallsignString();

		final DSTARRepeater repeater =
			DSTARRepeaterManager.getRepeater(getSystemID(), destinationRepeater);

		if(repeater == null) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Destination repeater " + destinationRepeater +
					" is not found\n" + packet.toString(4)
				);
			}

			return false;
		}
		else if(
			repeater.getRepeaterType() != RepeaterTypes.ExternalICOMRepeater ||
			!(repeater instanceof ExternalICOMRepeater)
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag +
					"Destination repeater " + destinationRepeater +
					" is not repeater type " + RepeaterTypes.ExternalICOMRepeater
				);
			}

			return false;
		}

		final ExternalICOMRepeater dstRepeater = (ExternalICOMRepeater)repeater;

		return dstRepeater.writePacketFromIcomRepeater(packet);
	}

	private static RepeaterCommunicationService createCommunicator(
		final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		final ExecutorService workerExecutor,
		final SocketIO socketio,
		final EventListener<CommunicatorEvent> eventListener,
		final ICOMRepeaterProperties prop
	) {
		final ICOMRepeaterType repeaterType = prop.getRepeaterType();
		if(repeaterType == null) {return null;}

		try {
			@SuppressWarnings("unchecked")
			final Class<RepeaterCommunicationService> classObj =
				(Class<RepeaterCommunicationService>)Class.forName(repeaterType.getClassName());

			final Constructor<RepeaterCommunicationService> constructor =
				classObj.getConstructor(
					UUID.class,
					ThreadUncaughtExceptionListener.class,
					ExecutorService.class,
					SocketIO.class,
					EventListener.class
				);

			return constructor.newInstance(
				systemID,
				exceptionListener,
				workerExecutor,
				socketio,
				eventListener
			);
		} catch (ReflectiveOperationException ex) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag + "Could not load icom repeater communicator class..." +
					repeaterType.getClassName() + ".",
					ex
				);
			}
		}

		return null;
	}
}
