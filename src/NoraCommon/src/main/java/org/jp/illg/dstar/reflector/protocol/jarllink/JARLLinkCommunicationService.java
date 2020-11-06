package org.jp.illg.dstar.reflector.protocol.jarllink;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManager;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ConnectionRequest;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceStatus;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.protocol.ReflectorCommunicationServiceBase;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkEntry;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkInternalState;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkPacket;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkPacketType;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkTransmitPacketEntry;
import org.jp.illg.dstar.reflector.protocol.jarllink.model.JARLLinkTransmitType;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorReceivePacket;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlJARLLinkHandler;
import org.jp.illg.dstar.service.web.model.JARLLinkClient;
import org.jp.illg.dstar.service.web.model.JARLLinkConnectionData;
import org.jp.illg.dstar.service.web.model.JARLLinkStatusData;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARCRCCalculator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.dns.DNSRoundrobinUtil;
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

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JARLLinkCommunicationService
extends ReflectorCommunicationServiceBase<BufferEntry, JARLLinkEntry>
implements WebRemoteControlJARLLinkHandler{

	@SuppressWarnings("unused")
	private static final byte FlagGateway = (byte)0x80;
	private static final byte FlagZoneRepeater = (byte)0x40;
	private static final byte FlagForward = (byte)0x04;
	@SuppressWarnings("unused")
	private static final byte FlagXChange = (byte)0x08;

	private static final String logHeader;

	private static final Pattern keepalivePattern =
		Pattern.compile(
			"^[H][P][C][H]" +
			"((?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?))" +
			"([:]([0-9]{1,5})).*"
		);

	private static final Pattern errorMessagePattern =
		Pattern.compile(
			"(not.*(registed|registered|登録済))|(invalid|間違|未登録)",
			Pattern.CASE_INSENSITIVE
		);

	private class JARLLinkCachedHeader{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private int frameID;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long lastActivatedTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private DSTARPacket header;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private InetAddress remoteAddress;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private int remotePort;

		private JARLLinkCachedHeader() {
			super();

			setCreatedTimestamp(System.currentTimeMillis());
		}

		public JARLLinkCachedHeader(int frameID, DSTARPacket headerPacket, InetAddress remoteAddress, int remotePort) {
			this();
			setFrameID(frameID);
			setHeader(headerPacket);
			setRemoteAddress(remoteAddress);
			setRemotePort(remotePort);
		}

		public void updateLastActivatedTimestamp() {
			setLastActivatedTimestamp(System.currentTimeMillis());
		}
	}

	private final Map<Integer, JARLLinkCachedHeader> cachedHeaders;
	private final Lock cachedHeadersLocker = new ReentrantLock();

	private enum ProcessState{
		Initialize,
		MainProcess,
		Wait,
		;
	}

	private ProcessState currentState;
	private ProcessState nextState;
	private ProcessState callbackState;

	private final Timer stateTimeKeeper;

	private final JARLLinkRepeaterHostnameService hostnameService;

	private final byte[] keepaliveAppInformation;

	private final Queue<JARLLinkPacket> receivePackets;

	private final Queue<UUID> entryRemoveRequestQueue;
	private final Lock entryRemoveRequestQueueLocker = new ReentrantLock();

	private SocketIOEntryUDP incommingChannel;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	private final DNSRoundrobinUtil connectionObserverAddressResolver;

	@Getter
	@Setter
	private String connectionObserverAddress;
	@Getter
	private static final String connectionObserverAddressPropertyName = "ConnectionObserverAddress";
	public static final String connectionObserverAddressDefault = "hole-punchd.d-star.info";

	@Getter
	@Setter
	private int connectionObserverPort;
	@Getter
	private static final String connectionObserverPortPropertyName = "ConnectionObserverPort";
	public static final int connectionObserverPortDefault = 30010;

	@Getter
	@Setter
	private String repeaterHostnameServerAddress;
	@Getter
	private static final String repeaterHostnameServerAddressPropertyName = "RepeaterHostnameServerAddress";
	public static final String repeaterHostnameServerAddressDefault = "mfrptlst.k-dk.net";

	@Getter
	@Setter
	private int repeaterHostnameServerPort;
	@Getter
	private static final String repeaterHostnameServerPortPropertyName = "RepeaterHostnameServerPort";
	public static final int repeaterHostnameServerPortDefault = 30011;

	@Getter
	@Setter
	private boolean ignoreKeepalive;
	@Getter
	private static final String ignoreKeepalivePropertyName = "IgnoreKeepalive";
	public static final boolean ignoreKeepaliveDefault = false;

	@Getter
	@Setter
	private boolean ignoreLinkStateOnLinking;
	@Getter
	private static final String ignoreLinkStateOnLinkingPropertyName = "IgnoreLinkStateOnLinking";
	public static final boolean ignoreLinkStateOnLinkingDefault = false;

	@Getter
	private int protocolVersion;
	@Getter
	private static final String protocolVersionPropertyName = "ProtocolVersion";
	public static final int protocolVersionDefault = 1;
	public static final int protocolVersionMin = 1;
	public static final int protocolVersionMax = 2;

	@Getter
	@Setter
	private String loginCallsign;
	@Getter
	private static final String loginCallsignPropertyName = "LoginCallsign";
	private static final String loginCallsignDefault = DSTARDefines.EmptyLongCallsign;

	static {
		logHeader = JARLLinkCommunicationService.class.getSimpleName() + " : ";
	}

	public JARLLinkCommunicationService(
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
			JARLLinkCommunicationService.class,
			socketIO,
			BufferEntry.class, HostIdentType.RemoteLocalAddressPort,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);

		setBufferSizeUDP(1024 * 1024 * 4);

		stateTimeKeeper = new Timer();

		entryRemoveRequestQueue = new LinkedList<>();

		receivePackets = new LinkedList<>();

		cachedHeaders = new HashMap<>();

		connectionObserverAddressResolver = new DNSRoundrobinUtil();

		hostnameService = new JARLLinkRepeaterHostnameService(
			getExceptionListener(),
			this, getSocketIO(), connectionObserverAddressResolver,
			workerExecutor
		);

		incommingChannel = null;

		keepaliveAppInformation = new byte[10];
		Arrays.fill(keepaliveAppInformation, (byte)0x00);

		currentState = ProcessState.Initialize;
		nextState = ProcessState.Initialize;
		callbackState = ProcessState.Initialize;

		setRepeaterHostnameServerAddress(repeaterHostnameServerAddressDefault);
		setRepeaterHostnameServerPort(repeaterHostnameServerPortDefault);
		setConnectionObserverAddress(connectionObserverAddressDefault);
		setConnectionObserverPort(connectionObserverPortDefault);
		setIgnoreKeepalive(ignoreKeepaliveDefault);
		setIgnoreLinkStateOnLinking(ignoreLinkStateOnLinkingDefault);
		setProtocolVersion(protocolVersionDefault);
		setLoginCallsign(loginCallsignDefault);

		hostnameService.setServerAddress(getRepeaterHostnameServerAddress());
		hostnameService.setServerPort(getRepeaterHostnameServerPort());
	}

	public JARLLinkCommunicationService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		this(systemID, exceptionListener, gateway, workerExecutor, null, reflectorLinkManager, eventListener);
	}

	public void setProtocolVersion(int protocolVersion) {
		if(protocolVersion < protocolVersionMin)
			this.protocolVersion = protocolVersionMin;
		else if(protocolVersion > protocolVersionMax)
			this.protocolVersion = protocolVersionMax;
		else
			this.protocolVersion = protocolVersion;
	}

	@Override
	public boolean start() {
		if(isIgnoreKeepalive()) {
			if(log.isWarnEnabled()) {log.warn(logHeader + "IgnoreKeepAlive is enabled.");}
		}

		return super.start();
	}

	@Override
	public DSTARGateway getGateway() {
		return super.getGateway();
	}

	@Override
	public DSTARProtocol getProtocolType() {
		return DSTARProtocol.JARLLink;
	}

	@Override
	public ReflectorProtocolProcessorTypes getProcessorType() {
		return ReflectorProtocolProcessorTypes.JARLLink;
	}

	@Override
	public boolean setProperties(ReflectorProperties properties) {
/*
		setConnectionObserverAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				connectionObserverAddressPropertyName,
				connectionObserverAddressDefault
			)
		);
		if(getConnectionObserverAddress().equals("hole-punch.d-star.info")) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Connection observer = " + getConnectionObserverAddress() +
					" is not available, replaced with hole-punchd.d-star.info."
				);
			}
			setConnectionObserverAddress("hole-punchd.d-star.info");
		}

		setConnectionObserverPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				connectionObserverPortPropertyName,
				connectionObserverPortDefault
			)
		);

		setRepeaterHostnameServerAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				repeaterHostnameServerAddressPropertyName,
				repeaterHostnameServerAddressDefault
			)
		);
		if(getRepeaterHostnameServerAddress().equals("hole-punch.d-star.info")) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Repeater host name server = " + getRepeaterHostnameServerAddress() +
					" is not available, replaced with " + repeaterHostnameServerAddressDefault
				);
			}
			setRepeaterHostnameServerAddress(repeaterHostnameServerAddressDefault);
		}

		setRepeaterHostnameServerPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				repeaterHostnameServerPortPropertyName,
				repeaterHostnameServerPortDefault
			)
		);
*/
		setIgnoreKeepalive(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				ignoreKeepalivePropertyName,
				ignoreKeepaliveDefault
			)
		);

		setIgnoreLinkStateOnLinking(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				ignoreLinkStateOnLinkingPropertyName,
				ignoreLinkStateOnLinkingDefault
			)
		);

		setProtocolVersion(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				protocolVersionPropertyName,
				protocolVersionDefault
			)
		);

		setLoginCallsign(
			DSTARUtils.formatFullLengthCallsign(
				PropertyUtils.getString(
					properties.getConfigurationProperties(),
					loginCallsignPropertyName,
					loginCallsignDefault
				)
			)
		);
		if(!CallSignValidator.isValidUserCallsign(getLoginCallsign())) {
			final String gatewayCallsign =
				DSTARUtils.formatFullCallsign(getGateway().getGatewayCallsign(), ' ');

			if(log.isInfoEnabled()) {
				log.info(
					logHeader + "Property '" + loginCallsignPropertyName + "' = '" + getLoginCallsign() +
					"' is illegal callsign format, replaced by '" + gatewayCallsign + "'."
				);
			}

			setLoginCallsign(gatewayCallsign);
		}

		return true;
	}

	@Override
	public ReflectorProperties getProperties(ReflectorProperties properties) {

		//TODO

		return properties;
	}

	@Override
	public ReflectorCommunicationServiceStatus getStatus() {
		return currentState == ProcessState.MainProcess ?
			ReflectorCommunicationServiceStatus.InService : ReflectorCommunicationServiceStatus.OutOfService;
	}

	@Override
	public boolean hasWriteSpace() {
		return isRunning();
	}

	@Override
	public UUID linkReflector(
		final String reflectorCallsign, final ReflectorHostInfo reflectorHostInfo,
		final DSTARRepeater repeater
	) {
		if(
			reflectorHostInfo == null || repeater == null ||
			reflectorHostInfo.getReflectorProtocol() != getProtocolType() ||
			!CallSignValidator.isValidJARLLinkReflectorCallsign(reflectorCallsign)
		) {return null;}

		UUID entryID = null;

		entriesLocker.lock();
		try {
			for(final JARLLinkEntry entry : entries) {
				if(
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
					entry.getRepeaterCallsign().equals(repeater.getRepeaterCallsign())
				) {return null;}
			}

			final SocketIOEntryUDP channel =
				getSocketIO().registUDP(
					super.getHandler(),
					this.getClass().getSimpleName()
				);
			if(channel == null) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not register JARLLink outgoing udp channel.");

				return null;
			}

			final Optional<InetAddress> connectionObserver =
				connectionObserverAddressResolver.getCurrentHostAddress();
			if(!connectionObserver.isPresent()) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not resolve connection observer address.");

				return null;
			}

			final JARLLinkEntry newEntry = new JARLLinkEntry(
				generateLoopBlockID(),
				10,
				new InetSocketAddress(
					reflectorHostInfo.getReflectorAddress(), reflectorHostInfo.getReflectorPort()
				),
				channel.getLocalAddress(),
				ConnectionDirectionType.OUTGOING
			);
			newEntry.setOutgoingReflectorHostInfo(reflectorHostInfo);
			newEntry.setModCode(getModCode());
			newEntry.setProtocolVersion(getProtocolVersion());
			newEntry.setConnectionState(JARLLinkInternalState.Linking);
			newEntry.getConnectionStateTimeKeeper().setTimeoutTime(3, TimeUnit.SECONDS);
			newEntry.getConnectionStateTimeKeeper().updateTimestamp();
			newEntry.setOutgoingChannel(channel);
			newEntry.setConnectionObserverAddressPort(
				new InetSocketAddress(
					connectionObserver.get(),
					getConnectionObserverPort()
				)
			);
			newEntry.setDestinationRepeater(repeater);
			newEntry.setReflectorCallsign(reflectorCallsign);
			newEntry.setRepeaterCallsign(repeater.getRepeaterCallsign());
			newEntry.getReceiveRepeaterKeepAliveTimeKeeper().setTimeoutTime(20, TimeUnit.SECONDS);
