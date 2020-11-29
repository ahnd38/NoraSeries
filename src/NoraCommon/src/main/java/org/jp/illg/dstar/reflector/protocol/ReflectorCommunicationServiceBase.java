package org.jp.illg.dstar.reflector.protocol;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.model.events.ReflectorConnectionStateChangeEvent;
import org.jp.illg.dstar.reflector.model.events.ReflectorEvent;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionEntry;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorReceivePacket;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlReflectorHandler;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.service.web.util.WebSocketTool;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntry;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.Callback;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ReflectorCommunicationServiceBase
<
	BufferType extends BufferEntry,
	ReflectorEntryClass extends ReflectorConnectionEntry<? extends TransmitterPacket>
>
extends SocketIOHandlerWithThread<BufferType>
implements ReflectorCommunicationService, WebRemoteControlReflectorHandler{

	/**
	 * 通常時時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisNormalDefault = 100L;

	/**
	 * スリープ時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisSleepDefault = 1000L;

	/**
	 * 音声送信時処理ループ間隔ミリ秒
	 */
	private static final long processLoopIntervalTimeMillisVoiceTransmitDefault = 5L;


	private static final int eventLimit = 100;	// event queue limit
	private static final int receivePacketsLimit = 1000;


	private static class RemoteUsersUpdateEntry {
		@Getter
		private final DSTARRepeater targetRepeater;

		@Getter
		private final String remoteCallsign;

		@Getter
		private final ConnectionDirectionType connectionDir;

		@Getter
		private final List<ReflectorRemoteUserEntry> users;

		public RemoteUsersUpdateEntry(
			@NonNull final DSTARRepeater targetRepeater,
			@NonNull final String remoteCallsign,
			@NonNull final ConnectionDirectionType connectionDir,
			@NonNull final List<ReflectorRemoteUserEntry> users
		) {
			super();

			this.targetRepeater = targetRepeater;
			this.remoteCallsign = remoteCallsign;
			this.connectionDir = connectionDir;
			this.users = users;
		}
	}

	protected static enum ProcessIntervalMode {
		Normal,
		Sleep,
		VoiceTransfer,
		;
	}

	private static class EventEntry{
		@Getter
		private final ReflectorCommunicationServiceEvent event;
		@Getter
		private final Object attachment;

		public EventEntry(final ReflectorCommunicationServiceEvent event, final Object attachment) {
			this.event = event;
			this.attachment = attachment;
		}
	}

	private final String logTag;

	@Getter(AccessLevel.PROTECTED)
	private final UUID systemID;

	@Getter(AccessLevel.PROTECTED)
	private final ApplicationInformation<?> applicationInformation;

	@Getter(AccessLevel.PROTECTED)
	private final DSTARGateway gateway;

	@Getter(AccessLevel.PROTECTED)
	private final ExecutorService workerExecutor;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int transmitModCode;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private int receiveModCode;

	@Getter(AccessLevel.PROTECTED)
	@Setter(AccessLevel.PRIVATE)
	private WebRemoteControlService webRemoteControlService;

	@Getter(AccessLevel.PROTECTED)
	private final ReflectorLinkManager reflectorLinkManager;

	private boolean statusChanged;

	private static final Random modRandom;

	private final Queue<ReflectorEvent> events;
	private final Lock eventsLocker;

	private final Queue<ReflectorReceivePacket> receivePackets;
	private final Lock receivePacketsLocker;

	private final Timer notifyStatusToWebRemoteTimekeeper;

	private final Queue<RemoteUsersUpdateEntry> remoteUsersUpdateEntries;
	private final Lock remoteUsersUpdateEntriesLocker;
	private final Timer remoteUsersUpdateTimekeeper;

	protected final List<ReflectorEntryClass> entries;
	protected final Lock entriesLocker;

	private final Timer processLoopIntervalTimekeeper;

	private long requestProcessLoopIntervalTimeMillis;
	private final Timer processLoopIntervalTimeDelayTimer;

	private final EventListener<ReflectorCommunicationServiceEvent> eventListener;

	private final TaskQueue<EventEntry, Boolean> eventQueue;
	private final TaskQueue<Object, Boolean> receivePacketEventQueue;

	private final Consumer<EventEntry> eventDispacher = new Consumer<EventEntry>() {
		@Override
		public void accept(EventEntry e) {
			eventListener.event(e.getEvent(), e.getAttachment());
		}
	};

	private final Consumer<Object> receivePacketEventDispacher = new Consumer<Object>() {
		@Override
		public void accept(Object t) {
			processReceivePacket();
		}
	};

	/**
	 * スタティックイニシャライザ
	 */
	static {
		modRandom = new Random(System.currentTimeMillis());
	}

	public ReflectorCommunicationServiceBase(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final Class<?> processorClass,
		final SocketIO socketIO,
		@NonNull final Class<BufferType> bufferEntryClass,
		@NonNull final HostIdentType hostIdentType,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		super(exceptionListener, processorClass, socketIO, bufferEntryClass, hostIdentType);

		this.systemID = systemID;
		this.applicationInformation = applicationInformation;
		this.reflectorLinkManager = reflectorLinkManager;
		this.eventListener = eventListener;
		this.gateway = gateway;
		this.workerExecutor = workerExecutor;

		setProcessLoopIntervalTimeMillis(getProcessLoopIntervalTimeMillisSleep());

		logTag =
			this.getClass().getSimpleName() +
			"(" + ReflectorCommunicationServiceBase.class.getSimpleName() + ") : ";

		events = new LinkedList<>();
		eventsLocker = new ReentrantLock();

		eventQueue = new TaskQueue<>(getWorkerExecutor());

		receivePackets = new LinkedList<>();
		receivePacketsLocker = new ReentrantLock();

		receivePacketEventQueue = new TaskQueue<>(getWorkerExecutor());

		statusChanged = false;

		requestProcessLoopIntervalTimeMillis = -1;
		processLoopIntervalTimeDelayTimer = new Timer();

		notifyStatusToWebRemoteTimekeeper = new Timer();
		notifyStatusToWebRemoteTimekeeper.updateTimestamp();

		remoteUsersUpdateEntries = new ConcurrentLinkedQueue<>();
		remoteUsersUpdateEntriesLocker = new ReentrantLock();
		remoteUsersUpdateTimekeeper = new Timer();
		remoteUsersUpdateTimekeeper.updateTimestamp();

		entries = new LinkedList<>();
		entriesLocker = new ReentrantLock();

		processLoopIntervalTimekeeper = new Timer();

		generateModCode();
	}

	public ReflectorCommunicationServiceBase(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final Class<?> processorClass,
		@NonNull final Class<BufferType> bufferEntryClass,
		@NonNull final HostIdentType hostIdentType,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		this(
			systemID,
			applicationInformation,
			exceptionListener, processorClass,
			null,
			bufferEntryClass, hostIdentType,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);
	}

	protected abstract ThreadProcessResult processReceivePacket();

	@Override
	public boolean startAsync(@NonNull final Callback<Boolean> callback) {
		try {
			return workerExecutor.submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					callback.call(start());
				}
			}) != null;
		}catch(RejectedExecutionException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not schedule start process", ex);

			return start();
		}
	}

	@Override
	public boolean stopAsync(@NonNull final Callback<Boolean> callback) {
		try {
			return workerExecutor.submit(new RunnableTask(getExceptionListener()) {
				@Override
				public void task() {
					stop();

					callback.call(true);
				}
			}) != null;
		}catch(RejectedExecutionException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not schedule stop process", ex);

			stop();

			return false;
		}
	}

	@Override
	public String getApplicationName() {
		return applicationInformation.getApplicationName();
	}

	@Override
	public String getApplicationVersion() {
		return applicationInformation.getApplicationVersion();
	}

	@Override
	public final void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
		receivePacketEventQueue.addEventQueue(
			receivePacketEventDispacher, getExceptionListener()
		);
	}

	protected int getModCode() {
		return generateModCodeInt();
	}

	protected UUID generateLoopBlockID() {
		return DSTARUtils.generateLoopBlockID();
	}

	@Override
	public boolean writePacket(
		@NonNull DSTARRepeater repeater, @NonNull DSTARPacket packet,
		@NonNull ConnectionDirectionType direction
	) {
		if(
			direction == ConnectionDirectionType.Unknown ||
			packet.getPacketType() != DSTARPacketType.DV
		) {return false;}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(getTransmitModCode());

		//フラグをクリアする
		if(packet.getDVPacket().hasPacketType(PacketType.Header))
			Arrays.fill(packet.getRfHeader().getFlags(), (byte)0x00);

		if(log.isTraceEnabled())
			log.trace(logTag + "writePacket\n" + packet.toString(4));

		final boolean isSuccess = writePacketInternal(repeater, packet, direction);

		return isSuccess;
	}

	public abstract boolean writePacketInternal(
		DSTARRepeater repeater, DSTARPacket packet, ConnectionDirectionType direction
	);

	@Override
	public DSTARPacket readPacket(@NonNull DSTARRepeater repeater) {

		final DSTARPacket packet = readPacketInternal(repeater);

		if(packet != null) {
			//フレームIDを改変する
			packet.getBackBone().modFrameID(getReceiveModCode());

			//フラグをクリアする
			if(packet.getDVPacket().hasPacketType(PacketType.Header))
				Arrays.fill(packet.getRfHeader().getFlags(), (byte)0x00);
		}

		return packet;
	}

	@Override
	public ReflectorEvent getReflectorEvent() {
		ReflectorEvent event = null;

		eventsLocker.lock();
		try {
			if(!events.isEmpty()) {event = this.events.poll();}
		}finally {eventsLocker.unlock();}

		return event;
	}

	@Override
	public boolean writeUDP(SelectionKey key, InetSocketAddress dstAddress, ByteBuffer buffer) {
		return super.writeUDP(key, dstAddress, buffer);
	}

	@Override
	public boolean writeUDPPacket(SelectionKey key, InetSocketAddress dstAddress, ByteBuffer buffer) {
		return super.writeUDPPacket(key, dstAddress, buffer);
	}

	@Override
	public boolean writeTCP(SelectionKey key, ByteBuffer buffer) {
		return super.writeTCP(key, buffer);
	}

	@Override
	public boolean writeTCPPacket(SelectionKey key, ByteBuffer buffer) {
		return super.writeTCPPacket(key, buffer);
	}

	@Override
	public void closeChannel(SocketIOEntry<? extends SelectableChannel> entry) {
		super.closeChannel(entry);
	}

	@Override
	public boolean disconnectTCP(SelectionKey key) {
		return super.disconnectTCP(key);
	}

	@Override
	public boolean isLinked(DSTARRepeater repeater, ConnectionDirectionType connectionDir) {
		final List<ReflectorLinkInformation> linkInfo =
				getLinkInformation(repeater, connectionDir);

		return !linkInfo.isEmpty();
	}

	@Override
	public Optional<ReflectorLinkInformation> getLinkInformationOutgoing(DSTARRepeater repeater) {
		final List<ReflectorLinkInformation> linkInfo =
				getLinkInformation(repeater, ConnectionDirectionType.OUTGOING);

		if(!linkInfo.isEmpty())
			return Optional.of(linkInfo.get(0));
		else
			return Optional.empty();
	}

	@Override
	public List<ReflectorLinkInformation> getLinkInformationIncoming(DSTARRepeater repeater){
		return getLinkInformation(repeater, ConnectionDirectionType.INCOMING);
	}

	@Override
	public List<ReflectorLinkInformation> getLinkInformation(DSTARRepeater repeater){
		return getLinkInformation(repeater, null);
	}

	@Override
	public List<ReflectorLinkInformation> getLinkInformation() {
		return getLinkInformation(null, null);
	}

	@Override
	public String getWebSocketRoomId() {
		return WebSocketTool.formatRoomId(
			getGateway().getGatewayCallsign(), getProcessorType().toString()
		);
	}

	@Override
	public ReflectorProtocolProcessorTypes getReflectorType() {
		return getProcessorType();
	}

	@Override
	protected ThreadProcessResult processThread() {

		final ThreadProcessResult processResult = processLoop();

		cleanupReflectorReceivePackets();

		cleanupReflectorEvent();

		processWebRemoteControlService();

		processReflectorRemoteUsersUpdate();

		return processResult;
	}

	@Override
	public final boolean initializeWebRemoteControl(
		@NonNull WebRemoteControlService webRemoteControlService
	) {
		setWebRemoteControlService(webRemoteControlService);

		return initializeWebRemoteControlInternal(webRemoteControlService);
	}

	@Override
	public final List<ReflectorConnectionData> getReflectorConnections() {
		final List<ReflectorConnectionData> connections = new LinkedList<>();

		if(getReflectorConnectionsInternal(connections))
			return connections;
		else
			return new LinkedList<>();
	}

	@Override
	public final ReflectorStatusData createStatusData() {
		final ReflectorStatusData status = createStatusDataInternal();
		if(status == null)
			throw new NullPointerException("Status data must not null.");

		status.setWebSocketRoomId(getWebSocketRoomId());
		status.setReflectorType(getReflectorType());
		status.setConnections(getReflectorConnections());

		return status;
	}

	@Override
	public final Class<? extends ReflectorStatusData> getStatusDataType() {
		return getStatusDataTypeInternal();
	}

	@Override
	public WebRemoteControlReflectorHandler getWebRemoteControlHandler() {
		return this;
	}

	protected boolean isEnableWebRemoteControl() {
		return getWebRemoteControlService() != null;
	}

	protected void notifyStatusChanged() {
		statusChanged = true;
	}

	protected long getProcessLoopIntervalTimeMillisSleep() {
		return processLoopIntervalTimeMillisSleepDefault;
	}

	protected long getProcessLoopIntervalTimeMillisNormal() {
		return processLoopIntervalTimeMillisNormalDefault;
	}

	protected long getProcessLoopIntervalTimeMillisVoiceTransfer() {
		return processLoopIntervalTimeMillisVoiceTransmitDefault;
	}

	protected abstract ReflectorStatusData createStatusDataInternal();
	protected abstract Class<? extends ReflectorStatusData> getStatusDataTypeInternal();

	protected abstract boolean getReflectorConnectionsInternal(List<ReflectorConnectionData> connections);

	protected abstract boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService);

	/**
	 * リフレクターコミュニケーションサービスメイン処理ファンクション
	 *
	 * 各サービスは、このファンクションをオーバーライドして処理を行うこと
	 *
	 * @return 処理結果
	 */
	protected abstract ThreadProcessResult processConnectionState();

	protected abstract ThreadProcessResult processVoiceTransfer();

	protected abstract ProcessIntervalMode getProcessIntervalMode();

	protected abstract List<ReflectorLinkInformation> getLinkInformation(
		final DSTARRepeater repeater, ConnectionDirectionType connectionDirection
	);

	protected DSTARPacket createPreLastVoicePacket(
		final ReflectorEntryClass reflectorConnection,
		final int frameID, final byte sequence,
		final Header header
	) {
		return new InternalPacket(
			reflectorConnection.getLoopBlockID(), reflectorConnection.getConnectionDirection(),
			header != null ?
				DSTARUtils.createPreLastVoicePacket(frameID, sequence, header) :
				DSTARUtils.createPreLastVoicePacket(frameID, sequence)
		);
	}

	protected DSTARPacket createPreLastVoicePacket(
		final ReflectorEntryClass reflectorConnection,
		final int frameID, final byte sequence
	) {
		return createPreLastVoicePacket(reflectorConnection, frameID, sequence, null);
	}

	protected DSTARPacket createLastVoicePacket(
		final ReflectorEntryClass reflectorConnection,
		final int frameID, final byte sequence,
		final Header header
	) {
		return new InternalPacket(
			reflectorConnection.getLoopBlockID(), reflectorConnection.getConnectionDirection(),
			header != null ?
				DSTARUtils.createLastVoicePacket(frameID, sequence, header) :
				DSTARUtils.createLastVoicePacket(frameID, sequence)
		);
	}

	protected DSTARPacket createLastVoicePacket(
		final ReflectorEntryClass reflectorConnection,
		final int frameID, final byte sequence
	) {
		return createLastVoicePacket(reflectorConnection, frameID, sequence, null);
	}

	protected boolean addConnectionStateChangeEvent(
		@NonNull UUID connectionId,
		@NonNull ConnectionDirectionType connectionDirection,
		@NonNull String repeaterCallsign,
		@NonNull String reflectorCallsign,
		@NonNull ReflectorConnectionStates connectionState,
		@NonNull ReflectorHostInfo reflectorHostInfo
	) {
		boolean isSuccess = false;

		final ReflectorEvent event =
			new ReflectorConnectionStateChangeEvent(
				connectionId,
				connectionDirection,
				repeaterCallsign,
				reflectorCallsign,
				connectionState,
				reflectorHostInfo
			);

		eventsLocker.lock();
		try {
			while(events.size() >= eventLimit) {this.events.poll();}

			isSuccess = events.add(event);

		}finally {
			eventsLocker.unlock();
		}

		if(isSuccess && eventListener != null) {
			eventQueue.addEventQueue(
				eventDispacher,
				new EventEntry(ReflectorCommunicationServiceEvent.ReflectorEventAdded, event),
				getExceptionListener()
			);
		}

		return isSuccess;
	}

	protected boolean addReflectorReceivePacket(
		@NonNull ReflectorReceivePacket packet
	) {
		boolean isSuccess = false;

		receivePacketsLocker.lock();
		try {
			while(receivePackets.size() >= receivePacketsLimit) {receivePackets.poll();}

			isSuccess = receivePackets.add(packet);
		}finally {receivePacketsLocker.unlock();}

		if(isSuccess && eventListener != null) {
			eventQueue.addEventQueue(
				eventDispacher,
				new EventEntry(ReflectorCommunicationServiceEvent.ReceivePacket, packet),
				getExceptionListener()
			);
		}

		return isSuccess;
	}

	protected void onIncomingConnectionConnected(
		@NonNull InetSocketAddress remoteHost,
		String reflectorCallsign,
		@NonNull String repeaterCallsign
	) {
		if(log.isInfoEnabled()) {
			log.info(
				logTag +
				"Incoming connection repeater " + repeaterCallsign + "@" + remoteHost +
				" connected to " + (reflectorCallsign != null ? reflectorCallsign : "Unknown ") + "..." +
				"Total " +
				Stream.of(getLinkInformation())
				.filter(new Predicate<ReflectorLinkInformation>() {
					@Override
					public boolean test(ReflectorLinkInformation info) {
						return info.getConnectionDirection() == ConnectionDirectionType.INCOMING;
					}
				}).count() +
				" clients connected using " + getProtocolType().getName() + "."
			);
		}
	}

	protected void onIncomingConnectionDisconnected(
		@NonNull InetSocketAddress remoteHost,
		String reflectorCallsign,
		@NonNull String repeaterCallsign
	) {
		if(log.isInfoEnabled()) {
			log.info(
				logTag +
				"Incoming connection repeater " + repeaterCallsign + "@" + remoteHost +
				" disconnected from " + (reflectorCallsign != null ? reflectorCallsign : DSTARDefines.EmptyLongCallsign) + "..." +
				"Total " +
				Stream.of(getLinkInformation())
				.filter(new Predicate<ReflectorLinkInformation>() {
					@Override
					public boolean test(ReflectorLinkInformation info) {
						return info.getConnectionDirection() == ConnectionDirectionType.INCOMING;
					}
				}).count() +
				" clients connected using " + getProtocolType().getName() + "."
			);
		}
	}

	protected boolean updateRemoteUsers(
		@NonNull final DSTARRepeater targetRepeater,
		@NonNull final String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull final List<ReflectorRemoteUserEntry> users
	) {
		remoteUsersUpdateEntriesLocker.lock();
		try {
			for(final Iterator<RemoteUsersUpdateEntry> it = remoteUsersUpdateEntries.iterator(); it.hasNext();) {
				final RemoteUsersUpdateEntry entry = it.next();

				if(
					entry.getTargetRepeater().getRepeaterCallsign().equals(targetRepeater.getRepeaterCallsign()) &&
					entry.getRemoteCallsign().equals(remoteCallsign) &&
					entry.getConnectionDir() == connectionDir
				) {it.remove();}
			}

			return remoteUsersUpdateEntries.add(
				new RemoteUsersUpdateEntry(targetRepeater, remoteCallsign, connectionDir,
					Stream.of(users)
					.map(new Function<ReflectorRemoteUserEntry, ReflectorRemoteUserEntry>(){
						@Override
						public ReflectorRemoteUserEntry apply(ReflectorRemoteUserEntry t) {
							return t.clone();
						}
					}).toList()
				)
			);
		}finally {
			remoteUsersUpdateEntriesLocker.unlock();
		}
	}

	private DSTARPacket readPacketInternal(DSTARRepeater repeater) {
		final String repeaterCallsign = repeater.getRepeaterCallsign();

		receivePacketsLocker.lock();
		try {
			for(Iterator<ReflectorReceivePacket> it = receivePackets.iterator(); it.hasNext();) {
				final ReflectorReceivePacket reflectorPacket = it.next();

				if(repeaterCallsign.equals(reflectorPacket.getRepeaterCallsign())){
					it.remove();

					return reflectorPacket.getPacket();
				}
			}
		}finally {
			receivePacketsLocker.unlock();
		}

		return null;
	}

	private long getRequestProcessLoopIntervalTimeMillis(ProcessIntervalMode intervalMode) {
		switch(intervalMode) {
		case Normal:
			return getProcessLoopIntervalTimeMillisNormal();

		case Sleep:
			return getProcessLoopIntervalTimeMillisSleep();

		case VoiceTransfer:
			return getProcessLoopIntervalTimeMillisVoiceTransfer();

		default:
			return -1;
		}
	}

	private ThreadProcessResult processLoop() {
		ThreadProcessResult result = ThreadProcessResult.NoErrors;

		final ProcessIntervalMode requestMode = getProcessIntervalMode();
		switch(requestMode) {
		case Normal:
			if(getProcessLoopIntervalTimeMillis() != getProcessLoopIntervalTimeMillisNormal())
				setProcessLoopIntervalTimeMillis(getProcessLoopIntervalTimeMillisNormal());


			result = processConnectionState();
			break;

		case Sleep:
			if(getProcessLoopIntervalTimeMillis() != getProcessLoopIntervalTimeMillisSleep())
				setProcessLoopIntervalTimeMillis(getProcessLoopIntervalTimeMillisSleep());

			result = processConnectionState();
			break;

		case VoiceTransfer:
			if(getProcessLoopIntervalTimeMillis() != getProcessLoopIntervalTimeMillisVoiceTransfer()) {
				setProcessLoopIntervalTimeMillis(getProcessLoopIntervalTimeMillisVoiceTransfer());

				processLoopIntervalTimekeeper.updateTimestamp();
			}

			if(
				processLoopIntervalTimekeeper.isTimeout(
					getProcessLoopIntervalTimeMillisNormal(), TimeUnit.MILLISECONDS)
			) {
				processLoopIntervalTimekeeper.updateTimestamp();

				result = processConnectionState();
				if(result != ThreadProcessResult.NoErrors) {return result;}
			}

			final ThreadProcessResult vResult = processVoiceTransfer();
			if(vResult != ThreadProcessResult.NoErrors) {result = vResult;}

			break;
		}

		final long requestIntervalTimeMillis = getRequestProcessLoopIntervalTimeMillis(requestMode);
		if(requestIntervalTimeMillis > 0) {
			final long currentIntervalTimeMillis = getCurrentProcessIntervalTimeMillis();
			long newIntervalTimeMillis = -1;

			if(requestIntervalTimeMillis < currentIntervalTimeMillis) {
				newIntervalTimeMillis = requestIntervalTimeMillis;
				requestProcessLoopIntervalTimeMillis = requestIntervalTimeMillis;
			}
			else if(requestIntervalTimeMillis > currentIntervalTimeMillis){
				if(requestIntervalTimeMillis != requestProcessLoopIntervalTimeMillis) {
					processLoopIntervalTimeDelayTimer.updateTimestamp();
					requestProcessLoopIntervalTimeMillis = requestIntervalTimeMillis;
				}
				else if(processLoopIntervalTimeDelayTimer.isTimeout(1, TimeUnit.SECONDS)) {
					newIntervalTimeMillis = requestProcessLoopIntervalTimeMillis;
				}
			}

			if(newIntervalTimeMillis > 0 && currentIntervalTimeMillis != newIntervalTimeMillis) {
				setProcessLoopIntervalTime(newIntervalTimeMillis, TimeUnit.MILLISECONDS);

				if(log.isDebugEnabled())
					log.debug(logTag + "Interval time changed " + currentIntervalTimeMillis + " -> " + newIntervalTimeMillis + "(ms)");
			}
		}

		return result;
	}

	private void processWebRemoteControlService() {
		if(
			statusChanged &&
			notifyStatusToWebRemoteTimekeeper.isTimeout(1, TimeUnit.SECONDS)
		) {
			statusChanged = false;
			notifyStatusToWebRemoteTimekeeper.updateTimestamp();

			final WebRemoteControlService service = getWebRemoteControlService();
			if(isEnableWebRemoteControl() && service != null) {
				workerExecutor.submit(new Runnable() {
					@Override
					public void run() {
						service.notifyReflectorStatusChanged(getWebRemoteControlHandler());
					}
				});
			}
		}
	}

	private void processReflectorRemoteUsersUpdate() {
		if(
			!remoteUsersUpdateEntries.isEmpty() &&
			remoteUsersUpdateTimekeeper.isTimeout(1, TimeUnit.SECONDS)
		) {
			remoteUsersUpdateTimekeeper.updateTimestamp();

			while(!remoteUsersUpdateEntries.isEmpty()) {
				RemoteUsersUpdateEntry entry = null;

				remoteUsersUpdateEntriesLocker.lock();
				try {
					entry = remoteUsersUpdateEntries.poll();
				}finally {
					remoteUsersUpdateEntriesLocker.unlock();
				}
				if(entry == null) {break;}

				final RemoteUsersUpdateEntry taskEntry = entry;

				workerExecutor.submit(new RunnableTask(getExceptionListener()) {
					@Override
					public void task() {
						getGateway().notifyReflectorLoginUsers(
							getProcessorType(),
							getProtocolType(),
							taskEntry.getTargetRepeater(), taskEntry.getRemoteCallsign(),
							taskEntry.getConnectionDir(),
							taskEntry.getUsers()
						);
					}
				});
			}
		}
	}

	private void cleanupReflectorEvent() {
		eventsLocker.lock();
		try {
			for(Iterator<ReflectorEvent> it = events.iterator(); it.hasNext();) {
				ReflectorEvent event = it.next();
				if(System.currentTimeMillis() > (event.getCreatedTimestamp() + TimeUnit.SECONDS.toMillis(60))){it.remove();}
			}
		}finally {
			eventsLocker.unlock();
		}
	}

	private void cleanupReflectorReceivePackets() {
		receivePacketsLocker.lock();
		try {
			for(Iterator<ReflectorReceivePacket> it = receivePackets.iterator(); it.hasNext();) {
				ReflectorReceivePacket packet = it.next();

				if(packet.isTimeout()) {it.remove();}
			}
		}finally {receivePacketsLocker.unlock();}
	}

	private void generateModCode() {
		setTransmitModCode(generateModCodeInt());
		setReceiveModCode(generateModCodeInt());
	}

	private int generateModCodeInt() {
		synchronized(modRandom) {
			return modRandom.nextInt(0xFFFF) + 0x1;
		}
	}
}
