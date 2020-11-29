package org.jp.illg.dstar.reflector.protocol.jarllink;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.dns.DNSRoundrobinUtil;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.define.PacketTracerFunction;
import org.jp.illg.util.socketio.napi.define.ParserFunction;
import org.jp.illg.util.socketio.napi.define.UnknownPacketHandler;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Collectors;
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

	//from dmonitor 1.64
	private static final String currentAccessKey = "5ebe211107266a57b1af14a7fdcd8480";


	@SuppressWarnings("unused")
	private static final byte FlagGateway = (byte)0x80;
	private static final byte FlagZoneRepeater = (byte)0x40;
	private static final byte FlagForward = (byte)0x04;
	@SuppressWarnings("unused")
	private static final byte FlagXChange = (byte)0x08;

	private static enum DMModemType{
		ICOM    ((byte)0x00),
		DVAP    ((byte)0x01),
		DVMEGA  ((byte)0x02),
		NODE    ((byte)0x03),
		;

		@Getter
		private final byte value;

		private DMModemType(final byte value) {
			this.value = value;
		}
	}

	private static final String logTag;

	@SuppressWarnings("unused")
	private static final Pattern keepalivePattern =
		Pattern.compile(
			"^HPCH" +
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

		public JARLLinkCachedHeader(
			final int frameID, final DSTARPacket headerPacket,
			final InetAddress remoteAddress, final int remotePort
		) {
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

	private enum ProcessState{
		Initialize,
		MainProcess,
		Wait,
		;
	}

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean outgoingLink;
	private static final boolean outgoingLinkDefault = true;
	public static final String outgoingLinkPropertyName = "OutgoingLink";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxOutgoingLink;
	private static final int maxOutgoingLinkDefault = 8;
	private static final String maxOutgoingLinkPropertyName = "MaxOutgoingLink";

	@Getter
	@Setter
	private String connectionObserverAddress;
	@Getter
	private static final String connectionObserverAddressPropertyName = "ConnectionObserverAddress";
	public static final String connectionObserverAddressDefault = "hole-punchd.d-star.info";
//	public static final String connectionObserverAddressDefault = "127.0.0.1";

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

	@Getter
	@Setter
	private String accessKey;
	@Getter
	private static final String accessKeyPropertyName = "AccessKey";
	private static final String accessKeyDefault = currentAccessKey.toLowerCase(Locale.ENGLISH);

	@Getter
	@Setter
	private String applicationNameOverride;
	@Getter
	private static final String applicationNameOverridePropertyName = "ApplicationNameOverride";
	private static final String applicationNameOverrideDefault = "";


	private final PacketTracerFunction packetTracer = new PacketTracerFunction() {
		@Override
		public void accept(
			final ByteBuffer buffer,
			final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
		) {
			if(log.isTraceEnabled()) {
				log.trace(
					logTag +
					"Receive packet from " + remoteAddress + '\n' + FormatUtil.byteBufferToHexDump(buffer, 4)
				);
			}
		}
	};

	private final ParserFunction packetParser = new ParserFunction() {
		@Override
		public Boolean apply(
			final ByteBuffer buffer, final Integer packetSize,
			final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
		) {
			return processReceiveBuffer(buffer, packetSize, remoteAddress, localAddress);
		}
	};

	private final UnknownPacketHandler unknownPacketHandler = new UnknownPacketHandler() {
		@Override
		public void accept(
			final ByteBuffer buffer,
			final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag +
					"Unknown packet received from remoteAddress...\n" +
					FormatUtil.byteBufferToHexDump(buffer, 4)
				);
			}
		}
	};

	private ProcessState currentState;
	private ProcessState nextState;
	private ProcessState callbackState;
	private boolean stateChanged;

	private final Timer stateTimeKeeper;

	private final byte[] keepaliveAppInformation;

	private final Queue<JARLLinkPacket> receivePackets;

	private final Queue<UUID> entryRemoveRequestQueue;

	private final Map<Integer, JARLLinkCachedHeader> cachedHeaders;


	private final DNSRoundrobinUtil connectionObserverAddressResolver;

	static {
		logTag = JARLLinkCommunicationService.class.getSimpleName() + " : ";
	}

	public JARLLinkCommunicationService(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketIO,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		super(
			systemID,
			applicationInformation,
			exceptionListener,
			JARLLinkCommunicationService.class,
			socketIO,
			BufferEntry.class, HostIdentType.RemoteLocalAddressPort,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);

		setManualControlThreadTerminateMode(true);
		setBufferSizeUDP(1024 * 1024 * 4);

		stateTimeKeeper = new Timer();

		entryRemoveRequestQueue = new LinkedList<>();

		receivePackets = new LinkedList<>();

		cachedHeaders = new HashMap<>();

		connectionObserverAddressResolver = new DNSRoundrobinUtil();

		keepaliveAppInformation = new byte[10];
		Arrays.fill(keepaliveAppInformation, (byte)0x00);

		currentState = ProcessState.Initialize;
		nextState = ProcessState.Initialize;
		callbackState = ProcessState.Initialize;

		setOutgoingLink(outgoingLinkDefault);
		setMaxOutgoingLink(maxOutgoingLinkDefault);
		setRepeaterHostnameServerAddress(repeaterHostnameServerAddressDefault);
		setRepeaterHostnameServerPort(repeaterHostnameServerPortDefault);
		setConnectionObserverAddress(connectionObserverAddressDefault);
		setConnectionObserverPort(connectionObserverPortDefault);
		setProtocolVersion(protocolVersionDefault);
		setLoginCallsign(loginCallsignDefault);
		setAccessKey(accessKeyDefault);
		setApplicationNameOverride(applicationNameOverrideDefault);
	}

	public JARLLinkCommunicationService(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		this(
			systemID,
			applicationInformation,
			exceptionListener,
			gateway,
			workerExecutor,
			null,
			reflectorLinkManager,
			eventListener
		);
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

		setOutgoingLink(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				outgoingLinkPropertyName,
				outgoingLinkDefault
			)
		);

		setMaxOutgoingLink(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				maxOutgoingLinkPropertyName,
				maxOutgoingLinkDefault
			)
		);

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
					logTag +
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
		if(getRepeaterHostnameServerAddress().equals("hole-punchd.d-star.info")) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
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

		setProtocolVersion(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				protocolVersionPropertyName,
				protocolVersionDefault
			)
		);

		setLoginCallsign(
			DSTARUtils.formatFullCallsign(
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
					logTag + "Property '" + loginCallsignPropertyName + "' = '" + getLoginCallsign() +
					"' is illegal callsign format, replaced by '" + gatewayCallsign + "'."
				);
			}

			setLoginCallsign(gatewayCallsign);
		}

		setApplicationNameOverride(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				applicationNameOverridePropertyName,
				applicationNameOverrideDefault
			)
		);

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
			if(entries.size() >= getMaxOutgoingLink()) {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Reached outgoing link limit, ignore outgoing link request from repeater = " +
						repeater.getRepeaterCallsign() + "."
					);
				}

				return null;
			}
			else if(Stream.of(entries).anyMatch(new Predicate<JARLLinkEntry>() {
				@Override
				public boolean test(JARLLinkEntry entry) {
					return entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
						entry.getRepeaterCallsign().equals(repeater.getRepeaterCallsign());
				}
			})) {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag + "Could not link to duplicate reflector,ignore link request from repeater = " +
						repeater.getRepeaterCallsign() + " to reflector = " + reflectorCallsign + "."
					);
				}

				return null;
			}


			final SocketIOEntryUDP channel =
				getSocketIO().registUDP(
					super.getHandler(),
					this.getClass().getSimpleName()
				);
			if(channel == null) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not register JARLLink outgoing udp channel.");

				return null;
			}

			final Optional<InetAddress> connectionObserver =
				connectionObserverAddressResolver.getCurrentHostAddress();
			if(!connectionObserver.isPresent()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not resolve connection observer address.");

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
			newEntry.setNextConnectionState(JARLLinkInternalState.Initialize);
			newEntry.setCurrentConnectionState(JARLLinkInternalState.Initialize);
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

			entries.add(newEntry);

			if(log.isTraceEnabled())
				log.trace(logTag + "Added outgoing connection entry.\n" + newEntry.toString(4));

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

			switch(unlinkEntry.getCurrentConnectionState()) {
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
			log.trace(logTag + "Connected event.");

		return null;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(log.isTraceEnabled())
			log.trace(logTag + "Disconnected remote host " + remoteAddress.toString());
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress, Exception ex
	) {
		if(log.isTraceEnabled())
			log.trace(logTag + "error event received from " + remoteAddress + ".", ex);
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
		if(currentState != ProcessState.MainProcess)
			return ThreadProcessResult.NoErrors;

		processReceiveBuffer();

		for(final Iterator<JARLLinkPacket> it = receivePackets.iterator(); it.hasNext();) {
			final JARLLinkPacket packet = it.next();
			it.remove();

			if(packet.getDVPacket().hasPacketType(PacketType.Header))
				onReceiveHeader(packet);

			if(packet.getDVPacket().hasPacketType(PacketType.Voice))
				onReceiveVoice(packet);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processConnectionState() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		do {
			stateChanged = currentState != nextState;
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

		}while(
			currentState != nextState &&
			processResult == ThreadProcessResult.NoErrors
		);

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
						log.debug(logTag + "Transmitter cache underflow detected.\n" + entry.toString(4));
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
			if(entries.isEmpty())
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
				.filter(new Predicate<JARLLinkEntry>() {
					@Override
					public boolean test(JARLLinkEntry entry) {
						return entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING;
					}
				})
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

		nextState = ProcessState.MainProcess;

		//ホールパンチキープアライブ用アプリケーション情報セット
		String appInfo;	// ex. NoraGateway -> NG
		if(getApplicationNameOverride() == null || "".equals(getApplicationNameOverride())) {
			String tmp =
				getApplicationName().replaceAll("[^A-Z]", "") + (!getApplicationInformation().isBuildRelease() ? "D" : " ");
			tmp = tmp.substring(0, Math.min(tmp.length(), 3));

			appInfo = tmp + getApplicationVersion().replaceAll("[^0-9ab-]", "");
		}
		else {
			appInfo = getApplicationNameOverride();
		}
		Arrays.fill(keepaliveAppInformation, (byte)' ');
		ArrayUtil.copyOf(
			keepaliveAppInformation,
			String.format("%-10s", appInfo).substring(0, 10).getBytes(StandardCharsets.US_ASCII)
		);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateMainProcess() {

		if(stateChanged) {

		}
		else {
			entriesLocker.lock();
			try {
				for(final JARLLinkEntry entry : entries)
					processConnectionEntry(entry);

				if(!isWorkerThreadAvailable()) {
					Stream.of(entries)
					.filter(new Predicate<JARLLinkEntry>() {
						@Override
						public boolean test(JARLLinkEntry entry) {
							return entry.getCurrentConnectionState() == JARLLinkInternalState.Linking ||
								entry.getCurrentConnectionState() == JARLLinkInternalState.LinkEstablished;
						}
					})
					.forEach(new Consumer<JARLLinkEntry>() {
						@Override
						public void accept(JARLLinkEntry entry) {
							sendDisconnect(entry);
						}
					});

					setWorkerThreadTerminateRequest(true);
				}
			}finally {
				entriesLocker.unlock();
			}

			processEntryRemoveRequestQueue();
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

/*
	private void toWaitState(long waitTime, TimeUnit timeUnit, ProcessState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = ProcessState.Wait;
		this.callbackState = callbackState;
	}
*/

	private void processConnectionEntry(final JARLLinkEntry entry) {
		entriesLocker.lock();
		try {
			do {
				entry.setConnectionStateChanged(
					entry.getCurrentConnectionState() != entry.getNextConnectionState()
				);
				entry.setPreviousConnectionState(entry.getCurrentConnectionState());
				entry.setCurrentConnectionState(entry.getNextConnectionState());

				if(log.isTraceEnabled())
					log.trace(logTag + "State changed " + entry.getCurrentConnectionState() + " <- " + entry.getPreviousConnectionState());

				switch(entry.getCurrentConnectionState()) {
				case Initialize:
					entry.setNextConnectionState(JARLLinkInternalState.Linking);
					break;

				case Linking:
					onConnectionStateLinking(entry);
					break;

				case LinkEstablished:
					onConnectionStateLinkEstablished(entry);
					break;

				case Unlinking:
					onConnectionStateUnlinking(entry);
					break;

				case Unlinked:
					onConnectionStateUnlinked(entry);
					break;

				default:
					addEntryRemoveRequestQueue(entry.getId());
					break;
				}
			}while(entry.getCurrentConnectionState() != entry.getNextConnectionState());
		}finally {
			entriesLocker.unlock();
		}
	}

	private void onConnectionStateLinking(final JARLLinkEntry entry) {
		if(entry.isConnectionStateChanged()) {
			if(entry.getPreviousConnectionState() != JARLLinkInternalState.LinkEstablished) {
				for(int c = 0; c < 2; c++) {
					sendKeepAlive(entry);

					if(c < 1) {
						try {
							Thread.sleep(10L);
						} catch (InterruptedException e) {}
					}
				}
			}
			else {
				sendKeepAlive(entry);
			}

			entry.getConnectionStateTimeKeeper().updateTimestamp();
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();
		}
		else if(entry.getConnectionStateTimeKeeper().isTimeout(8, TimeUnit.SECONDS)) {

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

			notifyStatusChanged();
		}
		else if(entry.isRepeaterKeepAliveReceived()) {
			entry.setNextConnectionState(JARLLinkInternalState.LinkEstablished);

			entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();

			addConnectionStateChangeEvent(
				entry.getId(),
				entry.getConnectionDirection(),
				entry.getRepeaterCallsign(), entry.getReflectorCallsign(),
				ReflectorConnectionStates.LINKED,
				entry.getOutgoingReflectorHostInfo()
			);

			notifyStatusChanged();
		}
		else if(entry.getTransmitKeepAliveTimeKeeper().isTimeout(2, TimeUnit.SECONDS)) {
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

			sendKeepAliveToTargetRepeater(entry);
		}
	}

	private void onConnectionStateLinkEstablished(final JARLLinkEntry entry) {
		if(entry.isConnectionStateChanged()) {
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();
			entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();
		}
		else if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
			sendDisconnect(entry);

			entry.setNextConnectionState(JARLLinkInternalState.Unlinking);
		}
		else if(entry.getReceiveRepeaterKeepAliveTimeKeeper().isTimeout(30, TimeUnit.SECONDS)) {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag +
					"Keep alive timeout, going link state..." +
					entry.getReflectorCallsign() + " <-> " + entry.getRepeaterCallsign()
				);
			}

			entry.setNextConnectionState(JARLLinkInternalState.Linking);

			notifyStatusChanged();
		}
		else if(entry.getTransmitKeepAliveTimeKeeper().isTimeout(5, TimeUnit.SECONDS)) {
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

			sendKeepAliveToTargetRepeater(entry);
		}

		if(
			entry.getCurrentFrameID() != 0x0 &&
			entry.getFrameSequenceTimeKepper().isTimeout()
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag +
					String.format("Timeout frame id...0x%04X.\n%s", entry.getCurrentFrameID(), entry.toString(4))
				);
			}

			entry.setCurrentFrameID(0x0);
			entry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			entry.setCurrentFrameSequence((byte)0x0);
		}
	}

	private void onConnectionStateUnlinking(final JARLLinkEntry entry) {
		if(entry.isConnectionStateChanged()) {
			sendDisconnect(entry);

			entry.setNextConnectionState(JARLLinkInternalState.Unlinked);
		}
	}

	private void onConnectionStateUnlinked(final JARLLinkEntry entry) {
		if(entry.isConnectionStateChanged()) {
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

			notifyStatusChanged();
		}
	}

	private void onReceiveKeepalive(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 24) {return;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "Keep alive received from " + remoteAddress + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				final int savedPos = buffer.position();

				entriesLocker.lock();
				try {
					entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();
				}finally {
					entriesLocker.unlock();
				}

				buffer.position(savedPos + 24);
			}
		});

		wakeupProcessThread();
	}

	private void onReceiveConnectRequest(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 25) {return;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "Connect request received from " + remoteAddress + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				final int savedPos = buffer.position();

				entriesLocker.lock();
				try {
					entry.getReceiveRepeaterKeepAliveTimeKeeper().updateTimestamp();

					if(!entry.isRepeaterKeepAliveReceived()) {
						entry.setRepeaterKeepAliveReceived(true);

						if(log.isInfoEnabled()) {
							log.info(
								logTag +
								"Connected to " + entry.getReflectorCallsign() + " <-> " + entry.getRepeaterCallsign() + "."
							);
						}
					}
				}finally {
					entriesLocker.unlock();
				}

				sendConnectRequestResponse(entry, remoteAddress);

				buffer.position(savedPos + 25);
			}
		});

		wakeupProcessThread();
	}

	private void onReceiveCTBL(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 44) {return;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "CTBL message received from " + remoteAddress + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				final int savedPos = buffer.position();

				entriesLocker.lock();
				try {
					entry.getReceiveKeepAliveTimeKeeper().updateTimestamp();

					if(log.isTraceEnabled()) {
						log.trace(
							logTag + "CTBL received from " + remoteAddress + "\n" +
							FormatUtil.byteBufferToHexDump(buffer, 4)
						);
					}

					processCTBL(entry, buffer, getLoginCallsign());
				}finally {
					entriesLocker.unlock();
				}

				buffer.position(savedPos + 44);
			}
		});
	}

	private void onReceiveERROR(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 64) {return;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "ERROR message received from " + remoteAddress + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				final int savedPos = buffer.position();

				final byte[] data = new byte[64];
				buffer.get(data);
				final String message =
					new String(
						data, 5, data.length - 5, StandardCharsets.UTF_8
					).trim();

				//TODO 内容によっては切断する
				if(
					log.isWarnEnabled() &&
					errorMessagePattern.matcher(message).find()
				) {
					log.warn(
						logTag +
						"!!! WARNING !!! message received from " + entry.getReflectorCallsign() + ".\n    " + message
					);
				}
				else if(log.isInfoEnabled()){
					log.info(
						logTag +
						"Information message received from " + entry.getReflectorCallsign() + ".\n    " + message
					);
				}

				buffer.position(savedPos + 64);
			}
		});
	}

	private void onReceiveMESSG(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 64) {return;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "MESSG message received from " + remoteAddress + "\n" +
				FormatUtil.byteBufferToHexDump(buffer, 4)
			);
		}

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				final int savedPos = buffer.position();

				final byte[] data = new byte[64];
				buffer.get(data);
				final String message =
					new String(
						data, 5, data.length - 5, StandardCharsets.UTF_8
					).trim();

				if(log.isInfoEnabled()){
					log.info(
						logTag +
						"Information message received from " + entry.getReflectorCallsign() + ".\n    " + message
					);
				}

				buffer.position(savedPos + 64);
			}
		});
	}

	private void onReceiveDSTR(
		final ByteBuffer buffer,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(buffer.remaining() < 29) {return;}

		final int savedPos = buffer.position();
		final byte[] data = new byte[buffer.remaining()];

		final JARLLinkTransmitType packetTransmitType =
			JARLLinkTransmitType.getTypeByValue(buffer.get(6));

		final JARLLinkPacketType packetType =
			JARLLinkPacketType.getTypeByValue((byte)buffer.get(7));

		JARLLinkPacket packet = null;

		if(packetType == JARLLinkPacketType.DVPacket && data.length == 58) {	// Header
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

			buffer.position(savedPos + 58);
		}
		else if(packetType == JARLLinkPacketType.DVPacket && data.length == 29) {	// Voice
			final VoiceAMBE voice = new VoiceAMBE();
			ArrayUtil.copyOfRange(voice.getVoiceSegment(), data, 17, 26);
			ArrayUtil.copyOfRange(voice.getDataSegment(), data, 26, 29);

			packet = new JARLLinkPacket(
				voice,
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData)
			);
			ArrayUtil.copyOfRange(packet.getBackBone().getFrameID(), data, 14, 16);
			packet.getBackBone().setManagementInformation(data[16]);

			buffer.position(savedPos + 29);
		}
		else if(packetType == JARLLinkPacketType.DVPacket && data.length == 32) {	// Voice
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

			buffer.position(savedPos + 32);
		}
		else {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag + "Illegal data received, unknown packet size(" + data.length +"bytes)\n" +
					FormatUtil.bytesToHex(data, 4)
				);
			}

			return;
		}

		final JARLLinkPacket receivePacket = packet;

		findEntry(remoteAddress.getAddress(), remoteAddress.getPort(), localAddress.getPort())
		.ifPresent(new Consumer<JARLLinkEntry>() {
			@Override
			public void accept(final JARLLinkEntry entry) {
				receivePacket.setJARLLinkPacketType(packetType);
				receivePacket.setJARLLinkTransmitType(packetTransmitType);

				receivePacket.setLocalAddress(localAddress);
				receivePacket.setRemoteAddress(remoteAddress);

				receivePackets.add(receivePacket);

				if(log.isTraceEnabled()) {
					log.trace(
						logTag +
						"Receive JARLLink packet from repeater = " + entry.getReflectorCallsign() + ".\n" +
						receivePacket.toString(4) + "\n" +
						FormatUtil.bytesToHexDump(data, 4)
					);
				}
			}
		});
	}

	private boolean sendConnectRequestResponse(
		final JARLLinkEntry entry,
		final InetSocketAddress destination
	) {
		final byte[] buffer = new byte[28];
		Arrays.fill(buffer, (byte)0x00);

		ArrayUtil.copyOfRange(
			buffer,
			0,
			entry.getRemoteAddressPort().getAddress().getHostAddress().getBytes(StandardCharsets.US_ASCII)
		);
		ArrayUtil.copyOfRange(buffer, 16, getLoginCallsign().getBytes(StandardCharsets.US_ASCII));
		ArrayUtil.copyOfRange(buffer, 24, "REQ ".getBytes(StandardCharsets.US_ASCII));

		return writeUDPPacket(entry.getOutgoingChannel().getKey(), destination, ByteBuffer.wrap(buffer));
	}

	private boolean processReceiveBuffer() {
		return parseReceivedReadBuffer(packetTracer, packetParser, unknownPacketHandler);
	}

	private boolean processReceiveBuffer(
		final ByteBuffer buffer, final int receiveBytes,
		final InetSocketAddress remoteAddress, final InetSocketAddress localAddress
	) {
		if(
			receiveBytes >= 24 &&
			buffer.get(0) == 'H' && buffer.get(1) == 'P' && buffer.get(2) == 'C' && buffer.get(3) == 'H'
		) {
			if(receiveBytes == 24)	//multi_forwardからのキープアライブの返り
				onReceiveKeepalive(buffer, remoteAddress, localAddress);
			else if(receiveBytes == 25)	//multi_forwardからの接続要求
				onReceiveConnectRequest(buffer, remoteAddress, localAddress);
			else
				return false;
		}
		else if(
			receiveBytes == 44 &&
			buffer.get(0) == 'C' && buffer.get(1) == 'T' && buffer.get(2) == 'B' && buffer.get(3) == 'L'
		) {
			onReceiveCTBL(buffer, remoteAddress, localAddress);
		}
		else if(receiveBytes == 64) {
			if(
				buffer.get(0) == 'E' && buffer.get(1) == 'R' &&
				buffer.get(2) == 'R' && buffer.get(3) == 'O' && buffer.get(4) == 'R'
			) {
				onReceiveERROR(buffer, remoteAddress, localAddress);
			}
			else if(
				buffer.get(0) == 'M' && buffer.get(1) == 'E' &&
				buffer.get(2) == 'S' && buffer.get(3) == 'S' && buffer.get(4) == 'G'
			) {
				onReceiveMESSG(buffer, remoteAddress, localAddress);
			}
		}
		else if(
			receiveBytes == 44 &&
			buffer.get(0) == 'C' && buffer.get(1) == 'T' && buffer.get(2) == 'B' && buffer.get(3) == 'L'
		) {
			onReceiveCTBL(buffer, remoteAddress, localAddress);
		}
		else if(
			receiveBytes >= 29 &&
			buffer.get(0) == 'D' && buffer.get(1) == 'S' && buffer.get(2) == 'T' && buffer.get(3) == 'R'
		) {
			onReceiveDSTR(buffer, remoteAddress, localAddress);
		}
		else {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag + "Illegal data received, unknown packet from" + remoteAddress + "\n" +
					FormatUtil.byteBufferToHex(buffer, 4)
				);
			}

			return false;
		}

		return true;
	}

	private void onReceiveHeader(final JARLLinkPacket packet) {
		entriesLocker.lock();
		try {
			findEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			).ifPresent(new Consumer<JARLLinkEntry>() {
				@Override
				public void accept(JARLLinkEntry entry) {
					onReceiveHeader(entry, packet, false);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void onReceiveHeader(JARLLinkEntry entry, JARLLinkPacket packet, boolean resync) {
		if(
			entry.getCurrentConnectionState() != JARLLinkInternalState.LinkEstablished ||
			packet.getJARLLinkTransmitType() != JARLLinkTransmitType.Send ||
			packet.getJARLLinkPacketType() != JARLLinkPacketType.DVPacket
		) {return;}

		entriesLocker.lock();
		try {
			//フレームIDを変更する
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
					log.debug(logTag + "Received JARLLink header packet.\n" + packet.toString());

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
		}finally {
			entriesLocker.unlock();
		}
	}

	private void onReceiveVoice(final JARLLinkPacket packet) {
		entriesLocker.lock();
		try {
			findEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			).ifPresent(new Consumer<JARLLinkEntry>() {
				@Override
				public void accept(JARLLinkEntry entry) {
					onReceiveVoice(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void onReceiveVoice(JARLLinkEntry entry, JARLLinkPacket packet) {
		if(
			entry.getCurrentConnectionState() != JARLLinkInternalState.LinkEstablished ||
			packet.getJARLLinkTransmitType() != JARLLinkTransmitType.Send ||
			packet.getJARLLinkPacketType() != JARLLinkPacketType.DVPacket
		) {return;}

		entriesLocker.lock();
		try {
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
						log.debug(logTag + "JARLLink resyncing frame by slow data segment...\n" + resyncHeaderPacket.toString(4));

					onReceiveHeader(entry, resyncHeaderPacket, true);
				}
			}
			else if(	//ヘッダキャッシュからの再同期
				entry.getCurrentFrameID() == 0x0
			) {
				//resync
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
						log.debug(logTag + "JARLLink resyncing frame by header cache...\n" + resyncHeaderPacket.toString(4));

					onReceiveHeader(entry, resyncHeaderPacket, true);
				}

			}

			//フレームIDを変更する
			packet.getBackBone().modFrameID(entry.getModCode());

			//IDが異なるパケットは破棄
			if(entry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber()) {return;}

			packet.setConnectionDirection(entry.getConnectionDirection());

			packet.setLoopblockID(entry.getLoopBlockID());

			packet.getDVPacket().setRfHeader(entry.getCurrentHeader().getRfHeader().clone());

			entry.setCurrentFrameSequence(packet.getBackBone().getSequenceNumber());

			entry.getFrameSequenceTimeKepper().updateTimestamp();

			if(log.isTraceEnabled())
				log.trace(logTag + "JARLLink received voice packet.\n" + packet.toString(4));

			final JARLLinkCachedHeader cachedHeader = this.cachedHeaders.get(entry.getCurrentFrameID());
			if(cachedHeader != null) {cachedHeader.updateLastActivatedTimestamp();}

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
		}finally {
			entriesLocker.unlock();
		}
	}

	private boolean addCacheHeader(
		final int frameID,
		final DSTARPacket headerPacket,
		final InetAddress remoteAddress,
		final int remotePort
	) {
		final int overlimitHeaders = this.cachedHeaders.size() - 10;
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
				.collect(Collectors.toList());

			int c = 0;
			do{
				this.cachedHeaders.remove(sortedFrameID.get(c));
			}while(overlimitHeaders > c++);
		}

		final JARLLinkCachedHeader cacheHeaderEntry =
			new JARLLinkCachedHeader(frameID, headerPacket, remoteAddress, remotePort);
		if(this.cachedHeaders.containsKey(frameID)) {this.cachedHeaders.remove(frameID);}
		this.cachedHeaders.put(frameID, cacheHeaderEntry);

		return true;
	}

	private boolean addEntryRemoveRequestQueue(UUID id) {
		entriesLocker.lock();
		try {
			return entryRemoveRequestQueue.add(id);
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processEntryRemoveRequestQueue() {
		entriesLocker.lock();
		try {
			for(final Iterator<UUID> removeIt = entryRemoveRequestQueue.iterator(); removeIt.hasNext();) {
				final UUID removeID = removeIt.next();
				removeIt.remove();

				for(Iterator<JARLLinkEntry> refEntryIt = entries.iterator(); refEntryIt.hasNext();) {
					final JARLLinkEntry refEntry = refEntryIt.next();
					if(refEntry.getId().equals(removeID)) {
						finalizeEntry(refEntry);

						refEntryIt.remove();
						break;
					}
				}
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void finalizeEntry(JARLLinkEntry refEntry){
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
				log.debug(logTag + "Error occurred at channel close.", ex);
		}
	}

	private boolean sendDisconnect(final JARLLinkEntry entry) {
		final byte[] data = new byte[24];
		Arrays.fill(data, (byte)0x00);
		ArrayUtil.copyOfRange(data, 0, "DISCONNECT".getBytes(StandardCharsets.US_ASCII));
//		ArrayUtil.copyOfRange(data, 16, getLoginCallsign().getBytes(StandardCharsets.US_ASCII));

		return writeUDPPacket(
			entry.getOutgoingChannel().getKey(),
			entry.getRemoteAddressPort(),
			ByteBuffer.wrap(data)
		);
	}

	private boolean sendKeepAliveToTargetRepeater(
		final JARLLinkEntry entry
	) {
		return sendKeepAlive(entry, false, true);
	}

	private boolean sendKeepAlive(
		final JARLLinkEntry entry
	) {
		return sendKeepAlive(entry, true, true);
	}

	private boolean sendKeepAlive(
		final JARLLinkEntry entry,
		final boolean isSendObserver, final boolean isSendRepeater
	) {
		boolean isSuccess = true;

		if(isSendRepeater) {
			final byte[] buffer = new byte[24];

			// 4  .. 19 -> Destination IP Address
			// 20 .. 27 -> Connect Callsign
			Arrays.fill(buffer, (byte)0x00);
			ArrayUtil.copyOfRange(buffer, 0, entry.getRemoteAddressPort().getAddress().getHostAddress().getBytes(StandardCharsets.US_ASCII));
			ArrayUtil.copyOfRange(buffer, 16, getLoginCallsign().getBytes(StandardCharsets.US_ASCII));

			//接続先レピータへ送信
			if(
				!writeUDPPacket(
					entry.getOutgoingChannel().getKey(),
					entry.getRemoteAddressPort(),
					ByteBuffer.wrap(buffer)
				)
			) {
				isSuccess = false;
			}

			if(log.isTraceEnabled()) {
				log.trace(
					logTag +
					"Send keepalive to repeater " + entry.getRemoteAddressPort() + ".\n" +
					FormatUtil.bytesToHexDump(buffer, 4) + "\n"
				);
			}
		}

		if(isSendObserver) {
			final byte[] buffer = new byte[88];
			final long currentTime = System.currentTimeMillis() / 1000;

			// 0  .. 3  -> Header(HPCH)
			// 4  .. 19 -> Repeater IP Address
			// 20 .. 29 -> APP&Version
			// 32 .. 63 -> dmonitor MD5
			// 64 .. 71 -> Area Repeater Callsign
			// 72 .. 79 -> Zone Repeater Callsign
			// 80 .. 88 -> Connect Callsign
			Arrays.fill(buffer, (byte)0x00);
			ArrayUtil.copyOfRange(buffer, 0, "HPCH".getBytes(StandardCharsets.US_ASCII));
			ArrayUtil.copyOfRange(buffer, 4, entry.getRemoteAddressPort().getAddress().getHostAddress().getBytes(StandardCharsets.US_ASCII));

			ArrayUtil.copyOfRange(buffer, 20, keepaliveAppInformation);

			buffer[31] = DMModemType.ICOM.getValue();
			ArrayUtil.copyOfRange(buffer, 32, getAccessKey().getBytes(StandardCharsets.US_ASCII));
			for(int a = 0; a < 32; a += 4) {
				int b = 0;
				for(int i = 0; i < 32; i += 8) {
					final int offset = 32 + a + b;

					buffer[offset] = (byte)((buffer[offset] ^ (currentTime >> i)) & 0xFF);

					b++;
				}
			}

			ArrayUtil.copyOfRange(buffer, 64, entry.getReflectorCallsign().getBytes(StandardCharsets.US_ASCII));
			ArrayUtil.copyOfRange(buffer, 72,
				DSTARUtils.formatFullCallsign(entry.getReflectorCallsign(), ' ').getBytes(StandardCharsets.US_ASCII)
			);
			ArrayUtil.copyOfRange(buffer, 80,
				DSTARUtils.formatFullCallsign(getLoginCallsign(), ' ').getBytes(StandardCharsets.US_ASCII)
			);

			if(
				!writeUDPPacket(
					entry.getOutgoingChannel().getKey(),
					entry.getConnectionObserverAddressPort(),
					ByteBuffer.wrap(buffer)
				)
			) {
				isSuccess = false;
			}

			if(log.isTraceEnabled()) {
				log.trace(
					logTag +
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
							value.getCurrentConnectionState() == JARLLinkInternalState.Linking ||
							value.getCurrentConnectionState() == JARLLinkInternalState.LinkEstablished
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
						t.getCurrentConnectionState() == JARLLinkInternalState.LinkEstablished,
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
			entry.getCurrentConnectionState() != JARLLinkInternalState.LinkEstablished ||
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
			log.debug(logTag + String.format("Start transmit frame 0x%04X.", entry.getCurrentFrameID()));


		switch(entry.getConnectionDirection()) {
		case OUTGOING:
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
			entry.getCurrentConnectionState() != JARLLinkInternalState.LinkEstablished ||
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
			break;

		default:

			break;
		}

		if(packet.isEndVoicePacket()) {
			if(log.isDebugEnabled())
				log.debug(logTag + String.format("End transmit frame 0x%04X.", entry.getCurrentFrameID()));

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
				entry.getOutgoingChannel() : null;
		if(!dstChannel.getKey().isValid()){return false;}

		if(log.isTraceEnabled()) {
			log.trace(
				logTag + "JARLLink send packet.\n" +
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
		final JARLLinkEntry entry, final ByteBuffer data,
		final String myLoginCallsign
	) {
		if(data.remaining() < 44) {return;}

		final byte[] buffer = new byte[44];
		data.get(buffer);
		final String ctbl = new String(buffer, 4, buffer.length - 4, StandardCharsets.US_ASCII);

		if(ctbl.startsWith("START")) {
			entry.getLoginUsersCache().clear();
			entry.getLoginUsersTimekeeper().setTimeoutTime(10, TimeUnit.SECONDS);
			entry.setLoginUsersReceiving(true);

			if(log.isTraceEnabled())
				log.trace(logTag + "Start CTBL receive.");
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
						log.info(logTag + "User " + user.getUserCallsign() + " logout from " + entry.getReflectorCallsign() + ".");

					notifyStatusChanged();
				}
			}
			if(!entry.isLoginUsersReceived() && log.isInfoEnabled()) {
				final StringBuilder sb = new StringBuilder();
				sb.append(logTag);
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

				notifyStatusChanged();
			}
			entry.getLoginUsersCache().clear();
			entry.setLoginUsersReceived(true);

			updateRemoteUsers(
				entry.getDestinationRepeater(), entry.getReflectorCallsign(), entry.getConnectionDirection(),
				entry.getRemoteUsers()
			);

			if(log.isTraceEnabled())
				log.trace(logTag + "End CTBL receive.");

		}
		else if(entry.getLoginUsersTimekeeper().isTimeout()) {
			entry.getLoginUsersCache().clear();
			if(log.isDebugEnabled())
				log.debug(logTag + "CTBL receive timeout.");
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
							log.info(logTag + "User " + callsign + " login to " + entry.getReflectorCallsign() + ".");

						notifyStatusChanged();
					}
				}
			}

			entry.getLoginUsersTimekeeper().updateTimestamp();
		}
	}
}
