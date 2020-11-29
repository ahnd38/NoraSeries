package org.jp.illg.dstar.reflector.protocol.dplus;

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
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusConnect;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusConnectionInternalState;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusPacket;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusPacketImpl;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusPacketType;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusPoll;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusReflectorEntry;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusTransmitFrameEntry;
import org.jp.illg.dstar.reflector.protocol.dplus.model.DPlusTransmitPacketEntry;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectTypes;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorReceivePacket;
import org.jp.illg.dstar.reporter.model.ReflectorStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlDPlusHandler;
import org.jp.illg.dstar.service.web.model.DPlusConnectionData;
import org.jp.illg.dstar.service.web.model.DPlusStatusData;
import org.jp.illg.dstar.service.web.model.ReflectorConnectionData;
import org.jp.illg.dstar.service.web.model.ReflectorStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.util.ApplicationInformation;
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
public class DPlusCommunicationService
extends ReflectorCommunicationServiceBase<BufferEntry, DPlusReflectorEntry>
implements WebRemoteControlDPlusHandler
{

	private static final int dplusStandardPort = 20001;

	private static final int receiveKeepAliveTimeoutSeconds = 30;
	private static final int transmitKeepAlivePeriodSeconds = 1;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String loginCallsign;
	private static final String loginCallsignDefault = DSTARDefines.EmptyLongCallsign;
	public static final String loginCallsignPropertyName = "LoginCallsign";

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

	private static final boolean dPlusFullSupportDefault = false;
	public static final String dPlusFullSupportPropertyName = "DPlusFullSupport";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int dPlusPort;
	private static final int dPlusPortDefault = dplusStandardPort;
	public static final String dPlusPortPropertyName = "DPlusPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxOutgoingLink;
	private static final int maxOutgoingLinkDefault = 8;
	public static final String maxOutgoingLinkPropertyName = "MaxOutgoingLink";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int maxIncomingLink;
	private static final int maxIncomingLinkDefault = 64;
	public static final String maxIncomingLinkPropertyName = "MaxIncomingLink";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableOpenDStar;
	private static final boolean enableOpenDStarDefault = true;
	public static final String enableOpenDStarPropertyName = "EnableOpenDStar";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String openDStarServerAddress;
	private static final String openDStarServerAddressDefault = "auth.dstargateway.org";
	public static final String openDStarServerAddressPropertyName = "OpenDStarServerAddress";

	private static final Pattern supportCallsignPattern =
		Pattern.compile("^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-Z])|(([R][E][F])[0-9]{3}[ ][A-Z]))$");

	private final String logHeader;

	private final Queue<UUID> entryRemoveRequestQueue;
	private final Timer entryCleanupIntervalTimekeeper;


	private final Queue<DPlusPacket> receivePacketQueue;
	private final Lock receivePacketQueueLocker;

	private SocketIOEntryUDP incomingChannel;

	private DPlusAuthenticator authOpenDStar;
	private DPlusAuthenticator authDutchStar;


	public DPlusCommunicationService(
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
			DPlusCommunicationService.class,
			socketIO,
			BufferEntry.class,
			HostIdentType.RemoteLocalAddressPort,
			gateway, workerExecutor, reflectorLinkManager,
			eventListener
		);
		setBufferSizeTCP(1024 * 512);

		logHeader = DPlusCommunicationService.class.getSimpleName() + " : ";

		entryRemoveRequestQueue = new LinkedList<>();
		entryCleanupIntervalTimekeeper = new Timer(10, TimeUnit.SECONDS);

		receivePacketQueue = new LinkedList<>();
		receivePacketQueueLocker = new ReentrantLock();

		setLoginCallsign(loginCallsignDefault);
		setOutgoingLink(outgoingLinkDefault);
		setIncomingLink(incomingLinkDefault);
		setDPlusPort(dPlusPortDefault);
		setMaxIncomingLink(maxIncomingLinkDefault);
		setMaxOutgoingLink(maxOutgoingLinkDefault);

		setEnableOpenDStar(enableOpenDStarDefault);
		setOpenDStarServerAddress(openDStarServerAddressDefault);
	}

	public DPlusCommunicationService(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<?> applicationInformation,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorLinkManager reflectorLinkManager,
		final EventListener<ReflectorCommunicationServiceEvent> eventListener
	) {
		this(systemID, applicationInformation, exceptionListener, gateway, workerExecutor, null, reflectorLinkManager, eventListener);
	}


	@Override
	public DSTARProtocol getProtocolType() {
		return DSTARProtocol.DPlus;
	}

	@Override
	public ReflectorProtocolProcessorTypes getProcessorType() {
		return ReflectorProtocolProcessorTypes.DPlus;
	}

	@Override
	public boolean setProperties(ReflectorProperties properties) {

		final String configLoginCallsign =
			DSTARUtils.formatFullLengthCallsign(
				PropertyUtils.getString(
					properties.getConfigurationProperties(),
					loginCallsignPropertyName,
					loginCallsignDefault
				)
			);
		if(
			!"JX1XXX  ".equals(configLoginCallsign) &&
			CallSignValidator.isValidUserCallsign(configLoginCallsign)
		) {
			setLoginCallsign(configLoginCallsign);
		}
		else {
			final String gatewayCallsign = DSTARUtils.formatFullCallsign(getGateway().getGatewayCallsign());

			if(log.isWarnEnabled()) {
				log.warn(
					logHeader + "Illegal login callsign = " + configLoginCallsign + ", has been replaced by " +
					gatewayCallsign
				);
			}

			setLoginCallsign(gatewayCallsign);
		}

		if(!CallSignValidator.isValidUserCallsign(getLoginCallsign())) {
			if(DSTARDefines.EmptyLongCallsign.equals(getLoginCallsign())) {
				if(log.isErrorEnabled()) {
					log.error(
						logHeader + loginCallsignPropertyName + " must be set, " +
						"please set Reflectors->DPlus->ConfigurationParameter->" + loginCallsignPropertyName + "."
					);
				}
			}

			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Illegal login callsign [" +
					loginCallsignPropertyName + ":" + getLoginCallsign() + "] is set."
				);
			}

			return false;
		}

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
				dPlusFullSupportPropertyName,
				dPlusFullSupportDefault
			)
		);

		setDPlusPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				dPlusPortPropertyName,
				dPlusPortDefault
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

		setEnableOpenDStar(
			isIncomingLink() ?
				PropertyUtils.getBoolean(
					properties.getConfigurationProperties(),
					enableOpenDStarPropertyName,
					enableOpenDStarDefault
				) : false
		);

		setOpenDStarServerAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				openDStarServerAddressPropertyName,
				openDStarServerAddressDefault
			)
		);

		return true;
	}

	@Override
	public ReflectorProperties getProperties(ReflectorProperties properties) {

		//loginCallsign
		if(properties.getConfigurationProperties().containsKey(loginCallsignPropertyName))
			properties.getConfigurationProperties().remove(loginCallsignPropertyName);

		properties.getConfigurationProperties().put(loginCallsignPropertyName, getLoginCallsign());

		//DPlusFullSupport
		if(properties.getConfigurationProperties().containsKey(dPlusFullSupportPropertyName))
			properties.getConfigurationProperties().remove(dPlusFullSupportPropertyName);

		properties.getConfigurationProperties().put(
			dPlusFullSupportPropertyName, String.valueOf(isIncomingLink())
		);

		//DPlusPort
		if(properties.getConfigurationProperties().containsKey(dPlusPortPropertyName))
			properties.getConfigurationProperties().remove(dPlusPortPropertyName);

		properties.getConfigurationProperties().put(dPlusPortPropertyName, String.valueOf(getDPlusPort()));

		//maxOutgoingLink
		if(properties.getConfigurationProperties().containsKey(maxOutgoingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxOutgoingLinkPropertyName);

		properties.getConfigurationProperties().put(maxOutgoingLinkPropertyName, String.valueOf(getMaxOutgoingLink()));

		//maxIncommingLink
		if(properties.getConfigurationProperties().containsKey(maxIncomingLinkPropertyName))
			properties.getConfigurationProperties().remove(maxIncomingLinkPropertyName);

		properties.getConfigurationProperties().put(maxIncomingLinkPropertyName, String.valueOf(getMaxIncomingLink()));

		//enableOpenDStar
		if(properties.getConfigurationProperties().containsKey(enableOpenDStarPropertyName))
			properties.getConfigurationProperties().remove(enableOpenDStarPropertyName);

		properties.getConfigurationProperties().put(enableOpenDStarPropertyName, isEnableOpenDStar());

		//openDStarServerAddress
		if(properties.getConfigurationProperties().containsKey(openDStarServerAddressPropertyName))
			properties.getConfigurationProperties().remove(openDStarServerAddressPropertyName);

		properties.getConfigurationProperties().put(openDStarServerAddressPropertyName, getOpenDStarServerAddress());

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
	public DSTARGateway getGateway() {
		return super.getGateway();
	}

	@Override
	public UUID linkReflector(
		final String reflectorCallsign, final ReflectorHostInfo reflectorHostInfo,
		final DSTARRepeater repeater
	) {
		if(
			reflectorHostInfo == null || repeater == null ||
			!supportCallsignPattern.matcher(reflectorCallsign).matches() ||
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
			final Optional<DPlusReflectorEntry> duplicateEntry =
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

			final DPlusReflectorEntry entry = new DPlusReflectorEntry(
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
			entry.setDestinationRepeater(repeater);
			entry.setOutgoingChannel(outgoingChannel);
			entry.setNextState(DPlusConnectionInternalState.Initialize);
			entry.setConnectionRequest(ConnectionRequest.LinkRequest);
			entry.setModCode(getModCode());

			entry.getActivityTimeKepper().updateTimestamp();

			addEntry(entry);

			entryID = entry.getId();
		}finally {entriesLocker.unlock();}

		wakeupProcessThread();

		return entryID;
	}

	@Override
	public UUID unlinkReflector(final DSTARRepeater repeater) {
		if(
			!isRunning() ||
			repeater == null
		) {return null;}

		final ProcessResult<UUID> result = new ProcessResult<>();

		entriesLocker.lock();
		try {
			findReflectorEntry(ConnectionDirectionType.OUTGOING, repeater.getRepeaterCallsign(), null)
			.findFirst()
			.ifPresent(new Consumer<DPlusReflectorEntry>() {
				@Override
				public void accept(DPlusReflectorEntry entry) {
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
	public boolean isSupportTransparentMode() {
		return false;
	}

	@Override
	public boolean writePacketInternal(
		final DSTARRepeater repeater, final DSTARPacket packet, final ConnectionDirectionType direction
	) {
		if(repeater == null || packet == null || direction == null) {return false;}

		if(log.isTraceEnabled())
			log.trace(logHeader + "Input packet from gateway..dir=" + direction + "\n" + packet.toString(4));

		entriesLocker.lock();
		try {
			findReflectorEntry(
				direction == ConnectionDirectionType.BIDIRECTIONAL ? null : direction,
				(direction == ConnectionDirectionType.INCOMING || direction == ConnectionDirectionType.BIDIRECTIONAL)
					? null : repeater.getRepeaterCallsign(),
				null
			)
			.forEach(new Consumer<DPlusReflectorEntry>() {
				@Override
				public void accept(DPlusReflectorEntry entry) {
					if(
						entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
						entry.isReadonly()
					) {return;}

					if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
						writeHeader(
							repeater.getRepeaterCallsign(),
							entry, packet, direction
						);
					}

					if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {
						writeVoice(
							repeater.getRepeaterCallsign(),
							entry, packet, direction
						);
					}
				}
			});
		}finally {entriesLocker.unlock();}


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
		final OperationRequest ops = new OperationRequest();

		if(authOpenDStar != null)
			ops.combine(authOpenDStar.connectedEvent(key, localAddress, remoteAddress));

		if(authDutchStar != null)
			ops.combine(authDutchStar.connectedEvent(key, localAddress, remoteAddress));

		return ops;
	}

	@Override
	public void disconnectedEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress
	) {
		if(authOpenDStar != null)
			authOpenDStar.disconnectedEvent(key, localAddress, remoteAddress);

		if(authDutchStar != null)
			authDutchStar.disconnectedEvent(key, localAddress, remoteAddress);
	}

	@Override
	public void errorEvent(
		SelectionKey key, ChannelProtocol protocol,
		InetSocketAddress localAddress, InetSocketAddress remoteAddress,
		Exception ex
	) {
		if(authOpenDStar != null)
			authOpenDStar.errorEvent(key, localAddress, remoteAddress, ex);

		if(authDutchStar != null)
			authDutchStar.errorEvent(key, localAddress, remoteAddress, ex);
	}

	@Override
	public boolean isEnableIncomingLink() {
		return isIncomingLink();
	}

	@Override
	public int getIncomingLinkPort() {
		return getDPlusPort();
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
									new InetSocketAddress(getDPlusPort()),
									DPlusCommunicationService.this.getHandler(),
									DPlusCommunicationService.this.getClass().getSimpleName() + "@" + getDPlusPort()
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
		if(isEnableOpenDStar()) {
			authOpenDStar =
				new DPlusAuthenticator(
					this,
					getLoginCallsign(),
					getGateway().getGatewayCallsign(),
					getOpenDStarServerAddress(),
					20001,
					'2',
					false
				);
		}

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
		entriesLocker.lock();
		try {
			receivePacketQueueLocker.lock();
			try {
				parsePacket(receivePacketQueue);

				for(Iterator<DPlusPacket> it = receivePacketQueue.iterator(); it.hasNext();) {
					DPlusPacket packet = it.next();
					it.remove();

					switch(packet.getDPlusPacketType()) {
					case CONNECT:
						processConnect(packet);
						break;

					case POLL:
						processPoll(packet);
						break;

					case HEADER:
						processHeader(packet);
						break;

					case VOICE:
						processVoice(packet);
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

		entriesLocker.lock();
		try {
			for(final DPlusReflectorEntry entry : entries) {
				boolean reProcess;
				do {
					reProcess = false;

					final boolean stateChanged =
						entry.getCurrentState() != entry.getNextState();
					entry.setStateChanged(stateChanged);
					if(stateChanged) {notifyStatusChanged();}

					if(log.isDebugEnabled() && entry.isStateChanged()) {
						log.debug(
							logHeader +
							"State changed " +
								entry.getCurrentState().toString() + " -> " + entry.getNextState().toString() +
								"(" + entry.getConnectionDirection() + ":" + entry.getRepeaterCallsign() + ")"
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

		if(authOpenDStar != null) {authOpenDStar.process();}
		if(authDutchStar != null) {authDutchStar.process();}

		processEntryRemoveRequestQueue();
		cleanupProcessEntry();

		return processResult;
	}

	@Override
	protected ThreadProcessResult processVoiceTransfer() {
		entriesLocker.lock();
		try {
			for(final DPlusReflectorEntry entry : entries) {
				for(
					final Iterator<DPlusTransmitFrameEntry> it = entry.getTransmitterFrameEntries().values().iterator();
					it.hasNext();
				) {
					final DPlusTransmitFrameEntry frameEntry = it.next();

					boolean timeout = false;
					if(
						transmitPacket(entry, frameEntry.getCacheTransmitter(), frameEntry.getActivityTimestamp()) ||
						(timeout = frameEntry.getActivityTimestamp().isTimeout(1, TimeUnit.SECONDS))
					) {
						it.remove();

						if(timeout) {addTxEndPacket(entry, frameEntry);}

						if(log.isDebugEnabled()) {
							log.debug(
								logHeader +
								"Remove transmit frame entry = " + String.format("0x%04X", frameEntry.getFrameID()) + "."
							);
						}
					}
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

			for(final DPlusReflectorEntry entry : entries) {
				if(!entry.getTransmitterFrameEntries().isEmpty())
					return ProcessIntervalMode.VoiceTransfer;
			}
		}finally {
			entriesLocker.unlock();
		}

		return ProcessIntervalMode.Normal;
	}

	/**
	 * キャッシュからパケットを送信する
	 *
	 * @param entry リフレクターコネクションエントリ
	 * @param cacheTransmitter キャッシュ
	 * @param activityTimestamp アクティビティタイムスタンプ
	 * @return 終端パケットを送信したらtrue
	 */
	private boolean transmitPacket(
		final DPlusReflectorEntry entry, final CacheTransmitter<DPlusTransmitPacketEntry> cacheTransmitter,
		final Timer activityTimestamp
	) {
		final ProcessResult<Boolean> isEndPacket = new ProcessResult<>(false);

		while(cacheTransmitter.hasOutputRead()) {
			cacheTransmitter.outputRead()
			.ifPresent(new Consumer<DPlusTransmitPacketEntry>() {
				@Override
				public void accept(final DPlusTransmitPacketEntry packetEntry) {
					packetEntry.getPacket().getBackBone().undoModFrameID();

					DPlusPacket packet = null;

					switch(packetEntry.getPacketType()) {
					case Header:
						packet =
							new DPlusPacketImpl(
								packetEntry.getPacket().getRfHeader(),
								packetEntry.getPacket().getBackBone()
							);
						break;

					case Voice:
						packet =
							new DPlusPacketImpl(
								packetEntry.getPacket().getVoiceData(),
								packetEntry.getPacket().getBackBone()
							);
						break;

					default:
						return;
					}

					sendPacket(entry, packet, packetEntry.isOneShot());

					if(activityTimestamp != null)
						activityTimestamp.updateTimestamp();

					if(packet.getDvPacket().isEndVoicePacket())
						isEndPacket.setResult(true);
				}
			});
		}

		return isEndPacket.getResult();
	}

	/**
	 * スリープに入れるか
	 *
	 * @return スリープに入れる(やるべき処理がない)のであればtrue
	 */
	protected boolean isCanSleep() {
		entriesLocker.lock();
		try {
			return entries.isEmpty();
		}finally {
			entriesLocker.unlock();
		}
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
		report.setIncomingLinkPort(getDPlusPort());
		report.setIncomingStatus("");
		report.setOutgoingStatus("");

		return report;
	}

	@Override
	protected boolean initializeWebRemoteControlInternal(WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeReflectorDPlus(this);
	}

	@Override
	protected boolean getReflectorConnectionsInternal(
		@NonNull List<ReflectorConnectionData> connections
	) {
		entriesLocker.lock();
		try {
			for(final DPlusReflectorEntry entry : entries) {
				final DPlusConnectionData con = new DPlusConnectionData();

				con.setConnectionId(entry.getId());
				con.setReflectorType(getReflectorType());
				con.setConnectionDirection(entry.getConnectionDirection());
				if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING) {
					con.setReflectorCallsign(entry.getReflectorCallsign());
					con.setRepeaterCallsign(entry.getRepeaterCallsign());
				}
				else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
					con.setReflectorCallsign(entry.getRepeaterCallsign());
					con.setRepeaterCallsign(entry.getReflectorCallsign());
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
		final DPlusStatusData status = new DPlusStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ReflectorStatusData> getStatusDataTypeInternal() {
		return DPlusStatusData.class;
	}

	private ThreadProcessResult onStateInitialize(DPlusReflectorEntry entry) {
		assert entry != null;

		entry.setNextState(DPlusConnectionInternalState.Linking);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateLinking(DPlusReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				sendConnectLinkPacket(entry);

			entry.setConnectionRequest(ConnectionRequest.Nothing);

			entry.getStateTimeKeeper().updateTimestamp();
		}
		else if(entry.getStateTimeKeeper().isTimeout(4, TimeUnit.SECONDS)){
			switch(entry.getConnectionDirection()) {
			case OUTGOING:
				if(entry.getStateRetryCount() < 1) {
					toWaitState(entry, 100, TimeUnit.MILLISECONDS, DPlusConnectionInternalState.Linking);
					entry.setStateRetryCount(entry.getStateRetryCount() + 1);
				}
				else {
					entry.setStateRetryCount(0);

					addConnectionStateChangeEvent(
						entry.getId(),
						entry.getConnectionDirection(),
						entry.getRepeaterCallsign(),
						entry.getReflectorCallsign(),
						ReflectorConnectionStates.LINKFAILED,
						entry.getOutgoingReflectorHostInfo()
					);

					entry.setNextState(DPlusConnectionInternalState.Unlinked);
				}
				break;

			case INCOMING:
				sendConnectUnlinkPacket(entry);

				entry.setNextState(DPlusConnectionInternalState.Unlinked);
				break;

			default:
				break;
			}
		}
		else if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				entry.setNextState(DPlusConnectionInternalState.Unlinking);
			else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				sendConnectUnlinkPacket(entry);

				entry.setNextState(DPlusConnectionInternalState.Unlinked);
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateLinkEstablished(DPlusReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {
			entry.setStateRetryCount(0);

			entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS);
			entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(transmitKeepAlivePeriodSeconds, TimeUnit.SECONDS);

			if(log.isDebugEnabled())
				log.debug(logHeader + entry.getConnectionDirection().toString() + " link established.\n" + entry.toString(4));
		}
		else if(entry.getReceiveKeepAliveTimeKeeper().isTimeout()) {
			if(entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING)
				entry.setNextState(DPlusConnectionInternalState.Linking);
			else if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				if(log.isInfoEnabled()) {
					log.info(
						logHeader +
						"Incoming connection poll timeout(RepeaterCallsign:" + entry.getRepeaterCallsign() +
						entry.getRemoteAddressPort() + ")"
					);
				}

				entry.setNextState(DPlusConnectionInternalState.Unlinked);
			}
		}
		else if(
			entry.getTransmitKeepAliveTimeKeeper().isTimeout()
		) {
			entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(transmitKeepAlivePeriodSeconds, TimeUnit.SECONDS);
			entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

			sendPollPacket(entry);
		}
		else if(entry.getConnectionRequest() == ConnectionRequest.UnlinkRequest) {
			entry.setNextState(DPlusConnectionInternalState.Unlinking);
		}

		//受信中のフレームがタイムアウトしたか
		if(
			entry.getFrameSequenceTimeKepper().isTimeout() && entry.getRecevingFrameID() != 0x0000
		) {
			addRxEndPacket(entry);

			clearReceiveFrameSequence(entry, true);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateUnlinking(DPlusReflectorEntry entry) {
		assert entry != null;

		if(entry.isStateChanged()) {

			sendConnectUnlinkPacket(entry);
			sendConnectUnlinkPacket(entry);

			entry.getStateTimeKeeper().setTimeoutTime(1, TimeUnit.SECONDS);

			//もし受信中なら終端パケットをゲートウェイ側に投げる
			if(entry.getRecevingFrameID() != 0x0) {
				addRxEndPacket(entry);
				clearReceiveFrameSequence(entry, false);
			}
		}
		else if(entry.getStateTimeKeeper().isTimeout()) {
			if(entry.getStateRetryCount() < 2) {
				toWaitState(entry, 100, TimeUnit.MILLISECONDS, DPlusConnectionInternalState.Unlinking);

				entry.setStateRetryCount(entry.getStateRetryCount() + 1);
			}
			else {
				entry.setNextState(DPlusConnectionInternalState.Unlinked);

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

	private ThreadProcessResult onStateUnlinked(DPlusReflectorEntry entry) {
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

	private ThreadProcessResult onStateWait(DPlusReflectorEntry entry) {
		assert entry != null;

		if(entry.getStateTimeKeeper().isTimeout())
			entry.setNextState(entry.getCallbackState());

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(DPlusReflectorEntry entry, int time, TimeUnit timeUnit, DPlusConnectionInternalState callbackState) {
		assert entry != null && timeUnit != null && callbackState != null;

		if(time < 0) {time = 0;}

		if(time > 0) {
			entry.setNextState(DPlusConnectionInternalState.Wait);
			entry.setCallbackState(callbackState);
			entry.getStateTimeKeeper().setTimeoutTime(time, timeUnit);
		}
		else {
			entry.setNextState(callbackState);
		}
	}

	private void processPoll(final DPlusPacket packet) {
		assert packet != null;

		if(packet.getDPlusPacketType() != DPlusPacketType.POLL) {return;}

		findReflectorEntry(
			packet.getRemoteAddress().getAddress(),
			packet.getRemoteAddress().getPort(),
			packet.getLocalAddress().getPort(),
			DPlusConnectionInternalState.LinkEstablished
		)
		.forEach(new Consumer<DPlusReflectorEntry>() {
			@Override
			public void accept(DPlusReflectorEntry entry) {
				entry.getActivityTimeKepper().updateTimestamp();

				entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS);
				entry.getReceiveKeepAliveTimeKeeper().updateTimestamp();
			}
		});
	}

	private void processConnect(final DPlusPacket packet) {
		assert packet != null;

		if(packet.getDPlusPacketType() != DPlusPacketType.CONNECT) {return;}

		final DPlusConnect connect = packet.getConnect();

		findReflectorEntry(
			packet.getRemoteAddress().getAddress(),
			packet.getRemoteAddress().getPort(),
			packet.getLocalAddress().getPort()
		)
		.findFirst()
		.ifPresentOrElse(new Consumer<DPlusReflectorEntry>() {
			@Override
			public void accept(DPlusReflectorEntry entry) {
				processConnect(entry, packet);

				entry.getActivityTimeKepper().updateTimestamp();
			}
		}, new Runnable() {
			@Override
			public void run() {
				if(
					!isIncomingLink() ||
					connect.getType() != ReflectorConnectTypes.LINK
				) {return;}
/*
				final Optional<DPlusReflectorEntry> duplicateEntry =
					findReflectorEntry(
						packet.getRemoteAddress().getAddress(),
						packet.getRemoteAddress().getPort(),
						packet.getLocalAddress().getPort()
					)
					.findFirst();
				if(duplicateEntry.isPresent()) {return;}
*/
				final DPlusReflectorEntry entry = new DPlusReflectorEntry(
					generateLoopBlockID(),
					10,
					packet.getRemoteAddress(),
					packet.getLocalAddress(),
					ConnectionDirectionType.INCOMING
				);
				entry.setReflectorCallsign(DSTARDefines.EmptyLongCallsign);
				entry.setRepeaterCallsign(DSTARDefines.EmptyLongCallsign);
				entry.setCurrentState(DPlusConnectionInternalState.Initialize);
				entry.setNextState(DPlusConnectionInternalState.Linking);
				entry.setConnectionRequest(ConnectionRequest.Nothing);
				entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(
					transmitKeepAlivePeriodSeconds, TimeUnit.SECONDS
				);
				entry.setModCode(getModCode());

				entry.getActivityTimeKepper().updateTimestamp();

				if(getMaxIncomingLink() <= countLinkEntry(ConnectionDirectionType.INCOMING)) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Reached incomming link limit, ignore incomming link request from " +
							entry.getRemoteAddressPort() + "."
						);
					}

					sendConnectNakPacket(entry);

					return;
				}

				addEntry(entry);

				sendConnectLinkPacket(entry);
			}
		});
	}

	private void processConnect(DPlusReflectorEntry entry, DPlusPacket packet) {
		assert entry != null && packet != null;

		if(packet.getDPlusPacketType() != DPlusPacketType.CONNECT) {return;}

		if(
			!entry.getRemoteAddressPort().getAddress().equals(packet.getRemoteAddress().getAddress()) ||
			entry.getRemoteAddressPort().getPort() != packet.getRemoteAddress().getPort() ||
			entry.getLocalAddressPort().getPort() != packet.getLocalAddress().getPort()
		) {return;}

		DPlusConnect connect = packet.getConnect();

		if(log.isTraceEnabled())
			log.trace(logHeader + connect.getType().toString() + "packet received.\n" + entry.toString(4));

		switch(entry.getConnectionDirection()) {
		case OUTGOING:
			switch(connect.getType()) {
			case ACK:
				if(entry.getCurrentState() == DPlusConnectionInternalState.Linking) {
					entry.setNextState(DPlusConnectionInternalState.LinkEstablished);

					entry.setConnectionRequest(ConnectionRequest.Nothing);

					entry.setReadonly(connect.isReadonly());
					if(connect.isReadonly()) {
						if(log.isInfoEnabled())
							log.info(logHeader + "Login callsign is not registered, Transmit is not possible.");
					}

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
				if(entry.getCurrentState() == DPlusConnectionInternalState.Linking) {
					entry.setNextState(DPlusConnectionInternalState.Unlinked);

					addConnectionStateChangeEvent(
						entry.getId(),
						entry.getConnectionDirection(),
						entry.getRepeaterCallsign(),
						entry.getReflectorCallsign(),
						ReflectorConnectionStates.LINKFAILED,
						entry.getOutgoingReflectorHostInfo()
					);
				}
				break;

			case UNLINK:
				if(entry.getCurrentState() == DPlusConnectionInternalState.Unlinking) {
					entry.setNextState(DPlusConnectionInternalState.Unlinked);

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

			case LINK:
				sendConnectLink2Packet(entry, getLoginCallsign());
				break;

			default:
				break;
			}
			break;

		case INCOMING:
			switch(connect.getType()) {
			case LINK2:
				if(entry.getCurrentState() == DPlusConnectionInternalState.Linking) {
					entry.setRepeaterCallsign(
						DSTARUtils.formatFullLengthCallsign(connect.getCallsign())
					);

					if(
						getReflectorLinkManager()
							.isAllowReflectorIncomingConnectionWithRemoteRepeater(entry.getRepeaterCallsign())
					) {
						sendConnectAckPacket(entry);

						entry.getReceiveKeepAliveTimeKeeper().setTimeoutTime(
							receiveKeepAliveTimeoutSeconds, TimeUnit.SECONDS
						);
						entry.getReceiveKeepAliveTimeKeeper().updateTimestamp();

						entry.getTransmitKeepAliveTimeKeeper().setTimeoutTime(
							transmitKeepAlivePeriodSeconds, TimeUnit.SECONDS
						);
						entry.getTransmitKeepAliveTimeKeeper().updateTimestamp();

						entry.setNextState(DPlusConnectionInternalState.LinkEstablished);

						onIncomingConnectionConnected(
							entry.getRemoteAddressPort(), DSTARDefines.EmptyLongCallsign, entry.getRepeaterCallsign()
						);
					}
					else {
						sendConnectNakPacket(entry);

						addEntryRemoveRequestQueue(entry.getId());

						if(log.isInfoEnabled()) {
							log.info(
								logHeader +
								"Denied connection from repeater = " + entry.getRepeaterCallsign() + "@" +
								entry.getRemoteAddressPort() + ", It's listed the reflector callsign at black list."
							);
						}
					}
				}

				break;

			case UNLINK:
				sendConnectUnlinkPacket(entry);

				entry.setNextState(DPlusConnectionInternalState.Unlinked);

				break;

			default:
				break;
			}
			break;

		default:
			break;
		}

	}

	private void processHeader(final DPlusPacket packet) {
		assert packet != null && packet.getDPlusPacketType() == DPlusPacketType.HEADER;

		entriesLocker.lock();
		try {
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			)
			.findFirst()
			.ifPresent(new Consumer<DPlusReflectorEntry>() {
				@Override
				public void accept(DPlusReflectorEntry entry) {
					processHeader(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processHeader(final DPlusReflectorEntry entry, final DPlusPacket packet) {
		assert entry != null && packet != null && packet.getDPlusPacketType() == DPlusPacketType.HEADER;

		if(
			entry.getCurrentState() != DPlusConnectionInternalState.LinkEstablished ||
			entry.getRecevingFrameID() != 0x0 ||
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDvPacket().hasPacketType(PacketType.Header)
		) {return;}

		DSTARRepeater destinationRepeater = null;
		if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
			destinationRepeater = getGateway().getRepeater(String.valueOf(packet.getRepeater1Callsign()));
			if(destinationRepeater == null)
				destinationRepeater = getGateway().getRepeater(String.valueOf(packet.getRepeater2Callsign()));

			if(
				destinationRepeater == null ||
				//対象のレピータがIncoming接続を許可していなければ、ドロップさせる
				!getReflectorLinkManager()
					.isAllowReflectorIncomingConnectionWithLocalRepeater(destinationRepeater.getRepeaterCallsign())
			) {return;}


		}
		else if(
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
			!entry.getReflectorCallsign().equals(
				String.valueOf(packet.getRfHeader().getRepeater1Callsign())
			) &&
			!entry.getReflectorCallsign().equals(
				String.valueOf(packet.getRfHeader().getRepeater2Callsign())
			)
		) {
			return;
		}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		packet.setLoopblockID(entry.getLoopBlockID());

		//経路情報を保存
		packet.getRfHeader().setSourceRepeater2Callsign(
			entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
				entry.getReflectorCallsign() : entry.getRepeaterCallsign()
		);

		switch(entry.getConnectionDirection()) {
		case OUTGOING:
			addRxPacket(
				packet,
				entry.getRepeaterCallsign(),
				entry
			);
			break;

		case INCOMING:
			addRxPacket(
				packet,
				destinationRepeater.getRepeaterCallsign(),
				entry
			);

			entry.setReflectorCallsign(destinationRepeater.getRepeaterCallsign());
			break;

		default:
			throw new RuntimeException();
		}

		entry.setRecevingHeader(packet.clone());

		entry.setRecevingFrameID(packet.getBackBone().getFrameIDNumber());
		entry.setReceivingFrameSequence((byte)0x0);

		if(log.isDebugEnabled()) {
			log.debug(
				String.format(
					"%s Start receive frame 0x%04X.\n%s", logHeader, entry.getRecevingFrameID(), packet.toString(4)
				)
			);
		}

		entry.getFrameSequenceTimeKepper().setTimeoutTime(1, TimeUnit.SECONDS);
		entry.getFrameSequenceTimeKepper().updateTimestamp();
	}

	private void processVoice(final DPlusPacket packet) {
		assert packet != null && packet.getDPlusPacketType() == DPlusPacketType.VOICE;

		entriesLocker.lock();
		try {
			findReflectorEntry(
				packet.getRemoteAddress().getAddress(),
				packet.getRemoteAddress().getPort(),
				packet.getLocalAddress().getPort()
			)
			.forEach(new Consumer<DPlusReflectorEntry>() {
				@Override
				public void accept(DPlusReflectorEntry entry) {
					processVoice(entry, packet);

					entry.getActivityTimeKepper().updateTimestamp();
				}
			});
		}finally {
			entriesLocker.unlock();
		}
	}

	private void processVoice(DPlusReflectorEntry entry, final DPlusPacket packet) {
		if(
			entry.getCurrentState() != DPlusConnectionInternalState.LinkEstablished ||
			entry.getRecevingFrameID() == 0x0 ||
			packet.getPacketType() != DSTARPacketType.DV ||
			!packet.getDvPacket().hasPacketType(PacketType.Voice)
		) {return;}

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		packet.setConnectionDirection(entry.getConnectionDirection());
		packet.setLoopblockID(entry.getLoopBlockID());

		packet.getDVPacket().setRfHeader(entry.getRecevingHeader().getRFHeader());
		packet.getDVPacket().setPacketType(PacketType.Header, PacketType.Voice);

		if(entry.getRecevingFrameID() == packet.getBackBone().getFrameIDNumber()) {

			DSTARRepeater destinationRepeater = null;
			if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
				destinationRepeater =
					getGateway().getRepeater(
						String.valueOf(entry.getRecevingHeader().getRFHeader().getRepeater1Callsign())
					);
			if(destinationRepeater == null)
				destinationRepeater = getGateway().getRepeater(
					String.valueOf(entry.getRecevingHeader().getRFHeader().getRepeater2Callsign())
				);
			}

			if(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ||
				(
					entry.getConnectionDirection() == ConnectionDirectionType.INCOMING &&
					destinationRepeater != null
				)
			) {
				addRxPacket(
					packet,
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
						entry.getRepeaterCallsign() : destinationRepeater.getRepeaterCallsign(),
					entry
				);
			}

			if(
				!packet.isLastFrame() &&
				packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber &&
				entry.getRecevingHeader() != null
			) {
				switch(entry.getConnectionDirection()) {
				case OUTGOING:
					addRxPacket(
						entry.getRecevingHeader().clone(),
						entry.getRepeaterCallsign(),
						entry
					);
					break;

				case INCOMING:
					if(destinationRepeater != null) {
						addRxPacket(
							entry.getRecevingHeader().clone(),
							destinationRepeater.getRepeaterCallsign(),
							entry
						);
					}
					break;

				default:
					return;
				}
			}

			entry.setReceivingFrameSequence(packet.getBackBone().getSequenceNumber());

			if(packet.getBackBone().isEndSequence()) {
				clearReceiveFrameSequence(entry, false);
			}

			entry.getFrameSequenceTimeKepper().updateTimestamp();
		}
	}

	private void clearReceiveFrameSequence(final DPlusReflectorEntry entry, final boolean timeout) {
		assert entry != null;

		if(log.isDebugEnabled()) {
			log.debug(
				String.format(
					"%s Clear sequence receive frame 0x%04X%s.",
					logHeader, entry.getRecevingFrameID(), timeout ? "[TIMEOUT]" : ""
				)
			);
		}

		entry.setRecevingFrameID(0x0);
		entry.setReceivingFrameSequence((byte)0x0);
		entry.setRecevingHeader(null);
	}

	private boolean sendConnectLinkPacket(DPlusReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.LINK, null);
	}

	private boolean sendConnectLink2Packet(DPlusReflectorEntry entry, String callsign) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.LINK2, callsign);
	}

	private boolean sendConnectUnlinkPacket(DPlusReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.UNLINK, null);
	}

	private boolean sendConnectAckPacket(DPlusReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.ACK, null);
	}

	private boolean sendConnectNakPacket(DPlusReflectorEntry entry) {
		assert entry != null;

		return sendConnectPacket(entry, ReflectorConnectTypes.NAK, null);
	}

	private boolean sendConnectPacket(DPlusReflectorEntry entry, ReflectorConnectTypes type, String callsign) {
		assert entry != null && type != null;

		DPlusConnect connectPacket = new DPlusConnect(type);
		if(callsign != null) {connectPacket.setCallsign(callsign);}

		return sendPacket(entry, new DPlusPacketImpl(connectPacket), false);
	}

	private boolean sendPollPacket(DPlusReflectorEntry entry) {
		assert entry != null;

		DPlusPoll poll = new DPlusPoll();

		return sendPacket(entry, (DPlusPacket)new DPlusPacketImpl(poll), false);
	}

	private boolean addEntry(DPlusReflectorEntry entry) {
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

		return entryRemoveRequestQueue.add(id);
	}

	private void processEntryRemoveRequestQueue() {
		entriesLocker.lock();
		try {
			for(Iterator<UUID> removeIt = entryRemoveRequestQueue.iterator(); removeIt.hasNext();) {
				final UUID removeID = removeIt.next();
				removeIt.remove();

				for(Iterator<DPlusReflectorEntry> refEntryIt = entries.iterator(); refEntryIt.hasNext();) {
					DPlusReflectorEntry refEntry = refEntryIt.next();
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
			entriesLocker.unlock();
		}
	}

	private void cleanupProcessEntry() {
		if(entryCleanupIntervalTimekeeper.isTimeout()) {
			entryCleanupIntervalTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			entryCleanupIntervalTimekeeper.updateTimestamp();

			entriesLocker.lock();
			try {
				for(Iterator<DPlusReflectorEntry> it = entries.iterator(); it.hasNext();) {
					DPlusReflectorEntry refEntry = it.next();
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

	private void finalizeEntry(DPlusReflectorEntry refEntry){
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
			log.debug(logHeader + "Error occurred at channel close.", ex);
		}
	}

	private boolean writeHeader(
		final String repeaterCallsign,
		final DPlusReflectorEntry entry, final DSTARPacket writePacket, final ConnectionDirectionType direction
	) {
		if(
			(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
				!entry.getRepeaterCallsign().equals(repeaterCallsign)
			) ||
			(
				entry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			entry.getCurrentState() != DPlusConnectionInternalState.LinkEstablished ||
			entry.getRecevingFrameID() != 0x0 ||
			entry.getLoopBlockID().equals(writePacket.getLoopblockID())
		) {return false;}

		final DSTARPacket packet = writePacket.clone();

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		switch(entry.getConnectionDirection()) {
		case OUTGOING:
			String loginCallsign = DSTARUtils.formatFullLengthCallsign(getLoginCallsign());
			loginCallsign =
				loginCallsign.substring(0, DSTARDefines.CallsignFullLength - 1) +
				entry.getRepeaterCallsign().charAt(DSTARDefines.CallsignFullLength - 1);

			packet.getRfHeader().setRepeater1Callsign(loginCallsign.toCharArray());
			packet.getRfHeader().setRepeater2Callsign(entry.getReflectorCallsign().toCharArray());
			break;

		case INCOMING:
			packet.getRfHeader().setRepeater1Callsign(repeaterCallsign.toCharArray());
			packet.getRfHeader().setRepeater2Callsign(repeaterCallsign.toCharArray());
			break;

		default:
			return false;
		}

		packet.getBackBone().setDestinationRepeaterID((byte)0x00);
		packet.getBackBone().setSendRepeaterID((byte)0x01);
		packet.getBackBone().setSendTerminalID((byte)0x02);

		DPlusTransmitFrameEntry frameEntry =
			entry.getTransmitterFrameEntries().get(packet.getBackBone().getFrameIDNumber());
		if(frameEntry == null) {
			frameEntry =
				new DPlusTransmitFrameEntry(packet.getBackBone().getFrameIDNumber(), packet.clone());

			entry.getTransmitterFrameEntries().put(packet.getBackBone().getFrameIDNumber(), frameEntry);

			frameEntry.getCacheTransmitter().reset();

			frameEntry.getCacheTransmitter().inputWrite(
				new DPlusTransmitPacketEntry(
					PacketType.Header,
					packet,
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
						entry.getOutgoingChannel() : incomingChannel,
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					false,
					FrameSequenceType.Start
				)
			);
		}
		else {
			return true;
		}

		frameEntry.getActivityTimestamp().updateTimestamp();

		if(log.isDebugEnabled())
			log.debug(String.format("%s Start transmit frame 0x%04X.\n%s", logHeader, packet.getBackBone().getFrameIDNumber(), packet.toString(4)));

		return true;
	}

	private boolean writeVoice(
		final String repeaterCallsign,
		final DPlusReflectorEntry entry, final DSTARPacket writePacket, final ConnectionDirectionType direction
	) {
		if(
			(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING &&
				!entry.getRepeaterCallsign().equals(repeaterCallsign)
			) ||
			(
				entry.getConnectionDirection() != direction &&
				direction != ConnectionDirectionType.BIDIRECTIONAL
			) ||
			entry.getCurrentState() != DPlusConnectionInternalState.LinkEstablished ||
			entry.getLoopBlockID().equals(writePacket.getLoopblockID())
		) {return false;}

		final DSTARPacket packet = writePacket.clone();

		//フレームIDを改変する
		packet.getBackBone().modFrameID(entry.getModCode());

		packet.getBackBone().setDestinationRepeaterID((byte)0x00);
		packet.getBackBone().setSendRepeaterID((byte)0x01);
		packet.getBackBone().setSendTerminalID((byte)0x02);

		final DPlusTransmitFrameEntry frameEntry =
			entry.getTransmitterFrameEntries().get(packet.getBackBone().getFrameIDNumber());
		if(frameEntry == null) {return false;}

		frameEntry.getCacheTransmitter().inputWrite(
			new DPlusTransmitPacketEntry(
				PacketType.Voice,
				packet,
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				false,
				packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
			)
		);
		frameEntry.setPacketCount(
			frameEntry.getPacketCount() < Long.MAX_VALUE ? frameEntry.getPacketCount() + 1 : frameEntry.getPacketCount()
		);

		if(
			!packet.isLastFrame() &&
			packet.getBackBone().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
		) {
			frameEntry.getCacheTransmitter().inputWrite(
				new DPlusTransmitPacketEntry(
					PacketType.Header,
					frameEntry.getHeader().clone(),
					entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
						entry.getOutgoingChannel() : incomingChannel,
					entry.getRemoteAddressPort().getAddress(),
					entry.getRemoteAddressPort().getPort(),
					true,
					FrameSequenceType.None
				)
			);

			frameEntry.setPacketCount(
				frameEntry.getPacketCount() < Long.MAX_VALUE ? frameEntry.getPacketCount() + 1 : frameEntry.getPacketCount()
			);
		}

		frameEntry.setFrameSequence(packet.getBackBone().getSequenceNumber());
		frameEntry.getActivityTimestamp().updateTimestamp();

		if(packet.isEndVoicePacket() && log.isDebugEnabled())
			log.debug(String.format("%s End of transmit frame 0x%04X.", logHeader, packet.getBackBone().getFrameIDNumber()));

		return true;
	}

	private boolean sendPacket(DPlusReflectorEntry entry, DPlusPacket packet, boolean oneShot) {
		assert entry != null && packet != null;

		byte[] buffer = null;
		int txPackets = 0;

		switch(packet.getDPlusPacketType()) {
		case CONNECT:
			txPackets = 1;
			final Optional<byte[]> connectPacket = DPlusPacketImpl.assembleConenctPacket(packet);
			if(connectPacket.isPresent())
				buffer = connectPacket.get();
			else
				return false;
			break;

		case POLL:
			txPackets = 1;
			final Optional<byte[]> pollPacket = DPlusPacketImpl.assemblePollPacket(packet);
			if(pollPacket.isPresent())
				buffer = pollPacket.get();
			else
				return false;
			break;

		case HEADER:
			final Optional<byte[]> headerVoicePacket = DPlusPacketImpl.assembleHeaderPacket(packet);
			txPackets = 5;
			if(headerVoicePacket.isPresent())
				buffer = headerVoicePacket.get();
			else
				return false;
			break;

		case VOICE:
			txPackets = 1;
			final Optional<byte[]> voicePacket = DPlusPacketImpl.assembleVoicePacket(packet);
			if(voicePacket.isPresent())
				buffer = voicePacket.get();
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
			if(log.isErrorEnabled())
				log.error(logHeader + "destination channel is null.\n" + entry.toString(4));

			return false;
		}

		if(oneShot) {txPackets = 1;}

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"Send packet to " + entry.getRemoteAddressPort() + ", repeat = " + txPackets + " counts,\n" +
				packet.toString(4) + "\n" + FormatUtil.bytesToHexDump(buffer, 4)
			);
		}

		boolean result = true;
		for(int cnt = 0; cnt < txPackets; cnt++) {
			if(
				!super.writeUDPPacket(dstChannel.getKey(), entry.getRemoteAddressPort(), ByteBuffer.wrap(buffer))
			) {result = false;}
		}

		return result;
	}

	private boolean addRxEndPacket(final DPlusReflectorEntry entry) {
		if(entry.getConnectionDirection() == ConnectionDirectionType.INCOMING) {
			final DSTARRepeater destinationRepeater =
				getGateway().getRepeater(entry.getReflectorCallsign());

			if(destinationRepeater == null) {return false;}
		}

		final ReflectorReceivePacket endPacket =
			new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				createPreLastVoicePacket(
					entry, entry.getRecevingFrameID(), entry.getReceivingFrameSequence(),
					entry.getRecevingHeader().getRFHeader()
				)
			);

		final ReflectorReceivePacket lastPacket =
			new ReflectorReceivePacket(
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getRepeaterCallsign() : entry.getReflectorCallsign(),
				createLastVoicePacket(
					entry,
					entry.getRecevingFrameID(),
					DSTARUtils.getNextShortSequence(entry.getReceivingFrameSequence()),
					entry.getRecevingHeader().getRFHeader()
				)
			);

		if(log.isDebugEnabled())
			log.debug(logHeader + "Inserted end packet.\n" + lastPacket);

		return addReflectorReceivePacket(endPacket) && addReflectorReceivePacket(lastPacket);
	}

	private boolean addTxEndPacket(
		final DPlusReflectorEntry entry, final DPlusTransmitFrameEntry frameEntry
	) {
		frameEntry.getCacheTransmitter().inputWrite(
			new DPlusTransmitPacketEntry(
				PacketType.Voice,
				createPreLastVoicePacket(
					entry, frameEntry.getFrameID(), frameEntry.getFrameSequence()
				),
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				false,
				FrameSequenceType.None
			)
		);

		frameEntry.getCacheTransmitter().inputWrite(
			new DPlusTransmitPacketEntry(
				PacketType.Voice,
				createLastVoicePacket(
					entry, frameEntry.getFrameID(),
					DSTARUtils.getNextShortSequence(frameEntry.getFrameSequence())
				),
				entry.getConnectionDirection() == ConnectionDirectionType.OUTGOING ?
					entry.getOutgoingChannel() : incomingChannel,
				entry.getRemoteAddressPort().getAddress(),
				entry.getRemoteAddressPort().getPort(),
				false,
				FrameSequenceType.End
			)
		);

		return true;
	}

	private boolean addRxPacket(
		final DSTARPacket header,
		final String destinationRepeaterCallsign,
		final DPlusReflectorEntry entry
	) {
		header.setConnectionDirection(entry.getConnectionDirection());
		header.getBackBone().setFrameType(BackBoneHeaderFrameType.VoiceDataHeader);
		header.getRfHeader().setYourCallsign(DSTARDefines.CQCQCQ.toCharArray());

		final ReflectorReceivePacket packet =
			new ReflectorReceivePacket(
				destinationRepeaterCallsign,
				header
			);

		if(log.isTraceEnabled())
			log.trace(logHeader + "Added received header packet.\n" + header.toString(4));

		return addReflectorReceivePacket(packet);

	}
/*
	private boolean addRxPacket(
		final VoiceData voice, final Header header, final BackBoneHeader backbone,
		final DPlusReflectorEntry entry,
		final String destinationRepeaterCallsign
	) {
		assert voice != null && entry != null;


			final DvPacket voicePacket = new DvPacket(voice, DStarProtocol.DPlus);
			voicePacket.setRfHeader(header);
			voicePacket.setBackBone(backbone);
			voicePacket.setConnectionDirection(entry.getConnectionDirection());

			final ReflectorReceivePacket dcsPacket =
				new ReflectorReceivePacket(
//					entry.getRepeaterCallsign(),
					destinationRepeaterCallsign,
					voicePacket
				);

			if(log.isTraceEnabled())
				log.trace(logHeader + "Added received voice packet.\n" + voicePacket.toString(4));

			return addReflectorReceivePacket(dcsPacket);
	}
*/
	@SuppressWarnings("unused")
	private Stream<DPlusReflectorEntry> findReflectorEntry(){
		return findReflectorEntry(null, -1, -1, null, null, null, null);
	}

	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, null, null, null);
	}

	@SuppressWarnings("unused")
	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, direction, null, null, null);
	}

	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, direction, repeaterCallsign, reflectorCallsign, null);
	}

	@SuppressWarnings("unused")
	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, repeaterCallsign, reflectorCallsign, null);
	}

	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final DPlusConnectionInternalState currentState
	){
		return findReflectorEntry(remoteAddress, remotePort, localPort, null, null, null, currentState);
	}

	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign
	){
		return findReflectorEntry(null, -1, -1, direction, repeaterCallsign, reflectorCallsign, null);
	}

	private Stream<DPlusReflectorEntry> findReflectorEntry(
		final InetAddress remoteAddress, final int remotePort, final int localPort,
		final ConnectionDirectionType direction,
		final String repeaterCallsign,
		final String reflectorCallsign,
		final DPlusConnectionInternalState currentState
	){
		entriesLocker.lock();
		try {
			Stream<DPlusReflectorEntry> result =
				Stream.of(entries)
				.filter(new Predicate<DPlusReflectorEntry>() {
					@Override
					public boolean test(DPlusReflectorEntry entry) {
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

	private boolean parsePacket(Queue<DPlusPacket> receivePackets) {
		assert receivePackets != null;

		boolean update = false;

		Optional<BufferEntry> opEntry = null;
		while((opEntry = getReceivedReadBuffer()).isPresent()) {
			final BufferEntry buffer = opEntry.get();

			buffer.getLocker().lock();
			try {
				if(!buffer.isUpdate()) {continue;}

				buffer.setBufferState(
					BufferState.toREAD(
						buffer.getBuffer(), buffer.getBufferState()
					)
				);

				for (
					Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator();
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

					if(authOpenDStar != null)
						authOpenDStar.readEvent(buffer.getLocalAddress(), buffer.getRemoteAddress(), receivePacket);

					if(authDutchStar != null)
						authDutchStar.readEvent(buffer.getLocalAddress(), buffer.getRemoteAddress(), receivePacket);

					receivePacket.rewind();

					boolean match = false;
					Optional<DPlusPacket> validPacket = null;
					do {
						if (
							(validPacket = DPlusPacketImpl.isValidConnectPacket(receivePacket)).isPresent() ||
							(validPacket = DPlusPacketImpl.isValidPollPacket(receivePacket)).isPresent() ||
							(validPacket = DPlusPacketImpl.isValidHeaderPacket(receivePacket)).isPresent() ||
							(validPacket = DPlusPacketImpl.isValidVoicePacket(receivePacket)).isPresent()
						) {
							DPlusPacket copyPacket = validPacket.get();

							copyPacket.setRemoteAddress(buffer.getRemoteAddress());
							copyPacket.setLocalAddress(buffer.getLocalAddress());

							if(copyPacket.getDPlusPacketType() == DPlusPacketType.HEADER) {
								String repeater1Callsign =
									String.valueOf(copyPacket.getRepeater1Callsign());
								if(repeater1Callsign.startsWith("XRF")) {
									copyPacket.getRfHeader().setRepeater1Callsign(
										repeater1Callsign.replace("XRF", "REF").toCharArray()
									);
								}

								String repeater2Callsign =
									String.valueOf(copyPacket.getRepeater2Callsign());
								if(repeater2Callsign.startsWith("XRF")) {
									copyPacket.getRfHeader().setRepeater2Callsign(
										repeater2Callsign.replace("XRF", "REF").toCharArray()
									);
								}
							}

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
			for(Iterator<DPlusReflectorEntry> it = entries.iterator(); it.hasNext(); ) {
				DPlusReflectorEntry refEntry = it.next();

				finalizeReflectorEntry(refEntry);

				it.remove();
			}
		}finally {
			entriesLocker.unlock();
		}
	}

	private void finalizeReflectorEntry(DPlusReflectorEntry refEntry){
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
				.filter(new Predicate<DPlusReflectorEntry>() {
					@Override
					public boolean test(DPlusReflectorEntry entry) {
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
			.filter(new Predicate<DPlusReflectorEntry>() {
				@Override
				public boolean test(DPlusReflectorEntry value) {
					return
						(
							repeater == null ||
							value.getDestinationRepeater() == repeater
						) &&
						(
							value.getCurrentState() == DPlusConnectionInternalState.Linking ||
							value.getCurrentState() == DPlusConnectionInternalState.LinkEstablished
						) &&
						(connectionDirection == null || value.getConnectionDirection() == connectionDirection);
				}
			})
			.map(new Function<DPlusReflectorEntry, ReflectorLinkInformation>(){
				@Override
				public ReflectorLinkInformation apply(DPlusReflectorEntry t) {
					return new ReflectorLinkInformation(
						t.getId(),
						t.getReflectorCallsign(),
						DSTARProtocol.DPlus,
						t.getDestinationRepeater(),
						t.getConnectionDirection(),
						false,
						t.getCurrentState() == DPlusConnectionInternalState.LinkEstablished,
						t.getRemoteAddressPort().getAddress(),
						t.getRemoteAddressPort().getPort(),
						t.getOutgoingReflectorHostInfo()
					);
				}
			}).toList();
		}finally {entriesLocker.unlock();}
	}
}

