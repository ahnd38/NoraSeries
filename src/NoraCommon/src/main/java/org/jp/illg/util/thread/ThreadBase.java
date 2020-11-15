package org.jp.illg.util.thread;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.PerformanceTimer;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ThreadBase implements Runnable {

	/**
	 * 処理時間ログテーブルサイズ
	 */
	private static final int processLoopIntervalTimeLogTableSize = 10;

	/**
	 * スレッドインスタンス
	 */
	@Getter(AccessLevel.PROTECTED)
	private Thread workerThread;

	/**
	 * スレッド生存フラグ
	 *
	 *
	 */
	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private boolean workerThreadAvailable;

	/**
	 * スレッド例外捕捉リスナ
	 */
	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private ThreadUncaughtExceptionListener exceptionListener;

	/**
	 * スレッド例外メッセージ
	 */
	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private String threadFatalErrorMessage;

	/**
	 * スレッド例外
	 */
	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private Exception threadFatalErrorException;

	/**
	 * スレッド名
	 */
	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private String workerThreadName;

	/**
	 * スレッド初期化フラグ
	 */
	@Getter(AccessLevel.PUBLIC)
	@Setter(AccessLevel.PRIVATE)
	private boolean threadInitialized;

	/**
	 * マニュアルスレッド終了コントロールフラグ<br>
	 * <br>
	 * デフォルト:false<br>
	 * trueの場合には、スレッドの終了を上位側でコントロールする必要がある<br>
	 * 上位側において、{@link ThreadBase#workerThreadAvailable} = falseを定期的にチェックし、
	 * 必要な処理を行った後に、{@link ThreadBase#workerThreadTerminateRequest}をtrueにセットするとスレッドが終了する
	 *
	 * @see ThreadBase#workerThreadTerminateRequest
	 */
	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PROTECTED)
	private boolean manualControlThreadTerminateMode;

	/**
	 * スレッド終了要求
	 *
	 *
	 */
	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PROTECTED)
	private boolean workerThreadTerminateRequest;

	/**
	 * スレッドメインループ間隔時間(ms)<br>
	 * <br>
	 * {@link ThreadBase#process()}を呼び出す間隔の時間設定<br>
	 * 正の値である必要がある<br>
	 * 範囲外の場合には、{@link ThreadBase#processLoopPeriodMillisDefault}が適用される
	 *
	 * @see ThreadBase#process()
	 */
	@Getter
	protected long processLoopIntervalTimeMillis;

	private final String logHeader;

	private final Lock lock;
	private final Condition threadIntializedCondition;

	private static final long processLoopIntervalTimeMillisDefault = 10;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private long currentProcessIntervalTimeMillis;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PROTECTED)
	private boolean enableIntervalTimeCorrection;

	private final boolean enableIntervalTimeCorrectionDefault = true;

	@Getter
	@Setter
	private boolean threadBaseDebug;

	private long processLoopIntervalTimeAverageNanos;

	private final PerformanceTimer processLoopIntervalTimer;

	private final Timer outputStatusTimer;

	private boolean wakeupRequested;

	private final long[] processLoopIntervalTimeLogTable;
	private int processLoopIntervalTimeLogTablePtr;

	private long processLoopIntervalTimeOffsetNanos;

	private final Timer processLoopIntervalAverageCalcTimer;

	private ThreadBase() {
		super();

		logHeader = ThreadBase.class.getSimpleName() + "(" + this.getClass().getSimpleName() + ") : ";

		setThreadBaseDebug(false);
		processLoopIntervalTimer = new PerformanceTimer();
		outputStatusTimer = new Timer(30, TimeUnit.SECONDS);

		lock = new ReentrantLock();
		threadIntializedCondition = lock.newCondition();

		setThreadInitialized(false);
		setManualControlThreadTerminateMode(false);
		setWorkerThreadTerminateRequest(false);
		setEnableIntervalTimeCorrection(enableIntervalTimeCorrectionDefault);

		setProcessLoopIntervalTimeMillis(processLoopIntervalTimeMillisDefault);
		setCurrentProcessIntervalTimeMillis(0L);

		wakeupRequested = false;

		processLoopIntervalTimeAverageNanos = 0;

		processLoopIntervalTimeLogTable = new long[processLoopIntervalTimeLogTableSize];
		processLoopIntervalTimeLogTablePtr = 0;

		processLoopIntervalAverageCalcTimer = new Timer();

		processLoopIntervalTimeOffsetNanos = 0;
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName
	) {
		this();

		setExceptionListener(exceptionListener);

		if(workerThreadName != null && !"".equals(workerThreadName))
			setWorkerThreadName(workerThreadName);
		else
			setWorkerThreadName(ThreadBase.class.getSimpleName());
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		final long processLoopPeriodMillis
	) {
		this(exceptionListener, workerThreadName);

		setProcessLoopIntervalTimeMillis(processLoopPeriodMillis);
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		final long processLoopIntervalTime,
		@NonNull final TimeUnit processLoopIntervalTimeUnit
	) {
		this(exceptionListener, workerThreadName);

		setProcessLoopIntervalTime(processLoopIntervalTime, processLoopIntervalTimeUnit);
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		final long processLoopPeriodMillis,
		final boolean manualControlThreadTerminate
	) {
		this(exceptionListener, workerThreadName);

		setProcessLoopIntervalTimeMillis(processLoopPeriodMillis);
		setManualControlThreadTerminateMode(manualControlThreadTerminate);
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		final long processLoopIntervalTime,
		@NonNull final TimeUnit processLoopIntervalTimeUnit,
		final boolean manualControlThreadTerminate
	) {
		this(exceptionListener, workerThreadName);

		setProcessLoopIntervalTime(processLoopIntervalTime, processLoopIntervalTimeUnit);
		setManualControlThreadTerminateMode(manualControlThreadTerminate);
	}

	protected ThreadBase(
		final ThreadUncaughtExceptionListener exceptionListener,
		final String workerThreadName,
		final boolean manualControlThreadTerminate
	) {
		this(exceptionListener, workerThreadName);

		setManualControlThreadTerminateMode(manualControlThreadTerminate);
	}

	protected void setProcessLoopIntervalTimeMillis(final long processLoopPeriodMillis) {
		if(processLoopPeriodMillis < 0)
			this.processLoopIntervalTimeMillis = -1;
		else
			this.processLoopIntervalTimeMillis = processLoopPeriodMillis;
	}

	protected void setProcessLoopIntervalTime(final long time, @NonNull final TimeUnit timeunit) {
		setProcessLoopIntervalTimeMillis(timeunit.toMillis(time));
	}

	protected long getProcessLoopIntervalTime(@NonNull final TimeUnit timeunit) {
		return  timeunit.convert(getProcessLoopIntervalTimeMillis(), TimeUnit.MILLISECONDS);
	}

	public boolean start() {
		if(
			this.workerThreadAvailable &&
			this.workerThread != null && this.workerThread.isAlive()
		) {this.stop();}

		setThreadInitialized(false);
		setWorkerThreadTerminateRequest(false);

		this.workerThread = new Thread(this);
		this.workerThread.setName(getWorkerThreadName() + "_" + this.workerThread.getId());
		this.workerThreadAvailable = true;
		this.workerThread.start();

		if(log.isTraceEnabled())
			log.trace(logHeader + this.workerThread.getName() + " thread started.");

		return true;
	}

	public void stop() {
		this.workerThreadAvailable = false;
		setThreadInitialized(false);

		if(this.workerThread != null && this.workerThread.isAlive()) {
			this.workerThread.interrupt();
			try {
				this.workerThread.join();

				if(log.isTraceEnabled())
					log.trace(logHeader + this.workerThread.getName() + " thread stopped.");
			} catch (InterruptedException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + this.workerThread.getName() + " function stop() received interrupt.",ex);
			}
		}
	}

	public boolean isRunning() {
		if(
			this.workerThreadAvailable &&
			this.workerThread != null && this.workerThread.isAlive()
		)
			return true;
		else
			return false;
	}

	public long getThreadId() {
		return isRunning() ? workerThread.getId() : -1;
	}

	public boolean waitThreadInitialize(long timeoutMillis) {
		if(
//			!isRunning() ||
			Thread.currentThread().isInterrupted() ||
			timeoutMillis < 0
		) {return false;}
		else if(isThreadInitialized()) {return true;}

		lock.lock();
		try {
			return threadIntializedCondition.await(timeoutMillis, TimeUnit.MILLISECONDS) && isThreadInitialized();
		}catch (InterruptedException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Interrupted exception occurred at waitThreadInitialize.");
		}finally {
			lock.unlock();
		}

		return false;
	}

	public boolean waitThreadTerminate(final long timeout, @NonNull final TimeUnit timeoutUnit) {
		try {
			if(timeout > 0)
				workerThread.join(TimeUnit.MILLISECONDS.convert(timeout, timeoutUnit));
			else
				workerThread.join();
		}catch(InterruptedException ex) {
			return false;
		}

		return true;
	}

	public boolean waitThreadTerminate() {
		return waitThreadTerminate(0, TimeUnit.SECONDS);
	}

	@Override
	public void run() {
		try {
			if(this.threadInitializeInternal() != ThreadProcessResult.NoErrors) {
				if(log.isDebugEnabled())
					log.debug(logHeader + this.workerThread.getName() + " could not initialize.");

				notifyThreadFatalApplicationErrorEvent(getThreadFatalErrorMessage(), getThreadFatalErrorException());
				return;
			}
			this.notifyThreadInitialized();	// notify initialize completed to waiting threads.

			try {
				final PerformanceTimer processTimer = new PerformanceTimer();

				do {
					processTimer.start();

					final long requestProcessLoopIntervalTimeMillis = getProcessLoopIntervalTimeMillis();

					if(requestProcessLoopIntervalTimeMillis != getCurrentProcessIntervalTimeMillis()) {
						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"Loop interval time changed " +
								getCurrentProcessIntervalTimeMillis() + " -> " + requestProcessLoopIntervalTimeMillis + "(ms)"
							);
						}

						final long requestProcessLoopIntervalTimeNanos =
							TimeUnit.NANOSECONDS.convert(requestProcessLoopIntervalTimeMillis, TimeUnit.MILLISECONDS);

						processLoopIntervalTimer.reset();
						Arrays.fill(
							processLoopIntervalTimeLogTable, requestProcessLoopIntervalTimeNanos
						);
						processLoopIntervalTimeLogTablePtr = 0;

						processLoopIntervalTimeAverageNanos = requestProcessLoopIntervalTimeNanos;

						processLoopIntervalAverageCalcTimer.updateTimestamp();

						processLoopIntervalTimeOffsetNanos = 0;

						setCurrentProcessIntervalTimeMillis(requestProcessLoopIntervalTimeMillis);
					}

					if(processLoopIntervalTimer.isRunning()) {
						final long processLoopIntervalTimeNanos =
							processLoopIntervalTimer.getTimeFromTimerStart(TimeUnit.NANOSECONDS);
						processLoopIntervalTimeLogTable[
							processLoopIntervalTimeLogTablePtr > 0 ?
								processLoopIntervalTimeLogTablePtr % processLoopIntervalTimeLogTableSize : 0
						] = processLoopIntervalTimeNanos;

						if(processLoopIntervalTimeLogTablePtr < Integer.MAX_VALUE)
							processLoopIntervalTimeLogTablePtr++;
						else
							processLoopIntervalTimeLogTablePtr = 0;

						if(processLoopIntervalAverageCalcTimer.isTimeout(1,TimeUnit.SECONDS)) {
							processLoopIntervalAverageCalcTimer.updateTimestamp();

							final int maxIndex =
								processLoopIntervalTimeLogTablePtr < processLoopIntervalTimeLogTable.length ?
									processLoopIntervalTimeLogTablePtr : processLoopIntervalTimeLogTable.length;
							long sumNanos = 0;
							for(int i = 0; i < maxIndex; i++) {sumNanos += processLoopIntervalTimeLogTable[i];}
							processLoopIntervalTimeAverageNanos = ((sumNanos > 0 && maxIndex > 0) ? sumNanos / maxIndex : 0);

							processLoopIntervalTimeOffsetNanos =
								TimeUnit.NANOSECONDS.convert(requestProcessLoopIntervalTimeMillis, TimeUnit.MILLISECONDS) -
								processLoopIntervalTimeAverageNanos;

							if(log.isTraceEnabled()) {
								log.trace(
									logHeader +
									"Target = " + requestProcessLoopIntervalTimeMillis + "(ms)"+
									"/Average = " + String.format("%.3f", (float)processLoopIntervalTimeAverageNanos / 1000000F) + "(ms)" +
									"/Offset = " + String.format("%.3f", (float)processLoopIntervalTimeOffsetNanos / 1000000F) + "(ms)");
							}
						}

						if(outputStatusTimer.isTimeout(1, TimeUnit.MINUTES)) {
							outputStatusTimer.updateTimestamp();

							if(log.isTraceEnabled()) {
								log.trace(
									logHeader + "[Thread Process Time Report]\n" +
									"    Interval -> " +
									String.format(
										"Average : %.06fms / Offset : %.06fms / Target : %dms\n",
										(float)processLoopIntervalTimeAverageNanos / 1000000F,
										(float)(-processLoopIntervalTimeOffsetNanos) / 1000000F,
										getCurrentProcessIntervalTimeMillis()
									)
								);
							}
						}
					}
					processLoopIntervalTimer.start();

					final ThreadProcessResult processResult = process();

					processTimer.stop();
					final long processTimeNanos = processTimer.getTimeFromTimerStart(TimeUnit.NANOSECONDS);

					if(processResult == null) {
						final String msg = "Threads must return ThreadProcessResult.";
						if(log.isErrorEnabled()) {log.error(logHeader + msg);}

						notifyThreadUncaughtExceptionEvent(new RuntimeException(msg));
						break;
					}
					else if(processResult == ThreadProcessResult.NormalTerminate) {
						if(log.isTraceEnabled())
							log.trace(logHeader + this.workerThread.getName() + " normaly terminated.");

						break;
					}
					else if(processResult != ThreadProcessResult.NoErrors) {
						if(log.isDebugEnabled())
							log.debug(logHeader + this.workerThread.getName() + " error occurred and thread terminated.");

						notifyThreadFatalApplicationErrorEvent(getThreadFatalErrorMessage(), getThreadFatalErrorException());
						break;
					}
					else {
						synchronized(workerThread) {
							try {
								if(getCurrentProcessIntervalTimeMillis() < 0 || wakeupRequested) {
									wakeupRequested = false;
									processLoopIntervalTimer.stop();
								}
								else if(getCurrentProcessIntervalTimeMillis() == 0) {
									workerThread.wait();
								}
								else {
									final long currentIntervalTimeNanos = getCurrentProcessIntervalTimeMillis() * 1000 * 1000;
									long waitTimeNanos = currentIntervalTimeNanos - processTimeNanos;

									if(isEnableIntervalTimeCorrection())
										waitTimeNanos += processLoopIntervalTimeOffsetNanos;	//補正

									if(waitTimeNanos > 0)
										workerThread.wait((long)(waitTimeNanos / 1000000L), (int)(waitTimeNanos % 1000000L));
									else
										workerThread.wait(getCurrentProcessIntervalTimeMillis());
								}
							}catch(InterruptedException ex) {}
						}
					}
				}while(
					(isManualControlThreadTerminateMode() && !isWorkerThreadTerminateRequest()) ||
					(!isManualControlThreadTerminateMode() && !this.workerThread.isInterrupted() && this.workerThreadAvailable)
				);

			}finally {
				try {
					this.threadFinalizeInternal();
				}catch(Exception ex) {
					if(log.isDebugEnabled())
						log.debug(logHeader + this.workerThread.getName() + "error occurred at thread finalize.", ex);
				}
			}

		}catch(Exception ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + this.workerThread.getName() + " uncaught exception occurred. thread was terminated.",ex);

			notifyThreadUncaughtExceptionEvent(ex);
		}catch(AssertionError ae) {
			if(log.isDebugEnabled())
				log.debug(logHeader + this.workerThread.getName() + " uncaught assertion error occurred. thread was terminated.",ae);

			notifyThreadUncaughtExceptionEvent(new Exception(ae));
		}

		return;
	}

	protected abstract void threadFinalize();

	private void threadFinalizeInternal() {
		threadFinalize();
	}

	private ThreadProcessResult threadInitializeInternal() {
		ThreadProcessResult initializeResult = ThreadProcessResult.Unknown;

		if((initializeResult = threadInitialize()) != ThreadProcessResult.NoErrors)
			setThreadInitialized(false);
		else
			setThreadInitialized(true);

		return initializeResult;
	}

	protected abstract ThreadProcessResult threadInitialize();

	protected abstract ThreadProcessResult process();

	protected void wakeupProcessThread() {
		if(this.workerThread != null) {
			synchronized(this.workerThread) {
				wakeupRequested = true;
				this.workerThread.notifyAll();
			}
		}
	}

	@Deprecated
	protected void waitProcessThread(long timeoutMillis) throws InterruptedException{
		if(this.workerThread != null) {
			synchronized(this.workerThread) {
				if(timeoutMillis > 0)
					this.workerThread.wait(timeoutMillis);
				else
					this.workerThread.wait();
			}
		}
	}

	protected ThreadProcessResult threadFatalError(String message, Exception exception) {
		setThreadFatalErrorMessage(message != null ? message : "");
		setThreadFatalErrorException(exception);

		return ThreadProcessResult.FatalErrorWithInfo;
	}

	protected ThreadProcessResult threadFatalError(String message) {
		return threadFatalError(message, null);
	}

	protected boolean threadSleep(long timeMillis) {
		if(timeMillis <= 0) {return true;}

		if(Thread.currentThread() != workerThread) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not sleep different thread.");

			return false;
		}

		try {
			Thread.sleep(timeMillis);
		}catch(InterruptedException ex) {
			return false;
		}

		return true;
	}

	private void notifyThreadInitialized() {
		lock.lock();
		try {
			threadIntializedCondition.signalAll();
		}finally {
			lock.unlock();
		}
	}

	private void notifyThreadUncaughtExceptionEvent(Exception exception) {
		if(getExceptionListener() != null) {
			try {
				getExceptionListener().threadUncaughtExceptionEvent(exception, workerThread);
			}catch(Exception ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Exception occurred by exception listener.", ex);
			}
		}
	}

	private void notifyThreadFatalApplicationErrorEvent(String message, Exception exception) {
		if(getExceptionListener() != null) {
			try {
				getExceptionListener().threadFatalApplicationErrorEvent(message, exception, workerThread);
			}catch(Exception ex) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Exception occurred by application error listener.", ex);
			}
		}
	}
}
