package org.jp.illg.dstar.repeater.modem.icomap;


import java.nio.ByteBuffer;
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

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.model.defines.RepeaterRoute;
import org.jp.illg.dstar.model.defines.VoiceCodecType;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemBase;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemEvent;
import org.jp.illg.dstar.repeater.modem.icomap.model.AccessPointCommand;
import org.jp.illg.dstar.repeater.modem.icomap.model.HeartbeatCommand;
import org.jp.illg.dstar.repeater.modem.icomap.model.InitializeCommand;
import org.jp.illg.dstar.repeater.modem.icomap.model.VoiceData;
import org.jp.illg.dstar.repeater.modem.icomap.model.VoiceDataHeader;
import org.jp.illg.dstar.repeater.modem.icomap.model.VoiceDataHeaderToRigResponse;
import org.jp.illg.dstar.repeater.modem.icomap.model.VoiceDataToRigResponse;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlNewAccessPointHandler;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.service.web.model.NewAccessPointStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.DataSegmentEncoder;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.RepairCacheTransporter;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.PerformanceTimer;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;
import org.jp.illg.util.uart.UartInterface;
import org.jp.illg.util.uart.UartInterfaceFactory;
import org.jp.illg.util.uart.UartInterfaceType;
import org.jp.illg.util.uart.model.UartFlowControlModes;
import org.jp.illg.util.uart.model.UartParityModes;
import org.jp.illg.util.uart.model.UartStopBitModes;
import org.jp.illg.util.uart.model.events.UartEvent;
import org.jp.illg.util.uart.model.events.UartEventListener;
import org.jp.illg.util.uart.model.events.UartEventType;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
* @author AHND
*
*/
@Slf4j
public class NewAccessPointInterface extends DStarRepeaterModemBase
	implements WebRemoteControlNewAccessPointHandler
{

	private static final int inetHeaderCacheLimit = 8;

	private static final int retryLimit = 2;

	private class HeaderCommandCache{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private int frameID;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTimestamp;

		@Getter
		private final Timer activityTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private AccessPointCommand voiceHeader;

		private HeaderCommandCache() {
			super();

			activityTimestamp = new Timer();
		}

		public HeaderCommandCache(int frameID, AccessPointCommand voiceHeader) {
			this();
			setFrameID(frameID);
			setCreatedTimestamp(System.currentTimeMillis());
			updateActivityTimestamp();
			setVoiceHeader(voiceHeader);
		}

		public void updateActivityTimestamp() {
			getActivityTimestamp().updateTimestamp();
		}
	}

	private static enum CommunicationState{
		INITIALIZE,
		PORT_OPEN,
		INITIALIZE_CMD,
		WAIT_MAIN,
		WAIT_HB_CMD,
		SEND_VOICE_TO_RIG,
		RECV_VOICE_FROM_RIG,
		PORT_ERROR,
		TIME_WAIT,
		;
	}

	private static enum GatewayMode{
		Unknown,
		TerminalMode,
		AccessPointMode,
	}

	/**
	 * 無線機と接続されているシリアルポート名
	 */
	@Getter
	@Setter
	private String rigPortName = "";
	private static final String rigPortNameDefault = "";
	public static final String rigPortNamePropertyName = "PortName";


	/**
	 * 無線機データビットレート(bps)
	 */
	@Getter
	@Setter
	private int rigBitRate = 38400;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UartInterfaceType uartType;
	public static final String uartTypePropertyName = "UartType";
	private static final UartInterfaceType uartTypeDefault = UartInterfaceType.Serial;

	@Getter
	@Setter
	private boolean enableCodeSquelch;
	private static final boolean enableCodeSquelchDefault = false;
	public static final String enableCodeSquelchPropertyName = "EnableCodeSquelch";

	@Getter
	@Setter
	private byte codeSquelchCode;
	private static final byte codeSquelchCodeDefault = (byte)0x00;
	public static final String codeSquelchCodePropertyName = "CodeSquelchCode";

	@Getter
	@Setter
	private boolean enablePacketSlip;
	private static final boolean enablePacketSlipDefault = true;
	public static final String enablePacketSlipPropertyName = "EnablePacketSlip";

	@Getter
	@Setter
	private int packetSlipLimit;
	private static final int packetSlipLimitDefault = 20;
	public static final String packetSlipLimitPropertyName = "PacketSlipLimit";

	@Getter
	@Setter
	private boolean disableSlowDataToInet;
	private static final boolean disableSlowDataToInetDefault = false;
	public static final String disableSlowDataToInetPropertyName = "DisableSlowDataToInet";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean blockDIRECT;
	private static final boolean blockDIRECTDefault = false;
	public static final String blockDIRECTPropertyName = "BlockDIRECT";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean ignoreResponse;
	private static final boolean ignoreResponseDefault = false;
	public static final String ignoreResponsePropertyName = "IgnoreResponse";

	private String logHeader;

	/**
	 * 無線機データポートIF
	 */
	private UartInterface rigDataPort;

	private int rigDataPortErrorCount;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DataSegmentDecoder dataSegmentDecoder;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DataSegmentEncoder dataSegmentEncoder;

	private int voicePacketCounter;
	private int voicePacketBackboneSequence;

	private int voicePacketSlipCounter;

	private PerformanceTimer performanceCounter;
	private int packetCounter;

	private int currentFrameID;

	private int retryCount;

	private final UUID loopBlockID;

	private int currentWriteFrameID;
	private final Timer writeFrameTimekeeper;

	private ByteBuffer recvBuffer;
	private BufferState recvBufferState;
	private final Timer recvTimestamp;

	private final InitializeCommand initializeCommand;
	private final HeartbeatCommand heartbeatCommand;
	private final VoiceDataHeader voiceDataHeader;
	private final VoiceData voiceData;
	private final VoiceDataHeaderToRigResponse voiceDataHeaderToRigResponse;
	private final VoiceDataToRigResponse voiceDataToRigResponse;

	private final Queue<AccessPointCommand> sendCommandQueue;
	private final List<AccessPointCommand> recvCommands;

	private final Map<Integer, HeaderCommandCache> inetHeaderCaches;

	private final Lock stateLocker;

	private CommunicationState callbackState;
	private CommunicationState currentState;
	private CommunicationState nextState;
	private final Timer stateTimeKeeper;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	private GatewayMode gatewayMode;

	private AccessPointCommand lastSendCommand;

	private boolean codeSquelchReceived;
	private byte receivedCodeSquelchCode;

	private DSTARPacket currentReceivingHeader;

	private final RepairCacheTransporter<TransmitterPacket> transporter;
	private final Lock transporterLocker;

	private final TaskQueue<UartEvent, Boolean> uartEventQueue;

	private final UartEventListener uartEventListener = new UartEventListener() {
		@Override
		public UartEventType getLinteningEventType() {
			return UartEventType.DATA_AVAILABLE;
		}

		@Override
		public void uartEvent(UartEvent event) {
			uartEventQueue.addEventQueue(uartEvent, event, getExceptionListener());
		}
	};

	private final Function<UartEvent, Boolean> uartEvent = new Function<UartEvent, Boolean>(){
		@Override
		public Boolean apply(UartEvent event) {
			try {
				synchronized(recvBuffer) {
					final BufferUtilObject putResult =
						BufferUtil.putBuffer(
							logHeader,
							recvBuffer, recvBufferState, recvTimestamp,
							event.getReceiveData()
						);
					recvBufferState = putResult.getBufferState();
				}
			}catch(Exception ex) {
				if(log.isErrorEnabled())
					log.error(logHeader + "Failed uart event handle.", ex);
			}

			processModem();

			return true;
		}
	};

	{
		initializeCommand =
			new InitializeCommand();
		heartbeatCommand =
			new HeartbeatCommand();
		voiceDataHeader =
			new VoiceDataHeader();
		voiceData =
			new VoiceData();
		voiceDataHeaderToRigResponse =
			new VoiceDataHeaderToRigResponse();
		voiceDataToRigResponse =
			new VoiceDataToRigResponse();
	}

	/**
	 * コンストラクタ
	 */
	public NewAccessPointInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		this(exceptionListener, workerExecutor, gateway, repeater, eventListener, null);
	}

	public NewAccessPointInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			NewAccessPointInterface.class.getSimpleName(),
			workerExecutor,
			ModemTypes.getTypeByClassName(NewAccessPointInterface.class.getName()),
			gateway,
			repeater,
			eventListener,
			socketIO
		);

		logHeader = this.getClass().getSimpleName() + " : ";

		uartEventQueue = new TaskQueue<>(getWorkerExecutor());

		stateLocker = new ReentrantLock();

		rigDataPortErrorCount = 0;

		enableCodeSquelch = enableCodeSquelchDefault;
		codeSquelchCode = codeSquelchCodeDefault;
		codeSquelchReceived = false;
		receivedCodeSquelchCode = (byte)0x00;

		currentReceivingHeader = null;

		enablePacketSlip = enablePacketSlipDefault;
		packetSlipLimit = packetSlipLimitDefault;

		recvBuffer = ByteBuffer.allocate(2048);
		recvBufferState = BufferState.INITIALIZE;
		recvTimestamp = new Timer(1, TimeUnit.SECONDS);

		gatewayMode = GatewayMode.Unknown;

		setDataSegmentDecoder(new DataSegmentDecoder());
		setDataSegmentEncoder(new DataSegmentEncoder());

		voicePacketCounter = 0;
		voicePacketBackboneSequence = 0;

		voicePacketSlipCounter = 0;

		currentFrameID = 0x0;

		performanceCounter = new PerformanceTimer();
		packetCounter = 0;

		recvCommands = new LinkedList<>();
		recvCommands.clear();
		sendCommandQueue = new LinkedList<>();

		currentState = CommunicationState.INITIALIZE;
		nextState = CommunicationState.INITIALIZE;
		callbackState = CommunicationState.INITIALIZE;
		stateTimeKeeper = new Timer();

		lastSendCommand = null;

		inetHeaderCaches = new HashMap<>();

		retryCount = 0;

		transporter = new RepairCacheTransporter<>(10);
		transporterLocker = new ReentrantLock();

		loopBlockID = UUID.randomUUID();

		currentWriteFrameID = 0x0;
		writeFrameTimekeeper = new Timer();

		setUartType(uartTypeDefault);
		setEnableCodeSquelch(enableCodeSquelchDefault);
		setCodeSquelchCode(codeSquelchCodeDefault);
		setEnablePacketSlip(enablePacketSlipDefault);
		setPacketSlipLimit(packetSlipLimitDefault);
		setDisableSlowDataToInet(disableSlowDataToInetDefault);
		setBlockDIRECT(blockDIRECTDefault);
		setIgnoreResponse(ignoreResponseDefault);
	}

	/**
	 *
	 * @return
	 */
	public boolean start() {
		return super.start();
	}

	/**
	 *
	 */
	public void stop() {
		if(rigDataPort != null) {rigDataPort.closePort();}

		super.stop();
	}

	@Override
	public VoiceCodecType getCodecType() {
		return VoiceCodecType.AMBE;
	}

	@Override
	public ModemTransceiverMode getDefaultTransceiverMode() {
		return ModemTransceiverMode.HalfDuplex;
	}

	@Override
	public ModemTransceiverMode[] getSupportedTransceiverModes() {
		return new ModemTransceiverMode[] {
			ModemTransceiverMode.HalfDuplex,
			ModemTransceiverMode.RxOnly,
			ModemTransceiverMode.TxOnly
		};
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {
		if(log.isInfoEnabled())
			log.info(logHeader + "Notify received " + users.size() + " users from " + remoteCallsign + "@" + protocol);
	}

	@Override
	public boolean initializeWebRemoteControlInt(final WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeModemNewAccessPoint(this);
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		if(isDisableSlowDataToInet())
			log.info(logHeader + "Configulation parameter " + disableSlowDataToInetPropertyName + " is true.");

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {

	}

	@Override
	protected ThreadProcessResult processModem() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		stateLocker.lock();
		try {
			//受信バッファにデータがあれば解析
			analyzeReceiveBuffer();

			boolean reProcess;
			do {
				reProcess = false;

				setStateChanged(currentState != nextState);
				currentState = nextState;

				switch(currentState) {
				case INITIALIZE:
					processResult = onStateInitialize();
					break;

				case PORT_OPEN:
					processResult = onStatePortOpen();
					break;

				case INITIALIZE_CMD:
					processResult = onStateIninializeCommand();
					break;

				case WAIT_MAIN:
					processResult = onStateWaitMain();
					break;

				case WAIT_HB_CMD:
					processResult = onStateHeartbeatCommand();
					break;

				case SEND_VOICE_TO_RIG:
					processResult = onStateSendVoiceToRig();
					break;

				case RECV_VOICE_FROM_RIG:
					processResult = onStateRecvVoiceFromRig();
					break;

				case TIME_WAIT:
					processResult = onStateWait();
					break;

				default:
					if (rigDataPort != null) { rigDataPort.closePort(); }
					nextState = CommunicationState.INITIALIZE;
					receiveBufferClear();
					break;
				}

				if(
					currentState != nextState &&
					processResult == ThreadProcessResult.NoErrors
				) {reProcess = true;}

			}while(reProcess);

			// Clean old inet header caches
			for(Iterator<HeaderCommandCache> it = inetHeaderCaches.values().iterator(); it.hasNext();) {
				final HeaderCommandCache cacheHeader = it.next();

				if((cacheHeader.getActivityTimestamp().isTimeout(5, TimeUnit.MINUTES))) {
					it.remove();
				}
			}
		}finally {
			stateLocker.unlock();
		}

		return processResult;
	}

	@Override
	protected ProcessIntervalMode getProcessLoopIntervalMode() {
		return ProcessIntervalMode.Normal;
	}

	@Override
	protected ModemStatusReport getStatusReportInternal(ModemStatusReport report) {

		return report;
	}

	@Override
	protected ModemStatusData createStatusDataInternal() {
		final NewAccessPointStatusData status =
			new NewAccessPointStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ModemStatusData> getStatusDataTypeInternal() {
		return NewAccessPointStatusData.class;
	}

	private void toWaitState(long waitTime, TimeUnit timeUnit, CommunicationState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = CommunicationState.TIME_WAIT;
		this.callbackState = callbackState;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateInitialize() {
		if(rigDataPort != null && rigDataPort.isOpen()) {rigDataPort.closePort();}

		rigDataPort = UartInterfaceFactory.createUartInterface(getExceptionListener(), getUartType());

		if(rigDataPort != null){
			rigDataPort.addEventListener(uartEventListener);

			rigDataPort.setBaudRate(getRigBitRate());
			rigDataPort.setDataBits(8);
			rigDataPort.setStopBitMode(UartStopBitModes.STOPBITS_ONE);
			rigDataPort.setParityMode(UartParityModes.PARITY_NONE);
			rigDataPort.setFlowControlMode(UartFlowControlModes.FLOWCONTROL_DISABLE);

			nextState = CommunicationState.PORT_OPEN;

			return ThreadProcessResult.NoErrors;
		}
		else{
			return threadFatalError("Could not create uart interface type " + getUartType() + ".", null);
		}
	}

	private ThreadProcessResult onStatePortOpen() {
		if (!rigDataPort.openPort(getRigPortName())) {

			rigDataPort.closePort();

			if (rigDataPortErrorCount % 60 == 0) {
				if(log.isErrorEnabled()) {log.error(logHeader + " Open failed.");}
			}

			if (rigDataPortErrorCount >= Integer.MAX_VALUE)
				rigDataPortErrorCount = 0;
			else
				rigDataPortErrorCount++;

			toWaitState(1, TimeUnit.SECONDS, CommunicationState.INITIALIZE);
		}
		else {
			nextState = CommunicationState.INITIALIZE_CMD;
			rigDataPortErrorCount = 0;
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateIninializeCommand() {
		if(isStateChanged()) {
			//受信バッファクリア
			receiveBufferClear();

			if (sendCommand(new InitializeCommand())) {
				stateTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
				stateTimeKeeper.updateTimestamp();
			}
			else {
				if(log.isErrorEnabled())
					log.error(logHeader + "Could not transmit command, initialize process failed.");

				toWaitState(10, TimeUnit.SECONDS, CommunicationState.INITIALIZE);
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isErrorEnabled())
				log.error(logHeader + "No responce from AccessPoint, initialize process failed.");

			toWaitState(10, TimeUnit.SECONDS, CommunicationState.INITIALIZE);
		}
		else if (recvCommands.size() > 0) {
			AccessPointCommand command = null;
			for (Iterator<AccessPointCommand> it = recvCommands.iterator(); it.hasNext(); ) {
				command = it.next();
				it.remove();

				if (command != null && command instanceof InitializeCommand) {
					nextState = CommunicationState.WAIT_MAIN;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWaitMain() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(2, TimeUnit.SECONDS);
		}
		//無線機からの受信データがあれば流す
		else if (recvCommands.size() > 0) {
			for (Iterator<AccessPointCommand> it = recvCommands.iterator(); it.hasNext(); ) {
				AccessPointCommand recvCommand = it.next();
				it.remove();

				boolean voiceCommand = false;
				if (
					recvCommand != null &&
					(
						recvCommand instanceof VoiceDataHeader ||
						(voiceCommand = recvCommand instanceof VoiceData)
					)
				) {
					boolean foundValidHeader = false;

					// 低速データセグメントからヘッダの再生を試みる
					if (voiceCommand) {
						if (
							getDataSegmentDecoder().decode(recvCommand.getDataSegment()) ==
								DataSegmentDecoderResult.Header
						) {
							final AccessPointCommand newHeaderCommand = new VoiceDataHeader();
							newHeaderCommand.setDvHeader(getDataSegmentDecoder().getHeader());
							recvCommand = newHeaderCommand;

							if(log.isDebugEnabled()) {
								log.debug(
									logHeader +
									" Found header information from slow data segment.\n    " +
									recvCommand.getDvHeader().toString()
								);
							}

							foundValidHeader = true;
						}
						else {
							foundValidHeader = false;
						}
					}
					else {
						foundValidHeader = true;
					}

					if (foundValidHeader) {
						final Header receiveHeader = recvCommand.getDvHeader();

						if (checkValidRFHeader(receiveHeader)) {

							getDataSegmentDecoder().reset();
							getDataSegmentEncoder().reset();

							codeSquelchReceived = false;
							nextState = CommunicationState.RECV_VOICE_FROM_RIG;

							voicePacketCounter = 0;

							//フレームID生成
							currentFrameID = super.generateFrameID();

							//ゲートウェイ動作モード判定
							GatewayMode currentMode =
								DSTARDefines.DIRECT.equals(String.valueOf(recvCommand.getRepeater2Callsign())) &&
								DSTARDefines.DIRECT.equals(String.valueOf(recvCommand.getRepeater1Callsign())) ?
								GatewayMode.TerminalMode : GatewayMode.AccessPointMode;

							if(gatewayMode != currentMode) {
								if(log.isInfoEnabled())
									log.info(logHeader + "You are using TERMINAL MODE");

								gatewayMode = currentMode;
							}


							final BackBoneHeader backbone = new BackBoneHeader(
								BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader,
								currentFrameID
							);

							final InternalPacket receivePacket = new InternalPacket(
								loopBlockID, ConnectionDirectionType.Unknown,
								new DVPacket(backbone, recvCommand.getDvHeader())
							);

							currentReceivingHeader = receivePacket;

							if(log.isDebugEnabled())
								log.debug(logHeader + " Receive header packet from radio.\n    " + recvCommand.toString());
						}
						else {
							if(log.isInfoEnabled())
								log.info(logHeader + " Reject illegal header.\n    " + receiveHeader.toString());
						}
					}
					break;
				}
			}

			stateTimeKeeper.updateTimestamp();
		}
		//上位側からのヘッダがあれば無線機に送信
		else if (hasTransporterReadablePacket()) {

			transporterLocker.lock();
			try {
				transporter.setRepairEnable(SystemUtil.getAvailableProcessors() >= 3);
			}finally {
				transporterLocker.unlock();
			}

			Optional<TransmitterPacket> opPacket;
			while((opPacket = readTransporterPacket()).isPresent()) {
				final TransmitterPacket packet = opPacket.get();

				AccessPointCommand command =
					convertDvPacketToAccessPointCommand(packet.getPacketType(), packet.getPacket().getDVPacket());

				boolean frameStart = false;

				if (command != null && command instanceof VoiceDataHeader) {
					final HeaderCommandCache cacheHeader =
							inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());
					// new header?
					if (cacheHeader == null) {
						// remove old header cache
						removeOldHeaderCache();

						final HeaderCommandCache newHeaderCache =
								new HeaderCommandCache(command.getBackBone().getFrameIDNumber(), command);

						inetHeaderCaches.put(newHeaderCache.getFrameID(), newHeaderCache);

					} else {
						cacheHeader.updateActivityTimestamp();
					}

					frameStart = true;
				}
				else if (command != null && command instanceof VoiceData) {
					// Resync frame by voice data frame id
					final HeaderCommandCache cacheHeader =
							inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());

					//同じFrameIDかつ、前回のパケット受信から30秒以内ならヘッダをキャッシュから補完する
					if (
						cacheHeader != null &&
						!cacheHeader.getActivityTimestamp().isTimeout(30, TimeUnit.SECONDS)
					) {
						command = cacheHeader.getVoiceHeader();

						if(log.isDebugEnabled())
							log.debug(logHeader + " Resync inet frame from header cache...\n    " + command.getDvPacket().toString());

						frameStart = true;
					}
				}

				if (frameStart) {
					//無線機へヘッダ送信
					if(sendCommand(command)) {
						if(log.isDebugEnabled())
							log.debug(logHeader + " Start voice tranmit");

//						nextState = CommunicationState.SEND_VOICE_TO_RIG;
						toWaitState(200, TimeUnit.MILLISECONDS, CommunicationState.SEND_VOICE_TO_RIG);

						if (
							command.getDvHeader().getFlags()[0] != 0x0 &&
							(command.getDvHeader().getFlags()[0] & RepeaterControlFlag.getMask()) != RepeaterControlFlag.AUTO_REPLY.getValue()
						)
							getDataSegmentEncoder().setEnableEncode(false);
						else
							getDataSegmentEncoder().setEnableEncode(true);

						getDataSegmentEncoder().reset();
						getDataSegmentEncoder().setCodeSquelchCode(getCodeSquelchCode());
						getDataSegmentEncoder().setEnableCodeSquelch(isEnableCodeSquelch());

						voicePacketCounter = 0;
						voicePacketBackboneSequence = 0;

						lastSendCommand = new VoiceData();
						ArrayUtil.copyOf(lastSendCommand.getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);

						performanceCounter.start();
						packetCounter = 0;

						if(log.isDebugEnabled())
							log.debug(logHeader + " Send header packet to radio.\n    " + command.toString());
					}
					else{
						if(log.isErrorEnabled())
							log.error(logHeader + " Could not transmit command, transmit process failed.");

						toWaitState(1, TimeUnit.SECONDS, CommunicationState.INITIALIZE);
					}
					break;
				}
			}
		//生存確認コマンドを送信
		} else if (stateTimeKeeper.isTimeout()) {
			nextState = CommunicationState.WAIT_HB_CMD;
		}

		return ThreadProcessResult.NoErrors;
	}



	private ThreadProcessResult onStateHeartbeatCommand() {
		if(isStateChanged()) {
			receiveBufferClear();

			if(sendCommand(new HeartbeatCommand())){
				stateTimeKeeper.setTimeoutTime(200, TimeUnit.MILLISECONDS);
			}else{
				if(log.isErrorEnabled())
					log.error(logHeader + " Could not transmit command, heartbeat process failed.");

				toWaitState(100, TimeUnit.MILLISECONDS, CommunicationState.INITIALIZE);
			}
		}
		//タイムアウト?
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isWarnEnabled())
				log.warn(logHeader + " Communication state " + currentState.toString() + " timeout occurred.");

			toWaitState(500, TimeUnit.MILLISECONDS, CommunicationState.INITIALIZE);

		} else if (recvCommands.size() > 0) {
			for (Iterator<AccessPointCommand> it = recvCommands.iterator(); it.hasNext(); ) {
				final AccessPointCommand command = it.next();

				if (command != null) {
					if(command instanceof HeartbeatCommand) {
						it.remove();
					}

					nextState = CommunicationState.WAIT_MAIN;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSendVoiceToRig() {
		boolean timeout = stateTimeKeeper.isTimeout();
		boolean retry = (retryCount < retryLimit) && timeout;

		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutMillis(750L);
			stateTimeKeeper.updateTimestamp();
		}
		//応答が返って来ていない場合
		else if (timeout) {
			//再試行回数制限到達か？
			if(!retry) {
				//再試行回数制限に達したので諦めて、初期化ステートに以降
				if(log.isDebugEnabled())
					log.debug(logHeader + " Communication state " + currentState.toString() + " timeout occurred.");

				toWaitState(100, TimeUnit.MILLISECONDS, CommunicationState.INITIALIZE_CMD);

				retryCount = 0;
			}
			else {
				retryCount++;

				if(lastSendCommand != null){sendVoiceToRig(lastSendCommand);}
			}
		}
		else if(
			!recvCommands.isEmpty() ||
			//BluetoothSPPの場合には応答を読まない
			isIgnoreResponse()
		){

			AccessPointCommand command = null;

			boolean responseFound = false;

			//ボイスデータ応答を探してボイスデータを送信
			for(Iterator<AccessPointCommand> it = recvCommands.iterator();it.hasNext();) {
				command = it.next();
				it.remove();

				if(
					command != null &&
					(
						command instanceof VoiceDataToRigResponse ||
						command instanceof VoiceDataHeaderToRigResponse
					)
				) {
					responseFound = true;

					break;
				}
				else{
					continue;
				}
			}

			//音声パケットの応答があったか？
			if(
				responseFound ||
				// Bluetoothの場合はレイテンシが大きい為、レスポンスを無視
				isIgnoreResponse()
			){
				transporterLocker.lock();
				try {
					Optional<TransmitterPacket> opPacket;
					while((opPacket = transporter.readPacket()).isPresent()) {
						final TransmitterPacket packet = opPacket.get();

						if(packet.getPacketType() != PacketType.Voice) {continue;}

						command = convertDvPacketToAccessPointCommand(
							packet.getPacketType(), packet.getPacket().getDVPacket()
						);
						if(command != null){
							sendCommandQueue.add(command);
							break;
						}
					}
				}finally{transporterLocker.unlock();}

				if(!isIgnoreResponse()) {
					//スリップパケット挿入有効で、上位側からのコマンドが何も無い場合にはスライドパケットを挿入する
					if(isEnablePacketSlip() && sendCommandQueue.isEmpty()) {

						if(voicePacketSlipCounter == 0) {
							if(log.isDebugEnabled())
								log.debug(logHeader + " [Underflow detected!]");
						}

						//スリップパケット挿入が連続していないか？
						if(voicePacketSlipCounter < getPacketSlipLimit()) {

							if(lastSendCommand != null && lastSendCommand instanceof VoiceData) {
								sendCommandQueue.add(lastSendCommand);
							}
							else {
								AccessPointCommand nullVoice = new VoiceData();
								nullVoice.setBackBone(lastSendCommand.getBackBone());
								ArrayUtil.copyOf(nullVoice.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);
								ArrayUtil.copyOf(nullVoice.getVoiceData().getDataSegment(), DSTARDefines.SlowdataNullBytes);

								sendCommandQueue.add(nullVoice);
							}

							voicePacketSlipCounter++;
						}
						else {
							//パケットスリップリミットへ到達したので、終端パケットを挿入する
							ArrayUtil.copyOf(lastSendCommand.getVoiceSegment(), VoiceAMBE.lastVoiceSegment);
							lastSendCommand.getBackBone().setEndSequence();

							sendCommandQueue.add(lastSendCommand);

							if(log.isDebugEnabled())
								log.debug(logHeader + " Inserted end packet, limit of packet slip.");

							voicePacketSlipCounter = 0;

							toWaitState(100, TimeUnit.MILLISECONDS, CommunicationState.INITIALIZE_CMD);
						}
					}
					//パケットスリップが無効で、上位側からのコマンドが無い場合には、終端コマンドを挿入する
					else if(!isEnablePacketSlip() && sendCommandQueue.isEmpty()) {
						ArrayUtil.copyOf(lastSendCommand.getVoiceSegment(), VoiceAMBE.lastVoiceSegment);
						lastSendCommand.getBackBone().setEndSequence();

						sendCommandQueue.add(lastSendCommand);
					}
					//上位側からのコマンドがある場合
					else if(!sendCommandQueue.isEmpty()){
						if(voicePacketSlipCounter != 0) {
							if(log.isDebugEnabled()) {
								log.debug(
									logHeader +
									" add slide packet because underflow detected...slip count:" +
									(voicePacketSlipCounter + 1)
								);
							}
						}

						voicePacketSlipCounter = 0;
					}
				}

				boolean foundVoice = false;
				for(Iterator<AccessPointCommand> it = sendCommandQueue.iterator(); it.hasNext();) {
					command = it.next();
					it.remove();

					if(command instanceof VoiceData) {
						foundVoice = true;

						final HeaderCommandCache cacheHeader =
							inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());
						if(cacheHeader != null) {cacheHeader.updateActivityTimestamp();}

						break;
					}
					else if(command instanceof VoiceDataHeader) {
						if(!inetHeaderCaches.containsKey(command.getBackBone().getFrameIDNumber())) {
							final HeaderCommandCache cacheHeader =
								new HeaderCommandCache(command.getBackBone().getFrameIDNumber(), command);
							inetHeaderCaches.put(command.getBackBone().getFrameIDNumber(), cacheHeader);
						}
					}
				}

				if(foundVoice || !isIgnoreResponse()) {
					if(!foundVoice){
						if(log.isDebugEnabled())
							log.debug(logHeader + "Could not found command from inet command queue.");

						final AccessPointCommand endVoice = new VoiceData();
						endVoice.setBackBone(lastSendCommand.getBackBone());
						endVoice.getBackBone().setEndSequence();
						ArrayUtil.copyOf(endVoice.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentLastBytes);
						ArrayUtil.copyOf(endVoice.getVoiceData().getDataSegment(), DSTARDefines.SlowdataLastBytes);

						command = endVoice;
					}

					int bbPacketCount;
					if(isEnableCodeSquelch()) {
						bbPacketCount =
							!command.isEndPacket() ?
									voicePacketBackboneSequence : voicePacketBackboneSequence | 0x40;
					}
					else {
						bbPacketCount = ((VoiceData)command).getBackBone().getSequenceNumber();
					}

					//パケットカウンタセット
					((VoiceData)command).setPacketCounter(
							voicePacketCounter,
							bbPacketCount
					);

					if(isEnableCodeSquelch()) {
						//データセグメント処理
						DataSegmentDecoderResult decoderResult =
								getDataSegmentDecoder().decode(command.getDataSegment());
						switch(decoderResult) {
						case ShortMessage:
							getDataSegmentEncoder().setEnableShortMessage(true);
							getDataSegmentEncoder().setShortMessage(getDataSegmentDecoder().getShortMessage());
							break;
						default:
							break;
						}

						getDataSegmentEncoder().encode(command.getDataSegment());
					}


					//送信
					if(!sendVoiceToRig(command)){
						toWaitState(1, TimeUnit.SECONDS, CommunicationState.INITIALIZE);
					}
					//ボイスデータ終端か？
					else if(command.isEndPacket()) {
						toWaitState(250, TimeUnit.MILLISECONDS, CommunicationState.WAIT_MAIN);
						sendCommandQueue.clear();

						if(log.isDebugEnabled())
							log.debug(logHeader + " End of voice transmit");
					}
					else {
						stateTimeKeeper.updateTimestamp();
					}

					if(voicePacketCounter >= 0xFF)
						voicePacketCounter = 0;
					else
						voicePacketCounter++;

					if(voicePacketBackboneSequence >= 0x14)
						voicePacketBackboneSequence = 0x00;
					else
						voicePacketBackboneSequence++;


					stateTimeKeeper.updateTimestamp();
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateRecvVoiceFromRig() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
			stateTimeKeeper.updateTimestamp();
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Communication state " + currentState.toString() + " timeout occured.");

			nextState = CommunicationState.WAIT_MAIN;
			currentFrameID = 0x00;
		}
		else {
			for(Iterator<AccessPointCommand> it = recvCommands.iterator();it.hasNext();) {
				final AccessPointCommand recvCommand = it.next();
				it.remove();

				if(!(recvCommand instanceof VoiceData)) {continue;}

				//コードスケルチ検出処理
				DataSegmentDecoderResult decoderResult =
						getDataSegmentDecoder().decode(recvCommand.getDataSegment());

				switch(decoderResult) {
				case CSQL:{
					codeSquelchReceived = true;
					receivedCodeSquelchCode = (byte)getDataSegmentDecoder().getCsqlCode();
					break;
				}
				case APRS:
					if(log.isDebugEnabled()) {
						log.debug(
							logHeader +
							"APRS message received.\n" + "    Message:" + getDataSegmentDecoder().getAprsMessage()
						);
					}

					break;
				default:
					break;
				}

				//SlowData除去処理
				if(isDisableSlowDataToInet())
					getDataSegmentEncoder().encode(recvCommand.getDataSegment());

				if(
					gatewayMode == GatewayMode.TerminalMode ||
					!enableCodeSquelch ||
					(
						enableCodeSquelch && codeSquelchReceived &&
						receivedCodeSquelchCode == codeSquelchCode
					)
				) {
					if(voicePacketCounter <= 0)
						addReadPacket(currentReceivingHeader);

					final DVPacket voiceDvPacket = recvCommand.getDvPacket();
					voiceDvPacket.setRfHeader(currentReceivingHeader.getRFHeader().clone());
					voiceDvPacket.setPacketType(PacketType.Header, PacketType.Voice);
					voiceDvPacket.getBackBone().setFrameIDNumber(currentFrameID);

					addReadPacket(new InternalPacket(
						loopBlockID, ConnectionDirectionType.Unknown, voiceDvPacket
					));

					if(log.isTraceEnabled())
						log.trace(logHeader + " Receive voice packet from radio.\n    " + recvCommand.toString());

					if(voicePacketCounter < Integer.MAX_VALUE) {voicePacketCounter++;}
				}

				//パケット終端か？
				if(recvCommand.isEndPacket()) {

					toWaitState(250, TimeUnit.MILLISECONDS, CommunicationState.WAIT_MAIN);
					currentFrameID = 0x0;
					voicePacketCounter = 0;

					if(log.isDebugEnabled())
						log.debug(logHeader + " Receive voice end packet from radio.\n    " + recvCommand.toString());

					break;
				}

				stateTimeKeeper.updateTimestamp();
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private void receiveBufferClear() {
		synchronized(recvBuffer) {
			recvBuffer.clear();
			recvBufferState = BufferState.INITIALIZE;

			recvTimestamp.updateTimestamp();
		}
	}

	private void analyzeReceiveBuffer() {
		AccessPointCommand command = null;
		boolean match = false;

		synchronized(recvBuffer) {

			do {
				if(
					//初期化コマンド応答か？
					(command = initializeCommand.analyzeCommandData(recvBuffer)) != null ||
					//生存確認コマンドか？
					(command = heartbeatCommand.analyzeCommandData(recvBuffer)) != null ||
					//ボイスヘッダか？
					(command = voiceDataHeader.analyzeCommandData(recvBuffer)) != null ||
					//ボイス&データか？
					(command = voiceData.analyzeCommandData(recvBuffer)) != null ||
					//ボイスヘッダに対する応答か？
					(command = voiceDataHeaderToRigResponse.analyzeCommandData(recvBuffer)) != null ||
					//ボイスデータに対する応答か？
					(command = voiceDataToRigResponse.analyzeCommandData(recvBuffer)) != null
				) {
					//受信コマンドキューへ追加
					recvCommands.add(command.clone());
//					recvTimestamp = System.currentTimeMillis();

					match = true;
				}else {
					match = false;
				}
			}while(match);
		}

		return;
	}

	@Override
	public boolean writePacketInternal(@NonNull final DSTARPacket packet) {
		if(log.isTraceEnabled())
			log.trace(logHeader + "Receive packet from repeater.\n    " + packet.toString(4));

		if(packet.getPacketType() != DSTARPacketType.DV) {return false;}

		transporterLocker.lock();
		try{
			TransmitterPacket writePacket = null;

			if(currentWriteFrameID != 0x0 && writeFrameTimekeeper.isTimeout(2, TimeUnit.SECONDS))
				currentWriteFrameID = 0x0000;

			if(currentWriteFrameID == 0x0000 && packet.getDVPacket().hasPacketType(PacketType.Header)) {
				currentWriteFrameID = packet.getBackBone().getFrameIDNumber();
				writeFrameTimekeeper.updateTimestamp();

				writePacket = new TransmitterPacketImpl(PacketType.Header, packet, FrameSequenceType.Start);

				if(log.isDebugEnabled())
					log.debug(logHeader + "VOICE START from network\n" + packet.toString(4));
			}
			else if(currentWriteFrameID == packet.getBackBone().getFrameIDNumber()) {
				writeFrameTimekeeper.updateTimestamp();

				if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {
					writePacket =
						new TransmitterPacketImpl(
							PacketType.Voice,
							packet, packet.isLastFrame() ? FrameSequenceType.End : FrameSequenceType.None
						);
				}

				if(packet.isLastFrame()) {
					currentWriteFrameID = 0x0000;

					if(log.isDebugEnabled())
						log.debug(logHeader + "VOICE END from network\n" + packet.toString(4));
				}
			}

			if(transporter.getCachePacketSize() < 100 && writePacket != null)
				return transporter.writePacket(writePacket);
			else
				return false;
		}finally{
			transporterLocker.unlock();
		}
	}

	@Override
	public void onReadPacket(DSTARPacket packet) {
		if(log.isTraceEnabled())
			log.trace(logHeader + "Transmit packet to repeater.\n    " + packet.toString(4));
	}

	private AccessPointCommand convertDvPacketToAccessPointCommand(
		final PacketType packetType, final DVPacket packet
	) {

		AccessPointCommand command = null;

		switch(packetType) {
		case Header:
			command = new VoiceDataHeader();
			command.setDvHeader(packet.getRfHeader());
			command.setBackBone(packet.getBackBone());
			break;

		case Voice:
			command = new VoiceData();
			command.setVoiceData(packet.getVoiceData());
			command.setBackBone(packet.getBackBone());
			break;

		default:
			break;
		}

		return command;
	}

	private boolean sendVoiceToRig(AccessPointCommand command) {

		//ボイスデータ送信
		boolean portError = !sendCommand(command);

		if(!portError) {
			if(log.isTraceEnabled())
				log.trace(logHeader + " Send voice packet to radio.\n    " + command.toString());
		}
		else {
			if(log.isErrorEnabled())
				log.error(logHeader + " Could not transmit command, voice transmit process failed.");
		}

		//
		lastSendCommand = command;

		if(performanceCounter.isTimeout(5, TimeUnit.SECONDS)) {
			performanceCounter.start();

			if(log.isTraceEnabled())
				log.trace(logHeader + " Voice packet send rate ..." + packetCounter / 5 + "packets/sec");

			packetCounter = 0;
		}else {
			packetCounter++;
		}

		return !portError;
	}

	private boolean sendCommand(AccessPointCommand command) {
		assert command != null;

		if(command instanceof VoiceDataHeader) {
			command.getDvHeader().getFlags()[0] = (byte)(
				(
					((command.getDvHeader().getFlags()[0] & ~RepeaterRoute.getMask()) | RepeaterRoute.TO_TERMINAL.getValue())
				)
			);
		}

		byte[] sendBytes = command.assembleCommandData();

		return rigDataPort.writeBytes(sendBytes, sendBytes.length) > 0;
	}

	@Override
	public boolean setPropertiesInternal(ModemProperties properties) {
		if(properties == null) {return false;}

		final String uartTypeString =
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				uartTypePropertyName, uartTypeDefault.getTypeName()
			);
		UartInterfaceType ifType = UartInterfaceType.getTypeByName(uartTypeString);
		if(ifType == null){ifType = UartInterfaceType.Serial;}
		setUartType(ifType);

		setRigPortName(
				PropertyUtils.getString(properties.getConfigurationProperties(), rigPortNamePropertyName, rigPortNameDefault)
		);
		logHeader = getClass().getSimpleName() + "[" + getRigPortName() + "] : ";

		setEnableCodeSquelch(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				enableCodeSquelchPropertyName,
				enableCodeSquelchDefault
			)
		);
		int csqlCode =
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				codeSquelchCodePropertyName,
				codeSquelchCodeDefault
			);
		if(csqlCode > 99) {
			if(log.isWarnEnabled())
				log.warn("Illegal CSQL code = " + csqlCode + ", replace to default code = " + codeSquelchCodeDefault + ".");

			csqlCode = codeSquelchCodeDefault;
		}
		setCodeSquelchCode((byte)csqlCode);

		setEnablePacketSlip(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				enablePacketSlipPropertyName,
				enablePacketSlipDefault
			)
		);
		setPacketSlipLimit(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				packetSlipLimitPropertyName,
				packetSlipLimitDefault
			)
		);

		setDisableSlowDataToInet(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				disableSlowDataToInetPropertyName,
				disableSlowDataToInetDefault
			)
		);

		setBlockDIRECT(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				blockDIRECTPropertyName,
				blockDIRECTDefault
			) ||
			!isAllowDIRECT()
		);

		setIgnoreResponse(
				PropertyUtils.getBoolean(
						properties.getConfigurationProperties(),
						ignoreResponsePropertyName,
						ignoreResponseDefault
				)
		);
		if(getUartType() == UartInterfaceType.BluetoothSPP){
			if(log.isInfoEnabled())
				log.info(logHeader + "Ignore response is set value to true, uart type is BluetoothSPP selected.");

			setIgnoreResponse(true);
		}

		return true;
	}

	@Override
	public ModemProperties getProperties(ModemProperties properties) {
		if(properties == null) {return null;}

		properties.getConfigurationProperties().setProperty(
				rigPortNamePropertyName, getRigPortName()
		);

		properties.getConfigurationProperties().setProperty(
				enableCodeSquelchPropertyName, String.valueOf(isEnableCodeSquelch())
		);
		properties.getConfigurationProperties().setProperty(
				codeSquelchCodePropertyName, String.valueOf(getCodeSquelchCode())
		);

		properties.getConfigurationProperties().setProperty(
				enablePacketSlipPropertyName, String.valueOf(isEnablePacketSlip())
		);
		properties.getConfigurationProperties().setProperty(
				packetSlipLimitPropertyName, String.valueOf(getPacketSlipLimit())
		);

		properties.getConfigurationProperties().setProperty(
				disableSlowDataToInetPropertyName, String.valueOf(isDisableSlowDataToInet())
		);

		properties.getConfigurationProperties().setProperty(
			blockDIRECTPropertyName, String.valueOf(isBlockDIRECT())
		);

		return properties;
	}

	@Override
	public boolean hasWriteSpace() {
		transporterLocker.lock();
		try{
			return transporter.hasWriteSpace();
		}finally{transporterLocker.unlock();}
	}

	/**
	 * 古いヘッダキャッシュ(上位側)を削除する
	 */
	private void removeOldHeaderCache() {

		while (inetHeaderCaches.size() >= inetHeaderCacheLimit) {
			Stream.of(inetHeaderCaches)
			.min(
				ComparatorCompat.comparingLong(
					new ToLongFunction<Map.Entry<Integer, HeaderCommandCache>>() {
						@Override
						public long applyAsLong(Map.Entry<Integer, HeaderCommandCache> integerHeaderCommandCacheEntry) {
							return integerHeaderCommandCacheEntry.getValue().getActivityTimestamp().getTimestampMilis();
						}
					}
				)
			)
			.map(
				new Function<Map.Entry<Integer, HeaderCommandCache>, HeaderCommandCache>() {
					@Override
					public HeaderCommandCache apply(Map.Entry<Integer, HeaderCommandCache> integerHeaderCommandCacheEntry) {
						return integerHeaderCommandCacheEntry.getValue();
					}
				}
			)
			.ifPresent(
				new Consumer<HeaderCommandCache>() {
					@Override
					public void accept(HeaderCommandCache headerCommandCache) {
						inetHeaderCaches.remove(headerCommandCache.getFrameID());
					}
				}
			);
		}
	}

	private boolean hasTransporterReadablePacket() {
		transporterLocker.lock();
		try{
			return transporter.hasReadablePacket();
		}finally{
			transporterLocker.unlock();
		}
	}

	private Optional<TransmitterPacket> readTransporterPacket(){
		transporterLocker.lock();
		try {
			return transporter.readPacket();
		}finally{
			transporterLocker.unlock();
		}
	}

	private boolean checkValidRFHeader(final Header receiveHeader) {
		return CallSignValidator.isValidUserCallsign(receiveHeader.getMyCallsign()) &&
			(
				CallSignValidator.isValidRepeaterCallsign(receiveHeader.getRepeater1Callsign()) ||
				(
					!isBlockDIRECT() &&
					DSTARDefines.DIRECT.equals(new String(receiveHeader.getRepeater1Callsign()))
				)
			) &&
			(
				(
					CallSignValidator.isValidRepeaterCallsign(receiveHeader.getRepeater2Callsign()) ||
					CallSignValidator.isValidGatewayCallsign(receiveHeader.getRepeater2Callsign())
				) ||
				(
					!isBlockDIRECT() &&
					DSTARDefines.DIRECT.equals(new String(receiveHeader.getRepeater2Callsign()))
				)
			) && !DSTARDefines.EmptyLongCallsign.equals(new String(receiveHeader.getYourCallsign()));
	}
}

