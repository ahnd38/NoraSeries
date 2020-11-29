package org.jp.illg.dstar.service.reflectorname.importers;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;

public abstract class ReflectorHostsImporterBase implements ReflectorHostsImporter{

	protected final UUID systemID;
	protected final ThreadUncaughtExceptionListener exceptionListerner;
	protected final ExecutorService workerExecutor;

	private Properties properties;

	private boolean isStarted;

	protected ReflectorHostsImporterBase(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor
	) {
		super();

		this.systemID = systemID;
		this.exceptionListerner = exceptionListener;
		this.workerExecutor = workerExecutor;

		properties = null;
		isStarted = false;
	}

	@Override
	public final boolean setProperties(@NonNull Properties properties) {
		this.properties = properties;

		return setPropertiesInternal(properties);
	}

	@Override
	public final Properties getProperties() {
		return properties;
	}

	@Override
	public boolean startImporter() {
		this.isStarted = true;

		return true;
	}

	@Override
	public void stopImporter() {
		this.isStarted = false;
	}

	@Override
	public boolean isRunning() {
		return isStarted;
	}

	protected abstract boolean setPropertiesInternal(@NonNull Properties properties);
}
