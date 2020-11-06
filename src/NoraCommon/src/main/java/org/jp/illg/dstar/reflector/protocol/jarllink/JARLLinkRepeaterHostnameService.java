package org.jp.illg.dstar.reflector.protocol.jarllink;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.Timer;
import org.jp.illg.util.dns.DNSRoundrobinUtil;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryTCPClient;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.model.OperationSet;
import org.jp.illg.util.socketio.napi.SocketIOHandler;
import org.jp.illg.util.socketio.napi.SocketIOHandlerInterface;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JARLLinkRepeaterHostnameService {

	/**
	 * レピータリスト取得間隔(分)
	 */
	private static final int repeaterListProcessIntervalMinutes = 30;

	/**
	 * レピータリスト起動後初回取得遅延時間(分)
	 */
	private static final int repeaterListProcessInitialDelayMinutes = 1;

	/**
	 * レピータリスト取得に失敗した場合の再取得待機時間(分)
	 */
	private static final int repeaterListProcessFaultWaitMinutes = 10;

	/**
	 * レピータリスト取得再試行リミット
	 */
	private static final int repeaterListProcessRetryLimit = 3;

	private class ConnectedTable{
		@Getter
		@Setter
		private long UpdateTimestamp;

		@Getter
		@Setter
		private List<ConnectedRepeaterInfo> ConnectedTable;

		@Override
		public String toString() {return toString(0);}

		public String toString(int indent) {
			if(indent < 0) {indent = 0;}
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < indent; i++) {sb.append(' ');}

			if(ConnectedTable != null) {
				sb.append("ConnectedTable:\n");
				for(ConnectedRepeaterInfo repeater : ConnectedTable) {
					sb.append(repeater.toString(indent + 4));
					sb.append("\n");
				}
			}

			return sb.toString();
		}
	}

	private class ConnectedRepeaterInfo{
		@Getter
		@Setter
		private String callsign;

		@Getter
		@Setter
		private String ip_address;

		@Getter
		@Setter
		private String port;

		@Getter
		@Setter
		private String status;

		@Getter
		@Setter
		private String area;

		@Override
		public String toString() {return toString(0);}

		public String toString(int indent) {
			if(indent < 0) {indent = 0;}
			StringBuilder sb = new StringBuilder();
			for(int i = 0; i < indent; i++) {sb.append(' ');}

			sb.append("callsign:");
			sb.append(callsign);
			sb.append("/ip_address:");
			sb.append(String.format("%-15s", ip_address));
			sb.append("/port:");
			sb.append(String.format("%-5s", port));
			sb.append("/status:");
			sb.append(status);
			sb.append("/area:");
			sb.append(area);

			return sb.toString();
		}
	}

	/**
	 * ログヘッダ
	 */
	private static final String logTag = JARLLinkRepeaterHostnameService.class.getSimpleName() + " : ";

	/**
	 * hole-punch管理サーバアドレス
	 */
	@Getter
	@Setter
	private String serverAddress;
	private static final String serverAddressDefault = "mfrptlst.k-dk.net";

	/**
	 * hole-punch管理サーバポート
	 */

	@Getter
	@Setter
	private int serverPort;
	private static final int serverPortDefault = 30011;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetSocketAddress serverAddressPort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private SelectionKey serverConnectionKey;

	@Getter(AccessLevel.PRIVATE)
	private final JARLLinkCommunicationService service;

	private final DNSRoundrobinUtil connectionServerAddressResolver;

	private final SocketIO localSocketIO;

	private SocketIOEntryTCPClient serverChannel;
	private final Lock serverChannelLocker;
	private final Timer stateTimeKeeper;

	private final ByteBuffer receiveBuffer;
	private BufferState receiveBufferState;
	private final Timer receiveBufferTimekeeper;

	private enum RepeaterHostnameUpdateState{
		WaitPeriod,
		Connecting,
		SendRequest,
		Wait,
		;
	}
	private RepeaterHostnameUpdateState currentState;
	private RepeaterHostnameUpdateState nextState;
	private RepeaterHostnameUpdateState callbackState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	private int stateRetryCount;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean connected;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean connectionError;

	private Map<InetAddress, ConnectedTable> connectedTable;

	private final SocketIOHandler<BufferEntry> localSocketIOHandler;

	private final ExecutorService executor;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private final SocketIOHandlerInterface networkHandler =
		new SocketIOHandlerInterface() {

			@Override
			public void updateReceiveBuffer(
				InetSocketAddress remoteAddress, int receiveBytes
			) {
				if(log.isTraceEnabled()) {
					log.trace(logTag + "Receive from " + remoteAddress + "/" + receiveBytes + "bytes.");
				}

				onSocketReceiveData();
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
				onSocketError(ex);

				if(log.isTraceEnabled()) {
					log.trace(logTag + "Connection error with " + remoteAddress + ".");
				}
			}

			@Override
			public void disconnectedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
//				onSocketDisconnected(key);

				if(log.isTraceEnabled()) {
					log.trace(logTag + "Disconnected from " + remoteAddress + ".");
				}
			}

			@Override
			public OperationRequest connectedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				if(log.isTraceEnabled()) {
					log.trace(logTag + "Connected to " + remoteAddress + ".");
				}

				return onSocketConnected(key);
			}

			@Override
			public OperationRequest acceptedEvent(
				SelectionKey key, ChannelProtocol protocol,
				InetSocketAddress localAddress, InetSocketAddress remoteAddress
			) {
				return null;
			}
		};

	public JARLLinkRepeaterHostnameService(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final JARLLinkCommunicationService service,
		@NonNull final SocketIO localSocketIO,
		@NonNull DNSRoundrobinUtil connectionServerAddressResolver,
		@NonNull ExecutorService executor
	) {
		super();

		this.exceptionListener = exceptionListener;
		this.service = service;
		this.executor = executor;

		serverChannelLocker = new ReentrantLock();
		stateTimeKeeper = new Timer(
			repeaterListProcessInitialDelayMinutes,
			TimeUnit.MINUTES
		);

		receiveBuffer = ByteBuffer.allocateDirect(1024 * 512);
		receiveBufferState = BufferState.INITIALIZE;
		receiveBufferTimekeeper = new Timer(100000);

		setServerAddress(serverAddressDefault);
		setServerPort(serverPortDefault);
		setServerAddressPort(null);

		currentState = RepeaterHostnameUpdateState.WaitPeriod;
		nextState = RepeaterHostnameUpdateState.WaitPeriod;
		callbackState = RepeaterHostnameUpdateState.WaitPeriod;
		stateRetryCount = 0;
		connectedTable = null;

		this.connectionServerAddressResolver = connectionServerAddressResolver;

		this.localSocketIO = localSocketIO;
		localSocketIOHandler =
			new SocketIOHandler<>(
				networkHandler,
				this.localSocketIO, exceptionListener,
				BufferEntry.class, HostIdentType.RemoteLocalAddressPort
			);
		localSocketIOHandler.setBufferSizeTCP(1024 * 1024);	//1MB

		setConnectionError(false);
		setConnected(false);
	}

	private OperationRequest onSocketConnected(SelectionKey key) {
		if(log.isTraceEnabled())
			log.trace(logTag + "Connected event, state=" + currentState);

		OperationRequest ops = null;

		serverChannelLocker.lock();
		try {
			if(
				key.equals(getServerConnectionKey()) &&
				currentState == RepeaterHostnameUpdateState.Connecting
			){
				setConnected(true);

				ops = new OperationRequest();
				ops.addUnsetRequest(OperationSet.CONNECT);
				ops.addSetRequest(OperationSet.READ);
			}
		}finally {serverChannelLocker.unlock();}

		process();

		return ops;
	}

	private void onSocketError(Exception ex) {
		serverChannelLocker.lock();
		try {
			if(
				currentState == RepeaterHostnameUpdateState.Connecting ||
				currentState == RepeaterHostnameUpdateState.SendRequest
			) {
				setConnectionError(true);

				if(log.isWarnEnabled())
					log.warn(logTag + "Server connection error occurred.", ex);
			}
		}finally {serverChannelLocker.unlock();}

		process();
	}

	private void onSocketReceiveData() {
		Optional<BufferEntry> opEntry = null;
		while((opEntry = localSocketIOHandler.getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				final boolean terminate =
					Stream.of(buffer.getBufferPacketInfo())
					.anyMatch(new Predicate<PacketInfo>() {
						@Override
						public boolean test(final PacketInfo packetInfo) {
							return packetInfo.getPacketBytes() == 0;
						}
					});

				process(buffer.getBuffer(), terminate);

				buffer.getBufferPacketInfo().clear();
				buffer.getBuffer().clear();
				buffer.setBufferState(BufferState.INITIALIZE);

				buffer.setUpdate(false);
			}finally {
				buffer.getLocker().unlock();
			}
		}
	}

	public boolean isActive() {
		return currentState != RepeaterHostnameUpdateState.WaitPeriod &&
			currentState != RepeaterHostnameUpdateState.Wait;
	}

	public ThreadProcessResult process() {
		return process(null, false);
	}

	public ThreadProcessResult process(ByteBuffer receiveData, boolean terminate) {
		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		serverChannelLocker.lock();
		try {
			if(receiveData != null) {
				final BufferUtilObject putResult =
					BufferUtil.putBuffer(
						logTag, receiveBuffer, receiveBufferState, receiveBufferTimekeeper, receiveData
					);
				receiveBufferState = putResult.getBufferState();
			}

			boolean reProcess;
			do {
				reProcess = false;

				setStateChanged(currentState != nextState);
				currentState = nextState;

				switch(currentState) {
				case WaitPeriod:
					processResult = onStateWaitPeriod();
					break;

				case Connecting:
					processResult = onStateConnecting();
					break;

				case SendRequest:
					processResult = onStateSendRequest(terminate);
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

			Optional<InetAddress> currentConnectionSrv = null;
			if(
				connectedTable != null &&
				(currentConnectionSrv = connectionServerAddressResolver.getCurrentHostAddress()).isPresent()
			) {
				final InetAddress addr = currentConnectionSrv.get();
				final Map<InetAddress, ConnectedTable> tables = connectedTable;

				executor.submit(new RunnableTask(exceptionListener) {
					@Override
					public void task() {
						registRepeaterTableToHostnameService(
							tables, addr,
							getServerAddress(), getServerPort(),
							getService()
						);
					}
				});

				connectedTable = null;
			}
		}finally {
			serverChannelLocker.unlock();
		}

		return processResult;
	}

	private ThreadProcessResult onStateWaitPeriod() {
		if(stateTimeKeeper.isTimeout()) {

			nextState = RepeaterHostnameUpdateState.Connecting;

			stateRetryCount = 0;

			closeServerChannel();
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateConnecting() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			stateTimeKeeper.updateTimestamp();

			closeServerChannel();

			setServerAddressPort(new InetSocketAddress(getServerAddress(), getServerPort()));

			if(getServerAddressPort() != null && !getServerAddressPort().isUnresolved()) {
				serverChannel =
					localSocketIO.registTCPClient(
						getServerAddressPort(),
						localSocketIOHandler,
						this.getClass().getSimpleName() + "@->" + getServerAddress() + ":" + getServerPort()
					);

				if(serverChannel != null) {
//					stateRetryCount = 0;

					setServerConnectionKey(serverChannel.getKey());

					setConnected(false);
					setConnectionError(false);

					if(log.isTraceEnabled()) {
						log.trace(
							logTag +
							"Trying connect to JARLLink hostname server..." +
							getServerAddressPort()
						);
					}
				}
				else {
					setServerConnectionKey(null);

					if(stateRetryCount < repeaterListProcessRetryLimit) {
						stateRetryCount++;

						toWaitState(5, TimeUnit.SECONDS, RepeaterHostnameUpdateState.Connecting);

						if(log.isDebugEnabled()) {
							log.debug(
								logTag +
								"Repeater host name server channel error, retry = " + stateRetryCount + "."
							);
						}
					}
					else {
						toWaitState(
							repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
							RepeaterHostnameUpdateState.WaitPeriod
						);

						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Repeater hostname server channel error, will retry in " +
								repeaterListProcessFaultWaitMinutes + " minutes."
							);
						}
					}
				}
			}
			else {
				toWaitState(
					repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
					RepeaterHostnameUpdateState.WaitPeriod
				);

				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Repeater hostname server dns resolve error, will retry in " +
						repeaterListProcessFaultWaitMinutes + " minutes."
					);
				}
			}
		}
		else if(stateTimeKeeper.isTimeout() || isConnectionError()) {
			closeServerChannel();
			setConnectionError(false);

			if(stateRetryCount < repeaterListProcessRetryLimit) {
				stateRetryCount++;

				toWaitState(5, TimeUnit.SECONDS, RepeaterHostnameUpdateState.Connecting);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Repeater host name server " + getServerAddressPort() +
						" connect timeout, retry = " + stateRetryCount + "."
					);
				}
			}
			else {
				toWaitState(
					repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
					RepeaterHostnameUpdateState.WaitPeriod
				);

				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Repeater hostname server " + getServerAddressPort() +
						" connect timeout, will retry in " +
						repeaterListProcessFaultWaitMinutes + " minutes."
					);
				}
			}
		}
		else if(isConnected()) {
			if(log.isTraceEnabled()) {
				log.trace(
					logTag +
					"Connected to JARLLink hostname server..." + getServerAddressPort()
				);
			}

			nextState = RepeaterHostnameUpdateState.SendRequest;
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSendRequest(boolean terminate) {
		if(isStateChanged()) {
			if(
				localSocketIOHandler.writeTCPPacket(
					serverChannel.getKey(),
					ByteBuffer.wrap("REQ".getBytes(StandardCharsets.US_ASCII))
				)
			) {
				stateTimeKeeper.setTimeoutTime(1500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Sending hostname list request to JARLLink hostname server..." +
						getServerAddressPort()
					);
				}
			}
			else {
				if(stateRetryCount < repeaterListProcessRetryLimit) {
					stateRetryCount++;

					toWaitState(500, TimeUnit.MILLISECONDS, RepeaterHostnameUpdateState.SendRequest);
				}
				else {
					disconnectServerChannel();

					toWaitState(
						repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
						RepeaterHostnameUpdateState.WaitPeriod
					);

					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Repeater hostname server transmit error, will retry in " +
							repeaterListProcessFaultWaitMinutes + " minutes."
						);
					}
				}

			}
		}
		else if(stateTimeKeeper.isTimeout() || isConnectionError()) {

			disconnectServerChannel();

			if(stateRetryCount < repeaterListProcessRetryLimit) {
				stateRetryCount++;

				toWaitState(
					5, TimeUnit.SECONDS, RepeaterHostnameUpdateState.Connecting
				);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Repeater host name server " + getServerAddressPort() +
						" response timeout, retry count = " + stateRetryCount + "."
					);
				}
			}
			else {
				toWaitState(
					repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
					RepeaterHostnameUpdateState.WaitPeriod
				);

				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Repeater hostname server " + getServerAddressPort() +
						" response timeout, will retry in " +
						repeaterListProcessFaultWaitMinutes + " minutes."
					);
				}
			}

			setConnectionError(false);
		}
		else if(terminate) {
			disconnectServerChannel();

			boolean parseError = false;

			if(receiveBufferState == BufferState.READ && receiveBuffer.hasRemaining()) {

				final char[] data = new char[receiveBuffer.remaining()];
				for(int c = 0; c < data.length && receiveBuffer.hasRemaining(); c++) {
					data[c] = (char)receiveBuffer.get();
				}
				final String repeaters = String.valueOf(data);

				final GsonBuilder gsonBuilder = new GsonBuilder();
				final TypeToken<Map<InetAddress, ConnectedTable>> type =
					new TypeToken<Map<InetAddress, ConnectedTable>>() {};
				gsonBuilder.registerTypeAdapter(
					type.getType(), repeaterhostDeserializer
				);

				final Gson gson = gsonBuilder.create();

				Map<InetAddress, ConnectedTable> table = null;
				try {
					table = gson.fromJson(repeaters, type.getType());
				}catch(JsonParseException ex) {
					parseError = true;

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Illegal data received from repeater hostname server " +
							getServerAddressPort() + ", parse error.", ex
						);
					}
				}

				if(table != null) {
					connectedTable = table;

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Received repeater host table from " +
							getServerAddressPort() + ".\n" + table.toString()
						);
					}
				}
			}
			else {
				if(log.isWarnEnabled())
					log.info(logTag + "No data comes from hostname server " + getServerAddressPort() + ".");

				parseError = true;
			}

			if(!parseError) {
				toWaitState(
					repeaterListProcessIntervalMinutes, TimeUnit.MINUTES,
					RepeaterHostnameUpdateState.WaitPeriod
				);
			}
			else {
				toWaitState(
					repeaterListProcessFaultWaitMinutes, TimeUnit.MINUTES,
					RepeaterHostnameUpdateState.WaitPeriod
				);

				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Illegal data received from repeater hostname server " +
						getServerAddressPort() +
						", will retry in " +
						repeaterListProcessFaultWaitMinutes + " minutes."
					);
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(long waitTime, TimeUnit timeUnit, RepeaterHostnameUpdateState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = RepeaterHostnameUpdateState.Wait;
		this.callbackState = callbackState;
	}

	private void closeServerChannel() {
		if(serverChannel != null && serverChannel.getChannel().isOpen()) {
			SocketIOHandler.closeChannel(serverChannel);
		}
		setServerConnectionKey(null);
		serverChannel = null;

		setConnected(false);
	}

	private void disconnectServerChannel() {
		if(serverChannel != null && serverChannel.getChannel().isOpen()) {
			if(!localSocketIOHandler.disconnectTCP(serverChannel.getKey())) {
				if(log.isDebugEnabled()) {log.debug(logTag + "Disconnect failed !");}
			}
		}
		setServerConnectionKey(null);
		serverChannel = null;

		setConnected(false);
	}

	private static void registRepeaterTableToHostnameService(
		final Map<InetAddress, ConnectedTable> table,
		final InetAddress currentServer,
		final String serverAddress, final int serverPort,
		final JARLLinkCommunicationService service
	) {
		Stream.of(table.entrySet())
		.filter(new Predicate<Entry<InetAddress, ConnectedTable>>(){
			@Override
			public boolean test(Entry<InetAddress, ConnectedTable> e) {
				return e.getKey().equals(currentServer);
			}
		})
		.forEach(new Consumer<Entry<InetAddress, ConnectedTable>>(){
			@Override
			public void accept(Entry<InetAddress, ConnectedTable> e) {
				registRepeaterTableToHostnameService(e.getValue(), serverAddress, serverPort, service);
			}
		});
	}

	private static boolean registRepeaterTableToHostnameService(
		final ConnectedTable repeaterTable,
		final String serverAddress, final int serverPort,
		final JARLLinkCommunicationService service
	) {
		assert repeaterTable != null;

		final String dataSource = serverAddress +  ":" + serverPort;

		final Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts = new HashMap<>();
		for(ConnectedRepeaterInfo info : repeaterTable.getConnectedTable()) {

			final String repeaterCallsign =
				DSTARUtils.formatFullLengthCallsign(info.getCallsign());

			int portNumber = -1;
			try {
				portNumber = Integer.valueOf(info.getPort());
			}catch(NumberFormatException ex) {
				if(log.isDebugEnabled())
					log.debug(logTag + "Could not convert port number = " + info.getPort() + "@" + info.getCallsign(), ex);
			}

			ReflectorHostInfo host = null;
			if(portNumber > 0) {
				host =
					new ReflectorHostInfo(
						DSTARProtocol.JARLLink,
						portNumber, repeaterCallsign, info.getIp_address(),
						ReflectorHostInfo.priorityNormal,
						repeaterTable.getUpdateTimestamp(),
						dataSource,
						""
					);
			}
			else {
				host =
					new ReflectorHostInfo(
						DSTARProtocol.JARLLink,
						DSTARProtocol.JARLLink.getPortNumber(),
						repeaterCallsign, info.getIp_address(),
						ReflectorHostInfo.priorityNormal,
						repeaterTable.getUpdateTimestamp(),
						dataSource,
						""
					);
			}

			hosts.put(
				new ReflectorHostInfoKey(host.getReflectorCallsign(), host.getDataSource()),
				host
			);
		}

		if(log.isInfoEnabled())
			log.info(logTag + "Update " + hosts.size() + " hosts to ReflectorNameService.");

		return service.getGateway().loadReflectorHosts(hosts, dataSource, true);
	}

	private JsonDeserializer<Map<InetAddress, ConnectedTable>> repeaterhostDeserializer =
		new JsonDeserializer<Map<InetAddress, ConnectedTable>>() {

		@Override
		public Map<InetAddress, ConnectedTable> deserialize(
				JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {

			final Map<InetAddress, ConnectedTable> result = new HashMap<>();
			if(!json.isJsonObject()) {return result;}

			final JsonObject jsonObject = json.getAsJsonObject();

			for(final Entry<String, JsonElement> e : jsonObject.entrySet()) {
				long updateTimestamp = System.currentTimeMillis() / 1000;
				final JsonElement updateTimestampElement = e.getValue().getAsJsonObject().get("UpdateTimestamp");
				if(updateTimestampElement != null) {updateTimestamp = updateTimestampElement.getAsLong();}

				final JsonElement connectedTableElement = e.getValue().getAsJsonObject().get("ConnectedTable");
				if(connectedTableElement == null) {break;}

				final ConnectedRepeaterInfo[] jsonRepeaterArray =
					context.deserialize(connectedTableElement, ConnectedRepeaterInfo[].class);

				if(jsonRepeaterArray != null) {
					InetAddress server = null;
					try {
						 server = InetAddress.getByName(e.getKey());
					}catch(UnknownHostException ex) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Unknown repeater host name server.", ex);

						break;
					}

					final ConnectedTable table = new ConnectedTable();
					table.setUpdateTimestamp(updateTimestamp);
					table.setConnectedTable(new ArrayList<>(jsonRepeaterArray.length));
					result.put(server, table);

					for(int i = 0; i < jsonRepeaterArray.length; i++) {
						ConnectedRepeaterInfo repeaterInfo = jsonRepeaterArray[i];
						if(
							repeaterInfo.getCallsign() == null || "".equals(repeaterInfo.getCallsign()) ||
							!CallSignValidator.isValidJapanRepeaterCallsign(repeaterInfo.getCallsign())
						) {
							if(log.isDebugEnabled()) {
								log.debug(
									logTag +
									"Illegal host information received from JapanTrust(multi_forward).\n + " + repeaterInfo.toString(4)
								);
							}
							continue;
						}

						table.getConnectedTable().add(repeaterInfo);
					}
				}
			}

			return result;
		}
	};
/*
	private String getCurrentHostAddressString() {
		final Optional<InetAddress> addr = serverAddressResolver.getCurrentHostAddress();

		return addr.isPresent() ? addr.get().toString() : "";
	}

	private boolean isEndConnectedTable(final ByteBuffer buffer) {
		if(buffer.remaining() < 4) {return false;}

		buffer.mark();
		buffer.position(buffer.limit() - 4);

		final byte[] delimitter = new byte[4];
		for(int i = 0; i < delimitter.length && buffer.hasRemaining(); i++)
			delimitter[i] = buffer.get();

		buffer.reset();

		return Arrays.equals(delimitter, "]\n}\n".getBytes(StandardCharsets.US_ASCII));
	}
*/
}
