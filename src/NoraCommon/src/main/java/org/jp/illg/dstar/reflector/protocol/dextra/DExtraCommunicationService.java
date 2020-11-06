package org.jp.illg.dstar.reflector.protocol.dextra;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceEvent;
import org.jp.illg.dstar.reflector.model.ReflectorCommunicationServiceStatus;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.protocol.ReflectorCommunicationServiceBase;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraConnectInfo;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraConnectionInternalStates;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacket;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacketImpl;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPacketImpl.DExtraPacketType;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraPoll;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraReflectorEntry;
import org.jp.illg.dstar.reflector.protocol.dextra.model.DExtraTransmitPacketEntry;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorReceivePacket;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDExtraHandler;
import org.jp.illg.dstar.service.web.model.DExtraConnectionData;
import org.jp.illg.dstar.service.web.model.DExtraStatusData;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
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

import com.annimon.stream.Collectors;
import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


@Slf4j
public class DExtraCommunicationService
extends ReflectorCommunicationServiceBase<BufferEntry, DExtraReflectorEntry>
implements WebRemoteControlDExtraHandler{

	private static final int dextraStandardPort = 30001;
	private static final int connectRetryLimit = 1;
	private static final int connectTimeoutSeconds = 3;
	private static final int maxCachedHeaders = 16;

	private class DExtraCachedHeader{
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

		private DExtraCachedHeader() {
			super();

			setCreatedTimestamp(System.currentTimeMillis());
		}

		public DExtraCachedHeader(int frameID, DSTARPacket headerPacket, InetAddress remoteAddress, int remotePort) {
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

	private static final Pattern supportCallsignPattern =
			Pattern.compile("^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-Z])|(([X][R][F])[0-9]{3}[ ][A-Z]))$");

	private final String logHeader;

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

	private static final boolean dextraFullSupportDefault = false;
	private static final String dextraFullSupportPropertyName = "DExtraFullSupport";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int dextraPort;
	private static final int dextraPortDefault = dextraStandardPort;
	private static final String dextraPortPropertyName = "DExtraPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxReflectors;
	private static final int maxReflectorsDefault = 8;
	private static final String maxReflectorsPropertyName = "MaxReflectors";

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

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean debugInsertPacketSequenceError;
	private static final String debugInsertPacketSequenceErrorPropertyName = "DebugInsertPacketSequenceError";
	private static final boolean debugInsertPacketSequenceErrorDefault = false;

	private SocketIOEntryUDP dextraChannel;

	private final Queue<DExtraPacket> recvPackets;

	private final Queue<UUID> reflectorEntryRemoveRequestQueue;

	private final Map<Integer, DExtraCachedHeader> cachedHeaders;

	public DExtraCommunicationService(
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
			DExtraCommunicationService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteLocalAddressPort,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);

		logHeader = DExtraCommunicationService.class.getSimpleName() + " : ";

		recvPackets = new LinkedList<>();

		reflectorEntryRemoveRequestQueue = new LinkedList<>();

		cachedHeaders = new HashMap<>();

		maxReflectors = maxReflectorsDefault;
		maxIncomingLink = maxIncomingLinkDefault;
		maxOutgoingLink = maxOutgoingLinkDefault;
		outgoingLink = outgoingLinkDefault;
		incomingLink = incomingLinkDefault;
		dextraPort = dextraPortDefault;
		debugInsertPacketSequenceError = debugInsertPacketSequenceErrorDefault;
	}

	public DExtraCommunicationService(
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
	public boolean isSupportedReflectorCallsign(String reflectorCallsign) {
		if(reflectorCallsign == null) {return false;}

		return supportCallsignPattern.matcher(reflectorCallsign).matches();
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
				dextraFullSupportPropertyName,
				dextraFullSupportDefault
			)
		);

		setDextraPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				dextraPortPropertyName,
				dextraPortDefault
			)
		);

		setMaxReflectors(
			PropertyUtils.getInteger(
					properties.getConfigurationProperties(),
					maxReflectorsPropertyName, maxReflectorsDefault
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

		setDebugInsertPacketSequenceError(
			PropertyUtils.getBoolean(
					properties.getConfigurationProperties(),
					debugInsertPacketSequenceErrorPropertyName, debugInsertPacketSequenceErrorDefault
			)
		);


		return true;
	}

	@Override
	public ReflectorProperties getProperties(ReflectorProperties properties) {

		//DExtraFullSupport
		if(properties.getConfigurationProperties().containsKey(dextraFullSupportPropertyName))
			properties.getConfigurationProperties().remove(dextraFullSupportPropertyName);

		properties.getConfigurationProperties().put(
			dextraFullSupportPropertyName, String.valueOf(isIncomingLink())
		);

		//DExtraPort
		if(properties.getConfigurationProperties().containsKey(dextraPortPropertyName))
			properties.getConfigurationProperties().remove(dextraPortPropertyName);

		properties.getConfigurationProperties().put(dextraPortPropertyName, String.valueOf(dextraPort));

		//maxReflectors
		if(properties.getConfigurationProperties().containsKey(maxReflectorsPropertyName))
			properties.getConfigurationProperties().remove(maxReflectorsPropertyName);

		properties.getConfigurationProperties().put(maxReflectorsPropertyName, String.valueOf(maxReflectors));

		//maxOutgoingLink
		if(properties.getConfigurationProperties().containsKey(maxOutgoingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxOutgoingLinkPropertyName);

		properties.getConfigurationProperties().put(maxOutgoingLinkPropertyName, String.valueOf(getMaxOutgoingLink()));

		//maxIncommingLink
		if(properties.getConfigurationProperties().containsKey(maxIncomingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxIncomingLinkPropertyName);

		properties.getConfigurationProperties().put(maxIncomingLinkPropertyName, String.valueOf(getMaxIncomingLink()));

		//DebugInsertPacketSequenceError
		if(properties.getConfigurationProperties().containsKey(debugInsertPacketSequenceErrorPropertyName))
			properties.getConfigurationProperties().remove(debugInsertPacketSequenceErrorPropertyName);

		properties.getConfigurationProperties().put(
				debugInsertPacketSequenceErrorPropertyName,
				String.valueOf(isDebugInsertPacketSequenceError())
		);

		return properties;
	}

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
							dextraChannel =
								getSocketIO().registUDP(
									new InetSocketAddress(getDextraPort()),
									DExtraCommunicationService.this.getHandler(),
									DExtraCommunicationService.this.getClass().getSimpleName() + "@" + getDextraPort()
								);
						}
					}
				}
			) ||
			(isIncomingLink() && dextraChannel == null)
		) {
			this.stop();

			closeDExtraChannel();

			return false;
		}

		if(isDebugInsertPacketSequenceError()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + debugInsertPacketSequenceErrorPropertyName + " is enabled.");
		}

		return true;
	}

	public void stop() {
		super.stop();

		closeDExtraChannel();

		finalizeReflectorEntries();
	}

	@Override
	public ReflectorCommunicationServiceStatus getStatus() {
		if(isRunning())
			return ReflectorCommunicationServiceStatus.InService;
		else
			return ReflectorCommunicationServiceStatus.OutOfService;
	}

	@Override
	public boolean isSupportTransparentMode() {
		return false;
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		super.threadFinalize();


		closeDExtraChannel();

		finalizeReflectorEntries();

		recvPackets.clear();

		reflectorEntryRemoveRequestQueue.clear();

		cachedHeaders.clear();
	}

	@Override
	protected ThreadProcessResult processReceivePacket() {
		entriesLocker.lock();
		try {
			parsePacket(recvPackets);

			for(final Iterator<DExtraPacket> it = recvPackets.iterator(); it.hasNext();) {
				final DExtraPacket packet = it.next();
				it.remove();

				switch(packet.getDExtraPacketType()) {
				case HEADER:
					processHeader(packet);
					break;

				case VOICE:
					processVoice(packet);
					break;

				case CONNECT:
					processConnect(packet);
					break;

				case POLL:
					processPoll(packet);
					break;

				default:
					break;
				}

				processReflectorentryRemoveRequest();
			}
		}finally {
			entriesLocker.unlock();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processConnectionState() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		entriesLocker.lock();
		try {
			for(
				final Iterator<DExtraReflectorEntry> it = entries.iterator(); it.hasNext();
			) {
				final DExtraReflectorEntry entry = it.next();

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
					case Linking:
						onStateLinking(entry);
						break;

					case LinkEstablished:
						onStateLinkEstablished(entry);
						break;

					case Unlinking:
						onStateUnlinking(entry);
						break;

					case Wait:
						onStateWait(entry);
						break;

					default:
						break;
					}

					if(
						entry.getCurrentState() != entry.getNextState() &&
						processResult == ThreadProcessResult.NoErrors
					) {reProcess = true;}

				}while(reProcess);

				if(entry.isTimeoutActivity()) {
					if(log.isDebugEnabled())
						log.debug(logHeader + "DExtra reflector link entry timeout occurred.\n" + entry.toString(4));

					removeReflectorEntry(entry.getId());
				}
			}

			processReflectorentryRemoveRequest();

		}finally {
			entriesLocker.unlock();
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processVoiceTransfer() {
		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry entry : entries) {
				processTransmitterPacket(entry);
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

			for(final DExtraReflectorEntry entry : entries) {
				if(
					entry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING &&
					entry.getCurrentFrameID() != 0x0
				) {
					return ProcessIntervalMode.VoiceTransfer;
				}
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
				.filter(e -> e.getConnectionDirection() == ConnectionDirectionType.INCOMING)
				.count()
			);
			report.setConnectedOutgoingLink(
				(int)Stream.of(entries)
				.filter(e -> e.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				.count()
			);
		}finally {
			entriesLocker.unlock();
		}
		report.setIncomingLinkPort(getDextraPort());
		report.setIncomingStatus("");
		report.setOutgoingStatus("");

		return report;
	}

	@Override
	public DSTARProtocol getProtocolType() {
		return DSTARProtocol.DExtra;
	}

	@Override
	public ReflectorProtocolProcessorTypes getProcessorType() {
		return ReflectorProtocolProcessorTypes.DExtra;
	}

	@Override
	public boolean writePacketInternal(
		final DSTARRepeater repeater, final DSTARPacket packet, final ConnectionDirectionType direction
	) {
		if(
			repeater == null ||
			packet == null || packet.getPacketType() != DSTARPacketType.DV
		) {return false;}

		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry refEntry : entries) {
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
	public boolean hasWriteSpace() {
		return isRunning();
	}

	@Override
	public UUID linkReflector(
		final String reflectorCallsign, final ReflectorHostInfo reflectorHostInfo,
		final DSTARRepeater repeater
	) {
		if(
			!isRunning() ||
			reflectorHostInfo == null ||
			!CallSignValidator.isValidReflectorCallsign(reflectorCallsign) ||
			repeater == null ||
			!CallSignValidator.isValidRepeaterCallsign(repeater.getRepeaterCallsign())
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
		final DStarRepeater destinationRepeater = getGateway().getRepeater(repeaterCallsign);
		if(destinationRepeater == null) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Dextra link request failed. Illegal destination repeater callsign.[" + repeaterCallsign + "]"
				);
			}

			return null;
		}
*/
		/*
		InetAddress reflectorAddress = null;
		try {
			reflectorAddress = InetAddress.getByName(reflectorHostInfo.getReflectorAddress());
		}catch(UnknownHostException ex) {
			if(log.isWarnEnabled()) {
				log.warn(
					logHeader +
					"Could not connect to " + reflectorCallsign +
					", unknown reflector address = " + reflectorHostInfo.getReflectorAddress() + "."
				);
			}

			return null;
		}
		*/

		UUID entryID = null;

		entriesLocker.lock();
		try {

			if(countLinkEntry(ConnectionDirectionType.OUTGOING) >= Math.max(getMaxReflectors(), getMaxOutgoingLink())) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Could not link to reflector. because over connection limit.");

				return null;
			}

			boolean found = false;
			for(final DExtraReflectorEntry entry : entries) {
				if(
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
					(entry.getRepeaterCallsign() != null && entry.getRepeaterCallsign().equals(repeater.getRepeaterCallsign()))
				) {
					found = true;
					break;
				}
			}

			if(found) {return null;}	// already reflector is linking to the repeater.

			if(log.isTraceEnabled())
				log.trace(logHeader + "Start DExtra outgoing channel register.");

			final SocketIOEntryUDP outgoingChannel = super.getSocketIO().registUDP(
				super.getHandler(),
				this.getClass().getSimpleName() + "@->" +
				reflectorHostInfo.getReflectorAddress() + ":" + reflectorHostInfo.getReflectorPort()
			);
			if(outgoingChannel == null){
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not register DExtra outgoing udp channel.");

				return null;
			}

			if(log.isTraceEnabled())
				log.trace(logHeader + "End of DExtra outgoing channel register.");

			final DExtraReflectorEntry newReflector = new DExtraReflectorEntry(
				generateLoopBlockID(),
				10,
				new InetSocketAddress(
					reflectorHostInfo.getReflectorAddress(), reflectorHostInfo.getReflectorPort()
				),
				outgoingChannel.getLocalAddress(),
				ConnectionDirectionType.OUTGOING
			);
			newReflector.setOutgoingReflectorHostInfo(reflectorHostInfo);
			newReflector.setModCode(super.getModCode());
			newReflector.setCurrentState(DExtraConnectionInternalStates.Initialize);
			newReflector.setNextState(DExtraConnectionInternalStates.Linking);
			newReflector.setOutgoingChannel(outgoingChannel);
			newReflector.setReflectorCallsign(reflectorCallsign);
			newReflector.setReflectorModule(reflectorCallsign.charAt(DSTARDefines.CallsignFullLength - 1));
			newReflector.setRepeaterCallsign(repeater.getRepeaterCallsign());
			newReflector.setRepeaterModule(repeater.getRepeaterCallsign().charAt(DSTARDefines.CallsignFullLength - 1));
			newReflector.setDestinationRepeater(repeater);
			newReflector.setLinkRequest(true);
			newReflector.setDongle(false);

//			newReflector.getCacheTransmitter().setDebugSwapPacket(isDebugInsertPacketSequenceError());
//			newReflector.getCacheTransmitter().setCacheSize(5);

			newReflector.updateStateTimestamp();

			sendConnectPacket(
				ReflectorConnectTypes.LINK,
				newReflector.getRepeaterCallsign(),
				newReflector.getRepeaterModule(),
				newReflector.getReflectorModule(),
				newReflector.getOutgoingChannel(),
				newReflector.getRemoteAddressPort().getAddress(),
				newReflector.getRemoteAddressPort().getPort()
			);

			entries.add(newReflector);

			entryID = newReflector.getId();
		}finally {
			entriesLocker.unlock();
		}

		wakeupProcessThread();

		return entryID;
	}

	@Override
	public UUID unlinkReflector(final DSTARRepeater repeater) {
		if(
			!isRunning() ||
			repeater == null ||
			!CallSignValidator.isValidRepeaterCallsign(repeater.getRepeaterCallsign())
		) {return null;}

		entriesLocker.lock();
		try {
			DExtraReflectorEntry unlinkEntry = null;
			for(final DExtraReflectorEntry entry : entries) {
				if(
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
					(entry.getRepeaterCallsign() != null && entry.getRepeaterCallsign().equals(repeater.getRepeaterCallsign()))
				) {
					unlinkEntry = entry;
					break;
				}
			}

			if(unlinkEntry == null) {return null;}

			switch(unlinkEntry.getCurrentState()) {
			case LinkEstablished:
			case Linking:
				unlinkEntry.setUnlinkRequest(true);
				break;

			case Unlinking:
			case Unlinked:
				break;

			default:
				removeReflectorEntry(unlinkEntry.getId());
				break;
			}

			return unlinkEntry.getId();
		}finally {
			entriesLocker.unlock();
		}
	}

	@Override
	public OperationRequest readEvent(
		final SelectionKey key, final ChannelProtocol protocol,
		final InetSocketAddress localAddress, final InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest acceptedEvent(
		final SelectionKey key, final ChannelProtocol protocol,
		final InetSocketAddress localAddress, final InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public OperationRequest connectedEvent(
		final SelectionKey key, final ChannelProtocol protocol,
		final InetSocketAddress localAddress, final InetSocketAddress remoteAddress
	) {
		return null;
	}

	@Override
	public void disconnectedEvent(
		final SelectionKey key, final ChannelProtocol protocol,
		final InetSocketAddress localAddress, final InetSocketAddress remoteAddress
	) {
	}

	@Override
	public void errorEvent(
		final SelectionKey key, final ChannelProtocol protocol,
		final InetSocketAddress localAddress, final InetSocketAddress remoteAddress, final Exception ex
	) {
		StringBuffer sb = new StringBuffer(this.getClass().getSimpleName() + " socket error.");
		if(localAddress != null) {sb.append("Local=" + localAddress.toString());}
		if(remoteAddress != null) {sb.append("/Remote=" + remoteAddress.toString());}

		if(log.isDebugEnabled())
			log.debug(logHeader + sb.toString(), ex);
	}

	@Override
	public boolean isEnableIncomingLink() {
		return isIncomingLink();
	}

	@Override
	public int getIncomingLinkPort() {
		return getDextraPort();
	}

	@Override
	protected List<ReflectorLinkInformation> getLinkInformation(
		final DSTARRepeater repeater, final ConnectionDirectionType connectionDirection
	){
		entriesLocker.lock();
		try {
			return Stream.of(entries)
			.filter(new Predicate<DExtraReflectorEntry>() {
				@Override
				public boolean test(DExtraReflectorEntry value) {
					return
						(
							repeater == null ||
							value.getDestinationRepeater() == repeater
						) &&
						(
							value.getCurrentState() == DExtraConnectionInternalStates.Linking ||
							value.getCurrentState() == DExtraConnectionInternalStates.LinkEstablished
						) &&
						(connectionDirection == null || value.getConnectionDirection() == connectionDirection);
				}
			})
			.map(new Function<DExtraReflectorEntry, ReflectorLinkInformation>(){
				@Override
				public ReflectorLinkInformation apply(DExtraReflectorEntry t) {
					return new ReflectorLinkInformation(
						t.getId(),
						(
							t.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
							t.getReflectorCallsign() : t.getRepeaterCallsign()
						),
						DSTARProtocol.DExtra,
						t.getDestinationRepeater(),
						t.getConnectionDirection(),
						t.isDongle(),
						t.getCurrentState() == DExtraConnectionInternalStates.LinkEstablished,
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

	@Override
	protected boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeReflectorDExtra(this);
	}

	@Override
	protected boolean getReflectorConnectionsInternal(
		@NonNull List<ReflectorConnectionData> connections
	) {
		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry entry : entries) {
				final DExtraConnectionData con = new DExtraConnectionData();

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
		final DExtraStatusData status = new DExtraStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ReflectorStatusData> getStatusDataTypeInternal() {
		return DExtraStatusData.class;
	}

	private void onStateLinking(final DExtraReflectorEntry refEntry) {
		if(refEntry.isTimeoutState(TimeUnit.SECONDS.toMillis(connectTimeoutSeconds))) {
			if(refEntry.isLinkRequest() || refEntry.getRetryCount() > 0) {
				if(refEntry.getRetryCount() < connectRetryLimit) {
					sendConnectPacket(
						ReflectorConnectTypes.LINK,
						refEntry.getRepeaterCallsign(), refEntry.getRepeaterModule(),
						refEntry.getReflectorModule(),
						refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
								refEntry.getOutgoingChannel() : this.dextraChannel,
						refEntry.getRemoteAddressPort().getAddress(),
						refEntry.getRemoteAddressPort().getPort()
					);
					refEntry.setNextState(DExtraConnectionInternalStates.Linking);
					refEntry.updateStateTimestamp();
					refEntry.setRetryCount(refEntry.getRetryCount() + 1);
				}
				else {
					if(log.isDebugEnabled())
						log.debug(logHeader + "DExtra outgoing connection failed.\n" + refEntry.toString(4));

					addConnectionStateChangeEvent(
						refEntry.getId(),
						refEntry.getConnectionDirection(),
						refEntry.getRepeaterCallsign(), refEntry.getReflectorCallsign(),
						ReflectorConnectionStates.LINKFAILED,
						refEntry.getOutgoingReflectorHostInfo()
					);

					removeReflectorEntry(refEntry.getId());

					notifyStatusChanged();
				}
			}
		}
		else if(refEntry.isUnlinkRequest()) {
			refEntry.updateStateTimestamp();
			refEntry.setNextState(DExtraConnectionInternalStates.Unlinking);
		}
	}

	private void onStateLinkEstablished(final DExtraReflectorEntry refEntry) {
		if(refEntry.isUnlinkRequest()) {
			if(refEntry.getCurrentFrameID() != 0x0000) {
				addReflectorPacket(
					refEntry,
					createPreLastVoicePacket(
						refEntry,
						refEntry.getCurrentFrameID(),
						refEntry.getCurrentFrameSequence()
					)
				);
				refEntry.setCurrentFrameID(0x00);
				refEntry.setCurrentFrameSequence((byte)0x00);
				refEntry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			}

			sendConnectPacket(
				ReflectorConnectTypes.UNLINK,
				refEntry.getRepeaterCallsign(), refEntry.getRepeaterModule(),
				refEntry.getReflectorModule(),
				refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
						refEntry.getOutgoingChannel() : this.dextraChannel,
				refEntry.getRemoteAddressPort().getAddress(),
				refEntry.getRemoteAddressPort().getPort()
			);
			refEntry.setNextState(DExtraConnectionInternalStates.Unlinking);

			refEntry.updateStateTimestamp();
		}
		else if(refEntry.isTimedoutKeepAlive()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "DExtra link keepalive timeout.\n" + refEntry.toString(4));

			if(refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
				refEntry.setLinkRequest(true);
				refEntry.setRetryCount(0);
				refEntry.setNextState(DExtraConnectionInternalStates.Linking);
				refEntry.updateStateTimestamp();
				refEntry.setLinkFailed(true);

				sendConnectPacket(
					ReflectorConnectTypes.LINK,
					refEntry.getRepeaterCallsign(), refEntry.getRepeaterModule(),
					refEntry.getReflectorModule(),
					refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
							refEntry.getOutgoingChannel() : this.dextraChannel,
					refEntry.getRemoteAddressPort().getAddress(),
					refEntry.getRemoteAddressPort().getPort()
				);
			}
			else if(refEntry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				sendConnectPacket(
					ReflectorConnectTypes.UNLINK,
					refEntry.getRepeaterCallsign(), refEntry.getRepeaterModule(),
					refEntry.getReflectorModule(),
					refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
							refEntry.getOutgoingChannel() : this.dextraChannel,
					refEntry.getRemoteAddressPort().getAddress(),
					refEntry.getRemoteAddressPort().getPort()
				);

				removeReflectorEntry(refEntry.getId());

				onIncomingConnectionDisconnected(
					refEntry.getRemoteAddressPort(),
					refEntry.getReflectorCallsign(), refEntry.getRepeaterCallsign()
				);
			}
		}
		else if(refEntry.isTimeoutedPoll()) {
			sendPollPacket(
				refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					refEntry.getRepeaterCallsign() : refEntry.getReflectorCallsign(),
				refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
						refEntry.getOutgoingChannel() : this.dextraChannel,
				refEntry.getRemoteAddressPort().getAddress(),
				refEntry.getRemoteAddressPort().getPort()
			);

			refEntry.updatePollTimestamp();
		}

		if(
			refEntry.getCurrentFrameID() != 0x0000 &&
			refEntry.isTimeoutedFrameSequence(TimeUnit.SECONDS.toMillis(1))
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logHeader + "Frame " +
					(refEntry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING ? "transmit":"receive") +
					" sequence timeout.\n" + refEntry.toString(4)
				);
			}

			if(refEntry.getCurrentFrameDirection() == ConnectionDirectionType.OUTGOING) {
				addReflectorPacket(
					refEntry,
					createPreLastVoicePacket(
						refEntry, refEntry.getCurrentFrameID(), refEntry.getCurrentFrameSequence()
					)
				);
			}

			refEntry.setCurrentFrameID(0x0);
			refEntry.setCurrentFrameSequence((byte)0x0);
			refEntry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			refEntry.setCurrentHeader(null);
		}
	}

	private void onStateUnlinking(final DExtraReflectorEntry refEntry) {
		final boolean timeout = refEntry.isTimeoutState(TimeUnit.SECONDS.toMillis(1));
		if(refEntry.getProtocolRevision() == 0 || timeout) {
			if(!timeout) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"DExtra reflector unlinked." +
						"[Reflector:" + refEntry.getReflectorCallsign() + "/Repeater:" + refEntry.getRepeaterCallsign() + "]"
					);
				}
			}else {
				if(log.isDebugEnabled())
					log.debug(logHeader + "DExtra unlink timeout.\n" + refEntry.toString(4));
			}

			addConnectionStateChangeEvent(
					refEntry.getId(),
					refEntry.getConnectionDirection(),
					refEntry.getRepeaterCallsign(), refEntry.getReflectorCallsign(),
					ReflectorConnectionStates.UNLINKED,
					refEntry.getOutgoingReflectorHostInfo()
			);

			removeReflectorEntry(refEntry.getId());

			notifyStatusChanged();
		}
	}

	private ThreadProcessResult onStateWait(DExtraReflectorEntry entry) {
		assert entry != null;

		if(entry.getStateTimeKeeper().isTimeout())
			entry.setNextState(entry.getCallbackState());

		return ThreadProcessResult.NoErrors;
	}

	@SuppressWarnings("unused")
	private void toWaitState(
		DExtraReflectorEntry entry, int time, TimeUnit timeUnit, DExtraConnectionInternalStates callbackState
	) {
		assert entry != null && timeUnit != null && callbackState != null;

		if(time < 0) {time = 0;}

		if(time > 0) {
			entry.setNextState(DExtraConnectionInternalStates.Wait);
			entry.setCallbackState(callbackState);
			entry.getStateTimeKeeper().setTimeoutTime(time, timeUnit);
		}
		else {
			entry.setNextState(callbackState);
		}
	}

	private void processHeader(final DExtraPacket packet) {
		assert packet != null && packet.getDExtraPacketType() == DExtraPacketType.HEADER;
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.HEADER) {return;}

		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry refEntry : entries) {
				if(
					refEntry.getRemoteAddressPort().equals(packet.getRemoteAddress()) &&
					refEntry.getLocalAddressPort().getPort() == packet.getLocalAddress().getPort()
				) {
					processHeader(refEntry, packet, false);

					refEntry.updateActivityTimestamp();
				}
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processHeader(
		final DExtraReflectorEntry refEntry, final DExtraPacket packet, final boolean resync
	) {
		if(
			packet == null || packet.getDExtraPacketType() != DExtraPacketType.HEADER ||
			refEntry == null || refEntry.getCurrentState() != DExtraConnectionInternalStates.LinkEstablished ||
			refEntry.getCurrentFrameID() != 0x0
		) {
			return;
		}

		packet.setConnectionDirection(refEntry.getConnectionDirection());
		packet.setLoopblockID(refEntry.getLoopBlockID());
		packet.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataHeader);
		packet.getRFHeader().setYourCallsign(DSTARDefines.CQCQCQ);

		//フレームIDを変更する
		packet.getBackBone().modFrameID(refEntry.getModCode());

		//経路情報を保存
		packet.getRfHeader().setSourceRepeater2Callsign(
			refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				refEntry.getReflectorCallsign() : refEntry.getRepeaterCallsign()
		);

		refEntry.setCurrentFrameID(packet.getDvPacket().getBackBone().getFrameIDNumber());
		refEntry.setCurrentFrameSequence((byte)0x0);
		refEntry.setCurrentFrameDirection(ConnectionDirectionType.INCOMING);
		refEntry.setCurrentHeader(packet.clone());

		addCacheHeader(
			refEntry.getCurrentFrameID(),
			packet.clone(),
			packet.getRemoteAddress().getAddress(),
			packet.getRemoteAddress().getPort()
		);

		if(log.isDebugEnabled())
			log.debug(logHeader + "DExtra received header packet.\n" + packet.toString(4));

		final boolean validReflectorCall = resync ? true:
			(
				refEntry.getReflectorCallsign().equals(String.valueOf(packet.getDvPacket().getRfHeader().getRepeater2Callsign())) ||
				refEntry.getReflectorCallsign().equals(String.valueOf(packet.getDvPacket().getRfHeader().getRepeater1Callsign()))
			);

		switch(refEntry.getConnectionDirection()) {
		case OUTGOING:
			if(!validReflectorCall) {return;}

			addReflectorPacket(refEntry, packet);

			break;

		case INCOMING:
			if(!DSTARDefines.EmptyLongCallsign.equals(refEntry.getReflectorCallsign())) {
				if(!validReflectorCall) {return;}

				addReflectorPacket(refEntry, packet);

			}
			else {
				DSTARRepeater repeater = null;
				if(
					(repeater = getGateway().getRepeater(String.valueOf(packet.getDvPacket().getRfHeader().getRepeater2Callsign()))) == null &&
					(repeater = getGateway().getRepeater(String.valueOf(packet.getDvPacket().getRfHeader().getRepeater1Callsign()))) == null
				) {return;}

				addReflectorPacket(repeater.getRepeaterCallsign(), packet);
			}

			break;

		default:
			break;
		}

		refEntry.updateFrameSequenceTimestamp();
	}

	private void processVoice(final DExtraPacket packet) {
		assert packet != null && packet.getDExtraPacketType() == DExtraPacketType.VOICE;
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.VOICE) {return;}

		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry refEntry : entries) {
				if(
					refEntry.getRemoteAddressPort().equals(packet.getRemoteAddress()) &&
					refEntry.getLocalAddressPort().getPort() == packet.getLocalAddress().getPort()
				) {
					processVoice(refEntry, packet);

					refEntry.updateActivityTimestamp();
				}
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processVoice(final DExtraReflectorEntry refEntry, final DExtraPacket packet) {
		if(
			packet == null || packet.getDExtraPacketType() != DExtraPacketType.VOICE ||
			refEntry.getCurrentState() != DExtraConnectionInternalStates.LinkEstablished
		) {return;}

		//再同期処理
		if(	//ミニデータからの再同期
			refEntry.getCurrentFrameID() == 0x0 &&
			refEntry.getSlowdataDecoder().decode(packet.getVoiceData().getDataSegment()) ==
				DataSegmentDecoderResult.Header
		) {
			final Header slowdataHeader = refEntry.getSlowdataDecoder().getHeader();
			if(slowdataHeader != null) {
				final DExtraPacket resyncHeaderPacket = new DExtraPacketImpl(
					slowdataHeader,
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader)
				);
				resyncHeaderPacket.setRemoteAddress(refEntry.getRemoteAddressPort());

				resyncHeaderPacket.getDvPacket().getBackBone().setFrameIDNumber(packet.getBackBone().getFrameIDNumber());

				if(log.isDebugEnabled())
					log.debug(logHeader + "DExtra resyncing frame by slow data segment...\n" + resyncHeaderPacket.toString(4));

				processHeader(refEntry, resyncHeaderPacket, true);
			}
		}
		else if(	//ヘッダキャッシュからの再同期
			refEntry.getCurrentFrameID() == 0x0
		) {
			final DExtraCachedHeader cachedHeader =
					this.cachedHeaders.get(packet.getDvPacket().getBackBone().getFrameIDNumber() ^ refEntry.getModCode());
			if(cachedHeader == null) {return;}

			if((System.currentTimeMillis() - cachedHeader.getLastActivatedTimestamp()) < TimeUnit.SECONDS.toMillis(15)) {
				final DExtraPacket resyncHeaderPacket = new DExtraPacketImpl(
					cachedHeader.getHeader().getRFHeader(),
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataLastFrame)
				);
				resyncHeaderPacket.setRemoteAddress(new InetSocketAddress(cachedHeader.getRemoteAddress(), cachedHeader.getRemotePort()));

				resyncHeaderPacket.getBackBone().setFrameIDNumber(packet.getBackBone().getFrameIDNumber());

				if(log.isDebugEnabled())
					log.debug(logHeader + "DExtra resyncing frame by header cache...\n" + resyncHeaderPacket.toString(4));

				processHeader(refEntry, resyncHeaderPacket, true);
			}
		}

		if(refEntry.getCurrentFrameID() == 0x0) {return;}

		//フレームIDを変更する
		packet.getBackBone().modFrameID(refEntry.getModCode());

		//IDが異なるパケットは破棄
		if(refEntry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber()) {return;}

		packet.setConnectionDirection(refEntry.getConnectionDirection());
		packet.setLoopblockID(refEntry.getLoopBlockID());
		packet.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceData);

		refEntry.setCurrentFrameSequence(packet.getDvPacket().getBackBone().getSequenceNumber());

		if(log.isTraceEnabled())
			log.trace(logHeader + "DExtra received voice packet.\n" + packet.toString(4));

		final DExtraCachedHeader cachedHeader = cachedHeaders.get(refEntry.getCurrentFrameID());
		if(cachedHeader != null) {cachedHeader.updateLastActivatedTimestamp();}

		packet.getDVPacket().setRfHeader(refEntry.getCurrentHeader().getRFHeader().clone());
		packet.getDVPacket().setPacketType(PacketType.Header, PacketType.Voice);

		addReflectorPacket(refEntry, packet);

		if(
			!packet.isLastFrame() &&
			packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
		) {
			addReflectorPacket(refEntry, refEntry.getCurrentHeader().clone());
		}

		if(packet.getDvPacket().isEndVoicePacket()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "DExtra end of voice packet.\n" + packet.toString(4));

			cachedHeaders.remove(refEntry.getCurrentFrameID());

			refEntry.setCurrentFrameID(0x0);
			refEntry.setCurrentFrameSequence((byte)0x0);
			refEntry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			refEntry.setCurrentHeader(null);
		}

		refEntry.updateFrameSequenceTimestamp();
	}

	private void processConnect(final DExtraPacket packet) {
		assert packet != null && packet.getDExtraPacketType() == DExtraPacketType.CONNECT;
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.CONNECT) {return;}

		final String repeaterCallsign =
				packet.getConnectInfo().getCallsign().substring(0, DSTARDefines.CallsignFullLength - 1) +
				packet.getConnectInfo().getCallsignModule();
		final char repeaterModule = packet.getConnectInfo().getCallsignModule();

		final String reflectorCallsign =
				getReflectorCallsign().substring(0, DSTARDefines.CallsignFullLength - 1) +
				packet.getConnectInfo().getReflectorModule();
		final char reflectorModule = packet.getConnectInfo().getReflectorModule();

		if(
			packet.getConnectInfo().getType() == ReflectorConnectTypes.ACK ||
			packet.getConnectInfo().getType() == ReflectorConnectTypes.NAK ||
			packet.getConnectInfo().getType() == ReflectorConnectTypes.UNLINK
		) {
			entriesLocker.lock();
			try {
				for(final DExtraReflectorEntry refEntry : entries) {
					if(refEntry.getRemoteAddressPort().equals(packet.getRemoteAddress())) {
						processConnect(refEntry, packet);

						refEntry.updateActivityTimestamp();
					}
				}
			}finally {
				entriesLocker.unlock();
			}

			return;
		}

		if(
			!isIncomingLink() ||
			packet.getConnectInfo().getType() != ReflectorConnectTypes.LINK ||
			dextraChannel == null
		) {return;}

		entriesLocker.lock();
		try {

			//重複コネクションを検索
			for(final DExtraReflectorEntry refEntry : entries) {
				if(
					refEntry.getConnectionDirection() == ConnectionDirectionType.INCOMING &&
					refEntry.getRemoteAddressPort().equals(packet.getRemoteAddress()) &&
					refEntry.getLocalAddressPort().getPort() == packet.getLocalAddress().getPort() &&
					refEntry.getRepeaterCallsign().equals(repeaterCallsign) &&
					refEntry.getReflectorCallsign().equals(reflectorCallsign)
				) {
					refEntry.updateActivityTimestamp();

					return;
				}
			}

			final DSTARRepeater destinationRepeater = getGateway().getRepeater(reflectorCallsign);
			if(destinationRepeater == null) {
				if(log.isDebugEnabled()) {
					log.debug(
						logHeader + "DExtra incoming connect to unknown repeater = " + reflectorCallsign +
						"\n" + packet.toString(4)
					);
				}

				sendConnectPacket(
					ReflectorConnectTypes.NAK,
					packet.getConnectInfo().getCallsign(),
					packet.getConnectInfo().getCallsignModule(),
					packet.getConnectInfo().getReflectorModule(),
					dextraChannel,
					packet.getRemoteAddress().getAddress(),
					packet.getRemoteAddress().getPort()
				);

				return;
			}
			else if(
				!getReflectorLinkManager().isAllowReflectorIncomingConnectionWithLocalRepeater(reflectorCallsign) ||
				!getReflectorLinkManager().isAllowReflectorIncomingConnectionWithRemoteRepeater(repeaterCallsign)
			) {
				if(log.isInfoEnabled()) {
					log.info(
						logHeader +
						"Denied connection from repeater = " + repeaterCallsign + "@" + packet.getRemoteAddress() + ", " +
						"It's listed the reflector callsign at black list or " +
						"isAllowIncomingConnection = false on repeater properties."
					);
				}

				sendConnectPacket(
					ReflectorConnectTypes.NAK,
					packet.getConnectInfo().getCallsign(),
					packet.getConnectInfo().getCallsignModule(),
					packet.getConnectInfo().getReflectorModule(),
					dextraChannel,
					packet.getRemoteAddress().getAddress(),
					packet.getRemoteAddress().getPort()
				);

				return;
			}

			final boolean linkCountOk =
				countLinkEntry(ConnectionDirectionType.INCOMING) < Math.max(getMaxReflectors(), getMaxIncomingLink());

			if(linkCountOk) {
				final DExtraReflectorEntry newReflector = new DExtraReflectorEntry(
					generateLoopBlockID(),
					10,
					packet.getRemoteAddress(),
					dextraChannel.getLocalAddress(),
					ConnectionDirectionType.INCOMING
				);
				newReflector.setModCode(super.getModCode());
				newReflector.setCurrentState(DExtraConnectionInternalStates.Linking);
				newReflector.setNextState(DExtraConnectionInternalStates.LinkEstablished);
				newReflector.setReflectorCallsign(reflectorCallsign);
				newReflector.setReflectorModule(reflectorModule);
				newReflector.setRepeaterCallsign(repeaterCallsign);
				newReflector.setRepeaterModule(repeaterModule);
				newReflector.setDestinationRepeater(destinationRepeater);
				newReflector.setProtocolRevision(packet.getConnectInfo().getRevision());

				newReflector.updateActivityTimestamp();

				entries.add(newReflector);

				sendConnectPacket(
					ReflectorConnectTypes.ACK,
					packet.getConnectInfo().getCallsign(),
					packet.getConnectInfo().getCallsignModule(),
					packet.getConnectInfo().getReflectorModule(),
					this.dextraChannel,
					packet.getRemoteAddress().getAddress(),
					packet.getRemoteAddress().getPort()
				);

				onIncomingConnectionConnected(
					packet.getRemoteAddress(), reflectorCallsign, repeaterCallsign
				);

				if(log.isDebugEnabled()) {
					log.debug(
						logHeader +
						"Link established incoming connection entry.\n" + newReflector.toString(4)
					);
				}

				notifyStatusChanged();
			}
			else {
				sendConnectPacket(
					ReflectorConnectTypes.NAK,
					packet.getConnectInfo().getCallsign(),
					packet.getConnectInfo().getCallsignModule(),
					packet.getConnectInfo().getReflectorModule(),
					this.dextraChannel,
					packet.getRemoteAddress().getAddress(),
					packet.getRemoteAddress().getPort()
				);

				if(!linkCountOk && log.isWarnEnabled()) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Reached incoming link limit, ignore incoming link request from " +
							packet.getRemoteAddress() + "."
						);
					}
				}
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processConnect(final DExtraReflectorEntry refEntry, final DExtraPacket packet) {
		assert packet != null && packet.getDExtraPacketType() == DExtraPacketType.CONNECT;
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.CONNECT) {return;}

		switch(packet.getConnectInfo().getType()) {
		case ACK:
			if(refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
				if(!refEntry.getRepeaterCallsign().equals(packet.getConnectInfo().getCallsign())) {return;}

				if(refEntry.getCurrentState() == DExtraConnectionInternalStates.Linking) {
					if(log.isTraceEnabled())
						log.trace(logHeader + "DExtra ACK message received.\n" + packet.toString());

					if(!refEntry.isLinkFailed()) {	//既に確率されたリンクが失われてから再確立した場合には通知しない
						addConnectionStateChangeEvent(
							refEntry.getId(),
							refEntry.getConnectionDirection(),
							refEntry.getRepeaterCallsign(), refEntry.getReflectorCallsign(),
							ReflectorConnectionStates.LINKED,
							refEntry.getOutgoingReflectorHostInfo()
						);
					}

					if(log.isDebugEnabled()) {
						log.debug(logHeader +
							"DExtra reflector outgoing link established. [LinkReflector:" +
							refEntry.getReflectorCallsign() + "/Repeater:" + refEntry.getRepeaterCallsign() + "]"
						);
					}

					refEntry.setNextState(DExtraConnectionInternalStates.LinkEstablished);
					refEntry.setLinkRequest(false);
					refEntry.updateKeepAliveTime();
					refEntry.setRetryCount(0);
					refEntry.setLinkFailed(false);

					notifyStatusChanged();
				}
			}
			break;

		case NAK:
			if(refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
				if(!refEntry.getRepeaterCallsign().equals(packet.getConnectInfo().getCallsign())) {return;}

				if(refEntry.getCurrentState() == DExtraConnectionInternalStates.Linking) {
					if(log.isTraceEnabled())
						log.trace(logHeader + "DExtra NAK message received.\n" + packet.toString());

					refEntry.setNextState(DExtraConnectionInternalStates.Unlinked);
					refEntry.setLinkRequest(false);

					addConnectionStateChangeEvent(
						refEntry.getId(),
						refEntry.getConnectionDirection(),
						refEntry.getRepeaterCallsign(), refEntry.getReflectorCallsign(),
						ReflectorConnectionStates.LINKFAILED,
						refEntry.getOutgoingReflectorHostInfo()
					);

					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"DExtra reflector outgoing link failed. [LinkReflector:" +
							refEntry.getReflectorCallsign() + "/Repeater:" + refEntry.getRepeaterCallsign() + "]"
						);
					}

					if(log.isTraceEnabled())
						log.trace(logHeader + "DExtra remove refletor entry.\n" + refEntry.toString(4));

					removeReflectorEntry(refEntry.getId());

					notifyStatusChanged();
				}
			}
			break;

		case UNLINK:
			if(
				!refEntry.getReflectorCallsign().equals(packet.getConnectInfo().getCallsign()) &&
				!refEntry.getRepeaterCallsign().equals(packet.getConnectInfo().getCallsign())
			) {return;}

			if(refEntry.getCurrentState() == DExtraConnectionInternalStates.LinkEstablished) {
				if(log.isTraceEnabled())
					log.trace(logHeader + "DExtra disconnect message received.\n" + packet.toString());


				refEntry.setNextState(DExtraConnectionInternalStates.Unlinked);
				refEntry.setUnlinkRequest(false);

				switch(refEntry.getConnectionDirection()) {
				case OUTGOING:
					addConnectionStateChangeEvent(
						refEntry.getId(),
						refEntry.getConnectionDirection(),
						refEntry.getRepeaterCallsign(), refEntry.getReflectorCallsign(),
						ReflectorConnectionStates.UNLINKED,
						refEntry.getOutgoingReflectorHostInfo()
					);
					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"DExtra reflector outgoing link disconnected. [LinkReflector:" +
							refEntry.getReflectorCallsign() + "/Repeater:" + refEntry.getRepeaterCallsign() + "]"
						);
					}
					break;

				case INCOMING:
					onIncomingConnectionDisconnected(
						refEntry.getRemoteAddressPort(),
						refEntry.getReflectorCallsign(), refEntry.getRepeaterCallsign()
					);
					break;

				default:
					break;
				}

				removeReflectorEntry(refEntry.getId());

				notifyStatusChanged();
			}
			break;

		default:
			break;
		}
	}

	private void processPoll(final DExtraPacket packet) {
		assert packet != null && packet.getDExtraPacketType() == DExtraPacketType.POLL;
		if(packet == null || packet.getDExtraPacketType() != DExtraPacketType.POLL) {return;}

		if(log.isTraceEnabled())
			log.trace(logHeader + "DExtra receive poll packet.\n" + packet.toString(4));

		entriesLocker.lock();
		try {
			for(final DExtraReflectorEntry refEntry : entries) {
				if(
					(
						refEntry.getReflectorCallsign().substring(0, DSTARDefines.CallsignFullLength - 1).equals(
								packet.getPoll().getCallsign().substring(0, DSTARDefines.CallsignFullLength - 1)
						) ||
						refEntry.getRepeaterCallsign().substring(0, DSTARDefines.CallsignFullLength - 1).equals(
							packet.getPoll().getCallsign().substring(0, DSTARDefines.CallsignFullLength - 1)
						)
					) &&
					refEntry.getRemoteAddressPort().equals(packet.getRemoteAddress()) &&
					refEntry.getLocalAddressPort().getPort() == packet.getLocalAddress().getPort() &&
					refEntry.getCurrentState() == DExtraConnectionInternalStates.LinkEstablished
				) {
					refEntry.updateKeepAliveTime();

					refEntry.updateActivityTimestamp();

					return;
				}
			}
		}finally {
			entriesLocker.unlock();
		}

		if(!packet.getPoll().isDongle()) {return;}

		entriesLocker.lock();
		try {
			if(countLinkEntry(ConnectionDirectionType.INCOMING) >= Math.max(getMaxReflectors(), getMaxIncomingLink())) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"DExtra could not add refletor, because over reflector connection limit." + packet.toString()
					);
				}

				return;
			}

			if(log.isTraceEnabled())
				log.trace(logHeader + "New incoming DExtra dongle." + packet.toString());

			final DExtraReflectorEntry newReflector = new DExtraReflectorEntry(
				generateLoopBlockID(),
				10,
				packet.getRemoteAddress(),
				dextraChannel.getLocalAddress(),
				ConnectionDirectionType.INCOMING
			);
			newReflector.setNextState(DExtraConnectionInternalStates.LinkEstablished);
			newReflector.setReflectorCallsign(DSTARDefines.EmptyLongCallsign);
			newReflector.setReflectorModule(' ');
			newReflector.setRepeaterCallsign(packet.getPoll().getCallsign());
			newReflector.setRepeaterModule(packet.getPoll().getCallsign().charAt(DSTARDefines.CallsignFullLength - 1));
			newReflector.setProtocolRevision(packet.getConnectInfo().getRevision());
			newReflector.setDongle(true);

			onIncomingConnectionConnected(
				packet.getRemoteAddress(),
				newReflector.getReflectorCallsign(), newReflector.getRepeaterCallsign()
			);

			entries.add(newReflector);
		}finally {
			entriesLocker.unlock();
		}


		sendPollPacket(
			getReflectorCallsign(),
			this.dextraChannel, packet.getRemoteAddress().getAddress(), packet.getRemoteAddress().getPort()
		);
	}

	private void writeHeader(
		final String repeaterCallsign, final DExtraReflectorEntry refEntry,
		final DSTARPacket packet, final ConnectionDirectionType direction
	) {
		if(
			refEntry.getCurrentState() != DExtraConnectionInternalStates.LinkEstablished ||
			(
				refEntry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			refEntry.getCurrentFrameID() != 0x0 ||
			refEntry.getLoopBlockID().equals(packet.getLoopblockID())
		) {return;}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(refEntry.getModCode());

		if(refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
			packet.getRfHeader().setRepeater1Callsign(refEntry.getRepeaterCallsign().toCharArray());
			packet.getRfHeader().setRepeater2Callsign(refEntry.getReflectorCallsign().toCharArray());
		}
		else if(refEntry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
			packet.getRfHeader().setRepeater1Callsign(refEntry.getRepeaterCallsign().toCharArray());
			packet.getRfHeader().setRepeater2Callsign(refEntry.getReflectorCallsign().toCharArray());
		}

		refEntry.setCurrentHeader(packet.clone());
		refEntry.setCurrentFrameID(packet.getBackBone().getFrameIDNumber());
		refEntry.setCurrentFrameSequence((byte)0x0);
		refEntry.setCurrentFrameDirection(ConnectionDirectionType.OUTGOING);

		switch(refEntry.getConnectionDirection()) {
		case OUTGOING:
			if(refEntry.getRepeaterCallsign().equals(repeaterCallsign)) {
				refEntry.getCacheTransmitter().reset();

				refEntry.getCacheTransmitter().inputWrite(
					new DExtraTransmitPacketEntry(
						PacketType.Header,
						packet, refEntry.getOutgoingChannel(),
						refEntry.getRemoteAddressPort().getAddress(), refEntry.getRemoteAddressPort().getPort(),
						packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
					)
				);
			}
			break;

		case INCOMING:
			if(
				refEntry.getRepeaterCallsign().equals(DSTARDefines.EmptyLongCallsign) ||
				refEntry.getRepeaterCallsign().equals(repeaterCallsign)
			) {
				refEntry.getCacheTransmitter().reset();

				refEntry.getCacheTransmitter().inputWrite(
					new DExtraTransmitPacketEntry(
						PacketType.Header,
						packet, dextraChannel,
						refEntry.getRemoteAddressPort().getAddress(), refEntry.getRemoteAddressPort().getPort(),
						packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
					)
				);
			}
			break;

		default:
			break;
		}

		refEntry.updateFrameSequenceTimestamp();
	}

	private void writeVoice(
		final String repeaterCallsign, final DExtraReflectorEntry refEntry,
		final DSTARPacket packet, final ConnectionDirectionType direction
	) {
		if(
			refEntry.getCurrentState() != DExtraConnectionInternalStates.LinkEstablished ||
			(
				refEntry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			refEntry.getCurrentFrameID() == 0x0 ||
			refEntry.getCurrentFrameDirection() != ConnectionDirectionType.OUTGOING ||
			refEntry.getLoopBlockID().equals(packet.getLoopblockID())
		) {return;}

		//フレームIDを改変する
//		packet.getBackBone().setFrameIDint((packet.getBackBone().getFrameIDint() ^ refEntry.getModCode()));
		packet.getBackBone().modFrameID(refEntry.getModCode());

		if(refEntry.getCurrentFrameID() != packet.getBackBone().getFrameIDNumber()) {return;}

		switch(refEntry.getConnectionDirection()) {
		case OUTGOING:
			if(refEntry.getRepeaterCallsign().equals(repeaterCallsign)) {
				refEntry.getCacheTransmitter().inputWrite(
					new DExtraTransmitPacketEntry(
						PacketType.Voice,
						packet, refEntry.getOutgoingChannel(),
						refEntry.getRemoteAddressPort().getAddress(), refEntry.getRemoteAddressPort().getPort(),
						packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
					)
				);
/*
				if(
					!packet.isEndVoicePacket() &&
					packet.getBackBone().getSequence() == 0x14 && refEntry.getHeader() != null
				) {
					final DvPacket header = new DvPacket(refEntry.getHeader(), DStarProtocol.DExtra);
					header.setBackBone(packet.getBackBone().clone());
					header.getBackBone().setSequence((byte)0x80);

					refEntry.getCacheTransmitter().inputWrite(
						new DExtraTransmitPacketEntry(
							header,
							refEntry.getOutgoingChannel(),
							refEntry.getRemoteAddress(), refEntry.getRemotePort(),
							FrameSequenceType.None
						)
					);
				}
*/
			}
			break;

		case INCOMING:
			if(
				refEntry.getReflectorCallsign().equals(DSTARDefines.EmptyLongCallsign) ||
				refEntry.getReflectorCallsign().equals(repeaterCallsign)
			) {
				refEntry.getCacheTransmitter().inputWrite(
					new DExtraTransmitPacketEntry(
						PacketType.Voice,
						packet, this.dextraChannel,
						refEntry.getRemoteAddressPort().getAddress(), refEntry.getRemoteAddressPort().getPort(),
						packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
					)
				);
/*
				if(
					!packet.isEndVoicePacket() &&
					packet.getBackBone().getSequence() == 0x14 && refEntry.getHeader() != null
				) {
					final DvPacket header = new DvPacket(refEntry.getHeader(), DStarProtocol.DExtra);
					header.setBackBone(packet.getBackBone().clone());
					header.getBackBone().setSequence((byte)0x80);

					refEntry.getCacheTransmitter().inputWrite(
						new DExtraTransmitPacketEntry(
							header,
							this.dextraChannel,
							refEntry.getRemoteAddress(), refEntry.getRemotePort(),
							FrameSequenceType.None
						)
					);
				}
*/
			}
			break;

		default:

			break;
		}

		refEntry.updateFrameSequenceTimestamp();
	}

	private boolean parsePacket(final Queue<DExtraPacket> receivePackets) {
		assert receivePackets != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
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
					Optional<DExtraPacket> validPacket = null;
					do {
						if (
							(validPacket = DExtraPacketTool.isValidConnectInfoPacket(receivePacket)).isPresent() ||
							(validPacket = DExtraPacketTool.isValidPollPacket(receivePacket)).isPresent() ||
							(validPacket = DExtraPacketTool.isValidHeaderPacket(receivePacket)).isPresent() ||
							(validPacket = DExtraPacketTool.isValidVoicePacket(receivePacket)).isPresent()
						) {
							final DExtraPacket copyPacket = validPacket.get().clone();

							copyPacket.setRemoteAddress(buffer.getRemoteAddress());
							copyPacket.setLocalAddress(buffer.getLocalAddress());

							receivePackets.add(copyPacket);

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

	private boolean sendPollPacket(
		final String repeaterCallsign,
		final SocketIOEntryUDP channel, final InetAddress destinationAddress, final int destinationPort
	) {
		assert CallSignValidator.isValidRepeaterCallsign(repeaterCallsign);
		assert destinationAddress != null && destinationPort >= 1024;

		final DExtraPoll poll = new DExtraPoll();
		poll.setCallsign(repeaterCallsign);

		final DExtraPacket packet = new DExtraPacketImpl(poll);
		packet.setRemoteAddress(new InetSocketAddress(destinationAddress, destinationPort));

		return sendPacket(packet, channel, destinationAddress, destinationPort);
	}

	private boolean sendConnectPacket(
		final ReflectorConnectTypes type,
		final String repeaterCallsign, final char repeaterModule, final char reflectorModule,
		final SocketIOEntryUDP channel, final InetAddress destinationAddress, final int destinationPort
	) {
		assert type != null;
		assert CallSignValidator.isValidRepeaterCallsign(repeaterCallsign);
		assert repeaterModule >= 'A' && repeaterModule <= 'Z';
		assert reflectorModule >= 'A' && reflectorModule <= 'Z';
		assert destinationAddress != null && destinationPort >= 1024;

		final DExtraConnectInfo connect = new DExtraConnectInfo();
		connect.setType(type);
		connect.setCallsign(repeaterCallsign.substring(0, DSTARDefines.CallsignFullLength - 1) + ' ');
		connect.setCallsignModule(repeaterModule);
		connect.setReflectorModule(reflectorModule);

		final DExtraPacket packet = new DExtraPacketImpl(connect);
		packet.setRemoteAddress(new InetSocketAddress(destinationAddress, destinationPort));

		return sendPacket(packet, channel, destinationAddress, destinationPort);
	}

	private boolean sendPacket(DExtraPacket packet, SocketIOEntryUDP channel, InetAddress destinationAddress, int destinationPort) {
		assert packet != null && destinationAddress != null;

		packet.setLocalAddress(channel.getLocalAddress());


		Optional<byte[]> buffer = null;
		int txPackets = 0;

		switch(packet.getDExtraPacketType()) {
		case CONNECT:
			buffer = DExtraPacketTool.assembleConnectInfoPacket(packet);
			txPackets = 2;
			break;
		case POLL:
			buffer = DExtraPacketTool.assemblePollPacket(packet);
			txPackets = 1;
			break;
		case HEADER:
			packet.getBackBone().setId((byte)0x20);
			packet.getBackBone().setSendRepeaterID((byte)0x01);
			packet.getBackBone().setDestinationRepeaterID((byte)0x01);
			packet.getBackBone().setSendTerminalID((byte)0x00);
			packet.getDvPacket().getRfHeader().setFlags(new byte[] {0x00, 0x00, 0x00});
			buffer = DExtraPacketTool.assembleHeaderPacket(packet);
			txPackets = 5;
			break;
		case VOICE:
			packet.getBackBone().setId((byte)0x20);
			packet.getBackBone().setSendRepeaterID((byte)0x01);
			packet.getBackBone().setDestinationRepeaterID((byte)0x01);
			packet.getBackBone().setSendTerminalID((byte)0x00);
			buffer = DExtraPacketTool.assembleVoicePacket(packet);
			txPackets = 1;
			break;
		default:
			return false;
		}

		if(!buffer.isPresent()) {return false;}

		SocketIOEntryUDP dstChannel = channel != null ? channel : this.dextraChannel;
		if(!dstChannel.getKey().isValid()){return false;}

		if(log.isTraceEnabled())
			log.trace(logHeader + "DExtra send packet.\n" + packet.toString(4));

		boolean result = true;
		for(int cnt = 0; cnt < txPackets; cnt++) {
			if(
				!super.writeUDPPacket(
					dstChannel.getKey(),
					packet.getRemoteAddress() != null ? packet.getRemoteAddress() : new InetSocketAddress(destinationAddress, destinationPort),
					ByteBuffer.wrap(buffer.get())
				)
			) {result = false;}
		}

		return result;
	}

	private String getReflectorCallsign() {
		return getGateway().getGatewayCallsign().substring(0, DSTARDefines.CallsignFullLength - 1) + ' ';
	}

	private boolean removeReflectorEntry(UUID id) {
		assert id != null;

		reflectorEntryRemoveRequestQueue.add(id);

		return true;
	}

	private void addCacheHeader(int frameID, DSTARPacket headerPacket, InetAddress remoteAddress, int remotePort) {
		final int overlimitHeaders = cachedHeaders.size() - maxCachedHeaders;
		if(overlimitHeaders >= 0) {
			final List<Integer> sortedFrameID =
				Stream.of(cachedHeaders)
				.sorted(
					ComparatorCompat.comparingLong(
						new ToLongFunction<Map.Entry<Integer, DExtraCachedHeader>>() {
							@Override
							public long applyAsLong(Map.Entry<Integer, DExtraCachedHeader> integerDExtraCachedHeaderEntry) {
								return integerDExtraCachedHeaderEntry.getValue().getLastActivatedTimestamp();
							}
						}
					)
				)
				.map(
					new Function<Map.Entry<Integer,DExtraCachedHeader>, Integer>() {
						@Override
						public Integer apply(Map.Entry<Integer, DExtraCachedHeader> integerDExtraCachedHeaderEntry) {
							return integerDExtraCachedHeaderEntry.getValue().getFrameID();
						}
					}
				)
				.collect(Collectors.<Integer>toList());

			int c = 0;
			do{
				cachedHeaders.remove(sortedFrameID.get(c));
			}while(overlimitHeaders > c++);
		}

		final DExtraCachedHeader cacheHeaderEntry =
			new DExtraCachedHeader(frameID, headerPacket, remoteAddress, remotePort);
//		if(cachedHeaders.containsKey(frameID)) {cachedHeaders.remove(frameID);}
		cachedHeaders.put(frameID, cacheHeaderEntry);
	}

	private void processTransmitterPacket(final DExtraReflectorEntry refEntry) {
		Optional<DExtraTransmitPacketEntry> transmitPacket = null;
		while((transmitPacket = refEntry.getCacheTransmitter().outputRead()).isPresent()) {
			final DExtraTransmitPacketEntry t = transmitPacket.get();
			t.getPacket().getBackBone().undoModFrameID();

			final DExtraPacket dextraPacket = new DExtraPacketImpl(
				t.getPacketType() == PacketType.Header ? DExtraPacketType.HEADER : DExtraPacketType.VOICE,
				null,
				ConnectionDirectionType.Unknown,
				null,
				null,
				t.getPacket().getDVPacket()
			);

			sendPacket(dextraPacket, t.getChannel(), t.getDestinationAddress(), t.getDestinationPort());

			if(t.getPacket().isEndVoicePacket()) {
				refEntry.setCurrentFrameID(0x0);
				refEntry.setCurrentFrameSequence((byte)0x0);
				refEntry.setCurrentFrameDirection(ConnectionDirectionType.Unknown);
			}
		}
		if(refEntry.getCacheTransmitter().isUnderflow() && !refEntry.isCacheTransmitterUndeflow()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Transmitter cache underflow detected.\n" + refEntry.toString(4));
		}
		refEntry.setCacheTransmitterUndeflow(refEntry.getCacheTransmitter().isUnderflow());
	}

	private void processReflectorentryRemoveRequest() {
		entriesLocker.lock();
		try {
			if(!reflectorEntryRemoveRequestQueue.isEmpty()) {
				for(
					final Iterator<UUID> removeIt = reflectorEntryRemoveRequestQueue.iterator();
					removeIt.hasNext();
				) {
					final UUID removeID = removeIt.next();
					removeIt.remove();

					for(
						final Iterator<DExtraReflectorEntry> refEntryIt = entries.iterator();
						refEntryIt.hasNext();
					) {
						final DExtraReflectorEntry refEntry = refEntryIt.next();
						if(refEntry.getId().equals(removeID)) {
							if(log.isDebugEnabled()) {
								log.debug(
									logHeader +
									"Remove reflector entry.\n" + refEntry.toString(4)
								);
							}

							finalizeReflectorEntry(refEntry);

							refEntryIt.remove();
							break;
						}
					}
				}
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private boolean addReflectorPacket(final DExtraReflectorEntry refEntry, final DSTARPacket packet) {
		assert refEntry != null && packet != null;

		return addReflectorPacket(
			refEntry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					refEntry.getRepeaterCallsign() : refEntry.getReflectorCallsign(),
			packet
		);
	}

	private boolean addReflectorPacket(final String destinationRepeaterCallsign, final DSTARPacket packet) {
		assert destinationRepeaterCallsign != null && packet != null;

		return addReflectorReceivePacket(new ReflectorReceivePacket(destinationRepeaterCallsign, packet));
	}

	private void finalizeReflectorEntries(){
		entriesLocker.lock();
		try {
			for(final Iterator<DExtraReflectorEntry> it = entries.iterator(); it.hasNext(); ) {
				final DExtraReflectorEntry refEntry = it.next();

				finalizeReflectorEntry(refEntry);

				it.remove();
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void finalizeReflectorEntry(DExtraReflectorEntry refEntry){
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
				log.debug(logHeader + logHeader + "Error occurred at channel close.", ex);
		}
	}

	private void closeDExtraChannel(){
		if(dextraChannel != null && dextraChannel.getChannel().isOpen()) {
			try {
				dextraChannel.getChannel().close();
				dextraChannel = null;
			}catch(IOException ex) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred at channel close.", ex);
			}
		}
	}

	private int countLinkEntry(final ConnectionDirectionType direction) {
		entriesLocker.lock();
		try {
			final long count =
				Stream.of(entries)
				.filter(new Predicate<DExtraReflectorEntry>() {
					@Override
					public boolean test(DExtraReflectorEntry entry) {
						return (direction == null || entry.getConnectionDirection() == direction);
					}
				})
				.count();

			return (int)count;
		}finally {
			entriesLocker.unlock();
		}
	}
}


