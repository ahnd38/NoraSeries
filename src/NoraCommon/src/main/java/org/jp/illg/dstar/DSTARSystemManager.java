package org.jp.illg.dstar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.dstar.service.Service;
import org.jp.illg.dstar.service.reflectorname.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARSystemManager {

	public static class DSTARSystem implements Service {
		@Getter
		private final UUID systemID;

		@Getter
		private final SocketIO socketIO;

		@Getter
		private DSTARGateway gateway;

		@Getter
		private List<DSTARRepeater> repeaters;

		private long lastTransferringTime = 0;

		private boolean isTransferring;

		private boolean isRunning;

		private DSTARSystem(
			final UUID systemID,
			final SocketIO socketio
		) {
			this.systemID = systemID;
			this.socketIO = socketio;

			lastTransferringTime = SystemUtil.getNanoTimeCounterValue();

			isTransferring = false;

			isRunning = false;
		}

		@Override
		public ThreadProcessResult processService() {
			process();

			return ThreadProcessResult.NoErrors;
		}

		@Override
		public boolean start() {
			final boolean isStartSuccess = startSystem(systemID);
			isRunning = isStartSuccess;

			return isStartSuccess;
		}

		@Override
		public void stop() {
			finalizeSystem(systemID);

			isRunning = false;
		}

		@Override
		public boolean isRunning() {
			return isRunning;
		}

		@Override
		public void close() {
			stop();
		}
	}

	private static final String logTag = DSTARSystemManager.class.getSimpleName() + " : ";

	private static final Lock locker;
	private static final Map<UUID, DSTARSystem> systemEntries;

	static {
		locker = new ReentrantLock();
		systemEntries = new ConcurrentHashMap<>();
	}

	private DSTARSystemManager() {}

	public static void process() {
		List<DSTARSystem> entries = null;
		locker.lock();
		try {
			entries = new ArrayList<>(systemEntries.values());
		}finally {
			locker.unlock();
		}

		for(final DSTARSystem entry : entries) {
			entry.isTransferring = isDataTransferringInternal(entry.systemID);
			if(entry.isTransferring)
				entry.lastTransferringTime = SystemUtil.getNanoTimeCounterValue();
		}
	}

	public static boolean startSystem(@NonNull final UUID systemID) {
		final DSTARSystem systemEntry = getSystemEntry(systemID);
		if(systemEntry == null){return false;}

		if(!systemEntry.gateway.start()){
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to start DSTAR gateway = " + systemEntry.gateway.getGatewayCallsign());

			finalizeSystem(systemID);

			return false;
		}

		for(final DSTARRepeater repeater : systemEntry.repeaters){
			if(!repeater.start()){
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to start DSTAR repeater = " + repeater.getRepeaterCallsign());

				finalizeSystem(systemID);

				return false;
			}
		}

		return true;
	}

	public static void stopSystem(@NonNull final UUID systemID) {
		finalizeSystem(systemID);
	}

	public static DSTARSystem createSystem(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final GatewayProperties gatewayProperties,
		@NonNull final Map<String, RepeaterProperties> repeaterProperties,
		@NonNull final ExecutorService workerExecutor,
		final WebRemoteControlService webRemoteControlService,
		@NonNull final ReflectorNameService reflectorNameService,
		@NonNull final RepeaterNameService repeaterNameService
	) {
		DSTARSystem systemEntry = null;
		DSTARGateway gateway = null;
		List<DSTARRepeater> repeaters = null;

		if((systemEntry = createSystemEntry(systemID, workerExecutor, exceptionListener)) == null){
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to create DSTAR system entry");

			return null;
		}
		else if(
			(gateway = createGateway(
				systemEntry,
				systemID,
				exceptionListener,
				systemEntry.socketIO,
				applicationInformation,
				workerExecutor,
				webRemoteControlService,
				reflectorNameService,
				repeaterNameService,
				gatewayProperties
			)) == null
		){
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to create DSTAR gateway");

			finalizeSystem(systemID);

			return null;
		}
		else if(
			(repeaters = createRepeaters(
				systemEntry,
				systemID,
				exceptionListener,
				gateway,
				systemEntry.socketIO,
				workerExecutor,
				webRemoteControlService,
				repeaterProperties.values()
			)) == null
		){
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to create DSTAR repeaters");

			finalizeSystem(systemID);

			return null;
		}
		else if(repeaters.isEmpty() || DSTARRepeaterManager.getRepeaters(systemID).isEmpty()){
			if(log.isErrorEnabled())
				log.error(logTag + "No DSTAR repeater defined, At least one repeater definition is required");

			finalizeSystem(systemID);

			return null;
		}

		return systemEntry;
	}

	public static void finalizeSystem(@NonNull final UUID systemID) {
		final DSTARSystem systemEntry = getSystemEntry(systemID);
		if(systemEntry != null) {
			if(systemEntry.gateway != null){systemEntry.gateway.stop();}
			if(systemEntry.repeaters != null){
				for(final DSTARRepeater repeater : systemEntry.repeaters)
					repeater.stop();
			}

			systemEntry.socketIO.close();
		}

		DSTARGatewayManager.finalizeSystem(systemID);
		DSTARRepeaterManager.finalizeSystem(systemID);

		locker.lock();
		try{
			systemEntries.remove(systemID);
		}finally{
			locker.unlock();
		}
	}

	/**
	 * 指定時間以上のアイドル期間の存在を確認する
	 * @param systemID システムID
	 * @param minimumIdleTime 最小アイドル時間
	 * @param minimumIdleTimeUnit 最小アイドル時間単位
	 * @return 指定時間以上のアイドル期間があればtrue
	 */
	public static boolean isIdleSystem(
		@NonNull final UUID systemID,
		final long minimumIdleTime,
		@NonNull final TimeUnit minimumIdleTimeUnit
	) {
		final DSTARSystem entry = getSystemEntry(systemID);
		if(entry == null) {return false;}

		entry.isTransferring = isDataTransferringInternal(entry.systemID);
		if(entry.isTransferring)
			entry.lastTransferringTime = SystemUtil.getNanoTimeCounterValue();

		return !entry.isTransferring && (
			entry.lastTransferringTime +
			TimeUnit.NANOSECONDS.convert(minimumIdleTime, minimumIdleTimeUnit)
		) < SystemUtil.getNanoTimeCounterValue();
	}

	private static boolean isDataTransferringInternal(final UUID systemID) {
		final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
		if(gateway != null && gateway.isDataTransferring()) {return true;}

		final List<DSTARRepeater> repeaters = DSTARRepeaterManager.getRepeaters(systemID);
		if(repeaters != null) {
			for(final DSTARRepeater repeater : repeaters) {
				if(repeater.isDataTransferring()) {return true;}
			}
		}

		return false;
	}

	private static DSTARSystem getSystemEntry(final UUID systemID) {
		locker.lock();
		try {
			return systemEntries.get(systemID);
		}finally {
			locker.unlock();
		}
	}

	@SuppressWarnings("resource")
	private static DSTARSystem createSystemEntry(
		final UUID systemID,
		final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		locker.lock();
		try{
			DSTARSystem entry = getSystemEntry(systemID);
			if(entry == null) {
				final SocketIO socketio = new SocketIO(
					exceptionListener, workerExecutor, "DSTAR System Common"
				);
				if(!socketio.start()){
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to start SocketIO");

					return null;
				}

				entry = new DSTARSystem(systemID, socketio);

				systemEntries.put(systemID, entry);

				return entry;
			}
		}finally{
			locker.unlock();
		}

		return null;
	}

	private static DSTARGateway createGateway(
		final DSTARSystem systemEntry,
		final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		final SocketIO localSocketIO,
		final ApplicationInformation<?> applicationInformation,
		final ExecutorService workerExecutor,
		final WebRemoteControlService webRemoteControlService,
		final ReflectorNameService reflectorNameService,
		final RepeaterNameService repeaterNameService,
		final GatewayProperties properties
	) {
		DSTARGateway gateway = null;

		DSTARGatewayManager.removeGateway(systemID, true);
		gateway = DSTARGatewayManager.createGateway(
			systemID,
			exceptionListener,
			properties.getCallsign(),
			workerExecutor,
			localSocketIO,
			webRemoteControlService,
			applicationInformation,
			reflectorNameService,
			repeaterNameService
		);
		if(gateway == null){
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to create gateway = " + properties.getCallsign());

			return null;
		}
		else if(!gateway.setProperties(properties)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Failed to set properties to gateway.");

			return null;
		}

		if(log.isInfoEnabled())
			log.info(logTag + "Create gateway..." + gateway.getGatewayCallsign());

		systemEntry.gateway = gateway;

		return gateway;
	}

	private static List<DSTARRepeater> createRepeaters(
		final DSTARSystem systemEntry,
		final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		final DSTARGateway gateway,
		final SocketIO localSocketIO,
		final ExecutorService workerExecutor,
		final WebRemoteControlService webRemoteControlService,
		final Collection<RepeaterProperties> properties
	) {
		DSTARRepeaterManager.removeRepeaters(systemID, true);

		final List<DSTARRepeater> repeaters = new LinkedList<>();

		for(final RepeaterProperties repeaterProperties : properties) {
			if(!repeaterProperties.isEnable()) {continue;}

			final DSTARRepeater repeater =
				DSTARRepeaterManager.createRepeater(
					systemID,
					localSocketIO,
					workerExecutor,
					gateway,
					gateway.getOnRepeaterEventListener(),
					repeaterProperties.getType(), repeaterProperties.getCallsign(),
					repeaterProperties,
					webRemoteControlService
				);
			if(repeater == null) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to create repeater = " + repeaterProperties.getCallsign());

				continue;
			}
			else if(!repeater.setProperties(repeaterProperties)){
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to set properties to repeater = " + repeaterProperties.getCallsign());

				continue;
			}

			repeaters.add(repeater);

			if(log.isInfoEnabled()) {
				log.info(
					logTag +
					"Create repeater..." + repeater.getRepeaterCallsign() + " / Type:" + repeater.getRepeaterType()
				);
			}
		}

		systemEntry.repeaters = repeaters;

		return repeaters;
	}
}
