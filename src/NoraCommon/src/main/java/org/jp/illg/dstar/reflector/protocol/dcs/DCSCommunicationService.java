package org.jp.illg.dstar.reflector.protocol.dcs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ConnectionRequest;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceStatus;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.protocol.ReflectorCommunicationServiceBase;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSConnect;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSHeaderVoice;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSLinkInternalState;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacket;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacketImpl;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPacketType;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSPoll;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSReflectorEntry;
import org.jp.illg.dstar.reflector.protocol.dcs.model.DCSTransmitPacketEntry;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorReceivePacket;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDCSHandler;
import org.jp.illg.dstar.service.web.model.DCSConnectionData;
import org.jp.illg.dstar.service.web.model.DCSStatusData;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

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
public class DCSCommunicationService
extends ReflectorCommunicationServiceBase<BufferEntry, DCSReflectorEntry>
implements WebRemoteControlDCSHandler
{
	private static final int dcsStandardPort = 30051;

	private static final int receiveKeepAliveTimeoutSeconds = 60;
	private static final int transmitKeepAliveIntervalSeconds = 5;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean outgoingLink;
	private static final boolean outgoingLinkDefault = true;
	public static final String outgoingLinkPropertyName = "OutgoingLink";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean incomingLink;
	private static final boolean incomingLinkDefault = false;
	public static final String incomingLinkPropertyName = "IncomingLink";

	private static final boolean dcsFullSupportDefault = false;
	private static final String dcsFullSupportPropertyName = "DCSFullSupport";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int dcsPort;
	private static final int dcsPortDefault = dcsStandardPort;
	private static final String dcsPortPropertyName = "DCSPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxOutgoingLink;
	private static final int maxOutgoingLinkDefault = 8;
	private static final String maxOutgoingLinkPropertyName = "MaxOutgoingLink";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxIncomingLink;
	private static final int maxIncomingLinkDefault = 64;
	private static final String maxIncomingLinkPropertyName = "MaxIncomingLink";

	private static final Pattern supportCallsignPattern =
		Pattern.compile("^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-Z])|((([D][C][S])|([X][L][X]))[0-9]{3}[ ][A-Z]))$");

	private static final String logHeader;

	private final Queue<UUID> entryRemoveRequestQueue;
	private final Lock entryRemoveRequestQueueLocker;
	private final Timer entryCleanupIntervalTimekeeper;

	private final Queue<DCSPacket> receivePacketQueue;
	private final Lock receivePacketQueueLocker;

	private SocketIOEntryUDP incomingChannel;

	static {
		logHeader = DCSCommunicationService.class.getSimpleName() + " : ";
	}

	public DCSCommunicationService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketIO,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		super(
			systemID,
			exceptionListener,
			DCSCommunicationService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteLocalAddressPort,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);

		entryRemoveRequestQueue = new LinkedList<>();
		entryRemoveRequestQueueLocker = new ReentrantLock();
		entryCleanupIntervalTimekeeper = new Timer();

		receivePacketQueue = new LinkedList<>();
		receivePacketQueueLocker = new ReentrantLock();

		setOutgoingLink(outgoingLinkDefault);
		setIncomingLink(incomingLinkDefault);
		setMaxIncomingLink(maxIncomingLinkDefault);
		setMaxOutgoingLink(maxOutgoingLinkDefault);
	}

	public DCSCommunicationService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		this(systemID, exceptionListener, gateway, workerExecutor, null, reflectorLinkManager, eventListener);
	}


	@Override
	public DSTARProtocol getProtocolType() {
		return DSTARProtocol.DCS;
	}

	@Override
	public ReflectorProtocolProcessorTypes getProcessorType() {
		return ReflectorProtocolProcessorTypes.DCS;
	}

	@Override
	public boolean setProperties(ReflectorProperties properties) {

		setOutgoingLink(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				outgoingLinkPropertyName,
				outgoingLinkDefault
			)
		);

		setIncomingLink(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				incomingLinkPropertyName,
				incomingLinkDefault
			) ||
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				dcsFullSupportPropertyName,
				dcsFullSupportDefault
			)
		);

		setDcsPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				dcsPortPropertyName,
				dcsPortDefault
			)
		);

		setMaxOutgoingLink(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				maxOutgoingLinkPropertyName,
				maxOutgoingLinkDefault
			)
		);
		setMaxIncomingLink(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
					maxIncomingLinkPropertyName,
					maxIncomingLinkDefault
			)
		);

		return true;
	}

	@Override
	public ReflectorProperties getProperties(ReflectorProperties properties) {

		//dcsFullSupport
		if(properties.getConfigurationProperties().containsKey(dcsFullSupportPropertyName))
			properties.getConfigurationProperties().remove(dcsFullSupportPropertyName);

		properties.getConfigurationProperties().put(
			dcsFullSupportPropertyName, String.valueOf(isIncomingLink())
		);

		//dcsPort
		if(properties.getConfigurationProperties().containsKey(dcsPortPropertyName))
			properties.getConfigurationProperties().remove(dcsPortPropertyName);

		properties.getConfigurationProperties().put(dcsPortPropertyName, String.valueOf(getDcsPort()));

		//maxOutgoingLink
		if(properties.getConfigurationProperties().containsKey(maxOutgoingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxOutgoingLinkPropertyName);

		properties.getConfigurationProperties().put(maxOutgoingLinkPropertyName, String.valueOf(getMaxOutgoingLink()));

		//maxIncommingLink
		if(properties.getConfigurationProperties().containsKey(maxIncomingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxIncomingLinkPropertyName);

		properties.getConfigurationProperties().put(maxIncomingLinkPropertyName, String.valueOf(getMaxIncomingLink()));

		return properties;
	}

	@Override
	public ReflectorCommunicationServiceStatus getStatus() {
		return isRunning() ? ReflectorCommunicationServiceStatus.InService : ReflectorCommunicationServiceStatus.OutOfService;
	}

	@Override
	public boolean hasWriteSpace() {
		return true;
	}

	@Override
	public UUID linkReflector(
		final String reflectorCallsign, final ReflectorHostInfo reflectorHostInfo, final DSTARRepeater repeater
	) {
		if(
			reflectorHostInfo == null ||
			!supportCallsignPattern.matcher(reflectorCallsign).matches() ||
			reflectorHostInfo.getReflectorAddress() == null
		) {return null;}

		if(!isOutgoingLink()) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Could not connect to " + reflectorCallsign + ", Outgoing connection is disabled."
				);
			}

			return null;
		}
