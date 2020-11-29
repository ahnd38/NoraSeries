package org.jp.illg.dstar.service.hfdownloader;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadServiceProperties;
import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadURLEntry;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.service.Service;
import org.jp.illg.dstar.service.hfdownloader.model.URLEntry;
import org.jp.illg.dstar.service.reflectorname.util.ReflectorHostsFileReaderWriter;
import org.jp.illg.dstar.DSTARSystemManager;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorHostFileDownloadService implements Service {

	public static class DownloadHostsData{
		@Getter
		private final Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts;

		@Getter
		private final URL url;

		private DownloadHostsData(
			final Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts,
			final URL url
		) {
			this.hosts = hosts;
			this.url = url;
		}
	}

	private final static String logTag =
		ReflectorHostFileDownloadService.class.getSimpleName() + " : ";

	private final Lock locker;

	private boolean isEnable;

	private final UUID systemID;

	private final List<URLEntry> urlEntries;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final ExecutorService workerExecutor;

	private final EventListener<DownloadHostsData> onLoadEventListener;

	private boolean isRunning;

	public ReflectorHostFileDownloadService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		@NonNull EventListener<DownloadHostsData> onLoadEventListener
	) {
		super();

		this.systemID = systemID;
		this.exceptionListener = exceptionListener;
		this.workerExecutor = workerExecutor;
		this.onLoadEventListener = onLoadEventListener;


		locker = new ReentrantLock();

		isEnable = false;

		urlEntries = new ArrayList<>(8);

		isRunning = false;
	}


	public boolean setProperties(
		@NonNull ReflectorHostFileDownloadServiceProperties properties
	) {
		isEnable = properties.isEnable();

		final Random rand = new Random(System.currentTimeMillis() ^ 0xfd32d5ba);
		for(final ReflectorHostFileDownloadURLEntry urlProperties : properties.getUrlEntries()) {
			final URLEntry urlEntry = new URLEntry();
			urlEntry.setEnable(urlProperties.isEnable());
			final int intervalMinutes = urlProperties.getIntervalMinutes();
			urlEntry.setIntervalMinutes(Math.max(intervalMinutes, 1));
			urlEntry.setUrl(urlProperties.getUrl());

			// Initial delay(60sec - 300sec)
			urlEntry.getIntervalTimekeeper().setTimeoutTime(rand.nextInt(240) + 60, TimeUnit.SECONDS);

			locker.lock();
			try {
				urlEntries.add(urlEntry);
			}finally {
				locker.unlock();
			}
		}

		return true;
	}

	@Override
	public boolean start() {
		isRunning = true;

		return true;
	}

	@Override
	public void stop() {
		isRunning = false;
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public ThreadProcessResult processService() {
		if(isEnable) {
			locker.lock();
			try {
				for(final URLEntry urlEntry : urlEntries)
					processURLEntry(systemID, exceptionListener, workerExecutor, onLoadEventListener, urlEntry);
			}finally {
				locker.unlock();
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private static void processURLEntry(
		final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		final ExecutorService workerExecutor,
		final EventListener<DownloadHostsData> onLoadEventListener,
		final URLEntry urlEntry
	) {
		if(
			urlEntry.isEnable() &&
			urlEntry.getIntervalTimekeeper().isTimeout() &&
			DSTARSystemManager.isIdleSystem(systemID, 1, TimeUnit.MINUTES)
		) {
			urlEntry.getIntervalTimekeeper().setTimeoutTime(
				urlEntry.getIntervalMinutes(), TimeUnit.MINUTES
			);
			urlEntry.getIntervalTimekeeper().updateTimestamp();

			try {
				final URL url = new URL(urlEntry.getUrl());

				if(log.isInfoEnabled())
					log.info(logTag + "Downloading host file = " + urlEntry.getUrl());

				final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
					ReflectorHostsFileReaderWriter.readHostFile(url, true);

				if(readHosts != null) {
					workerExecutor.submit(new RunnableTask() {
						@Override
						public void task() {
							onLoadEventListener.event(new DownloadHostsData(readHosts, url), null);
						}
					});
				}

			}catch(MalformedURLException ex) {
				if(log.isErrorEnabled())
					log.warn(logTag + "Bad url = " + urlEntry.getUrl());
			}
		}
	}

}
