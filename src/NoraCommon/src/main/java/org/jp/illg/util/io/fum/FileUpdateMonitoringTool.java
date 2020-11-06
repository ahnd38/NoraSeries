package org.jp.illg.util.io.fum;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.io.fum.model.MonitoringEntry;
import org.jp.illg.util.thread.CallableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUpdateMonitoringTool {

	private static final long fileSizeLimitBytes = 1048576L;

	private final String logTag = FileUpdateMonitoringTool.class.getSimpleName() + " : ";

	private final Lock locker;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final ExecutorService workerExecutor;

	private final List<MonitoringEntry> entries;


	public FileUpdateMonitoringTool(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor
	) {
		this.exceptionListener = exceptionListener;
		this.workerExecutor = workerExecutor;

		locker = new ReentrantLock();
		entries = new LinkedList<>();
	}

	public FileUpdateMonitoringTool(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		final FileUpdateMonitoringFunction... functions
	) {
		this(exceptionListener, workerExecutor);

		if(functions != null)
			addFunctions(Arrays.asList(functions));
	}

	public boolean addFunction(
		@NonNull final FileUpdateMonitoringFunction function
	) {
		locker.lock();
		try {
			return entries.add(new MonitoringEntry(function));
		}finally {
			locker.unlock();
		}
	}

	public boolean addFunctions(
		final FileUpdateMonitoringFunction... functions
	) {
		if(functions == null) {return false;}

		boolean isSuccess = true;
		for(final FileUpdateMonitoringFunction function : functions)
			isSuccess &= addFunction(function);

		return isSuccess;
	}

	public boolean addFunctions(
		@NonNull final Collection<FileUpdateMonitoringFunction> functions
	) {
		boolean isSuccess = true;
		for(final FileUpdateMonitoringFunction function : functions)
			isSuccess &= addFunction(function);

		return isSuccess;
	}

	public boolean removeFunction(
		@NonNull final FileUpdateMonitoringFunction function
	) {
		boolean isRemoved = false;
		locker.lock();
		try {
			for(final Iterator<MonitoringEntry> it = entries.iterator(); it.hasNext();) {
				final MonitoringEntry entry = it.next();

				if(entry.getFunction() == function) {
					it.remove();

					isRemoved = true;
				}
			}
		}finally {
			locker.unlock();
		}

		return isRemoved;
	}

	public boolean initialize() {
		return processFunctions(true, true);
	}

	public void process() {
		processFunctions(false, false);
	}

	private boolean processFunctions(
		final boolean forceInitialize,
		final boolean waitFunctionComplete
	) {
		boolean isSuccess = true;
		locker.lock();
		try {
			for(final MonitoringEntry entry : entries) {
				if(entry.isProcessing()) {continue;}

				isSuccess &= processFunction(forceInitialize, waitFunctionComplete, entry);
			}
		}finally {
			locker.unlock();
		}

		return isSuccess;
	}

	private boolean processFunction(
		final boolean forceInitialize,
		final boolean waitFunctionComplete,
		final MonitoringEntry entry
	) {
		if(
			!forceInitialize &&
			!entry.getIntervalTimekeeper().isTimeout(
				entry.getFunction().getMonitoringIntervalTimeSeconds(), TimeUnit.SECONDS
			)
		) {
			return true;
		}

		entry.getIntervalTimekeeper().updateTimestamp();

		final File targetFile = new File(entry.getTargetFilePath());
		if(!targetFile.exists()) {
			if(!entry.isError()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Target file is not exists = " + entry.getTargetFilePath());
			}
			entry.setError(true);

			return false;
		}
		else if(!targetFile.isFile()) {
			if(!entry.isError()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Target file is not file = " + entry.getTargetFilePath());
			}
			entry.setError(true);

			return false;
		}
		else if(!targetFile.canRead()) {
			if(!entry.isError()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Target file is can not read = " + entry.getTargetFilePath());
			}
			entry.setError(true);

			return false;
		}
		else if(targetFile.length() > fileSizeLimitBytes) {
			if(!entry.isError()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Target file size is too large = " + entry.getTargetFilePath());
			}
			entry.setError(true);

			return false;
		}

		entry.setError(false);

		final long targetFileLastModified = targetFile.lastModified();
		if(
			!forceInitialize &&
			entry.getLastUpdateTimestamp() >= targetFileLastModified
		) {
			return true;
		}

		final boolean isInitialize = forceInitialize || entry.getLastUpdateTimestamp() <= 0;

		entry.setLastUpdateTimestamp(targetFileLastModified);

		entry.setProcessing(true);

		return callUpdateFunction(entry, targetFile, isInitialize, waitFunctionComplete);
	}

	private boolean callUpdateFunction(
		final MonitoringEntry entry,
		final File targetFile,
		final boolean isInitialize,
		final boolean waitFunctionComplete
	) {
		Future<Boolean> f;
		try {
			f = workerExecutor.submit(new CallableTask<Boolean>(exceptionListener) {
				@Override
				public Boolean task() {
					try {
						final int bufferLength = (int)targetFile.length();
						if(bufferLength > fileSizeLimitBytes) {return false;}

						final byte[] buffer = new byte[bufferLength];

						try(final FileInputStream fis = new FileInputStream(targetFile)){
							int offset = 0;
							while(offset < bufferLength)
								offset += fis.read(buffer, offset, bufferLength - offset);
						}catch(IOException ex) {
							if(log.isErrorEnabled())
								log.error(logTag + "Could not read = " + targetFile.getAbsolutePath());

							return false;
						}

						try(final InputStream is = new ByteArrayInputStream(buffer)){
							boolean isSuccess = false;
							if(isInitialize) {
								isSuccess = entry.getFunction().initialize(is);
							}
							else {
								isSuccess = entry.getFunction().fileUpdate(is);

								if(isSuccess) {
									entry.setLastSuccessFile(buffer);
								}
								else if(!isSuccess && entry.getLastSuccessFile() != null) {
									try(
										final InputStream isr =
											new ByteArrayInputStream(entry.getLastSuccessFile())
									){
										isSuccess = entry.getFunction().rollback(isr);
									}
								}
							}

							return isSuccess;
						} catch (IOException ex) {
							if(log.isErrorEnabled())
								log.error(logTag + "Could not read = " + targetFile.getAbsolutePath());

							return false;
						}
					}finally {
						entry.setProcessing(false);
					}
				}
			});
		}catch(RejectedExecutionException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not execute function task", ex);

			entry.setProcessing(false);

			return false;
		}

		if(!waitFunctionComplete) {return true;}

		try {
			return f.get();
		} catch (InterruptedException ex) {
			f.cancel(true);

			return true;
		} catch (ExecutionException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Exception is occurred on task", ex);
		}

		return false;
	}
}
