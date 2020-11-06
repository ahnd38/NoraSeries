package org.jp.illg.nora.gateway.service.norausers;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.reporter.model.GatewayStatusReport;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.reporter.model.RepeaterStatusReport;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.nora.gateway.service.norausers.model.GatewayInformation;
import org.jp.illg.nora.gateway.service.norausers.model.ModemInformation;
import org.jp.illg.nora.gateway.service.norausers.model.NoraUsersStatusReporterState;
import org.jp.illg.nora.gateway.service.norausers.model.ReflectorInformation;
import org.jp.illg.nora.gateway.service.norausers.model.RepeaterInformation;
import org.jp.illg.nora.gateway.service.norausers.model.Request;
import org.jp.illg.nora.gateway.service.norausers.model.RequestType;
import org.jp.illg.nora.gateway.service.norausers.model.Result;
import org.jp.illg.nora.gateway.service.norausers.model.ResultType;
import org.jp.illg.nora.gateway.service.norausers.model.StatusInformation;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryTCPClient;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandler;
import org.jp.illg.util.socketio.napi.SocketIOHandlerInterface;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraUsersAPIService implements NoraGatewayStatusReportListener {

	private static final int requestInitialDelaySeconds = 1;
	private static final int statusSendPeriodMinutes = 10;
	private static final int stateRetryLimit = 5;


	private static final Pattern delimiterPattern =
		Pattern.compile("^.*(\r\n|[\n\r\u2028\u2029\u0085]){2,}$", Pattern.DOTALL);

	private static final String delimiterString = "\r\n\r\n";

	private static final String logTag =
		NoraUsersAPIService.class.getSimpleName() + " : ";

	@Getter
	@Setter
	private String apiServerAddress;

	@Getter
	@Setter
	private int apiServerPort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean versionAllow;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean versionDeny;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UUID noraId;

	private final ApplicationInformation<?> applicationVersion;

	private NoraUsersStatusReporterState currentState;
	private NoraUsersStatusReporterState nextState;
	private NoraUsersStatusReporterState callbackState;
	private boolean isStateChanged;

	private final Timer stateTimekeeper;

	private final Lock locker;

	private final SocketIO localSocketIO;

	private final SocketIOHandler<BufferEntry> localSocketIOHandler;

	private SocketIOEntryTCPClient channel;

	private int stateRetryCount;

	private ByteBuffer receiveBuffer;
	private BufferState receiveBufferState;

	private InetSocketAddress apiServerSocketAddress;

	private boolean disconnected;

	private final Timer statusTranmitIntervalTimekeeper;
	private final Timer checkVersionIntervalTimekeeper;

	private BasicStatusInformation report;

	private final String callsign;

	private final SocketIOHandlerInterface networkHandler =
		new SocketIOHandlerInterface() {

			@Override
			public void updateReceiveBuffer(
				InetSocketAddress remoteAddress, int receiveBytes
			) {
				if(log.isTraceEnabled()) {
					log.trace(logTag + "Receive from " + remoteAddress + "/" + receiveBytes + "bytes.");
				}
			}

			@Override
			public OperationRequest readEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				if(log.isTraceEnabled())
					log.trace(logTag + "Read event from " + remoteAddress + ".");

				return null;
			}

			@Override
			public void errorEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
			) {
				onSocketError(key, protocol, localAddress, remoteAddress, ex);

				if(log.isTraceEnabled()) {
					log.trace(logTag + "Connection error with " + remoteAddress + ".");
				}
			}

			@Override
			public void disconnectedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				onSocketDisconnected(key, protocol, localAddress, remoteAddress);

				if(log.isTraceEnabled()) {
					log.trace(logTag + "Disconnected from " + remoteAddress + ".");
				}
			}

			@Override
			public OperationRequest connectedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				onSocketConnected(key, protocol, localAddress, remoteAddress);

				if(log.isTraceEnabled()) {
					log.trace(logTag + "Connected to " + remoteAddress + ".");
				}

				return null;
			}

			@Override
			public OperationRequest acceptedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				return null;
			}
		};

	public NoraUsersAPIService(
		@NonNull ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ApplicationInformation<?> appVersion,
		@NonNull final SocketIO localSocketIO,
		@NonNull final String apiServerAddress,
		final int apiServerPort,
		@NonNull final String callsign
	) {
		super();

		this.applicationVersion = appVersion;

		this.callsign = callsign;

		locker = new ReentrantLock();

		versionAllow = false;
		versionDeny = false;

		currentState = NoraUsersStatusReporterState.Initialize;
		nextState = NoraUsersStatusReporterState.Initialize;
		callbackState = NoraUsersStatusReporterState.Initialize;
		stateTimekeeper = new Timer();

		this.localSocketIO = localSocketIO;
		localSocketIOHandler =
			new SocketIOHandler<>(
				networkHandler,
				this.localSocketIO, exceptionListener,
				BufferEntry.class, HostIdentType.RemoteLocalAddressPort
			);
		localSocketIOHandler.setBufferSizeTCP(1024 * 128);	//128kb
		channel = null;

		stateRetryCount = 0;

		disconnected = false;

		noraId = null;
		receiveBuffer = ByteBuffer.allocateDirect(1024 * 128);
		receiveBufferState = BufferState.INITIALIZE;
		apiServerSocketAddress = null;

		statusTranmitIntervalTimekeeper = new Timer();
		checkVersionIntervalTimekeeper = new Timer();

		report = null;

		this.apiServerAddress = apiServerAddress;
		this.apiServerPort = apiServerPort;
	}

	public void processService() {
		locker.lock();
		try {
			boolean reProcess = false;
			do {
				reProcess = false;

				isStateChanged = currentState != nextState;

				if(log.isDebugEnabled() && isStateChanged) {
					log.debug(
						logTag +
						"State changed " +
							currentState + " -> " + nextState
					);
				}

				currentState = nextState;

				switch(currentState) {
				case Initialize:
					onStateInitialize();
					break;

				case RequestIDConnect:
					onStateRequestIDConnect();
					break;

				case RequestID:
					onStateRequestID();
					break;

				case CheckVersionConnect:
					onStateCheckVersionConnect();
					break;

				case CheckVersion:
					onStateCheckVersion();
					break;

				case WaitStatusSendInterval:
					onStateWaitStatusSendInterval();
					break;

				case SendStatusConnect:
					onStateSendStatusConnect();
					break;

				case SendStatus:
					onStateSendStatus();
					break;

				case Wait:
					onStateWait();
					break;

				default:
					break;
				}

				reProcess = currentState != nextState;

			}while(reProcess);
		}finally {
			locker.unlock();
		}
	}

	@Override
	public void listenerProcess() {
		processService();
	}

	@Override
	public void report(BasicStatusInformation info) {
		if(info != null) {report = info;}
	}

	private void onSocketError(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
		locker.lock();
		try {
			switch(currentState) {
			case RequestIDConnect:
			case RequestID:
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Socket error communicate with api server on request id process, will retry in 10 seconds."
						);
					}
				}

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);

				break;

			case CheckVersion:
			case CheckVersionConnect:
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;
				}
				else {
					stateRetryCount = 0;

					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Socket error on communicate with api server on check version process, will retry in 10 seconds."
						);
					}
				}

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);

				break;

			case SendStatusConnect:
			case SendStatus:
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;

					toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.SendStatusConnect);
				}
				else {
					stateRetryCount = 0;

					nextState = NoraUsersStatusReporterState.WaitStatusSendInterval;

					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Socket error communicate with api server on send status process."
						);
					}
				}
				break;

			default:
				if(log.isDebugEnabled())
					log.debug(logTag + "Socket error on illegal state = " + currentState + ".");
				break;
			}

			closeChannel();

		}finally {
			locker.unlock();
		}

		processService();
	}

	private void onSocketDisconnected(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		locker.lock();
		try {
			if(
				apiServerSocketAddress != null && !apiServerSocketAddress.isUnresolved() &&
				apiServerSocketAddress.equals(remoteAddress)
			) {
				disconnected = true;
			}

			closeChannel();
		}finally {
			locker.unlock();
		}

		processService();
	}

	private void onSocketConnected(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		locker.lock();
		try {
			switch(currentState) {
			case RequestIDConnect:
				nextState = NoraUsersStatusReporterState.RequestID;
				disconnected = false;
				break;

			case CheckVersionConnect:
				nextState = NoraUsersStatusReporterState.CheckVersion;
				break;

			case SendStatusConnect:
				nextState = NoraUsersStatusReporterState.SendStatus;
				disconnected = false;
				break;

			default:
				break;
			}
		}finally {
			locker.unlock();
		}

		processService();
	}

	private void onStateInitialize() {
		toWaitState(
			requestInitialDelaySeconds, TimeUnit.SECONDS,
			NoraUsersStatusReporterState.RequestIDConnect
		);

		stateRetryCount = 0;
	}

	private void onStateRequestIDConnect() {
		if(isStateChanged) {
			closeChannel();

			disconnected = false;

			apiServerSocketAddress =
				new InetSocketAddress(apiServerAddress, apiServerPort);

			if(
				!apiServerSocketAddress.isUnresolved() &&
				(
					channel = localSocketIO.registTCPClient(
						apiServerSocketAddress, localSocketIOHandler,
						this.getClass().getSimpleName() + "@->" +
							apiServerAddress + ":" + apiServerPort + "(" + currentState + ")"
					)
				) != null
			) {
				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to create channel, will retry in 1 minutes.");

				toWaitState(1, TimeUnit.MINUTES, NoraUsersStatusReporterState.RequestIDConnect);
			}
		}
		else if(stateTimekeeper.isTimeout()) {
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Failed to connect to api server, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				stateRetryCount = 0;

				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to connect to api server, will retry in 30 seconds.");

				toWaitState(30, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);
			}

			closeChannel();
		}
	}

	private void onStateRequestID() {
		if(isStateChanged) {
			if(channel != null && !disconnected) {
				final Request request =
					new Request(callsign, getNoraId() != null ? getNoraId().toString() : "");
				request.setRequestType(RequestType.RequestID.getTypeName());
				final Gson gson = new GsonBuilder().create();
				final String jsonString = gson.toJson(request) + delimiterString;
				final byte [] txd = jsonString.getBytes(StandardCharsets.UTF_8);

				localSocketIOHandler.writeTCPPacket(
					channel.getKey(), ByteBuffer.wrap(txd)
				);

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Transmit to " + apiServerSocketAddress + "\n" + FormatUtil.bytesToHexDump(txd, 4)
					);
				}

				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
			else {
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;

					toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Api server connection error, retry = " + stateRetryCount + "."
						);
					}
				}
				else {
					stateRetryCount = 0;

					if(log.isWarnEnabled())
						log.warn(logTag + "Api server connection error, will retry in 10 minutes.");

					toWaitState(10, TimeUnit.MINUTES, NoraUsersStatusReporterState.RequestIDConnect);
				}

				closeChannel();
			}
		}
		else if(storeReceiveData2Buffer()) {
			receiveBufferState = BufferState.toREAD(receiveBuffer, receiveBufferState);
			final int savePosition = receiveBuffer.position();
			final byte[] receiveData = new byte[receiveBuffer.remaining()];
			receiveBuffer.get(receiveData);
			final String jsonString = new String(receiveData, StandardCharsets.UTF_8);

			if(delimiterPattern.matcher(jsonString).matches()) {
				final Gson gson = new GsonBuilder().create();

				Result result = null;
				try {
					result = gson.fromJson(jsonString, Result.class);
				}catch(JsonSyntaxException ex) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Systax error occurred at receive data from apiserver.", ex);

					result = null;
				}

				UUID id = null;

				if(result != null) {
					final ResultType resultType =
						ResultType.getTypeByValueIgnoreCase(result.getResultType());
					String idString = null;
					if(
						resultType != null && resultType == ResultType.ACK &&
						result.getResults() != null && !result.getResults().isEmpty() &&
						(idString = result.getResults().get("id")) != null
					) {
						try {
							id = UUID.fromString(idString);
						}catch(IllegalArgumentException ex) {
							if(log.isDebugEnabled())
								log.debug(logTag + "Illegal id format = " + idString + ".");

							id = null;
						}
					}
				}

				if(id != null) {
					setNoraId(id);

					stateRetryCount = 0;

					nextState = NoraUsersStatusReporterState.CheckVersionConnect;

					if(log.isInfoEnabled())
						log.info(logTag + "Nora ID = " + id + ".");
				}
				else {
					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Could not parse id data from api server.\n" + FormatUtil.bytesToHexDump(receiveData, 4)
						);
					}

					toWaitState(30, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);
				}

				closeChannel();
			}
			else {
				receiveBuffer.position(savePosition);
			}
		}
		else if(stateTimekeeper.isTimeout()){
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Could not found api server response for request id process, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				stateRetryCount = 0;

				if(log.isWarnEnabled())
					log.warn(logTag + "Could not found api server response for request id process, will retry in 30 seconds.");

				toWaitState(30, TimeUnit.SECONDS, NoraUsersStatusReporterState.RequestIDConnect);
			}

			closeChannel();
		}
	}

	private void onStateCheckVersionConnect() {
		if(isStateChanged) {
			closeChannel();

			disconnected = false;

			apiServerSocketAddress =
				new InetSocketAddress(apiServerAddress, apiServerPort);

			if(
				apiServerSocketAddress.isUnresolved() ||
				(
					channel = localSocketIO.registTCPClient(
						apiServerSocketAddress, localSocketIOHandler,
						this.getClass().getSimpleName() + "@->" +
							apiServerAddress + ":" + apiServerPort + "(" + currentState + ")"
					)
				) == null
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to create channel, will retry in 1 minutes.");

				toWaitState(1, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);
			}
			else {
				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
		}
		else if(stateTimekeeper.isTimeout()) {
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Failed to connect to api server, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				stateRetryCount = 0;

				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to connect to api server, will retry in 30 seconds.");

				toWaitState(30, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);
			}

			closeChannel();
		}
	}

	private void onStateCheckVersion() {
		if(isStateChanged) {
			if(channel != null && !disconnected) {
				final Request request =
					new Request(callsign, getNoraId() != null ? getNoraId().toString() : "");
				request.setRequestType(RequestType.CheckVersion.getTypeName());
				request.setApplicationName(applicationVersion.getApplicationName());
				request.setApplicationVersion(applicationVersion.getApplicationVersion());
				request.setRunningOsName(applicationVersion.getRunningOperatingSystem());

				final Gson gson = new GsonBuilder().create();
				final String jsonString = gson.toJson(request) + delimiterString;
				final byte [] txd = jsonString.getBytes(StandardCharsets.UTF_8);

				localSocketIOHandler.writeTCPPacket(
					channel.getKey(), ByteBuffer.wrap(txd)
				);

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Transmit to " + apiServerSocketAddress + "\n" + FormatUtil.bytesToHexDump(txd, 4)
					);
				}

				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
			else {
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;

					toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Api server connection error, retry = " + stateRetryCount + "."
						);
					}
				}
				else {
					stateRetryCount = 0;

					if(log.isWarnEnabled())
						log.warn(logTag + "Api server connection error, will retry in 30 seconds.");

					toWaitState(30, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);
				}

				closeChannel();
			}
		}
		else if(storeReceiveData2Buffer()) {
			receiveBufferState = BufferState.toREAD(receiveBuffer, receiveBufferState);
			final int savePosition = receiveBuffer.position();
			final byte[] receiveData = new byte[receiveBuffer.remaining()];
			receiveBuffer.get(receiveData);
			final String jsonString = new String(receiveData, StandardCharsets.UTF_8);

			if(delimiterPattern.matcher(jsonString).matches()) {
				final Gson gson = new GsonBuilder().create();

				Result result = null;
				try {
					result = gson.fromJson(jsonString, Result.class);
				}catch(JsonSyntaxException ex) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Systax error occurred at receive data from apiserver.", ex);

					result = null;
				}

				if(result != null) {
					final ResultType resultType =
						ResultType.getTypeByValueIgnoreCase(result.getResultType());

					if(resultType != null) {
						if(resultType == ResultType.ACK) {
							setVersionAllow(true);
							setVersionDeny(false);
						}
						else if(resultType == ResultType.NAK) {
							setVersionAllow(false);
							setVersionDeny(true);
						}
					}
				}

				stateRetryCount = 0;

				if(log.isDebugEnabled())
					log.debug(logTag + "Check version result = Allow:" + isVersionAllow() + " / " + "Deny:" + isVersionDeny());

				if(isVersionAllow() || isVersionDeny()) {
					nextState = NoraUsersStatusReporterState.WaitStatusSendInterval;

					checkVersionIntervalTimekeeper.updateTimestamp();
				}
				else {
					toWaitState(1, TimeUnit.MINUTES, NoraUsersStatusReporterState.CheckVersionConnect);
				}

				closeChannel();
			}
			else {
				receiveBuffer.position(savePosition);
			}
		}
		else if(stateTimekeeper.isTimeout()){
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Could not found api server response for check version process, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				stateRetryCount = 0;

				if(log.isWarnEnabled())
					log.warn(logTag + "Could not found api server response for check version process, will retry in 10 seconds.");
			}

			toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.CheckVersionConnect);

			closeChannel();
		}
	}

	private void onStateWaitStatusSendInterval() {
		if(checkVersionIntervalTimekeeper.isTimeout(1, TimeUnit.DAYS)) {
			checkVersionIntervalTimekeeper.updateTimestamp();

			nextState = NoraUsersStatusReporterState.CheckVersionConnect;
		}
		else if(
			statusTranmitIntervalTimekeeper.isTimeout(statusSendPeriodMinutes, TimeUnit.MINUTES) &&
			report != null
		) {
			statusTranmitIntervalTimekeeper.updateTimestamp();

			nextState = NoraUsersStatusReporterState.SendStatusConnect;
		}
	}

	private void onStateSendStatusConnect() {
		if(isStateChanged) {
			closeChannel();

			disconnected = false;

			apiServerSocketAddress =
				new InetSocketAddress(apiServerAddress, apiServerPort);

			if(
				apiServerSocketAddress.isUnresolved() ||
				(
					channel = localSocketIO.registTCPClient(
						apiServerSocketAddress, localSocketIOHandler,
						this.getClass().getSimpleName() + "@->" +
							apiServerAddress + ":" + apiServerPort + "(" + currentState + ")"
					)
				) == null
			) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to create channel, will retry in 10 minutes.");

				toWaitState(10, TimeUnit.MINUTES, NoraUsersStatusReporterState.SendStatusConnect);
			}
			else {
				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
		}
		else if(stateTimekeeper.isTimeout()) {
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;

				toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.SendStatusConnect);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Failed to connect to api server, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				stateRetryCount = 0;

				if(log.isWarnEnabled())
					log.warn(logTag + "Failed to connect to api server, will retry in 10 minutes.");

				toWaitState(10, TimeUnit.MINUTES, NoraUsersStatusReporterState.WaitStatusSendInterval);
			}

			closeChannel();
		}
	}

	private void onStateSendStatus() {
		if(isStateChanged) {
			if(channel != null && !disconnected) {
				final Request request =
					new Request(callsign, getNoraId() != null ? getNoraId().toString() : "");
				request.setRequestType(RequestType.UpdateStatusInformation.getTypeName());
				request.setStatusInformation(convertStatus(noraId, applicationVersion, report));
				final Gson gson = new GsonBuilder().create();
				final String jsonString = gson.toJson(request) + delimiterString;
				final byte[] txd = jsonString.getBytes(StandardCharsets.UTF_8);

				localSocketIOHandler.writeTCPPacket(
					channel.getKey(), ByteBuffer.wrap(txd)
				);

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Transmit to " + apiServerSocketAddress + "\n" + FormatUtil.bytesToHexDump(txd, 4)
					);
				}

				stateTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimekeeper.updateTimestamp();
			}
			else {
				if(stateRetryCount < stateRetryLimit) {
					stateRetryCount++;

					toWaitState(10, TimeUnit.SECONDS, NoraUsersStatusReporterState.SendStatusConnect);

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Api server connection error, retry = " + stateRetryCount + "."
						);
					}
				}
				else {
					stateRetryCount = 0;

					if(log.isWarnEnabled())
						log.warn(logTag + "Api server connection error, will retry in 10 minutes.");

					toWaitState(10, TimeUnit.MINUTES, NoraUsersStatusReporterState.WaitStatusSendInterval);
				}

				closeChannel();
			}
		}
		else if(disconnected || stateTimekeeper.isTimeout()) {
			stateRetryCount = 0;

			if(log.isDebugEnabled()) {
				log.debug(logTag + "Status transmit completed.");
			}

			nextState = NoraUsersStatusReporterState.WaitStatusSendInterval;

			closeChannel();
		}
	}

	private void onStateWait() {
		if(stateTimekeeper.isTimeout()) {nextState = callbackState;}
	}

	private void toWaitState(
		int time, final TimeUnit timeUnit,
		NoraUsersStatusReporterState callbackState
	) {
		assert timeUnit != null && callbackState != null;

		if(time < 0) {time = 0;}

		if(time > 0) {
			nextState = NoraUsersStatusReporterState.Wait;
			this.callbackState = callbackState;
			stateTimekeeper.setTimeoutTime(time, timeUnit);
		}
		else {
			nextState = callbackState;
		}
	}

	private boolean storeReceiveData2Buffer() {
		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = localSocketIOHandler.getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for (
					final Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator();
					itBufferBytes.hasNext();
				) {
					final PacketInfo packetInfo = itBufferBytes.next();
					final int bufferLength = packetInfo.getPacketBytes();
					itBufferBytes.remove();

					if (bufferLength <= 0) {continue;}

					final ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
					for (int i = 0; i < bufferLength; i++) {
						receivePacket.put(buffer.getBuffer().get());
					}
					BufferState.toREAD(receivePacket, BufferState.WRITE);

					if(log.isTraceEnabled()) {
						final StringBuilder sb = new StringBuilder(logTag);
						sb.append(bufferLength);
						sb.append(" bytes received from ");
						sb.append(buffer.getRemoteAddress().toString());
						sb.append(".\n");
						sb.append(FormatUtil.byteBufferToHexDump(receivePacket, 4));
						log.trace(sb.toString());

						receivePacket.rewind();
					}

					if(
						apiServerSocketAddress != null && !apiServerSocketAddress.isUnresolved() &&
						apiServerSocketAddress.equals(buffer.getRemoteAddress())
					) {
						receiveBufferState = BufferState.toWRITE(receiveBuffer, receiveBufferState);
						if(receiveBuffer.remaining() >= receivePacket.remaining()) {
							receiveBuffer.put(receivePacket);
						}
						else {
							final int overflowBytes =
								receivePacket.remaining() - receiveBuffer.remaining();
							final int newBufferSize =
								(receiveBuffer.capacity() + overflowBytes) * 2;
							final ByteBuffer newBuffer =
								ByteBuffer.allocateDirect(newBufferSize);
							receiveBufferState = BufferState.toREAD(receiveBuffer, receiveBufferState);
							BufferState.toWRITE(newBuffer, BufferState.INITIALIZE);
							newBuffer.put(receiveBuffer);
							newBuffer.put(receivePacket);
							BufferState.toREAD(newBuffer, BufferState.WRITE);
							receiveBuffer = newBuffer;
							receiveBufferState = BufferState.READ;

							if(log.isDebugEnabled()) {
								log.debug(
									logTag +
									"Receive buffer overflow = " + overflowBytes + "bytes, new " +
										newBufferSize + " bytes buffer realocated."
								);
							}
						}

						update = true;
					}
				}

				buffer.setUpdate(false);

			}finally{
				buffer.getLocker().unlock();
			}
		}

		return update;
	}

	private static StatusInformation convertStatus(
		final UUID id,
		final ApplicationInformation<?> applicationInformation,
		final BasicStatusInformation status
	) {
		final StatusInformation info = new StatusInformation();
		info.setId(id.toString());
		info.setUptimeSeconds(status.getApplicationUptime());
		info.setApplicationName(status.getApplicationName());
		info.setApplicationVersion(status.getApplicationVersion());
		info.setApplicationRunningOS(status.getApplicationRunningOS());

		info.setBuildTime(applicationInformation.getBuildTime());
		info.setBuilderName(applicationInformation.getBuilderName());
		info.setBuilderEMail(applicationInformation.getBuilderEMail());

		info.setGitBranchName(applicationInformation.getGitBranchName());
		info.setGitCommitID(applicationInformation.getGitCommitID());
		info.setGitCommitterName(applicationInformation.getGitCommitterName());
		info.setGitCommitterEMail(applicationInformation.getGitCommitterEMail());
		info.setGitCommitTime(applicationInformation.getGitCommitTime());
		info.setGitDirty(applicationInformation.isGitDirty());

		final GatewayStatusReport gatewayStatus = status.getGatewayStatusReport();
		if(gatewayStatus != null) {
			final GatewayInformation gatewayInfo = new GatewayInformation();
			gatewayInfo.setGatewayCallsign(gatewayStatus.getGatewayCallsign());
			gatewayInfo.setLastHeardCallsign(gatewayStatus.getLastHeardCallsign());
			gatewayInfo.setLatitude(gatewayStatus.getLatitude());
			gatewayInfo.setLongitude(gatewayStatus.getLongitude());
			gatewayInfo.setDescription1(gatewayStatus.getDescription1());
			gatewayInfo.setDescription2(gatewayStatus.getDescription2());
			gatewayInfo.setUrl(gatewayStatus.getUrl());
			gatewayInfo.setUseProxy(gatewayStatus.isUseProxy());
			gatewayInfo.setProxyServerAddress(gatewayStatus.getProxyServerAddress());
			gatewayInfo.setProxyServerPort(gatewayStatus.getProxyServerPort());
			gatewayInfo.setScope(
				gatewayStatus.getScope() != null ?
					gatewayStatus.getScope().getTypeName() : AccessScope.Unknown.getTypeName()
			);
			gatewayInfo.setName(gatewayStatus.getName());
			gatewayInfo.setLocation(gatewayStatus.getLocation());
			gatewayInfo.setDashboardUrl(gatewayStatus.getDashboardUrl());

			info.setGatewayInformation(gatewayInfo);

			info.setScope(gatewayInfo.getScope());
		}

		final RepeaterInformation[] repeaterInfos =
			new RepeaterInformation[status.getRepeaterStatusReports() != null ? status.getRepeaterStatusReports().size() : 0];
		info.setRepeaterInformation(repeaterInfos);
		for(int i = 0; i < repeaterInfos.length; i++) {
			final RepeaterStatusReport repeaterStatus = status.getRepeaterStatusReports().get(i);
			if(repeaterStatus == null) {continue;}

			final RepeaterInformation repeaterInfo = new RepeaterInformation();
			repeaterInfos[i] = repeaterInfo;

			repeaterInfo.setRepeaterCallsign(repeaterStatus.getRepeaterCallsign());
			repeaterInfo.setRepeaterType(
				repeaterStatus.getRepeaterType() != null ?
					repeaterStatus.getRepeaterType().getTypeName() : RepeaterTypes.Unknown.getTypeName()
			);
			repeaterInfo.setLinkedReflectorCallsign(repeaterStatus.getLinkedReflectorCallsign());
			repeaterInfo.setRoutingService(
				repeaterStatus.getRoutingService() != null ?
					repeaterStatus.getRoutingService().getTypeName() : RoutingServiceTypes.Unknown.getTypeName()
			);
			repeaterInfo.setLastHeardCallsign(repeaterStatus.getLastHeardCallsign());
			repeaterInfo.setFrequency(repeaterStatus.getFrequency());
			repeaterInfo.setFrequencyOffset(repeaterStatus.getFrequencyOffset());
			repeaterInfo.setRange(repeaterStatus.getRange());
			repeaterInfo.setLatitude(repeaterStatus.getLatitude());
			repeaterInfo.setLongitude(repeaterStatus.getLongitude());
			repeaterInfo.setAgl(repeaterStatus.getAgl());
			repeaterInfo.setDescription1(repeaterStatus.getDescription1());
			repeaterInfo.setDescription2(repeaterStatus.getDescription2());
			repeaterInfo.setUrl(repeaterStatus.getUrl());
			repeaterInfo.setScope(
				repeaterStatus.getScope() != null ?
					repeaterStatus.getScope().getTypeName() : AccessScope.Unknown.getTypeName()
			);
			repeaterInfo.setName(repeaterStatus.getName());
			repeaterInfo.setLocation(repeaterStatus.getLocation());
			repeaterInfo.getRepeaterProperties().putAll(repeaterStatus.getRepeaterProperties());

			final ModemInformation[] modemInfos =
				new ModemInformation[repeaterStatus.getModemReports() != null ? repeaterStatus.getModemReports().size() : 0];
			repeaterInfo.setModemInformation(modemInfos);
			for(int m = 0; m < modemInfos.length; m++) {
				final ModemStatusReport modemStatus = repeaterStatus.getModemReports().get(m);
				if(modemStatus == null) {continue;}

				final ModemInformation modemInfo = new ModemInformation();
				modemInfos[m] = modemInfo;

				modemInfo.setModemId(modemStatus.getModemId());
				modemInfo.setModemType(
					modemStatus.getModemType() != null ?
						modemStatus.getModemType().getTypeName() : ModemTypes.Unknown.getTypeName()
				);
				modemInfo.setScope(
					modemStatus.getScope() != null ? modemStatus.getScope().getTypeName() : AccessScope.Unknown.getTypeName()
				);
				final Map<String, String> modemProperties = new HashMap<>();
				modemInfo.setModemProperties(modemProperties);
				if(modemStatus.getModemProperties() != null && !modemStatus.getModemProperties().isEmpty()) {
					modemProperties.putAll(modemStatus.getModemProperties());
				}
			}
		}

		final ReflectorInformation[] reflectorInfos =
			new ReflectorInformation[status.getReflectorStatusReports() != null ? status.getReflectorStatusReports().size() : 0];
		info.setReflectorInformation(reflectorInfos);
		for(int i = 0; i < reflectorInfos.length; i++) {
			final ReflectorStatusReport reflectorStatus = status.getReflectorStatusReports().get(i);
			if(reflectorStatus == null) {continue;}

			final ReflectorInformation reflectorInfo = new ReflectorInformation();
			reflectorInfos[i] = reflectorInfo;

			reflectorInfo.setReflectorType(
				reflectorStatus.getReflectorType() != null?
					reflectorStatus.getReflectorType().getTypeName() : ReflectorProtocolProcessorTypes.Unknown.getTypeName()
			);
			reflectorInfo.setIncomingLink(reflectorStatus.isEnableIncomingLink());
			reflectorInfo.setOutgoingLink(reflectorStatus.isEnableOutgoingLink());
			reflectorInfo.setConnectedIncomingLink(reflectorStatus.getConnectedIncomingLink());
			reflectorInfo.setConnectedOutgoingLink(reflectorStatus.getConnectedOutgoingLink());
			reflectorInfo.setIncomingLinkPort(reflectorStatus.getIncomingLinkPort());
			reflectorInfo.setIncomingStatus(reflectorStatus.getIncomingStatus());
			reflectorInfo.setOutgoingStatus(reflectorStatus.getOutgoingStatus());
		}

		return info;
	}

	private void closeChannel() {
		if(channel != null) {
			SocketIOHandler.closeChannel(channel);
			channel = null;
		}
	}
}