//			newEntry.getReceiveServerKeepAliveTimeKeeper().setTimeoutTime(1, TimeUnit.MINUTES);
//			newEntry.setReceiveServerKeepAliveLastTime(0L);
			newEntry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(1, TimeUnit.SECONDS);

			entries.add(newEntry);

			if(log.isTraceEnabled())
				log.trace(logHeader + "Added outgoing connection entry.\n" + newEntry.toString(4));

			entryID = newEntry.getId();
		}finally {
			entriesLocker.unlock();
		}

		notifyStatusChanged();

		wakeupProcessThread();

		return entryID;
	}

	@Override
	public UUID unlinkReflector(DSTARRepeater repeater) {
		if(
			!isRunning() ||
			repeater == null ||
			!CallSignValidator.isValidRepeaterCallsign(repeater.getRepeaterCallsign())
		) {return null;}

		entriesLocker.lock();
		try {
			JARLLinkEntry unlinkEntry = null;
			for(JARLLinkEntry entry : entries) {
				if(
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
					entry.getRepeaterCallsign().equals(repeater.getRepeaterCallsign())
				) {
					unlinkEntry = entry;
					break;
				}
			}

			if(unlinkEntry == null) {return null;}

			switch(unlinkEntry.getConnectionState()) {
			case LinkEstablished:
			case Linking:
				unlinkEntry.setConnectionRequest(ConnectionRequest.UnlinkRequest);
				break;

			case Unlinking:
				break;

			case Unlinked:
				addConnectionStateChangeEvent(
					unlinkEntry.getId(),
					unlinkEntry.getConnectionDirection(),
					unlinkEntry.getRepeaterCallsign(), unlinkEntry.getReflectorCallsign(),
					ReflectorConnectionStates.UNLINKED,
					unlinkEntry.getOutgoingReflectorHostInfo()
				);
				break;

			default:
				addEntryRemoveRequestQueue(unlinkEntry.getId());
				break;
			}

			return unlinkEntry.getId();
		}finally {
			entriesLocker.unlock();
		}
	}

	@Override
	public boolean isSupportedReflectorCallsign(String reflectorCallsign) {
		return CallSignValidator.isValidUserCallsign(reflectorCallsign);
	}

	@Override
	public boolean writePacketInternal(DSTARRepeater repeater, DSTARPacket packet, ConnectionDirectionType direction) {
		if(
			repeater == null ||
			packet == null || packet.getPacketType() != DSTARPacketType.DV
		) {return false;}

		entriesLocker.lock();
		try {
			for(final JARLLinkEntry refEntry : entries) {
				if(
					direction == ConnectionDirectionType.BIDIRECTIONAL ||
					refEntry.getConnectionDirection() == direction
				) {
					if(packet.getDVPacket().hasPacketType(PacketType.Header))
						writeHeader(repeater.getRepeaterCallsign(), refEntry, packet, direction);

					if(packet.getDVPacket().hasPacketType(PacketType.Voice))
						writeVoice(repeater.getRepeaterCallsign(), refEntry, packet, direction);
				}
			}
		}finally {
			entriesLocker.unlock();
		}

		return true;
	}

	@Override
	public boolean isSupportTransparentMode() {
		return true;
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
		if(log.isTraceEnabled())
			log.trace(logHeader + "Connected event.");

		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isTraceEnabled())
			log.trace(logHeader + "Disconnected remote host " + remoteAddress.toString());
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
		if(log.isTraceEnabled())
			log.trace(logHeader + "error event received from " + remoteAddress + ".", ex);
	}

	@Override
	public boolean isEnableIncomingLink() {
		return false;
	}

	@Override
	public int getIncomingLinkPort() {
		return 0;
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processReceivePacket() {
		if(currentState == ProcessState.MainProcess) {
			processReceiveData(receivePackets);

			for(Iterator<JARLLinkPacket> it = receivePackets.iterator(); it.hasNext();) {
				final JARLLinkPacket packet = it.next();
				it.remove();

				if(packet.getDVPacket().hasPacketType(PacketType.Header))
					processHeader(packet);

				if(packet.getDVPacket().hasPacketType(PacketType.Voice))
					processVoice(packet);
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processConnectionState() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		boolean reProcess;
		do {
			reProcess = false;

			setStateChanged(currentState != nextState);
			currentState = nextState;

			switch(currentState) {
			case Initialize:
				processResult = onStateInitialize();
				break;

			case MainProcess:
				processResult = onStateMainProcess();
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

		hostnameService.process();

		return processResult;
	}

	@Override
	protected ThreadProcessResult processVoiceTransfer() {
		entriesLocker.lock();
		try {
			for(final JARLLinkEntry entry : entries) {
				// リモートへ流すデータがあれば送信する
				Optional<JARLLinkTransmitPacketEntry> transmitPacket = null;
				while((transmitPacket = entry.getCacheTransmitter().outputRead()).isPresent()) {
					JARLLinkTransmitPacketEntry t = transmitPacket.get();

					t.getPacket().getBackBone().undoModFrameID();

					sendPacket(t.getPacketType(), t.getPacket(), entry);
				}
				if(entry.getCacheTransmitter().isUnderflow() && !entry.isCacheTransmitterUndeflow()) {
					if(log.isDebugEnabled())
						log.debug(logHeader + "Transmitter cache underflow detected.\n" + entry.toString(4));
				}
				entry.setCacheTransmitterUndeflow(entry.getCacheTransmitter().isUnderflow());
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
			if(entries.isEmpty() && !hostnameService.isActive())
				return ProcessIntervalMode.Sleep;

			for(final JARLLinkEntry entry : entries) {
				if(
					entry.getCurrentFrameID() != 0x0 &&
					entry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING
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
		report.setEnableIncomingLink(false);
		report.setEnableOutgoingLink(true);
		report.setConnectedIncomingLink(0);
		entriesLocker.lock();
		try {

			report.setConnectedOutgoingLink(
				(int)Stream.of(entries)
				.filter(e -> e.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				.count()
			);
		}finally {
			entriesLocker.unlock();
		}
		report.setIncomingLinkPort(0);
		report.setIncomingStatus("");
		report.setOutgoingStatus("");

		return report;
	}

	@Override
	protected boolean initializeWebRemoteControlInternal(
		WebRemoteControlService webRemoteControlService
	) {
		return webRemoteControlService.initializeReflectorJARLLink(this);
	}

	@Override
	protected boolean getReflectorConnectionsInternal(
		@NonNull List<ReflectorConnectionData> connections
	) {
		entriesLocker.lock();
		try {
			for(final JARLLinkEntry entry : entries) {
				final JARLLinkConnectionData con = new JARLLinkConnectionData();
				con.setConnectionId(entry.getId());
				con.setReflectorType(getReflectorType());
				con.setConnectionDirection(entry.getConnectionDirection());
				con.setReflectorCallsign(entry.getReflectorCallsign());
				con.setRepeaterCallsign(entry.getRepeaterCallsign());
				con.setProtocolVersion(1);

				con.setLoginClients(
					Stream.of(entry.getRemoteUsers())
					.map(new Function<ReflectorRemoteUserEntry, JARLLinkClient>(){
						@Override
						public JARLLinkClient apply(ReflectorRemoteUserEntry user) {
							return new JARLLinkClient(user.getUserCallsign());
						}
					}).toList()
				);
				con.setExtraRepeaterLinked(entry.isRepeaterKeepAliveReceived());
				con.setServerSoftware(entry.getServerSoftware());

				connections.add(con);
			}
		}finally {
			entriesLocker.unlock();
		}

		return true;
	}

	@Override
	protected ReflectorStatusData createStatusDataInternal() {
		final JARLLinkStatusData status = new JARLLinkStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ReflectorStatusData> getStatusDataTypeInternal() {
		return JARLLinkStatusData.class;
	}

	private ThreadProcessResult onStateInitialize() {

		connectionObserverAddressResolver.setHostname(getConnectionObserverAddress());

		hostnameService.setServerAddress(getRepeaterHostnameServerAddress());
		hostnameService.setServerPort(getRepeaterHostnameServerPort());

		nextState = ProcessState.MainProcess;

		//ホールパンチキープアライブ用アプリケーション情報セット
		String applicationName = getApplicationName().replaceAll("[^A-Z]", "");
		applicationName = applicationName.substring(0, Math.min(applicationName.length(), 2));
		String applicationVersion = getApplicationVersion().replaceAll("[^0-9ab-]", "");
		Arrays.fill(keepaliveAppInformation, (byte)' ');
		ArrayUtil.copyOf(
			keepaliveAppInformation,
			(
				String.format("%-2s%-8s", applicationName, applicationVersion)
			).getBytes(StandardCharsets.US_ASCII)
		);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateMainProcess() {

		if(isStateChanged()) {

		}
		else {
			boolean notifyWebRemote = false;
			entriesLocker.lock();
			try {
				for(JARLLinkEntry entry : entries) {
					switch(entry.getConnectionState()) {
					case Linking:
						if(entry.getConnectionStateTimeKeeper().isTimeout(5, TimeUnit.SECONDS)) {

							addConnectionStateChangeEvent(
								entry.getId(),
								entry.getConnectionDirection(),
								entry.getRepeaterCallsign(), entry.getReflectorCallsign(),
								ReflectorConnectionStates.LINKFAILED,
								entry.getOutgoingReflectorHostInfo()
							);

							addEntryRemoveRequestQueue(entry.getId());

							connectionObserverAddressResolver.notifyDeadHostAddress(
								entry.getConnectionObserverAddressPort().getAddress()
							);
							connectionObserverAddressResolver.nextHostAddress();

							notifyWebRemote = true;
						}
						else if(
//							entry.getReceiveServerKeepAliveLastTime() > 0L ||
							entry.isRepeaterKeepAliveReceived() ||
							isIgnoreKeepalive() ||
							isIgnoreLinkStateOnLinking()
						) {
							entry.setConnectionState(JARLLinkInternalState.LinkEstablished);

							entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();
//							entry.getReceiveServerKeepAliveTimeKeeper().updateTimestamp();

							addConnectionStateChangeEvent(
								entry.getId(),
								entry.getConnectionDirection(),
								entry.getRepeaterCallsign(), entry.getReflectorCallsign(),
								ReflectorConnectionStates.LINKED,
								entry.getOutgoingReflectorHostInfo()
							);

							notifyWebRemote = true;
						}
						else if(entry.getTransmitKeepAliveTimeKeeper().isTimeout(1, TimeUnit.SECONDS)) {
							entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

							sendKeepAlive(entry, true, true);
						}
						break;

					case LinkEstablished:
						if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
							sendDisconnect(entry);

							entry.setConnectionState(JARLLinkInternalState.Unlinking);
						}
						else if(
							!isIgnoreKeepalive() &&
							(
								entry.getReceiveRepeaterKeepAliveTimeKeeper().isTimeout(3, TimeUnit.MINUTES)
//								entry.getReceiveServerKeepAliveTimeKeeper().isTimeout()
							)
						) {
							if(!isIgnoreLinkStateOnLinking()) {
								entry.setConnectionState(JARLLinkInternalState.Linking);

								entry.getConnectionStateTimeKeeper().updateTimestamp();

//								entry.setReceiveServerKeepAliveLastTime(0L);

								entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

								entry.setRepeaterKeepAliveReceived(false);
							}
							else {
								addConnectionStateChangeEvent(
									entry.getId(),
									entry.getConnectionDirection(),
									entry.getRepeaterCallsign(), entry.getReflectorCallsign(),
									ReflectorConnectionStates.LINKFAILED,
									entry.getOutgoingReflectorHostInfo()
								);

								addEntryRemoveRequestQueue(entry.getId());
/*
								if(entry.getReceiveServerKeepAliveTimeKeeper().isTimeout()) {
									connectionObserverAddressResolver.notifyDeadHostAddress(
										entry.getConnectionObserverAddressPort().getAddress()
									);
									connectionObserverAddressResolver.nextHostAddress();
								}
*/
								notifyWebRemote = true;
							}
						}
						else if(entry.getTransmitKeepAliveTimeKeeper().isTimeout(20, TimeUnit.SECONDS)) {
							entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

							sendKeepAlive(entry, false, true);
						}
						else if(
							entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
							(System.currentTimeMillis() -  entry.getCreateTime()) > TimeUnit.SECONDS.toMillis(15L) &&
							entry.isRepeaterKeepAliveReceived() &&
							!entry.isRepeaterConnectAnnounceOutputed() &&
							entry.getCurrentFrameID() == 0x0000
						) {
							entry.setRepeaterConnectAnnounceOutputed(true);

							announceRepeaterLinked(entry);
						}

						if(
							entry.getCurrentFrameID() != 0x0 &&
							entry.getFrameSequenceTimeKepper().isTimeout()
						) {
							if(log.isDebugEnabled()) {
								log.debug(
									logHeader +
									String.format("Timeout frame id...0x%04X.\n%s", entry.getCurrentFrameID(), entry.toString(4))
								);
							}

							entry.setCurrentFrameID(0x0);
							entry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
							entry.setCurrentFrameSequence((byte)0x0);
						}

						break;

					case Unlinking:
						entry.setConnectionState(JARLLinkInternalState.Unlinked);
						break;

					case Unlinked:
						updateRemoteUsers(
							entry.getDestinationRepeater(), entry.getReflectorCallsign(), entry.getConnectionDirection(),
							Collections.emptyList()
						);
						addConnectionStateChangeEvent(
							entry.getId(),
							entry.getConnectionDirection(),
							entry.getRepeaterCallsign(), entry.getReflectorCallsign(),
							ReflectorConnectionStates.UNLINKED,
							entry.getOutgoingReflectorHostInfo()
						);
						addEntryRemoveRequestQueue(entry.getId());

						notifyWebRemote = true;
						break;

					default:
						addEntryRemoveRequestQueue(entry.getId());
						break;
					}
				}
			}finally {
				entriesLocker.unlock();
			}

			processEntryRemoveRequestQueue();

			if(notifyWebRemote) {notifyStatusChanged();}

		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

	private void processReceiveData(Queue<JARLLinkPacket> receivePackets) {

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

				parseReceiveBuffer(buffer, receivePackets, buffer.getRemoteAddress());

				buffer.setUpdate(false);
			}finally {
				buffer.getLocker().unlock();
			}
		}
	}

	private boolean parseReceiveBuffer(
		final BufferEntry buffer, final Queue<JARLLinkPacket> packetDst, final InetSocketAddress remoteAddress
	) {
		assert buffer != null && packetDst != null;

		for(Iterator<PacketInfo> it = buffer.getBufferPacketInfo().iterator(); it.hasNext();) {
			final PacketInfo packetInfo = it.next();
			final int receiveBytes = packetInfo.getPacketBytes();
			it.remove();

			byte[] data = new byte[receiveBytes];
			for(int c = 0; c < data.length && buffer.getBuffer().hasRemaining(); c++) {
				data[c] = buffer.getBuffer().get();
			}

			if(
				receiveBytes == 25 ||
				receiveBytes == 26
			) {	// hole punch keepalive
				char[] recvChar = new char[data.length];
				ArrayUtil.copyOfRange(recvChar, 0, data);
				String recvStr = String.valueOf(recvChar);

				Matcher m = keepalivePattern.matcher(recvStr);
				if(m.matches()) {
					String ipStr = m.group(1);
					String portStr = m.group(3);

					InetAddress ip = null;
					try{
						ip = InetAddress.getByName(ipStr);
					}catch(UnknownHostException ex) {
						if(log.isDebugEnabled())
							log.debug(logHeader + "Illegal ip address format.\n    " + recvStr);
					}

					int port = -1;
					try {
						port = Integer.valueOf(portStr);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logHeader + "Illegal port number format.\n    " + recvStr);
					}

					final InetAddress ipAddr = ip;
					final int portNumber = port;

					if(ip != null && port > 0) {
						findEntry(
							ip,	//Remote Address
							-1,
							-1
						).ifPresent(new Consumer<JARLLinkEntry>() {
							@Override
							public void accept(final JARLLinkEntry entry) {
								if(
									portNumber > 0 &&
									entry.getRemoteAddressPort().getAddress().equals(ipAddr) &&
									entry.getRemoteAddressPort().getPort() != portNumber
								) {
									if(log.isDebugEnabled()) {
										log.debug(
											logHeader +
											"Remote repeater port number changed " +
											entry.getRemoteAddressPort().getAddress() + ":" +
											entry.getRemoteAddressPort().getPort() + "->" + portNumber +
											"."
										);
									}
									entry.setRemoteAddressPort(
										new InetSocketAddress(entry.getRemoteAddressPort().getAddress(), portNumber)
									);
								}

//								entry.getReceiveServerKeepAliveTimeKeeper().updateTimestamp();
//								entry.setReceiveServerKeepAliveLastTime(System.currentTimeMillis());
							}
						});
					}

					connectionObserverAddressResolver.notifyAliveHostAddress(remoteAddress.getAddress());
				}

				findEntry(
					buffer.getRemoteAddress().getAddress(),
					buffer.getRemoteAddress().getPort(),
					buffer.getLocalAddress().getPort()
				).ifPresent(new Consumer<JARLLinkEntry>() {
					@Override
					public void accept(final JARLLinkEntry entry) {
						if(!entry.isRepeaterKeepAliveReceived()) {
							entry.setRepeaterKeepAliveReceived(true);

							if(log.isInfoEnabled()) {
								log.info(
									logHeader +
									"Connected to " + entry.getReflectorCallsign() + " <-> " + entry.getRepeaterCallsign() + "."
								);
							}
						}

						entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();
					}
				});

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader + "Hole punch keepalive received from " + remoteAddress + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}

				continue;
			}
			else if(
				receiveBytes == 44 &&
				data[0] == 'C' && data[1] == 'T' && data[2] == 'B' && data[3] == 'L'
			) {
				findEntry(
					buffer.getRemoteAddress().getAddress(),
					buffer.getRemoteAddress().getPort(),
					buffer.getLocalAddress().getPort()
				).ifPresent(new Consumer<JARLLinkEntry>() {
					@Override
					public void accept(final JARLLinkEntry entry) {
						processCTBL(entry, data, getLoginCallsign());
					}
				});

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader + "CTBL received from " + remoteAddress + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}

				continue;
			}
			else if(
				receiveBytes == 64 &&
				data[0] == 'E' && data[1] == 'R' && data[2] == 'R' && data[3] == 'O' && data[4] == 'R'
			) {
				findEntry(
					buffer.getRemoteAddress().getAddress(),
					buffer.getRemoteAddress().getPort(),
					buffer.getLocalAddress().getPort()
				).ifPresent(new Consumer<JARLLinkEntry>() {
					@Override
					public void accept(final JARLLinkEntry entry) {
						processERROR(entry, data, getLoginCallsign());
					}
				});

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader + "ERROR message received from " + remoteAddress + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}

				continue;
			}

			if(
				data[0] != 'D' || data[1] != 'S' || data[2] != 'T' || data[3] != 'R'
			) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader + "Illegal data received, unknown packet from" + remoteAddress + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}

				continue;
			}

			final JARLLinkTransmitType packetTransmitType =
				JARLLinkTransmitType.getTypeByValue(data[6]);

			final JARLLinkPacketType packetType =
				JARLLinkPacketType.getTypeByValue((byte)data[7]);

			JARLLinkPacket packet = null;

			if(packetType == JARLLinkPacketType.DVPacket && receiveBytes == 58) {	// Header
				final Header header = new Header();
				ArrayUtil.copyOfRange(header.getFlags(), data, 17, 20);
				ArrayUtil.copyOfRange(header.getRepeater2Callsign(), data, 20, 28);
				ArrayUtil.copyOfRange(header.getRepeater1Callsign(), data, 28, 36);
				ArrayUtil.copyOfRange(header.getYourCallsign(), data, 36, 44);
				ArrayUtil.copyOfRange(header.getMyCallsign(), data, 44, 52);
				ArrayUtil.copyOfRange(header.getMyCallsignAdd(), data, 52, 56);

				header.saveRepeaterCallsign();

				packet = new JARLLinkPacket(
					header,
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader)
				);
				ArrayUtil.copyOfRange(packet.getBackBone().getFrameID(), data, 14, 16);
				packet.getBackBone().setManagementInformation((byte)0x80);
			}
			else if(packetType == JARLLinkPacketType.DVPacket && receiveBytes == 29) {	// Voice
				final VoiceAMBE voice = new VoiceAMBE();
				ArrayUtil.copyOfRange(voice.getVoiceSegment(), data, 17, 26);
				ArrayUtil.copyOfRange(voice.getDataSegment(), data, 26, 29);

				packet = new JARLLinkPacket(
					voice,
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData)
				);
				ArrayUtil.copyOfRange(packet.getBackBone().getFrameID(), data, 14, 16);
				packet.getBackBone().setManagementInformation(data[16]);
			}
			else if(packetType == JARLLinkPacketType.DVPacket && receiveBytes == 32) {	// Voice
				VoiceAMBE voice = new VoiceAMBE();
				ArrayUtil.copyOfRange(voice.getVoiceSegment(), data, 17, 26);
				ArrayUtil.copyOfRange(voice.getDataSegment(), data, 26, 29);

				packet = new JARLLinkPacket(
					voice,
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataLastFrame)
				);
				ArrayUtil.copyOfRange(packet.getBackBone().getFrameID(), data, 14, 16);
				packet.getBackBone().setManagementInformation(data[16]);
				packet.getBackBone().setEndSequence();
			}
			else {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader + "Illegal data received, unknown packet size(" + receiveBytes +"bytes)\n" +
						"    [ReceiveData]:" + FormatUtil.bytesToHex(data)
					);
				}
			}

			if(packet != null) {
				packet.setJARLLinkPacketType(packetType);
				packet.setJARLLinkTransmitType(packetTransmitType);

				packet.setLocalAddress(buffer.getLocalAddress());
				packet.setRemoteAddress(buffer.getRemoteAddress());

				packetDst.add(packet);

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader + "Receive JARLLink packet from repeater.\n" +
						packet.toString(4) + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}

			}
		}

		return true;
	}

	private void processHeader(final JARLLinkPacket packet) {
		entriesLocker.lock();
		try {
			findEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			).ifPresent(new Consumer<JARLLinkEntry>() {
				@Override
				public void accept(JARLLinkEntry entry) {
					processHeader(entry, packet, false);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
/*
			for(JARLLinkEntry entry : entries) {
				if(
						entry.getRemoteAddressPort().getAddress().equals(packet.getRemoteAddressPort().getAddress()) &&
						entry.getRemoteAddressPort().getPort() == packet.getRemoteAddressPort().getPort() &&
						entry.getLocalAddressPort().getPort() == packet.getLocalAddressPort().getPort()
				) {
					processHeader(entry, packet, false);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			}
*/
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processHeader(JARLLinkEntry entry, JARLLinkPacket packet, boolean resync) {
		if(
			entry.getConnectionState() != JARLLinkInternalState.LinkEstablished ||
			packet.getJARLLinkTransmitType() != JARLLinkTransmitType.Send ||
			packet.getJARLLinkPacketType() != JARLLinkPacketType.DVPacket
		) {return;}

		//フレームIDを変更する
//		packet.getBackBone().setFrameIDint(packet.getBackBone().getFrameIDint() ^ entry.getModCode());
		packet.getBackBone().modFrameID(entry.getModCode());

		packet.setConnectionDirection(entry.getConnectionDirection());

		packet.setLoopblockID(entry.getLoopBlockID());

		//経路情報を保存
		packet.getRfHeader().setSourceRepeater2Callsign(
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				entry.getReflectorCallsign() : entry.getRepeaterCallsign()
		);

		switch(entry.getConnectionDirection()) {
		case OUTGOING:
			if(entry.getCurrentFrameID() != 0x0) {return;}

			//希望する該当レピータのヘッダのみ通過するようフィルタ
			if(
				!entry.getReflectorCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater1Callsign())) &&
				!entry.getReflectorCallsign().equals(String.valueOf(packet.getRfHeader().getRepeater2Callsign()))
			){return;}

			packet.getRfHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

			entry.setCurrentFrameID(packet.getBackBone().getFrameIDNumber());
			entry.setCurrentFrameSequence((byte)0x0);
			entry.setCurrentFrameDirection(ConnectionDirectionType.INCOMING);
			entry.getFrameSequenceTimeKepper().setTimeoutTime(1, TimeUnit.SECONDS);
			entry.getFrameSequenceTimeKepper().updateTimestamp();
			entry.setCurrentHeader(packet);

			if(log.isDebugEnabled())
				log.debug(logHeader + "Received JARLLink header packet.\n" + packet.toString());

			addCacheHeader(
				entry.getCurrentFrameID(),
				packet,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort()
			);

			addReflectorReceivePacket(new ReflectorReceivePacket(entry.getRepeaterCallsign(), packet));

			break;

		case INCOMING:

			break;

		default:
			break;
		}
	}

	private void processVoice(final JARLLinkPacket packet) {
		entriesLocker.lock();
		try {
			findEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			).ifPresent(new Consumer<JARLLinkEntry>() {
				@Override
				public void accept(JARLLinkEntry entry) {
					processVoice(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
/*
			for(JARLLinkEntry entry : entries) {
				if(
						entry.getRemoteAddressPort().getAddress().equals(packet.getRemoteAddressPort().getAddress()) &&
						entry.getRemoteAddressPort().getPort() == packet.getRemoteAddressPort().getPort() &&
						entry.getLocalAddressPort().getPort() == packet.getLocalAddressPort().getPort()
				) {
					processVoice(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			}
*/
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processVoice(JARLLinkEntry entry, JARLLinkPacket packet) {
		if(
			entry.getConnectionState() != JARLLinkInternalState.LinkEstablished ||
			packet.getJARLLinkTransmitType() != JARLLinkTransmitType.Send ||
			packet.getJARLLinkPacketType() != JARLLinkPacketType.DVPacket
		) {return;}

		//再同期処理
		if(	//ミニデータからの再同期
			entry.getSlowdataDecoder().decode(packet.getVoiceData().getDataSegment()) ==
				DataSegmentDecoderResult.Header
			&& entry.getCurrentFrameID() == 0x0
		) {
			final Header slowdataHeader = entry.getSlowdataDecoder().getHeader();
			if(slowdataHeader != null) {
				JARLLinkPacket resyncHeaderPacket = new JARLLinkPacket(
					slowdataHeader,
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader)
				);
				resyncHeaderPacket.setRemoteAddress(entry.getRemoteAddressPort());

				resyncHeaderPacket.getBackBone().setFrameIDNumber(packet.getBackBone().getFrameIDNumber());

				if(log.isDebugEnabled())
					log.debug(logHeader + "JARLLink resyncing frame by slow data segment...\n" + resyncHeaderPacket.toString(4));

				processHeader(entry, resyncHeaderPacket, true);
			}
		}
		else if(	//ヘッダキャッシュからの再同期
			entry.getCurrentFrameID() == 0x0
		) {
			//resync
			cachedHeadersLocker.lock();
			try {
				JARLLinkCachedHeader cachedHeader =
						this.cachedHeaders.get(packet.getBackBone().getFrameIDNumber() ^ entry.getModCode());
				if(cachedHeader == null) {return;}

				if((System.currentTimeMillis() - cachedHeader.getLastActivatedTimestamp()) < TimeUnit.SECONDS.toMillis(15)) {
					final JARLLinkPacket resyncHeaderPacket = new JARLLinkPacket(
						cachedHeader.getHeader().getRFHeader(),
						new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader)
					);
					resyncHeaderPacket.setRemoteAddress(entry.getRemoteAddressPort());

					resyncHeaderPacket.getBackBone().setFrameIDNumber(packet.getBackBone().getFrameIDNumber());

					if(log.isDebugEnabled())
						log.debug(logHeader + "JARLLink resyncing frame by header cache...\n" + resyncHeaderPacket.toString(4));

					processHeader(entry, resyncHeaderPacket, true);
				}
			}finally {
				cachedHeadersLocker.unlock();
			}
		}

		//フレームIDを変更する
//		packet.getBackBone().setFrameIDint(packet.getBackBone().getFrameIDint() ^ entry.getModCode());
		packet.getBackBone().modFrameID(entry.getModCode());

		//IDが異なるパケットは破棄
		if(entry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber()) {return;}

		packet.setConnectionDirection(entry.getConnectionDirection());

		packet.setLoopblockID(entry.getLoopBlockID());

		packet.getDVPacket().setRfHeader(entry.getCurrentHeader().getRfHeader().clone());

		entry.setCurrentFrameSequence(packet.getBackBone().getSequenceNumber());

		entry.getFrameSequenceTimeKepper().updateTimestamp();

		if(log.isTraceEnabled())
			log.trace(logHeader + "JARLLink received voice packet.\n" + packet.toString(4));

		cachedHeadersLocker.lock();
		try{
			final JARLLinkCachedHeader cachedHeader = this.cachedHeaders.get(entry.getCurrentFrameID());
			if(cachedHeader != null) {cachedHeader.updateLastActivatedTimestamp();}
		}finally {
			cachedHeadersLocker.unlock();
		}

		addReflectorReceivePacket(new ReflectorReceivePacket(entry.getRepeaterCallsign(), packet));

		if(
			!packet.isEndVoicePacket() &&
			packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
		) {
			addReflectorReceivePacket(
				new ReflectorReceivePacket(entry.getRepeaterCallsign(), entry.getCurrentHeader().clone())
			);
		}

		if(packet.isEndVoicePacket()) {
			entry.setCurrentFrameID(0x0);
			entry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			entry.setCurrentFrameSequence((byte)0x0);
		}
	}
/*
	private void toWaitState(long waitTime, TimeUnit timeUnit, ProcessState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = ProcessState.Wait;
		this.callbackState = callbackState;
	}
*/
	private boolean addCacheHeader(
		final int frameID,
		final DSTARPacket headerPacket,
		final InetAddress remoteAddress,
		final int remotePort
	) {
		cachedHeadersLocker.lock();
		try {
			int overlimitHeaders = this.cachedHeaders.size() - 10;
			if(overlimitHeaders >= 0) {
				List<Integer> sortedFrameID =
					Stream.of(this.cachedHeaders)
					.sorted(
						ComparatorCompat.comparingLong(
							new ToLongFunction<Map.Entry<Integer, JARLLinkCachedHeader>>() {
								@Override
								public long applyAsLong(Map.Entry<Integer, JARLLinkCachedHeader> entry) {
									return entry.getValue().getLastActivatedTimestamp();
								}
							}
						)
					)
					.map(
						new Function<Map.Entry<Integer,JARLLinkCachedHeader>, Integer>() {
							@Override
							public Integer apply(Map.Entry<Integer, JARLLinkCachedHeader> entry) {
								return entry.getValue().getFrameID();
							}
						}
					)
					.collect(com.annimon.stream.Collectors.toList());

				int c = 0;
				do{
					this.cachedHeaders.remove(sortedFrameID.get(c));
				}while(overlimitHeaders > c++);
			}

			final JARLLinkCachedHeader cacheHeaderEntry =
				new JARLLinkCachedHeader(frameID, headerPacket, remoteAddress, remotePort);
			if(this.cachedHeaders.containsKey(frameID)) {this.cachedHeaders.remove(frameID);}
			this.cachedHeaders.put(frameID, cacheHeaderEntry);
		}finally {
			cachedHeadersLocker.unlock();
		}

		return true;
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

					for(Iterator<JARLLinkEntry> refEntryIt = entries.iterator(); refEntryIt.hasNext();) {
						JARLLinkEntry refEntry = refEntryIt.next();
						if(refEntry.getId().equals(removeID)) {
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

	private void finalizeEntry(JARLLinkEntry refEntry){
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

	private boolean sendDisconnect(final JARLLinkEntry entry) {
		final byte[] data = new byte[24];
		Arrays.fill(data, (byte)0x00);
		ArrayUtil.copyOfRange(data, 0, "DISCONNECT".getBytes(StandardCharsets.US_ASCII));
		ArrayUtil.copyOfRange(data, 16, getLoginCallsign().getBytes(StandardCharsets.US_ASCII));

		return writeUDPPacket(
			entry.getOutgoingChannel().getKey(),
			entry.getRemoteAddressPort(),
			ByteBuffer.wrap(data)
		);
	}

	private boolean sendKeepAlive(
		JARLLinkEntry entry,
		final boolean isSendObserver, final boolean isSendRepeater
	) {
		assert entry != null;

		boolean isSuccess = true;

		final byte[] buffer = new byte[80];

		if(isSendRepeater) {
			// 0  .. 19 -> NULL
			// 20 .. 27 -> Callsign
			// 28 .. 79 -> NULL
			Arrays.fill(buffer, (byte)0x00);
			ArrayUtil.copyOfRange(buffer, 20, getLoginCallsign().getBytes(StandardCharsets.US_ASCII));

			//接続先レピータへ送信
			if(
				!writeUDPPacket(
					entry.getOutgoingChannel().getKey(),
					entry.getRemoteAddressPort(),
					ByteBuffer.wrap(buffer)
				)
			) {isSuccess = false;}

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Send keepalive to repeater " + entry.getRemoteAddressPort() + ".\n" +
					FormatUtil.bytesToHexDump(buffer, 4) + "\n"
				);
			}
		}


		if(isSendObserver) {
			// 0  .. 3  -> Header(HPCH)
			// 4  .. 19 -> NULL
			// 20 .. 29 -> APP&Version
			// 32 .. 63 -> MD5(Not supprted)
			// 64 .. 71 -> Area Repeater Callsign
			// 72 .. 79 -> Zone Repeater Callsign
			Arrays.fill(buffer, (byte)0x00);
			ArrayUtil.copyOfRange(buffer, 0, "HPCH".getBytes(StandardCharsets.US_ASCII));
			ArrayUtil.copyOfRange(buffer, 20, keepaliveAppInformation);
			ArrayUtil.copyOfRange(buffer, 64, entry.getReflectorCallsign().getBytes(StandardCharsets.US_ASCII));
			ArrayUtil.copyOfRange(buffer, 72,
				DSTARUtils.formatFullCallsign(entry.getReflectorCallsign(), ' ').getBytes(StandardCharsets.US_ASCII)
			);

			if(
				!writeUDPPacket(
					entry.getOutgoingChannel().getKey(),
					entry.getConnectionObserverAddressPort(),
					ByteBuffer.wrap(buffer)
				)
			) {isSuccess = false;}

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Send keepalive to observer " + entry.getConnectionObserverAddressPort() + ".\n" +
					FormatUtil.bytesToHexDump(buffer, 4) + "\n"
				);
			}
		}

		return isSuccess;
	}

	@Override
	protected List<ReflectorLinkInformation> getLinkInformation(
		final DSTARRepeater repeater, final ConnectionDirectionType connectionDirection
	){
		entriesLocker.lock();
		try {
			return Stream.of(entries)
			.filter(new Predicate<JARLLinkEntry>() {
				@Override
				public boolean test(JARLLinkEntry value) {
					boolean match =
						(
							repeater == null ||
							value.getDestinationRepeater() == repeater
						) &&
						(
							value.getConnectionState() == JARLLinkInternalState.Linking ||
							value.getConnectionState() == JARLLinkInternalState.LinkEstablished
						) &&
						(connectionDirection == null || value.getConnectionDirection() == connectionDirection);
					return match;
				}
			})
			.map(new Function<JARLLinkEntry, ReflectorLinkInformation>(){
				@Override
				public ReflectorLinkInformation apply(JARLLinkEntry t) {
					return new ReflectorLinkInformation(
						t.getId(),
						(
							t.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
							t.getReflectorCallsign() : t.getRepeaterCallsign()
						),
						DSTARProtocol.JARLLink,
						t.getDestinationRepeater(),
						t.getConnectionDirection(),
						false,
						t.getConnectionState() == JARLLinkInternalState.LinkEstablished,
						t.getRemoteAddressPort().getAddress(),
						t.getRemoteAddressPort().getPort(),
						t.getOutgoingReflectorHostInfo()
					);
				}
			}).toList();
		}finally {
			entriesLocker.unlock();
		}
	}

	private void writeHeader(
		final String repeaterCallsign,
		final JARLLinkEntry entry,
		final DSTARPacket packet,
		final ConnectionDirectionType direction
	) {
		if(
			entry.getConnectionState() != JARLLinkInternalState.LinkEstablished ||
			entry.getConnectionDirection() != direction ||
			entry.getCurrentFrameID() != 0x0 ||
			!entry.getRepeaterCallsign().equals(repeaterCallsign)
		) {return;}

		packet.getBackBone().setManagementInformation((byte)0x80);
		packet.getBackBone().setId((byte)0x20);
		packet.getBackBone().setSendRepeaterID((byte)0x01);
		packet.getBackBone().setDestinationRepeaterID((byte)0x01);
		packet.getBackBone().setSendTerminalID((byte)0x00);

		entry.setTransmitHeader(packet.clone());

		entry.setCurrentFrameID(packet.getBackBone().getFrameIDNumber());
		entry.setCurrentFrameSequence((byte)0x0);
		entry.setCurrentFrameDirection(ConnectionDirectionType.OUTGOING);

		entry.getFrameSequenceTimeKepper().setTimeoutTime(1, TimeUnit.SECONDS);
		entry.getFrameSequenceTimeKepper().updateTimestamp();

		if(log.isDebugEnabled())
			log.debug(logHeader + String.format("Start transmit frame 0x%04X.", entry.getCurrentFrameID()));


		switch(entry.getConnectionDirection()) {
		case OUTGOING:
//			sendPacket(packet, entry);

//			entry.getCacheTransmitter().reset();

			entry.getCacheTransmitter().inputWrite(
				new JARLLinkTransmitPacketEntry(
					PacketType.Header,
					packet, entry.getOutgoingChannel(),
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					FrameSequenceType.Start
				)
			);
			break;

		case INCOMING:
//			sendPacket(packet, entry);

//			entry.getCacheTransmitter().reset();

			entry.getCacheTransmitter().inputWrite(
				new JARLLinkTransmitPacketEntry(
					PacketType.Header,
					packet, incommingChannel,
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					FrameSequenceType.Start
				)
			);
			break;

		default:

			break;
		}
	}

	private void writeVoice(
		final String repeaterCallsign,
		final JARLLinkEntry entry,
		final DSTARPacket packet,
		final ConnectionDirectionType direction
	) {
		if(
			entry.getConnectionState() != JARLLinkInternalState.LinkEstablished ||
			entry.getConnectionDirection() != direction ||
			entry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber() ||
			!entry.getRepeaterCallsign().equals(repeaterCallsign)
		) {return;}

		entry.setCurrentFrameSequence(packet.getBackBone().getSequenceNumber());

		entry.getFrameSequenceTimeKepper().updateTimestamp();

		switch(entry.getConnectionDirection()) {
		case OUTGOING:
			entry.getCacheTransmitter().inputWrite(
				new JARLLinkTransmitPacketEntry(
					PacketType.Voice,
					packet, entry.getOutgoingChannel(),
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					FrameSequenceType.None
				)
			);

			if(
				entry.getProtocolVersion() >= 2 &&
				!packet.isLastFrame() &&
				packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber &&
				entry.getTransmitHeader() != null
			) {
				entry.getCacheTransmitter().inputWrite(
					new JARLLinkTransmitPacketEntry(
						PacketType.Voice,
						entry.getTransmitHeader(), entry.getOutgoingChannel(),
						entry.getRemoteAddressPort().getAddress(),
						entry.getRemoteAddressPort().getPort(),
						FrameSequenceType.None
					)
				);
			}
			break;

		case INCOMING:
			entry.getCacheTransmitter().inputWrite(
				new JARLLinkTransmitPacketEntry(
					PacketType.Voice,
					packet,
					incommingChannel,
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					FrameSequenceType.None
				)
			);

			if(
				entry.getProtocolVersion() >= 2 &&
				!packet.isLastFrame() &&
				packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber &&
				entry.getTransmitHeader() != null
			) {
				entry.getCacheTransmitter().inputWrite(
					new JARLLinkTransmitPacketEntry(
						PacketType.Voice,
						entry.getTransmitHeader(), entry.getOutgoingChannel(),
						entry.getRemoteAddressPort().getAddress(),
						entry.getRemoteAddressPort().getPort(),
						FrameSequenceType.None
					)
				);
			}
			break;

		default:

			break;
		}

		if(packet.isEndVoicePacket()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + String.format("End transmit frame 0x%04X.", entry.getCurrentFrameID()));

			entry.setCurrentFrameID(0x0000);
			entry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			entry.setCurrentFrameSequence((byte)0x0);
		}
	}

	private boolean sendPacket(
		final PacketType packetType,
		final DSTARPacket packet,
		final JARLLinkEntry entry
	) {
		byte[] buffer = null;
		int txPackets = 0;

		switch(packetType) {
		case Header:
			char[] zoneCall = entry.getReflectorCallsign().toCharArray();
//			zoneCall[DStarDefines.CallsignFullLength - 1] = 'G';
			packet.getRfHeader().setRepeater1Callsign(zoneCall);

			char[] areaCall = entry.getReflectorCallsign().toCharArray();
			packet.getRfHeader().setRepeater2Callsign(areaCall);

			buffer = new byte[58];

			createSendPacketHeader(
				buffer,
				entry.getTransmitLongSequence(),
				packet.getBackBone().getFrameIDNumber(),
//				FlagGateway,
				FlagZoneRepeater,
				FlagForward
			);
			buffer[16] = (byte)0x80;

			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getFlags(), 17, 20, 0, 3);
			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getRepeater2Callsign(), 20, 28, 0, 8);
			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getRepeater1Callsign(), 28, 36, 0, 8);
			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getYourCallsign(), 36, 44, 0, 8);
			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getMyCallsign(), 44, 52, 0, 8);
			ArrayUtil.copyOfRange(buffer, packet.getRfHeader().getMyCallsignAdd(), 52, 56, 0, 4);
			int crc = DSTARCRCCalculator.calcCRCRange(buffer, 17, 56);
			buffer[56] = (byte)(crc & 0xff);
			buffer[57] = (byte)((crc >> 8) & 0xff);

			incrementBigSequence(entry);

			txPackets = 1;
			break;

		case Voice:
//			if(packet.isEndVoicePacket())
//				buffer = new byte[32];
//			else
			buffer = new byte[29];

			createSendPacketHeader(
				buffer,
				entry.getTransmitLongSequence(),
				packet.getBackBone().getFrameIDNumber(),
//				FlagGateway,
				FlagZoneRepeater,
				FlagForward
			);
			buffer[16] = (byte)(
				packet.getBackBone().getSequenceNumber() |
				(packet.isEndVoicePacket() ? BackBoneHeaderFrameType.VoiceDataLastFrame.getValue() : 0x0)
			);
			if(packet.isEndVoicePacket()) {
				ArrayUtil.copyOfRange(buffer, DSTARDefines.VoiceSegmentLastBytes, 17, 26, 0, 9);
				ArrayUtil.copyOfRange(buffer, DSTARDefines.SlowdataLastBytes, 26, 29, 0, 3);
			}
			else {
				ArrayUtil.copyOfRange(buffer, packet.getVoiceData().getVoiceSegment(), 17, 26, 0, 9);
				ArrayUtil.copyOfRange(buffer, packet.getVoiceData().getDataSegment(), 26, 29, 0, 3);
			}

			incrementBigSequence(entry);

			txPackets = 1;
			break;

		default:
			return false;
		}

		final SocketIOEntryUDP dstChannel =
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				entry.getOutgoingChannel() : incommingChannel;
		if(!dstChannel.getKey().isValid()){return false;}

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader + "JARLLink send packet.\n" +
				packet.toString(4) + "\n" +
				FormatUtil.bytesToHexDump(buffer, 4)
			);
		}


		boolean result = true;
		for(int cnt = 0; cnt < txPackets; cnt++) {
			if(
				!super.writeUDPPacket(
					dstChannel.getKey(),
					entry.getRemoteAddressPort(),
					ByteBuffer.wrap(buffer)
				)
			) {result = false;}
		}

		return result;
	}

	private static boolean createSendPacketHeader(byte[] buffer, int bigSequence, int frameID, byte...dstFlags) {
		assert buffer != null && frameID > 0;

		if(frameID <= 0 || bigSequence < 0) {return false;}
		if(buffer.length < 16) {return false;}

		buffer[0] = 'D';
		buffer[1] = 'S';
		buffer[2] = 'T';
		buffer[3] = 'R';

		buffer[4] = (byte)((bigSequence >> 8) & 0xff);
		buffer[5] = (byte)(bigSequence & 0xff);

		buffer[6] = 's';
		buffer[7] = 0x12;
		for(int c = 0; dstFlags != null && c < dstFlags.length; c++) {
			buffer[7] |= dstFlags[c];
		}
//		buffer[7] |= DStarDefines.JARLMultiForwardFlagZoneRepeater;
		final int length = buffer.length - 10;
		buffer[8] = (byte)((length >> 8) & 0xFF);
		buffer[9] = (byte)(length & 0xFF);
		buffer[10] = 0x20;
		buffer[11] = 0x00;
		buffer[12] = 0x01;
		buffer[13] = 0x02;
		buffer[14] = (byte)((frameID >> 8) & 0xff);
		buffer[15] = (byte)(frameID & 0xff);

		return true;
	}

	private static boolean incrementBigSequence(JARLLinkEntry entry) {
		if(entry == null) {return false;}

		int seq = entry.getTransmitLongSequence();
		seq++;
		seq &= 0xFFFF;

		entry.setTransmitLongSequence(seq);

		return true;
	}

	private Optional<JARLLinkEntry> findEntry(InetAddress remoteAddress, int remotePort, int localPort) {
		assert remoteAddress != null && remotePort > 0;

		entriesLocker.lock();
		try {
			for(JARLLinkEntry entry : entries) {
				if(
					(
						remoteAddress == null ||
						entry.getRemoteAddressPort().getAddress().equals(remoteAddress)
					) &&
					(
						remotePort <= -1 ||
						entry.getRemoteAddressPort().getPort() == remotePort
					) &&
					(
						localPort <= -1 ||
						entry.getLocalAddressPort().getPort() == localPort
					)
				) {
					return Optional.of(entry);
				}
			}
		}finally {
			entriesLocker.unlock();
		}

		return Optional.empty();
	}

	private void processCTBL(
		final JARLLinkEntry entry, final byte[] data, final String myLoginCallsign
	) {
		if(data.length != 44) {return;}

		final String ctbl = new String(data, 4, data.length - 4, StandardCharsets.US_ASCII);

		boolean notifyWebRemote = false;

		if(ctbl.startsWith("START")) {
			entry.getLoginUsersCache().clear();
			entry.getLoginUsersTimekeeper().setTimeoutTime(10, TimeUnit.SECONDS);
			entry.setLoginUsersReceiving(true);

			if(log.isTraceEnabled())
				log.trace(logHeader + "Start CTBL receive.");
		}
		else if(ctbl.startsWith("END")) {
			entry.setLoginUsersReceiving(false);

			final String serverSoftware =
				ctbl.substring(6, ctbl.length()).replaceAll("[^ \\p{Graph}]", "").trim();

			entry.setServerSoftware(serverSoftware);

			for(final Iterator<ReflectorRemoteUserEntry> it = entry.getRemoteUsers().iterator(); it.hasNext();) {
				final ReflectorRemoteUserEntry user = it.next();

				if(!Stream.of(entry.getLoginUsersCache()).anyMatch(new Predicate<ReflectorRemoteUserEntry>() {
					@Override
					public boolean test(ReflectorRemoteUserEntry u) {
						return u.getUserCallsign().equals(user.getUserCallsign());
					}
				})) {
					it.remove();

					if(log.isInfoEnabled())
						log.info(logHeader + "User " + user.getUserCallsign() + " logout from " + entry.getReflectorCallsign() + ".");

					notifyWebRemote = true;
				}
			}
			if(!entry.isLoginUsersReceived() && log.isInfoEnabled()) {
				final StringBuilder sb = new StringBuilder();
				sb.append(logHeader);
				sb.append(' ');
				sb.append("Login Users on ");
				sb.append(entry.getReflectorCallsign());
				sb.append('(');
				sb.append("ServerSoftware : ");
				sb.append(serverSoftware);
				sb.append(')');
				sb.append('\n');
				for(final Iterator<ReflectorRemoteUserEntry> it = entry.getRemoteUsers().iterator();it.hasNext();) {
					final ReflectorRemoteUserEntry user = it.next();
					sb.append("    ");
					sb.append(user.getUserCallsign());
					if(it.hasNext()) {sb.append(',');}
				}

				log.info(sb.toString());

				notifyWebRemote = true;
			}
			entry.getLoginUsersCache().clear();
			entry.setLoginUsersReceived(true);

			updateRemoteUsers(
				entry.getDestinationRepeater(), entry.getReflectorCallsign(), entry.getConnectionDirection(),
				entry.getRemoteUsers()
			);

			if(log.isTraceEnabled())
				log.trace(logHeader + "End CTBL receive.");

		}else if(entry.getLoginUsersTimekeeper().isTimeout()) {
			entry.getLoginUsersCache().clear();
			if(log.isDebugEnabled())
				log.debug(logHeader + "CTBL receive timeout.");
		}
		else {
			for(int i = 0; i < 5; i++) {
				final int ofs = i * 8;
				final String callsign = ctbl.substring(ofs, ofs + 8);
				if(!CallSignValidator.isValidUserCallsign(callsign)) {
					continue;
				}

				final ReflectorRemoteUserEntry user =
					new ReflectorRemoteUserEntry(
						UUID.randomUUID(), System.currentTimeMillis(),
						getGateway().getRepeater(entry.getRepeaterCallsign()),
						entry.getReflectorCallsign(),
						entry.getConnectionDirection(),
						callsign, DSTARDefines.EmptyShortCallsign
					);

				entry.getLoginUsersCache().add(user);

				if(
					!Stream.of(entry.getRemoteUsers()).anyMatch(new Predicate<ReflectorRemoteUserEntry>() {
						@Override
						public boolean test(ReflectorRemoteUserEntry user) {
							return user.getUserCallsign().equals(callsign);
						}
					}) &&
					!myLoginCallsign.equals(callsign)
				) {
					entry.getRemoteUsers().add(user);

					if(entry.isLoginUsersReceived()) {
						if(log.isInfoEnabled())
							log.info(logHeader + "User " + callsign + " login to " + entry.getReflectorCallsign() + ".");

						notifyWebRemote = true;
					}
				}
			}

			entry.getLoginUsersTimekeeper().updateTimestamp();
		}

		if(notifyWebRemote) {notifyStatusChanged();}
	}

	private static void processERROR(
		final JARLLinkEntry entry, final byte[] data, final String myLoginCallsign
	) {
		final String message =
			new String(
				data, 5, data.length - 5, StandardCharsets.UTF_8
			).trim();

		if(
			log.isWarnEnabled() &&
			errorMessagePattern.matcher(message).find()
		) {
			log.warn(
				logHeader +
				"!!! WARNING !!! message received from " + entry.getReflectorCallsign() + ".\n    " + message
			);
		}
		else if(log.isInfoEnabled()){
			log.info(
				logHeader +
				"Information message received from " + entry.getReflectorCallsign() + ".\n    " + message
			);
		}
	}

	private void announceRepeaterLinked(final JARLLinkEntry entry) {

		if(log.isInfoEnabled())
			log.info(logHeader + "Link announce started to " + entry.getRepeaterCallsign());

		final int frameID = DSTARUtils.generateFrameID();

		final Header header = new Header(
			DSTARDefines.CQCQCQ.toCharArray(),
			entry.getRepeaterCallsign().toCharArray(),
			entry.getRepeaterCallsign().toCharArray(),
			entry.getReflectorCallsign().toCharArray(),
			"INFO".toCharArray()
		);

		addReflectorReceivePacket(new ReflectorReceivePacket(
			entry.getRepeaterCallsign(),
			new JARLLinkPacket(
				entry.getLoopBlockID(), entry.getConnectionDirection(), header,
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID)
			)
		));

		final NewDataSegmentEncoder encoder = new NewDataSegmentEncoder();
		encoder.setShortMessage("== REPEATER LIKED ==");
		encoder.setEnableShortMessage(true);
		encoder.setEnableEncode(true);

		for(byte index = 0; index <= 0x14; index++) {
			final VoiceAMBE voice = new VoiceAMBE();
			final BackBoneHeader backbone =
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData, frameID, index);

			if(index <= 0x12) {
				voice.setVoiceSegment(DSTARUtils.getNullAMBE());
				encoder.encode(voice.getDataSegment());

				backbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
			}
			else if(index == 0x13) {
				voice.setVoiceSegment(DSTARUtils.getNullAMBE());
				voice.setDataSegment(DSTARUtils.getEndSlowdata());

				backbone.setFrameType(BackBoneHeaderFrameType.VoiceData);
			}
			else {
				voice.setVoiceSegment(DSTARUtils.getLastAMBE());
				voice.setDataSegment(DSTARUtils.getLastSlowdata());

				backbone.setFrameType(BackBoneHeaderFrameType.VoiceDataLastFrame);
			}

			addReflectorReceivePacket(new ReflectorReceivePacket(
				entry.getRepeaterCallsign(),
				new JARLLinkPacket(entry.getLoopBlockID(), entry.getConnectionDirection(), voice, backbone)
			));
		}
	}
}
