package org.jp.illg.dstar.repeater;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.repeater.model.DStarRepeaterEvent;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DSTARRepeaterManager {

	private static final String logHeader;

	private static final Map<UUID, Map<String, DSTARRepeater>> dstarRepeaters;
	private static final Lock dstarRepeatersLocker;

	static{
		logHeader = DSTARRepeaterManager.class.getSimpleName() + " : ";

		dstarRepeaters = new HashMap<>();
		dstarRepeatersLocker = new ReentrantLock();
	}

	private DSTARRepeaterManager() {
		super();
	}

	public static DSTARRepeater createRepeater(
		@NonNull final UUID systemID,
		@NonNull final SocketIO localSocketIO,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final DSTARGateway gateway,
		final EventListener<DStarRepeaterEvent> eventListener,
		@NonNull final String repeaterTypeString, @NonNull final String repeaterCallsign,
		@NonNull final RepeaterProperties repeaterProperties,
		final WebRemoteControlService webRemoteControlService
	) {
		return createRepeater(
			systemID,
			gateway, eventListener, repeaterTypeString, repeaterCallsign,
			repeaterProperties, webRemoteControlService,
			localSocketIO,
			workerExecutor
		);
	}

	public static DSTARRepeater createRepeater(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		final EventListener<DStarRepeaterEvent> eventListener,
		@NonNull final String repeaterTypeString, @NonNull final String repeaterCallsign,
		@NonNull final RepeaterProperties repeaterProperties,
		final WebRemoteControlService webRemoteControlService,
		@NonNull final SocketIO socketIO,
		@NonNull final ExecutorService workerExecutor
	) {

		if(
			gateway == null ||
			"".equals(repeaterTypeString)
		) {return null;}


		final RepeaterTypes repeaterType = RepeaterTypes.getTypeByTypeName(repeaterTypeString);
		if(repeaterType == null || repeaterType == RepeaterTypes.Unknown) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Could not create repeater, Illegal repeaterTypeString " + repeaterTypeString + "."
				);
			}

			return null;
		}

		dstarRepeatersLocker.lock();
		try {
			final DSTARRepeater repeater =
				DSTARRepeaterFactory.createRepeater(
					systemID,
					gateway, repeaterType, repeaterCallsign, workerExecutor, eventListener, socketIO
				);
			if(repeater == null) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Failed create repeater " + repeaterCallsign + ".");

				return null;
			}

			repeater.setWebRemoteControlService(webRemoteControlService);

			addRepeater(systemID, repeater);

			return repeater;
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static DSTARRepeater getRepeater(
		@NonNull final UUID systemID,
		@NonNull final String repeaterCallsign
	) {
		final Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
		if(repeaters == null) {return null;}

		dstarRepeatersLocker.lock();
		try {
			return repeaters.get(repeaterCallsign);
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static List<DSTARRepeater> getRepeaters(@NonNull final UUID systemID){
		final Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
		if(repeaters == null) {return Collections.emptyList();}

		dstarRepeatersLocker.lock();
		try {
			return new ArrayList<>(repeaters.values());
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static List<String> getRepeaterCallsigns(@NonNull final UUID systemID){
		final Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
		if(repeaters == null) {return Collections.emptyList();}

		dstarRepeatersLocker.lock();
		try {
			if(repeaters.isEmpty()) {return Collections.emptyList();}
			List<String> repeaterCallsigns = new ArrayList<String>();

			for(DSTARRepeater repeater : repeaters.values())
				repeaterCallsigns.add(repeater.getRepeaterCallsign());

			return repeaterCallsigns;
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static boolean removeRepeater(
		@NonNull final UUID systemID,
		@NonNull final String repeaterCallsign,
		final boolean stopRepeater
	) {
		if(!DSTARUtils.isValidCallsignFullLength(repeaterCallsign)) {return false;}

		Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
		if (repeaters == null) {
			return false;
		}

		dstarRepeatersLocker.lock();
		try {
			if(!repeaters.containsKey(repeaterCallsign)) {return false;}

			final DSTARRepeater repeater = repeaters.remove(repeaterCallsign);

			boolean removeSuccess = repeater != null;
			if(removeSuccess && repeater.isRunning()){repeater.stop();}

			if(repeaters.isEmpty())
				repeaters.remove(systemID);

			return removeSuccess;
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static boolean removeRepeaters(final UUID systemID, boolean stopRepeater) {

		List<DSTARRepeater> removeRepeaters;

		dstarRepeatersLocker.lock();
		try {
			final Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
			if(repeaters == null) {return false;}

			removeRepeaters = new ArrayList<DSTARRepeater>(repeaters.values());
		}finally {dstarRepeatersLocker.unlock();}

		for(final Iterator<DSTARRepeater> it = removeRepeaters.iterator(); it.hasNext();) {
			final DSTARRepeater repeater = it.next();
			it.remove();

			if(stopRepeater && repeater.isRunning()){repeater.stop();}
		}

		dstarRepeatersLocker.lock();
		try {
			return dstarRepeaters.remove(systemID) != null;
		}finally {dstarRepeatersLocker.unlock();}
	}

	public static boolean removeAllRepeaters() {
		List<UUID> removeSystems;
		dstarRepeatersLocker.lock();
		try {
			removeSystems = new ArrayList<>(dstarRepeaters.keySet());
		}finally {dstarRepeatersLocker.unlock();}

		boolean success = true;
		for(final UUID systemID : removeSystems)
			success &= removeRepeaters(systemID, true);

		return success;
	}

	public static void finalizeSystem(@NonNull final UUID systemID) {
		removeRepeaters(systemID, true);
	}

	public static void finalizeManager() {
		removeAllRepeaters();
	}

	private static Map<String, DSTARRepeater> getRepeatersInt(final UUID systemID){
		dstarRepeatersLocker.lock();
		try {
			return dstarRepeaters.get(systemID);
		}finally {
			dstarRepeatersLocker.unlock();
		}
	}

	private static boolean addRepeater(final UUID systemID, final DSTARRepeater repeater) {
		dstarRepeatersLocker.lock();
		try {
			Map<String, DSTARRepeater> repeaters = getRepeatersInt(systemID);
			if(repeaters == null) {
				repeaters = new HashMap<>();
				dstarRepeaters.put(systemID, repeaters);
			}

			return repeaters.put(repeater.getRepeaterCallsign(), repeater) == null;
		}finally {
			dstarRepeatersLocker.unlock();
		}
	}
}
