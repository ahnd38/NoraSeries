package org.jp.illg.dstar.routing.service.jptrust;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.routing.service.jptrust.model.AreaPositionQueryResponse;
import org.jp.illg.dstar.routing.service.jptrust.model.JpTrustCommand;
import org.jp.illg.dstar.routing.service.jptrust.model.LogTransportEntry;
import org.jp.illg.dstar.routing.service.jptrust.model.PositionQueryResponse;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryTCPClient;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;
import com.google.common.util.concurrent.RateLimiter;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JpTrustLogTransporter extends SocketIOHandlerWithThread<BufferEntry>{

	/**
	 * ログの送信間隔制限レート(/秒)
	 */
	private static final double intervalRateLimit = 0.01d;	//100秒

	/**
	 * 1回に送信するログの最大数
	 * ※変更不可
	 */
	private static final int maxTransportLogEntry = 16;

	/**
	 * ログの送信先ポート番号
	 * ※変更不可
	 */
	private static final int logTransportDestinationPort = 30000;

	/**
	 * ログエントリ有効期間
	 */
	private static final long entryCacheLifetimeSeconds = 600;

	private String logHeader;

	private enum ProcessState {
		Initialize,
		Idle,
		Connecting,
		LogTransmiting,
		Wait,
		;
	}

	@Getter
	@Setter
	private String trustServerAddress;

	@Getter
	@Setter
	private String proxyServerAddress;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter
	private boolean enableLogTransport;
	private final boolean defaultEnableLogTransport = true;

	private Lock locker;

	private ProcessState currentState;
	private ProcessState nextState;
	private ProcessState callbackState;
	private boolean isStateChanged;
	private final Timer stateTimeKeeper;

	private final List<LogTransportEntry> entries;
	private final List<LogTransportEntry> entriesCache;


	private SocketIOEntryTCPClient trustChannel;

	private final Timer entriesCacheCleanupPeriodKeeper;

	private boolean isConnectionError;

	private boolean isUseProxy;
	private boolean isProxyDirectRetry;
	private String targetServerAddress;
	private int transportEntryCount;

	private final RateLimiter intervalRateLimitter;

	private boolean serverConnectted;

	public JpTrustLogTransporter(ThreadUncaughtExceptionListener exceptionListener) {
		this(exceptionListener, null);
	}

	public JpTrustLogTransporter(ThreadUncaughtExceptionListener exceptionListener, SocketIO socketIO) {
		super(
			exceptionListener,
			JpTrustLogTransporter.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.LocalPortOnly
		);

		setProcessLoopIntervalTimeMillis(1000L);

		logHeader = JpTrustLogTransporter.class.getSimpleName() + " : ";

		locker = new ReentrantLock();

		entries = new LinkedList<>();
		entriesCache = new ArrayList<>();

		stateTimeKeeper = new Timer();

		entriesCacheCleanupPeriodKeeper = new Timer(5, TimeUnit.SECONDS);

		intervalRateLimitter = RateLimiter.create(intervalRateLimit);

		initialize();
	}

	private void initialize() {
		currentState = ProcessState.Initialize;
		nextState = ProcessState.Initialize;
		callbackState = ProcessState.Initialize;
		isStateChanged = false;

		trustChannel = null;

		isConnectionError = false;

		isUseProxy = false;
		isProxyDirectRetry = false;

		targetServerAddress = "";

		transportEntryCount = 0;

		serverConnectted = false;

		setTrustServerAddress("");
		setGatewayCallsign(DSTARDefines.EmptyLongCallsign);
		setEnableLogTransport(defaultEnableLogTransport);
	}

	public boolean addLogTransportEntry(
		@NonNull Header queryHeader,
		@NonNull JpTrustCommand queryResponse
	) {
		if(
			queryHeader == null || queryResponse == null ||
			(
				!(queryResponse instanceof AreaPositionQueryResponse) &&
				!(queryResponse instanceof PositionQueryResponse)
			)
		) {return false;}

		locker.lock();
		try {
			//キャッシュを探して存在するようならログ送信しない
			boolean found = false;
			for(final LogTransportEntry e : entriesCache) {
				if(
					Arrays.equals(e.getQueryHeader().getMyCallsign(), queryHeader.getMyCallsign()) &&
					Arrays.equals(e.getQueryHeader().getYourCallsign(), queryHeader.getYourCallsign())
				) {
					found = true;

					e.getActivityTimeKeeper().updateTimestamp();

					if(log.isDebugEnabled())
						log.debug("Log transfer process is suppressed.\n" + queryHeader.toString(4));

					break;
				}
			}

			if(!found) {
				final LogTransportEntry entry =
					new LogTransportEntry(queryHeader, queryResponse, entryCacheLifetimeSeconds);

				return (entries.add(entry) && entriesCache.add(entry));
			}
			else
				return true;
		}finally {locker.unlock();}
	}

	@Override
	public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			opEntry.ifPresent(new Consumer<BufferEntry>() {
				@Override
				public void accept(BufferEntry t) {
					t.getLocker().lock();
					try {
						if(!t.isUpdate()) {return;}

						t.getBuffer().clear();
						t.setBufferState(BufferState.INITIALIZE);
						t.getBufferPacketInfo().clear();

						t.setUpdate(false);
					}finally {t.getLocker().unlock();}
				}
			});
		}
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		locker.lock();
		try {
			serverConnectted = true;

			if(trustChannel != null && trustChannel.getKey() == key) {
				if(currentState == ProcessState.Connecting) {
					nextState = ProcessState.LogTransmiting;

					if(log.isTraceEnabled())
						log.trace("Connected to " + remoteAddress.toString() + ".");
				}
			}
		}finally {locker.unlock();}

		wakeupProcessThread();

		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		locker.lock();
		try {
			serverConnectted = false;
		}finally {
			locker.unlock();
		}

		wakeupProcessThread();
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
		locker.lock();
		try {
			if(
				currentState == ProcessState.Connecting ||
				currentState == ProcessState.LogTransmiting
			) {isConnectionError = true;}
		}finally {
			locker.unlock();
		}

		wakeupProcessThread();

		if(log.isDebugEnabled())
			log.debug("Error occurred at log transport process.", ex);
	}


	@Override
	protected ThreadProcessResult threadInitialize() {
		if(!isEnableLogTransport()) {
			if(log.isWarnEnabled())
				log.warn("Log transport function for Japan Trust is disabled.");
		}

		logHeader =
			JpTrustLogTransporter.class.getSimpleName() + " : ";

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		closeTrustChannel();
	}

	@Override
	protected ThreadProcessResult processThread() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		locker.lock();
		try {
			boolean reProcess = false;
			do {
				reProcess = false;

				final boolean stateChanged = currentState != nextState;
				this.isStateChanged = stateChanged;

				if(log.isDebugEnabled() && stateChanged) {
					log.debug(
						logHeader +
						"State changed " +
							currentState + " -> " + nextState
					);
				}

				currentState = nextState;

				switch(currentState) {
				case Initialize:
					nextState = ProcessState.Idle;
					break;

				case Idle:
					if(!isEnableLogTransport()) {
						entries.clear();
					}
					else {processResult = onStateIdle();}
					break;

				case Connecting:
					processResult = onStateConnecting();
					break;

				case LogTransmiting:
					processResult = onStateLogTransmitting();
					break;

				case Wait:
					processResult = onStateWait();
					break;
				}

				if(
					currentState != nextState &&
					processResult == ThreadProcessResult.NoErrors
				) {reProcess = true;}

			}while(reProcess);

		}finally {locker.unlock();}

		//ログキャッシュを掃除する
		cleanupEntriesCache();

		return processResult;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout()) {nextState = callbackState;}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateIdle() {
		if(entries.size() >= 1 && intervalRateLimitter.tryAcquire()) {
//		if(entries.size() >= 1) {

			closeTrustChannel();

			isUseProxy = getProxyServerAddress() != null;

			if(isProxyDirectRetry) {
				targetServerAddress = getTrustServerAddress();
			}
			else {
				targetServerAddress =
					isUseProxy ? getProxyServerAddress() : getTrustServerAddress();
			}

			nextState = ProcessState.Connecting;
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateConnecting() {
		if(isStateChanged) {

			isConnectionError = false;

			//接続要求
			trustChannel =
				getSocketIO().registTCPClient(
					new InetSocketAddress(
						targetServerAddress,
						logTransportDestinationPort
					),
					this.getHandler(),
					this.getClass().getSimpleName() + "@" + targetServerAddress + ":" + logTransportDestinationPort
				);

			if(trustChannel == null) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Coult not create channel = " + targetServerAddress
					);
				}

				toWaitState(30, TimeUnit.SECONDS, ProcessState.Idle);
			}
			else {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Trying connect to log server " + targetServerAddress
					);
				}

				stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			}
		}
		else if(stateTimeKeeper.isTimeout() || isConnectionError) {
			closeTrustChannel();

			if(isUseProxy) {
				if(!isProxyDirectRetry) {
					isProxyDirectRetry = true;

					nextState = ProcessState.Idle;

					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Proxy server does not support log forwarding, trying log transfer via directly to JapanTrust."
						);
					}
				}
				else {
					if(log.isWarnEnabled())
						log.warn(logHeader + "Log transmit process failed, connect timeout = " + targetServerAddress);

					isProxyDirectRetry = false;

					//次回のチャレンジは60秒後とする
					toWaitState(1, TimeUnit.MINUTES, ProcessState.Idle);
				}
			}
			else {
				//接続タイムアウトしたので、アイドルに戻す
				if(log.isWarnEnabled())
					log.warn(logHeader + "Log transmit process failed, connect timeout = " + targetServerAddress);

				//次回のチャレンジは60秒後とする
				toWaitState(1, TimeUnit.MINUTES, ProcessState.Idle);
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateLogTransmitting() {
		if(isStateChanged) {
			final int transportEntries =
				entries.size() > maxTransportLogEntry ? maxTransportLogEntry : entries.size();

			final Optional<ByteBuffer> logData =
				generateLogData(getGatewayCallsign(), entries, transportEntries);

			if(logData.isPresent() && super.writeTCP(trustChannel.getKey(), logData.get())) {

				transportEntryCount = transportEntries;

				logData.get().rewind();

				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Transmiting qso log data to japan trust server..." +
						(
							log.isTraceEnabled() ?
							("\n" + FormatUtil.byteBufferToHexDump(logData.get(), 4)) : ""
						)
					);
				}

				disconnectTCP(trustChannel.getKey());

				stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimeKeeper.updateTimestamp();
			}
			else {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Log transmit process failed, could not generate log data = " + targetServerAddress);

				closeTrustChannel();
				//次回のチャレンジは5分後とする
				toWaitState(5, TimeUnit.MINUTES, ProcessState.Idle);
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			isProxyDirectRetry = false;

			if(log.isWarnEnabled())
				log.warn(logHeader + "Log transmit process failed, data transmit timeout = " + targetServerAddress);

			closeTrustChannel();
			//次回のチャレンジは60秒後とする
			toWaitState(1, TimeUnit.MINUTES, ProcessState.Idle);
		}
		else if(super.isWriteCompleted(trustChannel.getKey()) || !serverConnectted) {
			isProxyDirectRetry = false;

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					"Transmit completed log data " + transportEntryCount + " entries to " + targetServerAddress
				);
			}

			//完了
			closeTrustChannel();

			//送信したログエントリを削除
			for(int c = 0; c < transportEntryCount; c++) {entries.remove(0);}

			nextState = ProcessState.Idle;
		}

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(int time, final TimeUnit timeUnit, ProcessState callbackState) {
		if(time > 0) {
			stateTimeKeeper.setTimeoutTime(time, timeUnit);
			stateTimeKeeper.updateTimestamp();

			nextState = ProcessState.Wait;
		}
		else {
			nextState = callbackState;
		}

		this.callbackState = callbackState;
	}

	private static Optional<ByteBuffer> generateLogData(
		final String gatewayCallsign,
		final List<LogTransportEntry> entries,
		final int transportEntries
	){
		if(
			!CallSignValidator.isValidGatewayCallsign(gatewayCallsign) ||
			entries.size() < transportEntries
		) {
			return Optional.empty();
		}

		byte[] buf = null;

		final int bufferSize =
			4 +	//ID
			4 +	//レコード数
			8 +	//ログ送信元コールサイン
			(64 * transportEntries);	//ログ

		buf = new byte[bufferSize];
		Arrays.fill(buf, (byte)0x0);

		buf[0] = 'D';
		buf[1] = 'S';
		buf[2] = 'L';
		buf[3] = 'G';
		DSTARUtils.writeInt32BigEndian(buf, 4, transportEntries);

		final char[] gatewayCall = gatewayCallsign.toCharArray();
		gatewayCall[DSTARDefines.CallsignFullLength - 1] = ' ';
		DSTARUtils.writeFullCallsignToBuffer(buf, 8, gatewayCall);

		for(int i = 0; i < transportEntries && i < entries.size(); i++) {
			LogTransportEntry entry = entries.get(i);

			final int offset = 16 + (i * 64);

			final long unixTime = entry.getCreatedTime() / 1000;
			final long usecTime = (entry.getCreatedTime() % 1000) * 1000;

			DSTARUtils.writeInt32BigEndian(buf, offset + 0, (int)unixTime);
			DSTARUtils.writeInt32BigEndian(buf, offset + 4, (int)usecTime);

			DSTARUtils.writeFullCallsignToBuffer(buf, offset + 8, entry.getQueryHeader().getMyCallsign());

			if(CallSignValidator.isValidAreaRepeaterCallsign(entry.getQueryHeader().getYourCallsign()))
				DSTARUtils.writeFullCallsignToBuffer(buf, offset + 16, DSTARDefines.CQCQCQ.toCharArray());
			else
				DSTARUtils.writeFullCallsignToBuffer(buf, offset + 16, entry.getQueryHeader().getYourCallsign());

			//送り元IP[4] = NULL
			//送り先IP[4] = NULL

			final char[] sourceZoneRepeater = entry.getQueryHeader().getRepeater2Callsign();
			sourceZoneRepeater[DSTARDefines.CallsignFullLength - 1] = ' ';
			DSTARUtils.writeFullCallsignToBuffer(buf, offset + 32, sourceZoneRepeater);

			final char[] destinationZoneRepeater = entry.getQueryResponse().getRepeater1Callsign();
			destinationZoneRepeater[DSTARDefines.CallsignFullLength - 1] = 'G';
			DSTARUtils.writeFullCallsignToBuffer(buf, offset + 40, destinationZoneRepeater);

			DSTARUtils.writeFullCallsignToBuffer(buf, offset + 48, entry.getQueryHeader().getRepeater1Callsign());

			DSTARUtils.writeFullCallsignToBuffer(buf, offset + 56, entry.getQueryResponse().getRepeater2Callsign());
		}

		return Optional.of(ByteBuffer.wrap(buf));
	}

	private void closeTrustChannel() {
		locker.lock();
		try {
			if(trustChannel != null && trustChannel.getChannel().isOpen()) {
				try {
					trustChannel.getChannel().close();
				}catch(IOException ex) {
					if(log.isDebugEnabled())
						log.debug("Error occurred at channel close.", ex);
				}
			}
		}finally {locker.unlock();}
	}

	private void cleanupEntriesCache() {
		if(entriesCacheCleanupPeriodKeeper.isTimeout()) {
			entriesCacheCleanupPeriodKeeper.updateTimestamp();

			locker.lock();
			try {
				for(Iterator<LogTransportEntry> it = entriesCache.iterator(); it.hasNext();) {
					LogTransportEntry entry = it.next();

					if(entry.getActivityTimeKeeper().isTimeout()) {
						it.remove();

						if(log.isDebugEnabled())
							log.debug("Remove log entry cache.\n" + entry.getQueryHeader().toString(4));
					}
				}
			}finally {locker.unlock();}
		}
	}
}
