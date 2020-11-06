package org.jp.illg.dstar.service.repeatername;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.config.RepeaterListImporterProperties;
import org.jp.illg.dstar.model.config.RepeaterNameServiceProperties;
import org.jp.illg.dstar.model.defines.RepeaterListImporterType;
import org.jp.illg.dstar.service.repeatername.model.RepeaterData;
import org.jp.illg.dstar.util.DSTARSystemManager;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.function.Consumer;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterNameService {

	private static final String logTag = RepeaterNameService.class.getSimpleName() + " : ";

	private final Lock locker;

	private final UUID systemID;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final ExecutorService executorService;

	private final List<RepeaterListImporter> importers;

	private final Map<String, RepeaterData> repeaters;

	private final Timer processIntervalTimer;

	private final TaskQueue<RepeaterListImporter, Boolean> importTaskQueue;

	private final Consumer<RepeaterListImporter> repeaterListImportTask =
		new Consumer<RepeaterListImporter>() {
			@Override
			public void accept(final RepeaterListImporter importer) {
				final List<RepeaterData> repeaterList = importer.getRepeaterList();

				locker.lock();
				try {
					for(final RepeaterData repeater : repeaterList)
						repeaters.put(repeater.getRepeaterCallsign(), repeater);
				}finally {
					locker.unlock();
				}

				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Read repeater list "  + repeaterList.size() +
						" entries from importer type = " + importer.getImporterType()
					);
				}
			}
	};

	public RepeaterNameService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService executorService
	) {
		super();

		locker = new ReentrantLock();

		this.systemID = systemID;
		this.exceptionListener = exceptionListener;
		this.executorService = executorService;

		importers = new ArrayList<>(4);

		repeaters = new HashMap<>();

		processIntervalTimer = new Timer();

		importTaskQueue = new TaskQueue<>(executorService);
	}

	public boolean initialize(@NonNull final RepeaterNameServiceProperties properties) {

		final List<RepeaterListImporterProperties> importerProperties = properties.getImporters();
		if(importerProperties == null) {
			if(log.isErrorEnabled())
				log.error(logTag + "Importer properties list is not set");

			return false;
		}

		locker.lock();
		try {
			for(final Iterator<RepeaterListImporter> it = importers.iterator(); it.hasNext();) {
				final RepeaterListImporter importer = it.next();

				importer.stopImporter();

				it.remove();
			}

			for(final RepeaterListImporterProperties prop : importerProperties) {
				if(!prop.isEnable()) {continue;}

				final RepeaterListImporterType importerType = prop.getImporterType();

				if(importerType == null) {
					if(log.isErrorEnabled())
						log.error(logTag + "Unknown importer type");

					return false;
				}

				final RepeaterListImporter importer =
					createImporter(importerType, exceptionListener, executorService);

				if(importer == null) {
					if(log.isErrorEnabled())
						log.error(logTag + "Importer instance create error");

					return false;
				}
				else if(!importer.setProperties(prop.getConfigurationProperties())) {
					if(log.isErrorEnabled())
						log.error(logTag + "Importer initialize error");

					return false;
				}

				importers.add(importer);
			}

		}finally {
			locker.unlock();
		}

		return true;
	}

	public String findRepeaterName(@NonNull final String repeaterCallsign) {
		locker.lock();
		try {
			final RepeaterData repeater = repeaters.get(repeaterCallsign);

			return repeater != null ? repeater.getName() : "";
		}finally {
			locker.unlock();
		}
	}

	public void processService() {
		locker.lock();
		try {
			if(
				!processIntervalTimer.isTimeout(60, TimeUnit.SECONDS) ||
				!DSTARSystemManager.isIdleSystem(systemID, 3, TimeUnit.MINUTES)
			) {return;}

			processIntervalTimer.updateTimestamp();

			for(final RepeaterListImporter importer : importers) {
				if(!importer.hasUpdateRepeaterList()) {continue;}

				importTaskQueue.addEventQueue(repeaterListImportTask, importer, exceptionListener);
			}
		}finally {
			locker.unlock();
		}
	}

	private static RepeaterListImporter createImporter(
		final RepeaterListImporterType importerType,
		final ThreadUncaughtExceptionListener exceptionListener,
		final ExecutorService executorService
	) {
		final Class<?> importerClass = importerType.getImplementationClass();
		try {
			@SuppressWarnings("unchecked")
			final Constructor<RepeaterListImporter> constructor =
				(Constructor<RepeaterListImporter>)importerClass.getConstructor(
					ThreadUncaughtExceptionListener.class,
					ExecutorService.class
				);

			final RepeaterListImporter instance = constructor.newInstance(
				exceptionListener, executorService
			);

			return instance;
		}catch(ReflectiveOperationException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not create importer instance type = " + importerClass.getName(), ex);

			return null;
		}

	}
}