/*
		final DStarRepeater repeater = DStarRepeaterManager.getRepeater(repeaterCallsign);
		if(repeater == null) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Unknown repeater " + repeaterCallsign + ",ignore outgoing link request.");

			return null;
		}
*/
		UUID entryID = null;

		entriesLocker.lock();
		try {
			final Optional<DCSReflectorEntry> duplicateEntry =
				findReflectorEntry(
					null, -1, -1, ConnectionDirectionType.OUTGOING,
					repeater.getRepeaterCallsign(), reflectorCallsign
				)
				.findFirst();
			if(duplicateEntry.isPresent()) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader + "Could not link to duplicate reflector,ignore link request from " +
						repeater.getRepeaterCallsign() + " to " + reflectorCallsign + "."
					);
				}

				return null;
			}

			if(getMaxOutgoingLink() <= countLinkEntry(ConnectionDirectionType.OUTGOING)) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Reached incomming link limit, ignore incomming link request from " +
						repeater.getRepeaterCallsign() + "."
					);
				}

				return null;
			}

			final SocketIOEntryUDP outgoingChannel =
				super.getSocketIO().registUDP(
					super.getHandler(),
					this.getClass().getSimpleName() + "@->" +
					reflectorHostInfo.getReflectorAddress() + ":" + reflectorHostInfo.getReflectorPort()
				);
			if(outgoingChannel == null) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not create udp channel.");

				return null;
			}

			final DCSReflectorEntry entry = new DCSReflectorEntry(
				generateLoopBlockID(),
				10,
				new InetSocketAddress(
					reflectorHostInfo.getReflectorAddress(), reflectorHostInfo.getReflectorPort()
				),
				outgoingChannel.getLocalAddress(),
				ConnectionDirectionType.OUTGOING
			);
			entry.setOutgoingReflectorHostInfo(reflectorHostInfo);
			entry.setRepeaterCallsign(repeater.getRepeaterCallsign());
			entry.setReflectorCallsign(reflectorCallsign);
			entry.setXlxMode(reflectorCallsign.startsWith("XLX"));
			entry.setDestinationRepeater(repeater);
			entry.setOutgoingChannel(outgoingChannel);
			entry.setNextState(DCSLinkInternalState.Initialize);
			entry.setConnectionRequest(ConnectionRequest.LinkRequest);
			entry.setModCode(getModCode());

			entry.getActivityTimeKepper().updateTimestamp();

			addEntry(entry);

			entryID = entry.getId();

		}finally {
			entriesLocker.unlock();
		}

		wakeupProcessThread();

		return entryID;
	}

	@Override
	public UUID unlinkReflector(DSTARRepeater repeater) {
		if(
			!isRunning() || repeater == null
		) {return null;}

		final ProcessResult<UUID> result = new ProcessResult<>();

		entriesLocker.lock();
		try {
			findReflectorEntry(ConnectionDirectionType.OUTGOING, repeater.getRepeaterCallsign(), null)
			.findFirst()
			.ifPresent(new Consumer<DCSReflectorEntry>() {
				@Override
				public void accept(final DCSReflectorEntry entry) {

					switch(entry.getCurrentState()) {
					case Linking:
					case LinkEstablished:
						entry.setConnectionRequest(ConnectionRequest.UnlinkRequest);
						break;

					default:
						addEntryRemoveRequestQueue(entry.getId());
						break;
					}

					result.setResult(entry.getId());
				}
			});
		}finally {entriesLocker.unlock();}

		return result.getResult();
	}

	@Override
	public boolean isSupportedReflectorCallsign(String reflectorCallsign) {
		if(reflectorCallsign == null) {return false;}

		return supportCallsignPattern.matcher(reflectorCallsign).matches();
	}

	@Override
	public boolean writePacketInternal(
		final DSTARRepeater repeater, final DSTARPacket packet, final ConnectionDirectionType direction
	) {
		if(repeater == null || packet == null || direction == null) {return false;}

		entriesLocker.lock();
		try {
			findReflectorEntry(
				direction == ConnectionDirectionType.BIDIRECTIONAL ? null : direction,
				direction == ConnectionDirectionType.OUTGOING ?
					repeater.getRepeaterCallsign() : null,
				direction == ConnectionDirectionType.INCOMING ?
					repeater.getRepeaterCallsign() : null
			)
			.forEach(new Consumer<DCSReflectorEntry>() {
				@Override
				public void accept(DCSReflectorEntry entry) {
					if(packet.getDVPacket().hasPacketType(PacketType.Header))
						writeHeader(repeater.getRepeaterCallsign(), entry, packet.clone(), direction);

					if(packet.getDVPacket().hasPacketType(PacketType.Voice))
						writeVoice(repeater.getRepeaterCallsign(), entry, packet.clone(), direction);
				}
			});
		}finally {entriesLocker.unlock();}


		return true;
	}

	@Override
	public boolean isSupportTransparentMode() {
		return false;
	}

	@Override
	public OperationRequest readEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
	}

	@Override
	public boolean isEnableIncomingLink() {
		return isIncomingLink();
	}

	@Override
	public int getIncomingLinkPort() {
		return getDcsPort();
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Already running.");

			return true;
		}

		if(
			!super.start(
				new Runnable() {
					@Override
					public void run() {
						if(isIncomingLink()) {
							incomingChannel =
								getSocketIO().registUDP(
									new InetSocketAddress(getDcsPort()),
									DCSCommunicationService.this.getHandler(),
									DCSCommunicationService.this.getClass().getSimpleName() + "@" + getDcsPort()
								);
						}
					}
				}
			) ||
			(isIncomingLink() && incomingChannel == null)
		) {
			this.stop();

			closeIncommingChannel();

			return false;
		}

		return true;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();

		finalizeReflectorEntries();

		closeIncommingChannel();
	}

	@Override
	protected ThreadProcessResult processReceivePacket() {
		//各セッション毎の処理
		entriesLocker.lock();
		try {
			//受信パケットを解析＆処理
			receivePacketQueueLocker.lock();
			try {
				parsePacket(receivePacketQueue);

				for(final Iterator<DCSPacket> it = receivePacketQueue.iterator(); it.hasNext();) {
					final DCSPacket packet = it.next();
					it.remove();

					switch(packet.getDCSPacketType()) {
					case CONNECT:
						processConnect(packet);
						break;

					case POLL:
						processPoll(packet);
						break;

					case HEADERVOICE:
						processHeaderVoice(packet);
						break;

					default:
						break;
					}

					processEntryRemoveRequestQueue();
				}
			}finally {
				receivePacketQueueLocker.unlock();
			}
		}finally {
			entriesLocker.unlock();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processConnectionState() {
		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		//各セッション毎の処理
		entriesLocker.lock();
		try {
			for(final DCSReflectorEntry entry : entries) {

				boolean reProcess;
				do {
					reProcess = false;

					final boolean stateChanged = entry.getCurrentState() != entry.getNextState();
					entry.setStateChanged(stateChanged);
					if(stateChanged) {notifyStatusChanged();}

					if(log.isDebugEnabled() && entry.isStateChanged()) {
						log.debug(
							logHeader +
							"State changed " +
								entry.getCurrentState().toString() + " -> " + entry.getNextState().toString() +
								"\n" +
								entry.toString(4)
						);
					}

					entry.setCurrentState(entry.getNextState());

					switch(entry.getCurrentState()) {
					case Initialize:
						processResult = onStateInitialize(entry);
						break;

					case Linking:
						processResult = onStateLinking(entry);
						break;

					case LinkEstablished:
						processResult = onStateLinkEstablished(entry);
						break;

					case Unlinking:
						processResult = onStateUnlinking(entry);
						break;

					case Unlinked:
						processResult = onStateUnlinked(entry);
						break;

					case Wait:
						processResult = onStateWait(entry);
						break;

					default:
						break;
					}

					if(
						entry.getCurrentState() != entry.getNextState() &&
						processResult == ThreadProcessResult.NoErrors
					) {reProcess = true;}

				}while(reProcess);
			}
		}finally {
			entriesLocker.unlock();
		}

		processEntryRemoveRequestQueue();

		cleanupProcessEntry();

		return processResult;
	}

	@Override
	protected ThreadProcessResult processVoiceTransfer() {
		entriesLocker.lock();
		try {
			for(final DCSReflectorEntry entry : entries) {
				//送信する音声パケットがあれば送信
				while(entry.getCacheTransmitter().hasOutputRead()) {
					entry.getCacheTransmitter().outputRead()
					.ifPresent(new Consumer<DCSTransmitPacketEntry>() {
						@Override
						public void accept(DCSTransmitPacketEntry packetEntry) {
							packetEntry.getPacket().getBackBone().undoModFrameID();

							final DCSPacket packet =
								new DCSPacketImpl(
									entry.getCurrentHeader().getRfHeader().clone(),
									packetEntry.getPacket().getVoiceData(),
									packetEntry.getPacket().getBackBone()
								);
							packet.setLongSequence(packetEntry.getLongSequence());

							sendPacket(entry, packet);

							if(packetEntry.getPacket().isEndVoicePacket()) {
								clearFrameSequence(entry, false);
							}
						}
					});
				}
			}
		}finally {
			entriesLocker.unlock();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ProcessIntervalMode getProcessIntervalMode() {
		entriesLocker.lock();
		try {
			if(entries.isEmpty())
				return ProcessIntervalMode.Sleep;

			for(final DCSReflectorEntry entry : entries) {
				if(
					entry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING &&
					entry.getCurrentFrameID() != 0x0
				) {return ProcessIntervalMode.VoiceTransfer;}
			}
		}finally {
			entriesLocker.unlock();
		}

		return ProcessIntervalMode.Normal;
	}

	@Override
	public ReflectorStatusReport getStatusReport() {

		final ReflectorStatusReport report = new ReflectorStatusReport();
		report.setReflectorType(getProcessorType());
		report.setServiceStatus(getStatus());
		report.setEnableIncomingLink(isIncomingLink());
		report.setEnableOutgoingLink(true);
		entriesLocker.lock();
		try {
			report.setConnectedIncomingLink(
				(int)Stream.of(entries)
				.filter(new Predicate<DCSReflectorEntry>() {
					@Override
					public boolean test(DCSReflectorEntry e) {
						return e.getConnectionDirection() == ConnectionDirectionType.INCOMING;
					}
				})
				.count()
			);
			report.setConnectedOutgoingLink(
				(int)Stream.of(entries)
				.filter(new Predicate<DCSReflectorEntry>() {
					@Override
					public boolean test(DCSReflectorEntry e) {
						return e.getConnectionDirection() == ConnectionDirectionType.OUTGOING;
					}
				})
				.count()
			);
		}finally {
			entriesLocker.unlock();
		}
		report.setIncomingLinkPort(getDcsPort());
		report.setIncomingStatus("");
		report.setOutgoingStatus("");

		return report;
	}

	@Override
	protected boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeReflectorDCS(this);
	}

	@Override
	protected boolean getReflectorConnectionsInternal(
		@NonNull List<ReflectorConnectionData> connections
	) {
		entriesLocker.lock();
		try {
			for(final DCSReflectorEntry entry : entries) {
				final DCSConnectionData con = new DCSConnectionData();

				con.setConnectionId(entry.getId());
				con.setReflectorType(getReflectorType());
				con.setConnectionDirection(entry.getConnectionDirection());
				switch(entry.getConnectionDirection()) {
				case OUTGOING:
					con.setReflectorCallsign(entry.getReflectorCallsign());
					con.setRepeaterCallsign(entry.getRepeaterCallsign());
					break;

				case INCOMING:
					con.setReflectorCallsign(entry.getRepeaterCallsign());
					con.setRepeaterCallsign(entry.getReflectorCallsign());
					break;

				default:
					con.setReflectorCallsign(DSTARDefines.EmptyLongCallsign);
					con.setRepeaterCallsign(DSTARDefines.EmptyLongCallsign);
					break;
				}
				con.setProtocolVersion(1);

				connections.add(con);
			}
		}finally {
			entriesLocker.unlock();
		}

		return true;
	}

	@Override
	protected ReflectorStatusData createStatusDataInternal() {
		final DCSStatusData status = new DCSStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ReflectorStatusData> getStatusDataTypeInternal() {
		return DCSStatusData.class;
	}

	private ThreadProcessResult onStateInitialize(final DCSReflectorEntry entry) {
		assert entry != null;

		entry.setNextState(DCSLinkInternalState.Linking);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateLinking(final DCSReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			sendConnectLinkPacket(entry);

			entry.setConnectionRequest(ConnectionRequest.Nothing);

			entry.getStateTimeKeeper().updateTimestamp();
		}
		else if(entry.getStateTimeKeeper().isTimeout(3, TimeUnit.SECONDS)){
			if(entry.getStateRetryCount() < 1) {
				toWaitState(entry, 100, TimeUnit.MILLISECONDS, DCSLinkInternalState.Linking);

				entry.setStateRetryCount(entry.getStateRetryCount() + 1);
			}
			else {
				entry.setStateRetryCount(0);

				if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
					addConnectionStateChangeEvent(
						entry.getId(),
						entry.getConnectionDirection(),
						entry.getRepeaterCallsign(),
						entry.getReflectorCallsign(),
						ReflectorConnectionStates.LINKFAILED,
						entry.getOutgoingReflectorHostInfo()
					);
				}

				entry.setNextState(DCSLinkInternalState.Unlinked);
			}
		}
		else if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
			entry.setNextState(DCSLinkInternalState.Unlinking);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateLinkEstablished(final DCSReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			entry.setStateRetryCount(0);

			entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(
				receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS
			);
			entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(
				transmitKeepAliveIntervalSeconds, TimeUnit.SECONDS
			);

			if(log.isDebugEnabled()) {
				log.debug(
					logHeader +
					entry.getConnectionDirection().toString() + " link established.\n" + entry.toString(4)
				);
			}
		}
		else if(
			entry.getReceiveKeepAliveTimeKeeper().isTimeout()
		) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				entry.setNextState(DCSLinkInternalState.Linking);
			else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				if(log.isInfoEnabled()) {
					log.info(
						logHeader +
						"Incoming connection poll timeout(RepeaterCallsign:" + entry.getRepeaterCallsign() +
						entry.getRemoteAddressPort() + ")"
					);
				}

				sendConnectUnlinkPacket(entry);

				entry.setNextState(DCSLinkInternalState.Unlinked);
			}
		}
		else if(
			entry.getConnectionDirection() == ConnectionDirectionType.INCOMING &&
			entry.getTransmitKeepAliveTimeKeeper().isTimeout()
		) {
			entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(transmitKeepAliveIntervalSeconds, TimeUnit.SECONDS);
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

			sendPollPacket(entry);
		}
		else if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				entry.setNextState(DCSLinkInternalState.Unlinking);
			else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {

				sendConnectUnlinkPacket(entry);

				entry.setNextState(DCSLinkInternalState.Unlinked);
			}
		}

		if(entry.getFrameSequenceTimeKepper().isTimeout() && entry.getCurrentFrameID() != 0x0000) {
			if(entry.getCurrentFrameDirection() == ConnectionDirectionType.INCOMING) {
				addRxEndPacket(entry);
			}
			else if(entry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING) {
				addTxLastPacket(entry);
			}

			clearFrameSequence(entry, true);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateUnlinking(DCSReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			sendConnectUnlinkPacket(entry);

			entry.getStateTimeKeeper().setTimeoutTime(1, TimeUnit.SECONDS);

			if(entry.getCurrentFrameID() != 0x0) {
				addRxEndPacket(entry);
				entry.setCurrentFrameID(0x0);
				entry.setCurrentFrameSequence((byte)0x0);
			}
		}
		else if(entry.getStateTimeKeeper().isTimeout()) {
			if(entry.getStateRetryCount() < 5) {
				toWaitState(entry, 100, TimeUnit.MILLISECONDS, DCSLinkInternalState.Unlinking);

				entry.setStateRetryCount(entry.getStateRetryCount() + 1);
			}
			else {
				entry.setNextState(DCSLinkInternalState.Unlinked);

				entry.setStateRetryCount(0);

				addConnectionStateChangeEvent(
					entry.getId(),
					entry.getConnectionDirection(),
					entry.getRepeaterCallsign(),
					entry.getReflectorCallsign(),
					ReflectorConnectionStates.LINKFAILED,
					entry.getOutgoingReflectorHostInfo()
				);
			}
		}


		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateUnlinked(DCSReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				onIncomingConnectionDisconnected(
					entry.getRemoteAddressPort(), entry.getReflectorCallsign(), entry.getRepeaterCallsign()
				);
			}

			addEntryRemoveRequestQueue(entry.getId());
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait(DCSReflectorEntry entry) {
		assert entry != null;

		if(entry.getStateTimeKeeper().isTimeout())
			entry.setNextState(entry.getCallbackState());

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(DCSReflectorEntry entry, int time, TimeUnit timeUnit, DCSLinkInternalState callbackState) {
		assert entry != null && timeUnit != null && callbackState != null;

		if(time < 0) {time = 0;}

		if(time > 0) {
			entry.setNextState(DCSLinkInternalState.Wait);
			entry.setCallbackState(callbackState);
			entry.getStateTimeKeeper().setTimeoutTime(time, timeUnit);
		}
		else {
			entry.setNextState(callbackState);
		}
	}

	private void processPoll(final DCSPacket packet) {
		assert packet != null;

		if(packet.getDCSPacketType() != DCSPacketType.POLL) {return;}

		entriesLocker.lock();
		try {
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort(),
				DCSLinkInternalState.LinkEstablished
			)
			.forEach(new Consumer<DCSReflectorEntry>() {
				@Override
				public void accept(DCSReflectorEntry entry) {
					entry.getActivityTimeKepper().updateTimestamp();

					String reflectorCallsign =
						DSTARUtils.formatFullLengthCallsign(packet.getPoll().getReflectorCallsign());
					final String repeaterCallsign =
						DSTARUtils.formatFullLengthCallsign(packet.getPoll().getRepeaterCallsign());

					if(entry.isXlxMode() && entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
						reflectorCallsign = reflectorCallsign.replace("DCS", "XLX");
					}

					switch(entry.getConnectionDirection()) {
					case OUTGOING:
						if(
							entry.getRepeaterCallsign().equals(repeaterCallsign) &&
							entry.getReflectorCallsign().equals(reflectorCallsign)
						) {
							entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS);
							entry.getReceiveKeepAliveTimeKeeper().updateTimestamp();

							sendPollPacket(entry);
						}
						break;

					case INCOMING:
						if(
							entry.getRepeaterCallsign().substring(0, DSTARDefines.CallsignFullLength - 1).equals(
								reflectorCallsign.substring(0, DSTARDefines.CallsignFullLength - 1)
							)
						) {
							entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS);
							entry.getReceiveKeepAliveTimeKeeper().updateTimestamp();
						}
						break;

					default:
						break;
					}
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processConnect(final DCSPacket packet) {
		assert packet != null;

		if(packet.getDCSPacketType() != DCSPacketType.CONNECT) {return;}

		final DCSConnect connect = packet.getConnect();

		if(
			connect.getType() == ReflectorConnectTypes.ACK ||
			connect.getType() == ReflectorConnectTypes.NAK ||
			connect.getType() == ReflectorConnectTypes.UNLINK
		) {
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			)
			.findFirst()
			.ifPresent(new Consumer<DCSReflectorEntry>() {
				@Override
				public void accept(DCSReflectorEntry entry) {
					processConnect(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});

			return;
		}

		if(incomingChannel == null || !isIncomingLink()) {return;}

		final Optional<DCSReflectorEntry> duplicateEntry =
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort(),
				ConnectionDirectionType.INCOMING,
				connect.getRepeaterCallsign(),
				connect.getReflectorCallsign()
			)
			.findFirst();

		if(duplicateEntry.isPresent()) {
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					"Already connected same remote host = " + packet.getRemoteAddress() +
					" ,ignore incomming link request" +
					"(RepeaterCallsign:" + connect.getRepeaterCallsign() + "/ReflectorCallsign:" + connect.getReflectorCallsign()
				);
			}

			addEntryRemoveRequestQueue(duplicateEntry.get().getId());
			processEntryRemoveRequestQueue();
		}

		final DSTARRepeater repeater = getGateway().getRepeater(connect.getReflectorCallsign());
		if(repeater == null) {
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					packet.getRemoteAddress() +
					" try connect to unknown repeater " + connect.getReflectorCallsign() + "."
				);
			}

			return;
		}
		else if(
			!getReflectorLinkManager()
				.isAllowReflectorIncomingConnectionWithRemoteRepeater(connect.getRepeaterCallsign())
		) {
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					packet.getRemoteAddress() +
					" attempted to connect to reoeater " + connect.getReflectorCallsign() +
					" where incoming connections are not allowed, It's listed the reflector callsign at black list."
				);
			}

			return;
		}

		final DCSReflectorEntry entry = new DCSReflectorEntry(
			generateLoopBlockID(),
			10,
			packet.getRemoteAddress(),
			packet.getLocalAddress(),
			ConnectionDirectionType.INCOMING
		);
		entry.setRepeaterCallsign(connect.getRepeaterCallsign());
		entry.setReflectorCallsign(connect.getReflectorCallsign());
		entry.setDestinationRepeater(repeater);
		entry.setCurrentState(DCSLinkInternalState.Linking);
		entry.setNextState(DCSLinkInternalState.LinkEstablished);
		entry.setConnectionRequest(ConnectionRequest.Nothing);
		entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(
			transmitKeepAliveIntervalSeconds, TimeUnit.SECONDS
		);
		entry.setModCode(getModCode());

		entry.getActivityTimeKepper().updateTimestamp();

		if(getMaxIncomingLink() <= countLinkEntry(ConnectionDirectionType.INCOMING)) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Reached incoming link limit, ignore incoming link request from " +
					entry.getRemoteAddressPort() + "."
				);
			}

			sendConnectNakPacket(entry);

			return;
		}
		else if(
			!getReflectorLinkManager()
				.isAllowReflectorIncomingConnectionWithLocalRepeater(connect.getReflectorCallsign()) ||
			!getReflectorLinkManager()
				.isAllowReflectorIncomingConnectionWithRemoteRepeater(connect.getRepeaterCallsign())
		) {
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					"Denied connection from repeater = " + connect.getRepeaterCallsign() + "@" +
					entry.getRemoteAddressPort() + ", " +
					"It's listed the reflector callsign at black list or " +
					"isAllowIncomingConnection = false on repeater properties."
				);
			}

			sendConnectNakPacket(entry);

			return;
		}

		addEntry(entry);

		sendConnectAckPacket(entry);

		onIncomingConnectionConnected(
			entry.getRemoteAddressPort(), entry.getReflectorCallsign(), entry.getRepeaterCallsign()
		);
	}

	private void processConnect(DCSReflectorEntry entry, DCSPacket packet) {
		assert entry != null && packet != null;

		if(packet.getDCSPacketType() != DCSPacketType.CONNECT) {return;}

		if(
			!entry.getRemoteAddressPort().getAddress().equals(packet.getRemoteAddress().getAddress()) ||
			entry.getRemoteAddressPort().getPort() != packet.getRemoteAddress().getPort() ||
			entry.getLocalAddressPort().getPort() != packet.getLocalAddress().getPort()
		) {return;}

		final DCSConnect connect = packet.getConnect();

		if(entry.isXlxMode() && entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
			connect.setReflectorCallsign(
				connect.getReflectorCallsign().replace("DCS", "XLX")
			);
		}

		switch(connect.getType()) {
		case ACK:
			if(!entry.getRepeaterCallsign().equals(connect.getRepeaterCallsign())) {
				return;
			}

			if(log.isTraceEnabled())
				log.trace(logHeader + "Ack packet received.\n" + entry.toString(4));

			if(entry.getCurrentState() == DCSLinkInternalState.Linking) {
				entry.setNextState(DCSLinkInternalState.LinkEstablished);

				addConnectionStateChangeEvent(
					entry.getId(),
					entry.getConnectionDirection(),
					entry.getRepeaterCallsign(),
					entry.getReflectorCallsign(),
					ReflectorConnectionStates.LINKED,
					entry.getOutgoingReflectorHostInfo()
				);
			}
			break;

		case NAK:
			if(!entry.getRepeaterCallsign().equals(connect.getRepeaterCallsign())) {
				return;
			}

			if(log.isTraceEnabled())
				log.trace(logHeader + "Nak packet received.\n" + entry.toString(4));

			if(entry.getCurrentState() == DCSLinkInternalState.Linking) {
				entry.setNextState(DCSLinkInternalState.Unlinked);

				addConnectionStateChangeEvent(
					entry.getId(),
					entry.getConnectionDirection(),
					entry.getRepeaterCallsign(),
					entry.getReflectorCallsign(),
					ReflectorConnectionStates.LINKFAILED,
					entry.getOutgoingReflectorHostInfo()
				);
			}
			else if(entry.getCurrentState() == DCSLinkInternalState.Unlinking) {
				entry.setNextState(DCSLinkInternalState.Unlinked);

				addConnectionStateChangeEvent(
					entry.getId(),
					entry.getConnectionDirection(),
					entry.getRepeaterCallsign(),
					entry.getReflectorCallsign(),
					ReflectorConnectionStates.UNLINKED,
					entry.getOutgoingReflectorHostInfo()
				);
			}
			break;

		case UNLINK:
			if(!entry.getRepeaterCallsign().equals(connect.getRepeaterCallsign())) {
				return;
			}

			if(log.isTraceEnabled())
				log.trace(logHeader + "Unlink packet received.\n" + entry.toString(4));

			if(entry.getCurrentState() == DCSLinkInternalState.LinkEstablished) {
				entry.setNextState(DCSLinkInternalState.Unlinked);

				if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
					addConnectionStateChangeEvent(
						entry.getId(),
						entry.getConnectionDirection(),
						entry.getRepeaterCallsign(),
						entry.getReflectorCallsign(),
						ReflectorConnectionStates.UNLINKED,
						entry.getOutgoingReflectorHostInfo()
					);
				}
				else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
					sendConnectNakPacket(entry);
				}

			}
			break;

		default:
			break;
		}
	}

	private void processHeaderVoice(final DCSPacket packet) {
		assert packet != null && packet.getDCSPacketType() == DCSPacketType.HEADERVOICE;

		entriesLocker.lock();
		try {
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			)
			.findFirst()
			.ifPresent(new Consumer<DCSReflectorEntry>() {
				@Override
				public void accept(DCSReflectorEntry entry) {
					processHeaderVoice(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processHeaderVoice(DCSReflectorEntry entry, DCSPacket packet) {
		if(entry.getCurrentState() != DCSLinkInternalState.LinkEstablished) {return;}

		if(entry.isXlxMode()) {
			String repeater2Callsign = String.valueOf(packet.getRfHeader().getRepeater2Callsign());
			repeater2Callsign = repeater2Callsign.replace("DCS", "XLX");
			ArrayUtil.copyOf(packet.getRfHeader().getRepeater2Callsign(), repeater2Callsign.toCharArray());
		}

		if(
			(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
				!entry.getReflectorCallsign().equals(
					String.valueOf(packet.getRfHeader().getRepeater2Callsign())
				)
			) ||
			(
				entry.getConnectionDirection() == ConnectionDirectionType.INCOMING &&
				!entry.getReflectorCallsign().equals(
					String.valueOf(packet.getRfHeader().getRepeater2Callsign())
				)
			)
		) {return;}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		if(entry.getCurrentFrameID() == 0x0 && packet.getBackBone().getSequenceNumber() != 0) {
			return;
		}

		//経路情報を保存
		packet.getRfHeader().setSourceRepeater2Callsign(
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				entry.getReflectorCallsign() : entry.getRepeaterCallsign()
		);

		packet.setLoopblockID(entry.getLoopBlockID());
		packet.setConnectionDirection(entry.getConnectionDirection());

		if(entry.getCurrentFrameID() == 0x0) {	//新規フレーム
			entry.setCurrentHeader(packet.clone());
			entry.setCurrentFrameID(packet.getBackBone().getFrameIDNumber());
			entry.setCurrentFrameSequence((byte)0x0);
			entry.setPacketCount(0L);
			entry.setCurrentFrameDirection(ConnectionDirectionType.INCOMING);

			if(log.isDebugEnabled())
				log.debug(String.format("%s Start receive frame 0x%04X.", logHeader, entry.getCurrentFrameID()));

			addRxPacket(entry, PacketType.Header, packet);

			entry.getFrameSequenceTimeKepper().setTimeoutTime(1, TimeUnit.SECONDS);
			entry.getFrameSequenceTimeKepper().updateTimestamp();
		}
		else if(entry.getCurrentFrameID() == packet.getBackBone().getFrameIDNumber()) {
			entry.getFrameSequenceTimeKepper().updateTimestamp();

			entry.setCurrentFrameSequence(packet.getBackBone().getSequenceNumber());

			addRxPacket(entry, PacketType.Voice, packet);

			if(
				!packet.isLastFrame() &&
				entry.getCurrentFrameSequence() == DSTARDefines.MaxSequenceNumber
			) {
				final DSTARPacket headerOnlyPacket = entry.getCurrentHeader().clone();
				headerOnlyPacket.getDVPacket().setPacketType(PacketType.Header);

				addRxPacket(entry, PacketType.Header, headerOnlyPacket);
			}

			entry.setPacketCount(
				entry.getPacketCount() < Long.MAX_VALUE ? entry.getPacketCount() + 1 : entry.getPacketCount()
			);

			if(packet.getBackBone().isEndSequence()) {
				clearFrameSequence(entry, false);
			}
		}
	}

	private void clearFrameSequence(DCSReflectorEntry entry, boolean timeout) {
		assert entry != null;

		if(log.isDebugEnabled()) {
			log.debug(
				String.format(
					"%s Clear sequence " +
					(entry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING ? "transmit" : "receive") +
					" frame 0x%04X%s.",
					logHeader, entry.getCurrentFrameID(), timeout ? "[TIMEOUT]" : ""
				)
			);
		}

		entry.setCurrentFrameID(0x0);
		entry.setCurrentFrameSequence((byte)0x0);
		entry.setCurrentLongFrameSequence(0);
		entry.setPacketCount(0L);
		entry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
	}

	private boolean sendConnectLinkPacket(DCSReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.LINK);
	}

	private boolean sendConnectUnlinkPacket(DCSReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.UNLINK);
	}

	private boolean sendConnectAckPacket(DCSReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.ACK);
	}

	private boolean sendConnectNakPacket(DCSReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.NAK);
	}

	private boolean sendConnectPacket(DCSReflectorEntry entry, ReflectorConnectTypes type) {
		assert entry != null && type != null;

		DCSConnect connectPacket = new DCSConnect();
		connectPacket.setType(type);
		if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
			connectPacket.setRepeaterCallsign(entry.getRepeaterCallsign());
			connectPacket.setReflectorCallsign(
				entry.isXlxMode() ? entry.getReflectorCallsign().replace("XLX", "DCS") : entry.getReflectorCallsign()
			);
		}
		else {
			connectPacket.setRepeaterCallsign(entry.getRepeaterCallsign());
			connectPacket.setReflectorCallsign(entry.getReflectorCallsign());
		}

		connectPacket.setApplicationName(getApplicationName());
		connectPacket.setApplicationVersion(getApplicationVersion());

		return sendPacket(entry, new DCSPacketImpl(connectPacket));
	}

	private boolean sendPollPacket(DCSReflectorEntry entry) {
		assert entry != null;

		final DCSPoll poll =
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
			(
				new DCSPoll(
					entry.getRepeaterCallsign(),
					entry.isXlxMode() ? entry.getReflectorCallsign().replace("XLX", "DCS") : entry.getReflectorCallsign(),
					entry.getConnectionDirection()
				)
			) :
			(
				new DCSPoll(
					entry.getReflectorCallsign(),
					entry.getRepeaterCallsign(),
					entry.getConnectionDirection()
				)
			);

		return sendPacket(entry, (DCSPacket)new DCSPacketImpl(poll));
	}

	private boolean addEntry(DCSReflectorEntry entry) {
		assert entry != null;

		if(log.isDebugEnabled())
			log.debug(logHeader + "Add new reflector entry.\n" + entry.toString(4));

		entriesLocker.lock();
		try {
			return entries.add(entry);
		}finally {entriesLocker.unlock();}
	}

	private boolean addEntryRemoveRequestQueue(UUID id) {
		assert id != null;

		entryRemoveRequestQueueLocker.lock();
		try {
			return entryRemoveRequestQueue.add(id);
		}finally {
			entryRemoveRequestQueueLocker.unlock();
		}
	}

	private void processEntryRemoveRequestQueue() {
		entriesLocker.lock();
		try {
			entryRemoveRequestQueueLocker.lock();
			try {
				for(Iterator<UUID> removeIt = entryRemoveRequestQueue.iterator(); removeIt.hasNext();) {
					UUID removeID = removeIt.next();
					removeIt.remove();

					for(Iterator<DCSReflectorEntry> refEntryIt = entries.iterator(); refEntryIt.hasNext();) {
						DCSReflectorEntry refEntry = refEntryIt.next();
						if(refEntry.getId().equals(removeID)) {
							if(log.isTraceEnabled())
								log.trace(logHeader + "Delete reflector entry.\n" + refEntry.toString(4));

							finalizeEntry(refEntry);

							refEntryIt.remove();
							break;
						}
					}
				}
			}finally {
				entryRemoveRequestQueueLocker.unlock();
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void cleanupProcessEntry() {
		if(entryCleanupIntervalTimekeeper.isTimeout()) {
			entryCleanupIntervalTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			entryCleanupIntervalTimekeeper.updateTimestamp();

			entriesLocker.lock();
			try {
				for(Iterator<DCSReflectorEntry> it = entries.iterator(); it.hasNext();) {
					DCSReflectorEntry refEntry = it.next();
					if(refEntry.getActivityTimeKepper().isTimeout(180, TimeUnit.SECONDS)) {
						if(log.isInfoEnabled())
							log.info(logHeader + "Delete inactive process entry.\n" + refEntry.toString(4));

						finalizeEntry(refEntry);

						it.remove();
						break;
					}
				}
			}finally {
				entriesLocker.unlock();
			}
		}
	}

	private void finalizeEntry(DCSReflectorEntry refEntry){
		assert refEntry != null;

		try {
			if (
				refEntry.getOutgoingChannel() != null &&
				refEntry.getOutgoingChannel().getChannel() != null &&
				refEntry.getOutgoingChannel().getChannel().isOpen()
			){
				refEntry.getOutgoingChannel().getChannel().close();
			}
		}catch(IOException ex){
			if(log.isDebugEnabled())
				log.debug(logHeader + "Error occurred at channel close.", ex);
		}
	}

	private boolean writeHeader(
		final String repeaterCallsign,
		final DCSReflectorEntry entry, final DSTARPacket packet,
		final ConnectionDirectionType direction
	) {
		if(
			(
				(
					entry.getConnectionDirection() != ConnectionDirectionType.OUTGOING ||
					!entry.getRepeaterCallsign().equals(repeaterCallsign)
				) &&
				(
					entry.getConnectionDirection() != ConnectionDirectionType.INCOMING ||
					!entry.getReflectorCallsign().equals(repeaterCallsign)
				)
			) ||
			(
				entry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			entry.getCurrentState() != DCSLinkInternalState.LinkEstablished ||
			entry.getCurrentFrameID() != 0x0 ||
			entry.getLoopBlockID().equals(packet.getLoopblockID())
		) {return false;}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		packet.setLoopblockID(entry.getLoopBlockID());

		if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
			packet.getRfHeader().setRepeater1Callsign(entry.getRepeaterCallsign().toCharArray());
			packet.getRfHeader().setRepeater2Callsign(entry.getReflectorCallsign().toCharArray());
		}
		else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
			packet.getRfHeader().setRepeater1Callsign(entry.getRepeaterCallsign().toCharArray());
			packet.getRfHeader().setRepeater2Callsign(entry.getReflectorCallsign().toCharArray());
		}
		entry.setCurrentHeader(packet);

		entry.setCurrentFrameID(packet.getBackBone().getFrameIDNumber());
		entry.setCurrentFrameSequence((byte)0x00);
		entry.setCurrentLongFrameSequence(0x0);
		entry.setCurrentFrameDirection(ConnectionDirectionType.OUTGOING);

		entry.getSlowdataDecoder().reset();
		entry.setTransmitMessage(null);

		entry.getFrameSequenceTimeKepper().setTimeoutTime(1, TimeUnit.SECONDS);
		entry.getFrameSequenceTimeKepper().updateTimestamp();

		entry.setPacketCount(0);

		if(log.isDebugEnabled())
			log.debug(String.format("%s Start transmit frame 0x%04X.", logHeader, packet.getBackBone().getFrameIDNumber()));

		return true;
	}

	private boolean writeVoice(
		final String repeaterCallsign,
		final DCSReflectorEntry entry,
		final DSTARPacket packet,
		final ConnectionDirectionType direction
	) {
		if(
			(
				(
					entry.getConnectionDirection() != ConnectionDirectionType.OUTGOING ||
					!entry.getRepeaterCallsign().equals(repeaterCallsign)
				) &&
				(
					entry.getConnectionDirection() != ConnectionDirectionType.INCOMING ||
					!entry.getReflectorCallsign().equals(repeaterCallsign)
				)
			) ||
			(
				entry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			entry.getCurrentState() != DCSLinkInternalState.LinkEstablished ||
			entry.getCurrentFrameID() == 0x0 ||
			entry.getCurrentFrameDirection() != ConnectionDirectionType.OUTGOING ||
			entry.getLoopBlockID().equals(packet.getLoopblockID())
		) {return false;}

		//フレームIDを改変する
//		packet.getBackBone().setFrameIDint((packet.getBackBone().getFrameIDint() ^ entry.getModCode()));
		packet.getBackBone().modFrameID(entry.getModCode());

		if(entry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber()) {return false;}

		packet.setLoopblockID(entry.getLoopBlockID());

		FrameSequenceType sequenceType = FrameSequenceType.None;
		if(entry.getPacketCount() == 0) {
			sequenceType = FrameSequenceType.Start;
		}
		else if(packet.isEndVoicePacket()) {
			sequenceType = FrameSequenceType.End;
		}
		else {
			sequenceType = FrameSequenceType.None;
		}

		entry.getCacheTransmitter().inputWrite(
			new DCSTransmitPacketEntry(
				PacketType.Voice,
				packet,
				entry.getCurrentLongFrameSequence(),
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				sequenceType
			)
		);

		if(
			entry.getSlowdataDecoder().decode(packet.getVoiceData().getDataSegment()) ==
			DataSegmentDecoderResult.ShortMessage
		) {
			entry.setTransmitMessage(String.valueOf(entry.getSlowdataDecoder().getShortMessage()));
		}

		entry.setCurrentFrameSequence(packet.getBackBone().getSequenceNumber());
		entry.setCurrentLongFrameSequence(entry.getCurrentLongFrameSequence() + 1);
		entry.setPacketCount(
			entry.getPacketCount() < Long.MAX_VALUE ? entry.getPacketCount() + 1 : entry.getPacketCount()
		);

		entry.getFrameSequenceTimeKepper().updateTimestamp();

		if(packet.isEndVoicePacket() && log.isDebugEnabled())
			log.debug(String.format("%s End of transmit frame 0x%04X.", logHeader, packet.getBackBone().getFrameIDNumber()));

		return true;
	}

	private boolean sendPacket(DCSReflectorEntry entry, DCSPacket packet) {
		assert entry != null && packet != null;

		byte[] buffer = null;

		switch(packet.getDCSPacketType()) {
		case CONNECT:
			Optional<byte[]> connectPacket = DCSConnect.assemblePacket(packet);
			if(connectPacket.isPresent())
				buffer = connectPacket.get();
			else
				return false;
			break;

		case POLL:
			Optional<byte[]> pollPacket = DCSPoll.assemblePacket(packet);
			if(pollPacket.isPresent())
				buffer = pollPacket.get();
			else
				return false;
			break;

		case HEADERVOICE:
			if(entry.getTransmitMessage() != null && !"".equals(entry.getTransmitMessage())) {
				packet.setText(entry.getTransmitMessage());
			}
			else {
				packet.setText(getApplicationName());
			}
			Optional<byte[]> headerVoicePacket = DCSHeaderVoice.assemblePacket(packet);
			if(headerVoicePacket.isPresent())
				buffer = headerVoicePacket.get();
			else
				return false;
			break;

		default:
			return false;
		}

		final SocketIOEntryUDP dstChannel =
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				entry.getOutgoingChannel() : incomingChannel;

		if(dstChannel == null) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "destination channel is null.\n" + entry.toString(4));

			return false;
		}

		if(log.isTraceEnabled())
			log.trace(logHeader + "Send packet.\n" + packet.toString(4) + "\n" + FormatUtil.bytesToHexDump(buffer, 4));

		final boolean result =
			super.writeUDPPacket(dstChannel.getKey(), entry.getRemoteAddressPort(), ByteBuffer.wrap(buffer));


		return result;
	}

	private boolean addRxEndPacket(final DCSReflectorEntry entry) {

		final ReflectorReceivePacket preLastPacket =
			new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				createPreLastVoicePacket(
					entry, entry.getCurrentFrameID(), entry.getCurrentFrameSequence()
				)
			);

		final ReflectorReceivePacket lastPacket =
			new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				createLastVoicePacket(
					entry,
					entry.getCurrentFrameID(),
					DSTARUtils.getNextShortSequence(entry.getCurrentFrameSequence())
				)
			);

		return addReflectorReceivePacket(preLastPacket) && addReflectorReceivePacket(lastPacket);
	}

	private boolean addTxLastPacket(final DCSReflectorEntry entry) {

		entry.getCacheTransmitter().inputWrite(
			new DCSTransmitPacketEntry(
				PacketType.Voice,
				createPreLastVoicePacket(
					entry,
					entry.getCurrentFrameID(), entry.getCurrentFrameSequence()
				),
				entry.getCurrentLongFrameSequence(),
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				FrameSequenceType.None
			)
		);

		entry.getCacheTransmitter().inputWrite(
			new DCSTransmitPacketEntry(
				PacketType.Voice,
				createLastVoicePacket(
					entry,
					entry.getCurrentFrameID(),
					DSTARUtils.getNextShortSequence(entry.getCurrentFrameSequence())
				),
				entry.getCurrentLongFrameSequence(),
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				FrameSequenceType.End
			)
		);

		return true;
	}

	private boolean addRxPacket(
		final DCSReflectorEntry entry,
		final PacketType packetType,
		final DSTARPacket packet
	) {
		ReflectorReceivePacket reflectorPacket = null;

		if(
			packetType == PacketType.Header &&
			packet.getDVPacket().hasPacketType(PacketType.Header)
		) {
			packet.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

			packet.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataHeader);
			packet.getBackBone().setSequenceNumber((byte)0x0);

			reflectorPacket = new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				packet
			);
		}
		else if(
			packetType == PacketType.Voice &&
			packet.getDVPacket().hasPacketType(PacketType.Voice)
		) {
			packet.getDVPacket().setRfHeader(entry.getCurrentHeader().getRFHeader());
			packet.getDVPacket().setPacketType(PacketType.Header, PacketType.Voice);

			reflectorPacket = new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				packet
			);
		}

		if(reflectorPacket != null && log.isTraceEnabled())
			log.trace(logHeader + "Added received header packet.\n" + packet.toString(4));

		return reflectorPacket != null && addReflectorReceivePacket(reflectorPacket);
	}

	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, null, null, null);
	}

	@SuppressWarnings("unused")
	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, direction, null, null, null);
	}

	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, direction, repeaterCallsign, reflectorCallsign, null);
	}

	@SuppressWarnings("unused")
	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, repeaterCallsign, reflectorCallsign, null);
	}

	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final DCSLinkInternalState currentState
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, null, null, currentState);
	}

	private Stream<DCSReflectorEntry> findReflectorEntry(
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(null, -1, -1, direction, repeaterCallsign, reflectorCallsign, null);
	}

	private Stream<DCSReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign,
		final DCSLinkInternalState currentState
	){
		entriesLocker.lock();
		try {
			Stream<DCSReflectorEntry> result =
				Stream.of(entries)
				.filter(new Predicate<DCSReflectorEntry>() {
					@Override
					public boolean test(DCSReflectorEntry entry) {
						boolean match =
							(
								remoteAddress == null ||
								entry.getRemoteAddressPort().getAddress().equals(remoteAddress)
							) &&
							(
								remotePort < 0 ||
								entry.getRemoteAddressPort().getPort() == remotePort
							) &&
							(
								localPort < 0 ||
								entry.getLocalAddressPort().getPort() == localPort
							) &&
							(
								direction == null ||
								entry.getConnectionDirection() == direction
							) &&
							(
								repeaterCallsign == null ||
								entry.getRepeaterCallsign().equals(repeaterCallsign)
							) &&
							(
								reflectorCallsign == null ||
								entry.getReflectorCallsign().equals(reflectorCallsign)
							) &&
							(
								currentState == null ||
								entry.getCurrentState() == currentState
							);
						return match;
					}
				});

			return result;
		}finally {entriesLocker.unlock();}
	}

	private boolean parsePacket(Queue<DCSPacket> receivePackets) {
		assert receivePackets != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				for (Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator(); itBufferBytes.hasNext(); ) {
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
						final StringBuilder sb = new StringBuilder(logHeader);
						sb.append(bufferLength);
						sb.append(" bytes received from ");
						sb.append(buffer.getRemoteAddress().toString());
						sb.append(".\n");
						sb.append(FormatUtil.byteBufferToHexDump(receivePacket, 4));
						log.trace(sb.toString());

						receivePacket.rewind();
					}

					boolean match = false;
					Optional<DCSPacket> validPacket = null;
					do {
						if (
							(validPacket = DCSConnect.validPacket(receivePacket)).isPresent() ||
							(validPacket = DCSPoll.validPacket(receivePacket)).isPresent() ||
							(validPacket = DCSHeaderVoice.validPacket(receivePacket)).isPresent()
						) {
							final DCSPacket copyPacket = validPacket.get();

							copyPacket.setRemoteAddress(buffer.getRemoteAddress());
							copyPacket.setLocalAddress(buffer.getLocalAddress());

							receivePackets.add(copyPacket);

							if(log.isTraceEnabled())
								log.trace(logHeader + "Receive packet.\n" + copyPacket.toString(4));

							update = match = true;
						} else {
							match = false;
						}
					} while (match);
				}

				buffer.setUpdate(false);

			}finally{
				buffer.getLocker().unlock();
			}
		}

		return update;
	}

	private void finalizeReflectorEntries(){
		entriesLocker.lock();
		try {
			for(Iterator<DCSReflectorEntry> it = entries.iterator(); it.hasNext(); ) {
				final DCSReflectorEntry refEntry = it.next();

				finalizeReflectorEntry(refEntry);

				it.remove();
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void finalizeReflectorEntry(DCSReflectorEntry refEntry){
		assert refEntry != null;

		try {
			if (
				refEntry.getOutgoingChannel() != null &&
				refEntry.getOutgoingChannel().getChannel() != null &&
				refEntry.getOutgoingChannel().getChannel().isOpen()
			){
				refEntry.getOutgoingChannel().getChannel().close();
			}
		}catch(IOException ex){
			log.debug(logHeader + logHeader + "Error occurred at channel close.", ex);
		}
	}

	private void closeIncommingChannel(){
		if(incomingChannel != null && incomingChannel.getChannel().isOpen()) {
			try {
				incomingChannel.getChannel().close();
				incomingChannel = null;
			}catch(IOException ex) {
				log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}

	private int countLinkEntry(final ConnectionDirectionType direction) {
		entriesLocker.lock();
		try {
			long count =
				Stream.of(entries)
				.filter(new Predicate<DCSReflectorEntry>() {
					@Override
					public boolean test(DCSReflectorEntry entry) {
						return (direction == null || entry.getConnectionDirection() == direction);
					}
				})
				.count();

			return (int)count;
		}finally {
			entriesLocker.unlock();
		}
	}

	@Override
	protected List<ReflectorLinkInformation> getLinkInformation(
		final DSTARRepeater repeater, ConnectionDirectionType connectionDirection
	){
		entriesLocker.lock();
		try {
			return Stream.of(entries)
			.filter(new Predicate<DCSReflectorEntry>() {
				@Override
				public boolean test(DCSReflectorEntry value) {
					return
						(
							repeater == null ||
							value.getDestinationRepeater() == repeater
						) &&
						(
							value.getCurrentState() == DCSLinkInternalState.Linking ||
							value.getCurrentState() == DCSLinkInternalState.LinkEstablished
						) &&
						(connectionDirection == null || value.getConnectionDirection() == connectionDirection);
				}
			})
			.map(new Function<DCSReflectorEntry, ReflectorLinkInformation>(){
				@Override
				public ReflectorLinkInformation apply(DCSReflectorEntry t) {
					return new ReflectorLinkInformation(
						t.getId(),
						t.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
							t.getReflectorCallsign() : t.getRepeaterCallsign(),
						DSTARProtocol.DCS,
						t.getDestinationRepeater(),
						t.getConnectionDirection(),
						false,
						t.getCurrentState() == DCSLinkInternalState.LinkEstablished,
						t.getRemoteAddressPort().getAddress(),
						t.getRemoteAddressPort().getPort(),
						t.getOutgoingReflectorHostInfo()
					);
				}
			}).toList();

		}finally {entriesLocker.unlock();}
	}
}
