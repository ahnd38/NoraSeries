package org.jp.illg.dstar.repeater.modem.mmdvm;

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

import org.apache.commons.lang3.NotImplementedException;
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
import org.jp.illg.dstar.model.defines.VoiceCodecType;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemBase;
import org.jp.illg.dstar.repeater.modem.DStarRepeaterModemEvent;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.Ack;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.DStarHeader;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.DStarVoice;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.DStarVoiceEOT;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.DStarVoiceLOST;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.GetStatus;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.GetVersion;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.MMDVMCommand;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.Nak;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.Serial;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.SetConfig;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.SetFrequency;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.SetMode;
import org.jp.illg.dstar.repeater.modem.mmdvm.command.Transparent;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMFrameType;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMHardwareType;
import org.jp.illg.dstar.repeater.modem.mmdvm.define.MMDVMMode;
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlMMDVMHandler;
import org.jp.illg.dstar.service.web.model.MMDVMInterfaceStatusData;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.IntervalTimeCollector;
import org.jp.illg.dstar.util.dvpacket2.FrameSequenceType;
import org.jp.illg.dstar.util.dvpacket2.RepairCacheTransporter;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacketImpl;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtil.BufferProcessResult;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ProcessResult;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandlerWithThread;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.napi.model.PacketInfo;
import org.jp.illg.util.socketio.support.HostIdentType;
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

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MMDVMInterface extends DStarRepeaterModemBase
implements WebRemoteControlMMDVMHandler
{

	private static final int stateRetryLimit = 10;
	private static final int dstarHeaderSpace = 4;
	private static final int dstarVoiceSpace = 1;

	private enum ProcessState{
		Initialize,
		OpenPort,
		ReadVersion,
		SetFrequency,
		SetConfig,
		Idle,
		GetStatus,
		SetMode,
		Wait,
		;
	}

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UartInterfaceType uartType;
	public static final String uartTypePropertyName = "UartType";
	private static final UartInterfaceType uartTypeDefault = UartInterfaceType.Serial;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int protocolVersion;
	private static final int protocolVersionDefault = 1;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private MMDVMHardwareType hardwareType;
	private static final MMDVMHardwareType hardwareTypeDefault = MMDVMHardwareType.Unknown;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String hardwareVersion;
	private static final String hardwareVersionDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String PortName;
	private static final String portNameDefault = "";
	public static final String portNamePropertyName = "PortName";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableCodeSquelch;
	private static final boolean enableCodeSquelchDefault = false;
	public static final String enableCodeSquelchPropertyName = "EnableCodeSquelch";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private byte codeSquelchCode;
	private static final byte codeSquelchCodeDefault = (byte)0x00;
	public static final String codeSquelchCodePropertyName = "CodeSquelchCode";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enablePacketSlip;
	private static final boolean enablePacketSlipDefault = true;
	public static final String enablePacketSlipPropertyName = "EnablePacketSlip";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int packetSlipLimit;
	private static final int packetSlipLimitDefault = 50;
	public static final String packetSlipLimitPropertyName = "PacketSlipLimit";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean disableSlowDataToInet;
	private static final boolean disableSlowDataToInetDefault = false;
	public static final String disableSlowDataToInetPropertyName = "DisableSlowDataToInet";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean duplex;
	private static boolean duplexDefualt = false;
	public static final String duplexPropertyName = "Duplex";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean rxInvert;
	private static boolean rxInvertDefualt = false;
	public static final String rxInvertPropertyName = "RxInvert";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean txInvert;
	private static boolean txInvertDefualt = false;
	public static final String txInvertPropertyName = "TxInvert";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean pttInvert;
	private static boolean pttInvertDefualt = false;
	public static final String pttInvertPropertyName = "PTTInvert";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int txDelay;
	private static int txDelayDefualt = 0;
	public static final String txDelayPropertyName = "TxDelay";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean debug;
	private static boolean debugDefualt = false;
	public static final String debugPropertyName = "Debug";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long rxFrequency;
	private static final long rxFrequencyDefault = 430800000L;
	public static final String rxFrequencyPropertyName = "RxFrequency";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long rxFrequencyOffset;
	private static final long rxFrequencyOffsetDefault = 0L;
	public static final String rxFrequencyOffsetPropertyName = "RxFrequencyOffset";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long txFrequency;
	private static final long txFrequencyDefault = 430800000L;
	public static final String txFrequencyPropertyName = "TxFrequency";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long txFrequencyOffset;
	private static final long txFrequencyOffsetDefault = 0L;
	public static final String txFrequencyOffsetPropertyName = "TxFrequencyOffset";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long rxDCOffset;
	private static final long rxDCOffsetDefault = 0L;
	public static final String rxDCOffsetPropertyName = "RxDCOffset";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long txDCOffset;
	private static final long txDCOffsetDefault = 0L;
	public static final String txDCOffsetPropertyName = "TxDCOffset";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long rfLevel;
	private static final long rfLevelDefault = 0L;
	public static final String rfLevelPropertyName = "RfLevel";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private float rxLevel;
	private static final float rxLevelDefault = 0.0F;
	public static final String rxLevelPropertyName = "RxLevel";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private float txLevel;
	private static final float txLevelDefault = 0.0F;
	public static final String txLevelPropertyName = "TxLevel";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean transparentEnable;
	private static final boolean transparentEnableDefault = false;
	private static final String transparentEnablePropertyName = "TransparentEnable";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String transparentRemoteAddress;
	private static final String transparentRemoteAddressDefault = "127.0.0.1";
	public static final String transparentRemoteAddressPropertyName = "TransparentRemoteAddress";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int transparentRemotePort;
	private static final int transparentRemotePortDefault = 63201;
	public static final String transparentRemotePortPropertyName = "TransparentRemotePort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int transparentLocalPort;
	private static final int transparentLocalPortDefault = 63200;
	public static final String transparentLocalPortPropertyName = "TransparentLocalPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int transparentSendFrameType;
	private static final int transparentSendFrameTypeDefault = 0;
	private static final int transparentSendFrameTypeMin = 0;
	private static final int transparentSendFrameTypeMax = 2;
	public static final String transparentSendFrameTypePropertyName = "TransparentSendFrameType";


	private String logTag;

	private final Lock stateLocker;

	private ProcessState currentState;
	private ProcessState nextState;
	private ProcessState callbackState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;
	private Timer stateTimeKeeper;
	private int stateRetryCount;

	private boolean noCheckResponse;

	private UartInterface mmdvmPort;

	private final Queue<MMDVMCommand> mmdvmTransmitQueue;
	private final Queue<MMDVMCommand> mmdvmReceiveQueue;
	private final Lock mmdvmQueueLocker;

	private final Queue<DSTARPacket> networkReceiveQueue;
	private final Lock networkQueueLocker;

	private final Queue<ByteBuffer> transparentExternalQueue;
	private final Queue<ByteBuffer> transparentInternalQueue;
	private final Lock transparentQueueLocker;

	private final ByteBuffer mmdvmReceiveBuffer;
	private BufferState mmdvmReceiveBufferState;
	private final Timer mmdvmReceiveBufferTimestamp;
	private final Lock mmdvmReceiveBufferLock;

	private int dstarSpace;

	private int dstarFrameID;
	private int dstarSequence;
	private Header dstarHeader;
	private UUID loopBlockID;

	private final Timer dstarFrameTimeKeeper;

	private MMDVMMode nextMode;
	private MMDVMMode currentMode;
	private final Timer modeTimeKeeper;

	private final DataSegmentDecoder mmdvmReceiveSlowdataDecoder;
	private final RepairCacheTransporter<TransmitterPacket> toMMDVMTransporter;
	private int currentToMMDVMFrameID;
	private final Timer toMMDVMFrameTimekeeper;
	private TransmitterPacket toMMDVMTransmitPendingPacket;

	private boolean lockout;

	private final GetVersion mmdvmGetVersionCommand;
	private final Ack mmdvmAckCommand;
	private final Nak mmdvmNakCommand;
	private final GetStatus mmdvmGetStatusCommand;
	private final DStarHeader mmdvmDStarHeaderCommand;
	private final DStarVoice mmdvmDStarVoiceCommnad;
	private final DStarVoiceEOT mmdvmDStarVoiceEOTCommand;
	private final DStarVoiceLOST mmdvmDStarVoiceLOSTCommand;
	private final Serial mmdvmSerialCommand;
	private final Transparent mmdvmTransparentCommand;

	private MMDVMTransparentProtocolProcessor transparentProtocolProcessor;

	private final TaskQueue<UartEvent, Boolean> uartEventQueue;

	private final IntervalTimeCollector mmdvmTransferIntervalTimeCollector;

	private final UartEventListener uartEventListener = new UartEventListener() {
		@Override
		public UartEventType getLinteningEventType() {
			return UartEventType.DATA_AVAILABLE;
		}

		@Override
		public void uartEvent(UartEvent uartEvent) {
			uartEventQueue.addEventQueue(
				MMDVMInterface.this.uartEvent,
				uartEvent,
				getExceptionListener()
			);
		}
	};

	private final Function<UartEvent, Boolean> uartEvent = new Function<UartEvent, Boolean>(){
		@Override
		public Boolean apply(UartEvent uartEvent) {
			try {
				mmdvmReceiveBufferLock.lock();
				try {
					final BufferUtilObject putResult =
						BufferUtil.putBuffer(
							logTag,
							mmdvmReceiveBuffer, mmdvmReceiveBufferState, mmdvmReceiveBufferTimestamp,
							uartEvent.getReceiveData()
						);
					mmdvmReceiveBufferState = putResult.getBufferState();
					if(putResult.getProcessResult() != BufferProcessResult.Success) {
						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Failed copy receive data to buffer, result = " + putResult.getProcessResult().toString() + "."
							);
						}
					}

					if(log.isTraceEnabled())
						log.trace(logTag + "Receive from MMDVM...\n" + FormatUtil.bytesToHexDump(uartEvent.getReceiveData(), 4));

				}finally {
					mmdvmReceiveBufferLock.unlock();
				}


				parseCommand();

			}catch(Exception ex) {
				if(log.isErrorEnabled()) {log.error(logTag + "Serial port input process failed.", ex);}
			}

			processModem();

			return true;
		}
	};

	public MMDVMInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		this(exceptionListener, workerExecutor, gateway, repeater, eventListener, null);
	}

	public MMDVMInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			MMDVMInterface.class.getSimpleName(),
			workerExecutor,
			ModemTypes.getTypeByClassName(MMDVMInterface.class.getName()),
			gateway,
			repeater,
			eventListener,
			socketIO
		);

		logTag = this.getClass().getSimpleName();

		stateLocker = new ReentrantLock();

		mmdvmQueueLocker = new ReentrantLock();

		mmdvmReceiveBuffer = ByteBuffer.allocateDirect(1024);
		mmdvmReceiveBufferTimestamp = new Timer();
		mmdvmReceiveBufferTimestamp.setTimeoutTime(5, TimeUnit.SECONDS);

		mmdvmReceiveBufferLock = new ReentrantLock();

		networkQueueLocker = new ReentrantLock();

		modeTimeKeeper = new Timer(1000);

		mmdvmReceiveSlowdataDecoder = new DataSegmentDecoder();

		toMMDVMTransporter = new RepairCacheTransporter<>(15, 1);
		currentToMMDVMFrameID = 0x0000;
		toMMDVMFrameTimekeeper = new Timer();

		mmdvmGetVersionCommand = new GetVersion();
		mmdvmAckCommand = new Ack();
		mmdvmNakCommand = new Nak();
		mmdvmGetStatusCommand = new GetStatus();
		mmdvmDStarHeaderCommand = new DStarHeader();
		mmdvmDStarVoiceCommnad = new DStarVoice();
		mmdvmDStarVoiceEOTCommand = new DStarVoiceEOT();
		mmdvmDStarVoiceLOSTCommand = new DStarVoiceLOST();
		mmdvmSerialCommand = new Serial();
		mmdvmTransparentCommand = new Transparent();

		dstarFrameTimeKeeper = new Timer();

		transparentProtocolProcessor = null;

		currentState = ProcessState.Initialize;
		nextState = ProcessState.Initialize;
		callbackState = ProcessState.Initialize;
		stateTimeKeeper = new Timer();
		stateRetryCount = 0;

		loopBlockID = null;
		dstarHeader = null;

		noCheckResponse = false;

		mmdvmTransmitQueue = new LinkedList<>();
		mmdvmReceiveQueue = new LinkedList<>();

		mmdvmReceiveBuffer.clear();
		mmdvmReceiveBufferState = BufferState.INITIALIZE;

		networkReceiveQueue = new LinkedList<>();

		transparentExternalQueue = new LinkedList<>();
		transparentInternalQueue = new LinkedList<>();
		transparentQueueLocker = new ReentrantLock();

		dstarSpace = 0;
		dstarFrameID = 0;
		dstarSequence = 0;

		lockout = false;

		nextMode = MMDVMMode.MODE_IDLE;
		currentMode = MMDVMMode.MODE_IDLE;

		toMMDVMTransporter.reset();
		toMMDVMTransmitPendingPacket = null;

		uartEventQueue = new TaskQueue<>(getWorkerExecutor());

		mmdvmTransferIntervalTimeCollector = new IntervalTimeCollector();

		setUartType(uartTypeDefault);

		setProtocolVersion(protocolVersionDefault);
		setHardwareType(hardwareTypeDefault);
		setHardwareVersion(hardwareVersionDefault);
	}

	@Override
	public void notifyReflectorLoginUsers(
		@NonNull final ReflectorProtocolProcessorTypes reflectorType,
		@NonNull final DSTARProtocol protocol,
		@NonNull String remoteCallsign,
		@NonNull final ConnectionDirectionType connectionDir,
		@NonNull List<ReflectorRemoteUserEntry> users
	) {

	}

	@Override
	public ModemTransceiverMode getDefaultTransceiverMode() {
		return ModemTransceiverMode.HalfDuplex;
	}

	@Override
	public ModemTransceiverMode[] getSupportedTransceiverModes() {
		return new ModemTransceiverMode[] {
			ModemTransceiverMode.HalfDuplex,
			ModemTransceiverMode.FullDuplex,
			ModemTransceiverMode.RxOnly,
			ModemTransceiverMode.TxOnly
		};
	}

	@Override
	public boolean setPropertiesInternal(ModemProperties properties) {
		String uartTypeString =
			PropertyUtils.getString(
					properties.getConfigurationProperties(),
					uartTypePropertyName, uartTypeDefault.getTypeName()
			);
		UartInterfaceType ifType = UartInterfaceType.getTypeByName(uartTypeString);
		if(ifType == null){ifType = UartInterfaceType.Serial;}
		setUartType(ifType);

		setPortName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				portNamePropertyName, portNameDefault)
		);
		logTag = this.getClass().getSimpleName() + "(" + getPortName() + ") : ";


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
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Illegal CSQL code = " + csqlCode + ", replace to default code = " +
					codeSquelchCodeDefault + "."
				);
			}

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

		setDuplex(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				duplexPropertyName, duplexDefualt
			)
		);
		setRxInvert(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				rxInvertPropertyName, rxInvertDefualt
			)
		);
		setTxInvert(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				txInvertPropertyName, txInvertDefualt
			)
		);
		setPttInvert(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				pttInvertPropertyName, pttInvertDefualt
			)
		);
		setTxDelay(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				txDelayPropertyName, txDelayDefualt
			)
		);
		setDebug(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				debugPropertyName, debugDefualt
			)
		);

		setRxFrequency(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				rxFrequencyPropertyName, rxFrequencyDefault
			)
		);
		setRxFrequencyOffset(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				rxFrequencyOffsetPropertyName, rxFrequencyOffsetDefault
			)
		);

		setTxFrequency(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				txFrequencyPropertyName, txFrequencyDefault
			)
		);
		setTxFrequencyOffset(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				txFrequencyOffsetPropertyName, txFrequencyOffsetDefault
			)
		);

		setRxDCOffset(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				rxDCOffsetPropertyName, rxDCOffsetDefault
			)
		);
		setTxDCOffset(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				txDCOffsetPropertyName, txDCOffsetDefault
			)
		);

		setRfLevel(
			PropertyUtils.getLong(
				properties.getConfigurationProperties(),
				rfLevelPropertyName, rfLevelDefault
			)
		);
		setRxLevel(
			PropertyUtils.getFloat(
				properties.getConfigurationProperties(),
				rxLevelPropertyName, rxLevelDefault
			)
		);
		setTxLevel(
			PropertyUtils.getFloat(
				properties.getConfigurationProperties(),
				txLevelPropertyName, txLevelDefault
			)
		);


		setTransparentEnable(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				transparentEnablePropertyName, transparentEnableDefault
			)
		);

		setTransparentRemoteAddress(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				transparentRemoteAddressPropertyName, transparentRemoteAddressDefault
			)
		);

		setTransparentRemotePort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				transparentRemotePortPropertyName, transparentRemotePortDefault
			)
		);

		setTransparentLocalPort(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				transparentLocalPortPropertyName, transparentLocalPortDefault
			)
		);

		setTransparentSendFrameType(
			PropertyUtils.getInteger(
				properties.getConfigurationProperties(),
				transparentSendFrameTypePropertyName, transparentSendFrameTypeDefault
			)
		);
		if(
			getTransparentSendFrameType() > transparentSendFrameTypeMax ||
			getTransparentSendFrameType() < transparentSendFrameTypeMin
		) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"TransparentSendFrameType is overrange value=" + getTransparentSendFrameType() +
					", replaced default value=" + transparentSendFrameTypeDefault + "."
				);
			}

			setTransparentSendFrameType(transparentSendFrameTypeDefault);
		}

		//設定にてトランシーバモードが設定されていなければ、モデム設定のDuplexを設定する
		if(
			properties.getTransceiverMode() == null ||
			properties.getTransceiverMode() == ModemTransceiverMode.Unknown
		) {
			setTransceiverMode(isDuplex() ? ModemTransceiverMode.FullDuplex : ModemTransceiverMode.HalfDuplex);
		}

		return true;
	}

	@Override
	public ModemProperties getProperties(ModemProperties properties) {
		//TODO
		return properties;
	}

	@Override
	public boolean initializeWebRemoteControlInt(final WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeModemMMDVM(this);
	}

	@Override
	public boolean writePacketInternal(DSTARPacket packet) {
		boolean success = false;

		networkQueueLocker.lock();
		try {
			success = networkReceiveQueue.add(packet);
		}finally {
			networkQueueLocker.unlock();
		}

		return success;
	}

	@Override
	public boolean hasWriteSpace() {
		boolean hasTransporterSpace;
		networkQueueLocker.lock();
		try{
			hasTransporterSpace = toMMDVMTransporter.hasWriteSpace();
		}finally{networkQueueLocker.unlock();}

		return
			(currentMode == MMDVMMode.MODE_IDLE || currentMode == MMDVMMode.MODE_DSTAR) &&
			hasTransporterSpace;
	}

	@Override
	public VoiceCodecType getCodecType() {
		return VoiceCodecType.AMBE;
	}

	public ByteBuffer readTransparent() {
		transparentQueueLocker.lock();
		try {
			return transparentInternalQueue.poll();
		}finally {
			transparentQueueLocker.unlock();
		}
	}

	public boolean writeTransparent(@NonNull ByteBuffer data) {
		if(!data.hasRemaining()) {return true;}

		byte typeCode = 0;

		if(getTransparentSendFrameType() > 0) {
			final byte receiveType = data.get();

			if(
				getTransparentSendFrameType() == 1 &&
				receiveType != MMDVMFrameType.SERIAL.getTypeCode()
			) {
				typeCode = MMDVMFrameType.TRANSPARENT.getTypeCode();
			}
			else {	// No check
				typeCode = receiveType;
			}
		}
		else {
			typeCode = MMDVMFrameType.TRANSPARENT.getTypeCode();
		}

		final byte[] buffer = new byte[data.remaining()];
		for(int i = 0; i < buffer.length && data.hasRemaining(); i++)
			buffer[i] = data.get();

		final Transparent cmd = new Transparent();
		cmd.setSerialData(buffer);
		if(typeCode != MMDVMFrameType.TRANSPARENT.getTypeCode())
			cmd.setCustomFrameType(typeCode);

		return addMMDVMTransmitQueue(cmd);
	}

	public boolean writeSerial(@NonNull ByteBuffer data) {
		if(!data.hasRemaining()) {return true;}

		final byte[] buffer = new byte[data.remaining()];
		for(int i = 0; i < buffer.length && data.hasRemaining(); i++)
			buffer[i] = data.get();

		final Transparent cmd = new Transparent();
		cmd.setSerialData(buffer);

		return addMMDVMTransmitQueue(cmd);
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		if(isTransparentEnable()) {
			transparentProtocolProcessor = new MMDVMTransparentProtocolProcessor(getExceptionListener());
			if(!transparentProtocolProcessor.start())
				return threadFatalError("Could not start transparent protocol processor.", null);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		if(transparentProtocolProcessor != null)
			transparentProtocolProcessor.stop();

		if(mmdvmPort != null && mmdvmPort.isOpen())
			mmdvmPort.closePort();
	}

	@Override
	protected ProcessIntervalMode getProcessLoopIntervalMode() {
		networkQueueLocker.lock();
		try {
			if(!networkReceiveQueue.isEmpty())
				return ProcessIntervalMode.VoiceTransfer;
		}finally {
			networkQueueLocker.unlock();
		}

		stateLocker.lock();
		try {
			networkQueueLocker.lock();
			try {
				if(!toMMDVMTransporter.isCachePacketEmpty())
					return ProcessIntervalMode.VoiceTransfer;
			}finally {
				networkQueueLocker.unlock();
			}

			mmdvmQueueLocker.lock();
			try {
				if(!mmdvmTransmitQueue.isEmpty())
					return ProcessIntervalMode.VoiceTransfer;
			}finally {
				mmdvmQueueLocker.unlock();
			}
		}finally {
			stateLocker.unlock();
		}

		return ProcessIntervalMode.Normal;
	}

	@Override
	protected ThreadProcessResult processModem() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		stateLocker.lock();
		try {
			boolean reProcess;
			do {
				reProcess = false;

				setStateChanged(currentState != nextState);
				currentState = nextState;

				switch(currentState) {
				case Initialize:
					processResult = onStateInitialize();
					break;

				case OpenPort:
					processResult = onStateOpenPort();
					break;

				case ReadVersion:
					processResult = onStateReadVersion();
					break;

				case SetFrequency:
					processResult = onStateSetFrequency();
					break;

				case SetConfig:
					processResult = onStateSetConfig();
					break;

				case SetMode:
					processResult = onStateSetMode();
					break;

				case Idle:
					processResult = onStateIdle();
					break;

				case GetStatus:
					processResult = onStateGetStatus();
					break;

				case Wait:
					processResult = onStateWait();
					break;
				}

				if(!sendMMDVMCommand()) {
					if(log.isWarnEnabled()) {log.warn(logTag + "Send failed to MMDVM.");}
					nextState = ProcessState.Initialize;
				}

				if(
					currentState != nextState &&
					processResult == ThreadProcessResult.NoErrors
				) {reProcess = true;}

			}while(reProcess);
		}finally {
			stateLocker.unlock();
		}

		if(transparentProtocolProcessor != null)
			transparentProtocolProcessor.processThread();

		return processResult;
	}

	@Override
	protected ModemStatusReport getStatusReportInternal(ModemStatusReport report) {

		return report;
	}

	@Override
	protected ModemStatusData createStatusDataInternal() {
		final MMDVMInterfaceStatusData status =
			new MMDVMInterfaceStatusData(getWebSocketRoomId());

		status.setUartType(getUartType());
		status.setProtocolVersion(getProtocolVersion());
		status.setHardwareType(getHardwareType());
		status.setPortName(getPortName());
		status.setEnableCodeSquelch(isEnableCodeSquelch());
		status.setEnablePacketSlip(isEnablePacketSlip());
		status.setPacketSlipLimit(getPacketSlipLimit());
		status.setDuplex(isDuplex());
		status.setRxInvert(isRxInvert());
		status.setTxInvert(isTxInvert());
		status.setPttInvert(isPttInvert());
		status.setTxDelay(getTxDelay());
		status.setDebug(isDebug());
		status.setRxFrequency(getRxFrequency());
		status.setRxFrequencyOffset(getRxFrequencyOffset());
		status.setTxFrequency(getTxFrequency());
		status.setTxFrequencyOffset(getTxFrequencyOffset());
		status.setRxDCOffset(getRxDCOffset());
		status.setTxDCOffset(getTxDCOffset());
		status.setRxLevel(getRxLevel());
		status.setTxLevel(getTxLevel());
		status.setTransparentEnable(isTransparentEnable());
		status.setTransparentRemoteAddress(getTransparentRemoteAddress());
		status.setTransparentRemotePort(getTransparentRemotePort());
		status.setTransparentLocalPort(getTransparentLocalPort());
		status.setTransparentSendFrameType(getTransparentSendFrameType());

		return status;
	}

	@Override
	protected Class<? extends ModemStatusData> getStatusDataTypeInternal() {
		return MMDVMInterfaceStatusData.class;
	}

	private ThreadProcessResult onStateInitialize() {
		nextState = ProcessState.OpenPort;

		mmdvmTransmitQueue.clear();
		mmdvmReceiveQueue.clear();

		setProtocolVersion(protocolVersionDefault);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateOpenPort() {
		if(mmdvmPort != null && mmdvmPort.isOpen())
			mmdvmPort.closePort();

		mmdvmPort = UartInterfaceFactory.createUartInterface(getExceptionListener(), getUartType());
		if(mmdvmPort == null)
			return threadFatalError("Could not create uart interface.", null);

		mmdvmPort.setBaudRate(115200);
		mmdvmPort.setDataBits(8);
		mmdvmPort.setStopBitMode(UartStopBitModes.STOPBITS_ONE);
		mmdvmPort.setParityMode(UartParityModes.PARITY_NONE);
		mmdvmPort.setFlowControlMode(UartFlowControlModes.FLOWCONTROL_DISABLE);

		if(mmdvmPort.openPort(getPortName())) {
			mmdvmPort.addEventListener(uartEventListener);

			nextState = ProcessState.ReadVersion;
		}
		else {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not open uart port. please see log file.");

			toWaitState(30L, TimeUnit.SECONDS, ProcessState.Initialize);
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateReadVersion() {
		if(isStateChanged()) {
			if(!addMMDVMTransmitQueue(new GetVersion())) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Error occurred at send GetVersion to MMDVM.");

				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
			}
			else {
				stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Timeout occurred at GetVersion process from MMDVM.");

			toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
		}
		else {
			Optional<MMDVMCommand> opCmd;
			while((opCmd = getCommandFromReceiveQueue(MMDVMFrameType.GET_VERSION)).isPresent()) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.GET_VERSION) {
					GetVersion gv = (GetVersion)cmd;
					setProtocolVersion(gv.getProtocolVersion());
					setHardwareVersion(gv.getVersion());
					setHardwareType(MMDVMHardwareType.getTypeByTypeNameStartWith(gv.getVersion()));

					if(log.isInfoEnabled()) {
						log.info(
							logTag + "MMDVM hardware detected.\n    " +
							"Type:" + getHardwareType().getTypeName() + "/" +
							"Version:" + getHardwareVersion() + "/" +
							"ProtocolVersion:" + getProtocolVersion()
						);
					}

					nextState = ProcessState.SetFrequency;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSetFrequency() {
		if(isStateChanged()) {
			SetFrequency cmd = new SetFrequency();
			cmd.setHardwareType(getHardwareType());
			cmd.setRfLevel(getRfLevel());
			cmd.setTxFrequency(getTxFrequency() + getTxFrequencyOffset());
			cmd.setRxFrequency(getRxFrequency() + getRxFrequencyOffset());

			log.info(
					logTag +
					"Setting configuration parameters to MMDVM...\n" +
					"  [Parameters]\n" +
					"    Rf level         : " + getRfLevel() +"\n" +
					"    TxFrequency      : " + getTxFrequency() + "Hz(Offset:" + String.format("%+d", getTxFrequencyOffset()) + "Hz)\n" +
					"    RxFrequency      : " + getRxFrequency() + "Hz(Offset:" + String.format("%+d", getRxFrequencyOffset()) + "Hz)\n" +
					"    RxInvert         : " + isRxInvert() +"\n" +
					"    TxInvert         : " + isTxInvert() +"\n" +
					"    PTTInvert        : " + isPttInvert() + "\n" +
					"    Debug            : " + isDebug() + "\n" +
					"    Duplex           : " + isDuplex() + "\n" +
					"    TxDelay          : " + getTxDelay() + "\n" +
					"    RxLevel          : " + getRxLevel() + "\n" +
					"    TxLevel          : " + getTxLevel() + "\n" +
					"    TxDCOffset       : " + getTxDCOffset() + "\n" +
					"    RxDCOffset       : " + getRxDCOffset()
			);

			if(!addMMDVMTransmitQueue(cmd)) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Error occurred at send SetFrequency to MMDVM.");

				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
			}
			else {
				stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Timeout occurred at SetFrequency process with MMDVM.");

			toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
		}
		else {
			Optional<MMDVMCommand> opCmd;
			while((opCmd = getCommandFromReceiveQueue(MMDVMFrameType.ACK, MMDVMFrameType.NAK)).isPresent()) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.ACK) {
					nextState = ProcessState.SetConfig;
				}
				else if(cmd.getCommandType() == MMDVMFrameType.NAK) {
					showNakReason(cmd, logTag + "Error occurred at SetFrequency process with MMDVM, returned NAK.");
					toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSetConfig() {
		if(isStateChanged()) {
			SetConfig cmd = new SetConfig();
			cmd.setRxInvert(isRxInvert());
			cmd.setTxInvert(isTxInvert());
			cmd.setPttInvert(isPttInvert());
			cmd.setDebug(isDebug());
			cmd.setDuplex(isDuplex());
			cmd.setTxDelay(getTxDelay());
			cmd.setRxLevel(getRxLevel());
			cmd.setTxLevel(getTxLevel());
			cmd.setTxDCOffset(getTxDCOffset());
			cmd.setRxDCOffset(getRxDCOffset());

			if(!addMMDVMTransmitQueue(cmd)) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Error occurred at send SetConfig to MMDVM.");

				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
			}
			else {
				stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();
			}
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Timeout occurred at SetConfig process with MMDVM.");

			toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
		}
		else {
			Optional<MMDVMCommand> opCmd;
			while((opCmd = getCommandFromReceiveQueue(MMDVMFrameType.ACK, MMDVMFrameType.NAK)).isPresent()) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.ACK) {
					nextState = ProcessState.SetMode;

					notifyStatusChanged();
				}
				else if(cmd.getCommandType() == MMDVMFrameType.NAK) {
					showNakReason(cmd, logTag + "Error occurred at SetConfig process with MMDVM, returned NAK");
					toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateGetStatus() {
		if(isStateChanged()) {
			GetStatus cmd = new GetStatus();

			if(!addMMDVMTransmitQueue(cmd)) {
				if(log.isWarnEnabled()) {log.warn(logTag + "Error occurred at send GetStatus to MMDVM.");}
				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
			}
			else if(noCheckResponse){
				nextState = ProcessState.Idle;
			}
			else {
				stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();
			}

			stateRetryCount = 0;
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;
				stateTimeKeeper.updateTimestamp();
			}
			else {
				if(log.isWarnEnabled())
					log.warn(logTag + "Timeout occurred at GetStatus process with MMDVM.");

				//toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
				nextState = ProcessState.Idle;
			}
		}
		else {
			Optional<MMDVMCommand> opCmd;
			while((opCmd = getCommandFromReceiveQueue(MMDVMFrameType.GET_STATUS)).isPresent()) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.GET_STATUS) {
					GetStatus gs = (GetStatus)cmd;

					if(!lockout && gs.isLockout()) {
						nextMode = MMDVMMode.MODE_IDLE;
						nextState = ProcessState.SetMode;
					}
					else if(gs.getDstarSpace() == 0) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Nothing DStar space.");

						toWaitState(1, TimeUnit.SECONDS, ProcessState.Idle);
					}
					else {
						nextState = ProcessState.Idle;
					}
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSetMode() {
		if(isStateChanged()) {
			SetMode cmd = new SetMode();
			cmd.setMode(nextMode);

			log.debug(logTag + "Mode change request " + cmd.getMode().toString() + " to MMDVM.");

			if(!addMMDVMTransmitQueue(cmd)) {
				if(log.isWarnEnabled()) {log.warn(logTag + "Error occurred at send SetMode to MMDVM.");}
				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
			}
			else if(noCheckResponse){
				currentMode = nextMode;
				nextState = ProcessState.Idle;
			}
			else {
				stateTimeKeeper.setTimeoutTime(500, TimeUnit.MILLISECONDS);
				stateTimeKeeper.updateTimestamp();
			}

			stateRetryCount = 0;
		}
		else if(stateTimeKeeper.isTimeout()) {
			if(stateRetryCount < stateRetryLimit) {
				stateRetryCount++;
				stateTimeKeeper.updateTimestamp();
			}
			else {
				if(log.isWarnEnabled()) {log.warn(logTag + "Timeout occurred at SetMode process from MMDVM.");}
//				toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
				nextState = ProcessState.Idle;
			}
		}
		else {
			Optional<MMDVMCommand> opCmd;
			while((opCmd = getCommandFromReceiveQueue(MMDVMFrameType.ACK, MMDVMFrameType.NAK)).isPresent()) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.ACK) {
					currentMode = nextMode;

					nextState = ProcessState.Idle;
				}
				else if(cmd.getCommandType() == MMDVMFrameType.NAK) {
					showNakReason(cmd, logTag + "Error occurred at SetMode process with MMDVM, returned NAK");
					toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateIdle() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(200, TimeUnit.MILLISECONDS);
			stateTimeKeeper.updateTimestamp();
			noCheckResponse = false;

			stateRetryCount = 0;
		}
		else if(modeTimeKeeper.isTimeout() && currentMode != MMDVMMode.MODE_IDLE) {
			nextMode = MMDVMMode.MODE_IDLE;

			nextState = ProcessState.SetMode;

			if(getHardwareType() == MMDVMHardwareType.DVMEGA) {noCheckResponse = true;}
		}
		else if(currentMode != nextMode) {
			nextState = ProcessState.SetMode;

			if(getHardwareType() == MMDVMHardwareType.DVMEGA) {noCheckResponse = true;}
		}
		else if(stateTimeKeeper.isTimeout()) {
			nextState = ProcessState.GetStatus;
//			noCheckResponse = true;
		}
		else {
			// Network -> MMDVM
			transportNetworkToMMDVM();


			Optional<MMDVMCommand> opCmd;
			while(
				(opCmd = getCommandFromReceiveQueue(
						MMDVMFrameType.ACK, MMDVMFrameType.NAK
					)
				).isPresent()
			) {
				MMDVMCommand cmd = opCmd.get();

				if(cmd.getCommandType() == MMDVMFrameType.NAK) {
					showNakReason(cmd, logTag + "Error occurred at SetMode process with MMDVM, returned NAK");
					toWaitState(10, TimeUnit.SECONDS, ProcessState.Initialize);
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

	private void toWaitState(long waitTime, TimeUnit timeUnit, ProcessState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = ProcessState.Wait;
		this.callbackState = callbackState;
	}

	private void parseCommand() {

		Queue<MMDVMCommand> receiveCommands = new LinkedList<>();

		boolean match;
		do {
			match = false;

			Optional<MMDVMCommand> command = null;

			mmdvmReceiveBufferLock.lock();
			try{
				BufferState.toREAD(mmdvmReceiveBuffer, mmdvmReceiveBufferState);

				if(
					(command = mmdvmGetVersionCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmAckCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmNakCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmGetStatusCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmDStarHeaderCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmDStarVoiceCommnad.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmDStarVoiceEOTCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmDStarVoiceLOSTCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmSerialCommand.parseCommand(mmdvmReceiveBuffer)).isPresent() ||
					(command = mmdvmTransparentCommand.parseCommand(mmdvmReceiveBuffer)).isPresent()
				) {
					match = true;

					if(log.isTraceEnabled()) {
						log.trace(
							logTag + "Receive " + command.get().getCommandType().toString() + " command from MMDVM."
						);
					}


					receiveCommands.add(command.get().clone());
				}
			}finally {
				mmdvmReceiveBufferLock.unlock();
			}
		}while(match);

		if(!receiveCommands.isEmpty()) {
			mmdvmQueueLocker.lock();
			try {
				mmdvmReceiveQueue.addAll(receiveCommands);
			}finally {
				mmdvmQueueLocker.unlock();
			}
		}
	}

	private boolean addMMDVMTransmitQueue(final MMDVMCommand command) {
		assert command != null;

		mmdvmQueueLocker.lock();
		try {
			return mmdvmTransmitQueue.add(command);
		}finally {
			mmdvmQueueLocker.unlock();
		}
	}

	private Optional<MMDVMCommand> getCommandFromMMDVMTransmitQueue(){
		mmdvmQueueLocker.lock();
		try {
			MMDVMCommand command = null;
			if(!mmdvmTransmitQueue.isEmpty())
				command = mmdvmTransmitQueue.poll();

			return Optional.ofNullable(command);
		}finally {
			mmdvmQueueLocker.unlock();
		}
	}

	private boolean sendMMDVMCommand() {
		Optional<MMDVMCommand> opCmd = null;
		while((opCmd = getCommandFromMMDVMTransmitQueue()).isPresent()) {
			if(log.isDebugEnabled()) {
				if(opCmd.get().getCommandType() == MMDVMFrameType.DSTAR_HEADER) {
					mmdvmTransferIntervalTimeCollector.tickHeader();
				}
				else if(opCmd.get().getCommandType() == MMDVMFrameType.DSTAR_DATA) {
					mmdvmTransferIntervalTimeCollector.tickData(false);
				}
				else if(
					opCmd.get().getCommandType() == MMDVMFrameType.DSTAR_EOT ||
					opCmd.get().getCommandType() == MMDVMFrameType.DSTAR_LOST
				) {
					mmdvmTransferIntervalTimeCollector.tickData(true);

					log.debug(logTag + "MMDVM data transfer interval report..." + mmdvmTransferIntervalTimeCollector);
				}
			}

			if(!sendMMDVMCommand(opCmd.get())) {return false;}
		}

		return true;
	}

	private boolean sendMMDVMCommand(final MMDVMCommand command) {
		if(
			command == null ||
			mmdvmPort == null || !mmdvmPort.isOpen()
		) {return false;}

		final ProcessResult<Boolean> result =
				new ProcessResult<>(Boolean.FALSE);

		if(log.isTraceEnabled())
			log.trace(logTag + "Send " + command.getCommandType().toString() + " command to MMDVM.");

		command.assembleCommand()
		.ifPresentOrElse(new Consumer<ByteBuffer>() {
			@Override
			public void accept(ByteBuffer t) {

				Timer limit = new Timer(20);
				while(!limit.isTimeout()) {
					byte[] data = ArrayUtil.convertByteBufferToByteArray(t);

					int writeBytes = mmdvmPort.writeBytes(data);
					if(writeBytes < 0) {
						if(log.isWarnEnabled())
							log.warn("Could not write to uart port. return code = " + writeBytes + ".");

						break;
					}
					else {
						if(log.isTraceEnabled()) {
							int savedPos = t.position();
							int savedLimit = t.limit();
							t.position(savedPos - writeBytes);
							t.limit(savedLimit);

							log.trace(
								logTag + "write " + writeBytes + "bytes to MMDVM..." + command.getCommandType() + "\n" +
								FormatUtil.byteBufferToHexDump(t, 4)
							);

							t.limit(savedLimit);
							t.position(savedPos);
						}
						int remainBytes = data.length - writeBytes;
						if(remainBytes > 0) {t.position(t.position() - remainBytes);}
					}
					if(!t.hasRemaining()) {
						result.setResult(Boolean.TRUE);
						break;
					}

					try {
						Thread.sleep(1);
					} catch (InterruptedException ex) {
						break;
					}
				}
			}
		}, new Runnable() {
			@Override
			public void run() {
				throw new NotImplementedException(command.getCommandType() + " is not implemented.");
			}
		});

		return result.getResult();
	}

	private Optional<MMDVMCommand> getCommandFromReceiveQueue() {
		mmdvmQueueLocker.lock();
		try {
			MMDVMCommand cmd = mmdvmReceiveQueue.poll();
			if(cmd != null)
				return Optional.of(cmd);
			else
				return Optional.empty();
		}finally {
			mmdvmQueueLocker.unlock();
		}
	}

	private Optional<MMDVMCommand> getCommandFromReceiveQueue(MMDVMFrameType... types){
		Optional<MMDVMCommand> opCmd = null;

		while((opCmd = getCommandFromReceiveQueue()).isPresent()) {
			MMDVMCommand command = (MMDVMCommand)opCmd.get();

			switch(command.getCommandType()) {
			case DSTAR_HEADER:
			case DSTAR_DATA:
			case DSTAR_EOT:
			case DSTAR_LOST:
				transportMMDVMToNetwork(command);
				break;

			case GET_STATUS:
				GetStatus gs = (GetStatus)command;

				if(!lockout && gs.isLockout()) {
					if(log.isWarnEnabled())
						log.warn(logTag + "MMVDM modem lockout error detected !");
				}

				lockout = gs.isLockout();
				dstarSpace = gs.getDstarSpace();

				break;

			case TRANSPARENT:
				Transparent transparent = (Transparent)command;
				if(transparent.getSerialData() != null) {
					transparentQueueLocker.lock();
					try {
						if(isTransparentEnable()) {
							while(transparentExternalQueue.size() >= 100)
								transparentExternalQueue.poll();

							transparentExternalQueue.add(ByteBuffer.wrap(transparent.getSerialData()));

							if(transparentProtocolProcessor != null)
								transparentProtocolProcessor.wakeupProcessThread();
						}

						while(transparentInternalQueue.size() >= 100)
							transparentInternalQueue.poll();

						transparentInternalQueue.add(ByteBuffer.wrap(transparent.getSerialData()));

					} finally {transparentQueueLocker.unlock();}
				}
				break;

			case SERIAL:
				Serial serial = (Serial)command;
				if(serial.getSerialData() != null && getTransparentSendFrameType() > 0) {
					int offset = getTransparentSendFrameType();
					if(offset > 1) {offset = 1;}

					final byte[] data = new byte[offset + serial.getSerialData().length];
					data[0] = serial.getCommandType().getTypeCode();
					for(int i = 0; i < serial.getSerialData().length; i++) {
						data[i + offset] = serial.getSerialData()[i];
					}

					transparentQueueLocker.lock();
					try {
						if(isTransparentEnable()) {
							while(transparentExternalQueue.size() >= 100)
								transparentExternalQueue.poll();

							transparentExternalQueue.add(ByteBuffer.wrap(data));
						}

						while(transparentInternalQueue.size() >= 100)
							transparentInternalQueue.poll();

						transparentInternalQueue.add(ByteBuffer.wrap(data));
					} finally {transparentQueueLocker.unlock();}
				}

				break;

			default:
				break;
			}

			if(types != null) {
				for(MMDVMFrameType type : types)
					if(command.getCommandType() == type) {return opCmd;}
			}
		}

		return Optional.empty();
	}

	private void transportMMDVMToNetwork(MMDVMCommand command) {
		assert command != null;

		if(dstarFrameID != 0 && dstarFrameTimeKeeper.isTimeout(1, TimeUnit.SECONDS)) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Reset DSTAR frame ID from " + String.format("%04X", dstarFrameID) + ".");

			dstarFrameID = 0;
		}

		DVPacket packet = null;
		boolean toNetwork = false;

		if(command.getCommandType() == MMDVMFrameType.DSTAR_HEADER) {
			dstarFrameID = DSTARUtils.generateFrameID();
			dstarFrameTimeKeeper.updateTimestamp();

			loopBlockID = DSTARUtils.generateLoopBlockID();

			clearDStarSequence();

			dstarHeader = ((DStarHeader)command).getHeader();

			packet = generateDvPacketForNetwork(dstarHeader);

			if(log.isDebugEnabled())
				log.debug(logTag + "Receive DSTAR HEADER, transporting to repeater...\n" + packet.toString());

			mmdvmReceiveSlowdataDecoder.reset();

			toNetwork = true;
		}
		else if(command.getCommandType() == MMDVMFrameType.DSTAR_DATA) {
			DStarVoice voice = (DStarVoice)command;

			if(
				dstarFrameID == 0 &&
				mmdvmReceiveSlowdataDecoder.decode(voice.getVoice().getDataSegment()) == DataSegmentDecoderResult.Header
			) {
				dstarFrameID = DSTARUtils.generateFrameID();
				dstarFrameTimeKeeper.updateTimestamp();

				clearDStarSequence();

				dstarHeader = mmdvmReceiveSlowdataDecoder.getHeader();

				packet = generateDvPacketForNetwork(dstarHeader);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Receive DSTAR HEADER by slow data, transporting to repeater...\n" + packet.toString()
					);
				}

				toNetwork = true;
			}
			else if(dstarFrameID != 0){
				dstarFrameTimeKeeper.updateTimestamp();

				final BackBoneHeader backbone = new BackBoneHeader(
					BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceData,
					dstarFrameID, (byte)dstarSequence
				);

				if(dstarHeader != null)
					packet = new DVPacket(backbone, dstarHeader, voice.getVoice());
				else
					packet = new DVPacket(backbone, voice.getVoice());

				incrementDStarSequence();

				toNetwork = true;
			}
		}
		else if(
			command.getCommandType() == MMDVMFrameType.DSTAR_EOT ||
			command.getCommandType() == MMDVMFrameType.DSTAR_LOST
		) {
			if(dstarFrameID != 0x0) {
				final VoiceAMBE voice = new VoiceAMBE();
				voice.setVoiceSegment(DSTARDefines.VoiceSegmentLastBytes);
				voice.setDataSegment(DSTARDefines.SlowdataEndBytes);

				final BackBoneHeader backbone =
					new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataLastFrame, dstarFrameID);

				if(dstarHeader != null)
					packet = new DVPacket(backbone, dstarHeader, voice);
				else
					packet = new DVPacket(backbone, voice);

				packet.getBackBone().setEndSequence();

				dstarFrameID = 0x0;
				dstarHeader = null;
				clearDStarSequence();

				toNetwork = true;

				if(
					log.isDebugEnabled() &&
					command.getCommandType() == MMDVMFrameType.DSTAR_LOST
				) {
					log.debug(
						logTag +
						"Receive DSTAR_LOST packet from MMDVM...\n" + packet.toString(4)
					);
				}
			}
		}

		if(toNetwork) {
			addReadPacket(new InternalPacket(loopBlockID, ConnectionDirectionType.Unknown, packet));

			if(currentMode == MMDVMMode.MODE_IDLE) {nextMode = MMDVMMode.MODE_DSTAR;}

			modeTimeKeeper.updateTimestamp();
		}
	}

	/**
	 * Network -> MMDVM
	 */
	private void transportNetworkToMMDVM() {

		networkQueueLocker.lock();
		try {
//				if(networkReceiveQueue.isEmpty()) {break;}
			while(!networkReceiveQueue.isEmpty()) {
				final DSTARPacket packet = networkReceiveQueue.poll();
				if(packet.getPacketType() != DSTARPacketType.DV) {continue;}

				if(currentToMMDVMFrameID != 0x0000 && toMMDVMFrameTimekeeper.isTimeout(2, TimeUnit.SECONDS))
					currentToMMDVMFrameID = 0x0000;

				if(currentToMMDVMFrameID == 0x0000 && packet.getDVPacket().hasPacketType(PacketType.Header)) {
					currentToMMDVMFrameID = packet.getBackBone().getFrameIDNumber();
					toMMDVMFrameTimekeeper.updateTimestamp();

					toMMDVMTransporter.writePacket(
						new TransmitterPacketImpl(PacketType.Header, packet, FrameSequenceType.Start)
					);

					if(log.isDebugEnabled())
						log.debug(logTag + "VOICE START from network\n" + packet.toString(4));

					//補償は3コア以上に限定する
					toMMDVMTransporter.setRepairEnable(SystemUtil.getAvailableProcessors() >= 3);
				}
				else if(currentToMMDVMFrameID == packet.getBackBone().getFrameIDNumber()) {
					toMMDVMFrameTimekeeper.updateTimestamp();

					if(packet.getDVPacket().hasPacketType(PacketType.Voice)) {
						toMMDVMTransporter.writePacket(
							new TransmitterPacketImpl(
								PacketType.Voice,
								packet,
								packet.isEndVoicePacket() ? FrameSequenceType.End : FrameSequenceType.None
							)
						);
					}

					if(packet.isEndVoicePacket()) {
						currentToMMDVMFrameID = 0x0000;

						if(log.isDebugEnabled())
							log.debug(logTag + "VOICE END from network\n" + packet.toString(4));
					}
				}
			}
		}finally {
			networkQueueLocker.unlock();
		}

		Optional<TransmitterPacket> opPacket = null;
		if(
			toMMDVMTransmitPendingPacket != null ||
			(opPacket = toMMDVMTransporter.readPacket()).isPresent()
		) {
			TransmitterPacket packet = null;
			if(toMMDVMTransmitPendingPacket != null) {
				packet = toMMDVMTransmitPendingPacket;

				toMMDVMTransmitPendingPacket = null;
			}
			else {
				packet = opPacket.get();
			}

			MMDVMCommand command = null;
			if(packet.getPacketType() == PacketType.Header) {
				if(!hasDStarSpace(dstarHeaderSpace)) {
					toMMDVMTransmitPendingPacket = packet;
					return;
				}

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Start transmit MMDVM(MY:" + String.valueOf(packet.getPacket().getRfHeader().getMyCallsign()) + ")"
					);
				}

				final DStarHeader header = new DStarHeader();
				header.setHeader(packet.getPacket().getRfHeader().clone());

				command = header;

				useDStarSpace(dstarHeaderSpace);
			}
			else if(packet.getPacketType() == PacketType.Voice) {
				if(
					!hasDStarSpace(dstarVoiceSpace) ||
					(packet.getPacket().isLastFrame() && !hasDStarSpace(dstarVoiceSpace * 2))
				) {
					toMMDVMTransmitPendingPacket = packet;
					return;
				}

				if(packet.getPacket().isLastFrame()) {	// ICOMファームウェアバグ対応
					final VoiceAMBE lastVoice = new VoiceAMBE();
					lastVoice.setVoiceSegment(DSTARDefines.VoiceSegmentLastBytesICOM);
					lastVoice.setDataSegment(DSTARDefines.SlowdataEndBytes);
					final DStarVoice voice = new DStarVoice();
					voice.setVoice(lastVoice);

					command = voice;

					useDStarSpace(dstarVoiceSpace * 2);
				}
				else {
					final DStarVoice voice = new DStarVoice();
					voice.setVoice(packet.getPacket().getVoiceData().clone());

					command = voice;

					useDStarSpace(dstarVoiceSpace);
				}
			}

			if(command != null) {
				if(!addMMDVMTransmitQueue(command)) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Send failed to MMDVM.");

					toWaitState(1, TimeUnit.SECONDS, ProcessState.Initialize);
				}

				if(packet.getPacket().isLastFrame()) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Stop transmit MMDVM.");

					if(!addMMDVMTransmitQueue(new DStarVoiceEOT())) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Send failed to MMDVM.");

						toWaitState(1, TimeUnit.SECONDS, ProcessState.Initialize);
					}
				}

				if(currentMode == MMDVMMode.MODE_IDLE) {nextMode = MMDVMMode.MODE_DSTAR;}

				modeTimeKeeper.updateTimestamp();
			}
		}
	}

	private void showNakReason(MMDVMCommand cmd, String message) {
		assert cmd != null;

		if(
			cmd != null &&
			cmd.getCommandType() == MMDVMFrameType.NAK &&
			cmd instanceof Nak
		) {
			final Nak nak = (Nak)cmd;

			if(log.isWarnEnabled())
				log.warn(message + "[Command:" + nak.getReasonCommand() + "/Reason:" + nak.getReason() + "]");
		}
	}

	private void incrementDStarSequence() {
		dstarSequence = DSTARUtils.getNextShortSequence((byte)dstarSequence);
	}

	private void clearDStarSequence() {
		dstarSequence = 0;
	}

	private void useDStarSpace(int space) {
		if(dstarSpace > space)
			dstarSpace -= space;
		else
			dstarSpace = 0;
	}

	private boolean hasDStarSpace(int space) {
		return dstarSpace >= space;
	}

	private DVPacket generateDvPacketForNetwork(final Header header) {
		assert header != null;

		final BackBoneHeader backbone =
			new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, dstarFrameID);

		final DVPacket packet = new DVPacket(backbone, header);

		return packet;
	}



	private class MMDVMTransparentProtocolProcessor extends SocketIOHandlerWithThread<BufferEntry>{

		private SocketIOEntryUDP channel;

		public MMDVMTransparentProtocolProcessor(
			ThreadUncaughtExceptionListener exceptionListener
		) {
			super(
				exceptionListener,
				MMDVMTransparentProtocolProcessor.class,
				MMDVMInterface.this.getSocketIO(),
				BufferEntry.class,
				HostIdentType.RemoteAddressPort
			);

			setProcessLoopIntervalTimeMillis(1000L);
		}

		@Override
		public boolean start() {
			if(
				!super.start(
					false,
					new Runnable() {
						@Override
						public void run() {
							channel = MMDVMInterface.this
								.getSocketIO().registUDP(
									new InetSocketAddress(getTransparentLocalPort()),
									MMDVMTransparentProtocolProcessor.this.getHandler(),
									MMDVMTransparentProtocolProcessor.this.getClass().getSimpleName() + "@" +
									getTransparentLocalPort() + "->" +
									getTransparentRemoteAddress() + ":" + getTransparentRemotePort()
								);
						}
					}
				) ||
				channel == null
			) {
				this.stop();

				closeChannel(channel);

				return false;
			}

			return true;
		}

		@Override
		public void stop() {
			closeChannel(channel);

			super.stop();
		}

		@Override
		public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
			MMDVMInterface.this.wakeupProcessThread();
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
			return null;
		}

		@Override
		public void disconnectedEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress
		) {

		}

		@Override
		public void errorEvent(
			SelectionKey key, ChannelProtocol protocol, InetSocketAddress localAddress,
			InetSocketAddress remoteAddress, Exception ex
		) {

		}

		@Override
		protected ThreadProcessResult threadInitialize() {
			return ThreadProcessResult.NoErrors;
		}

		@Override
		protected ThreadProcessResult processThread() {

			Optional<BufferEntry> opEntry = null;
			while((opEntry = getReceivedReadBuffer()).isPresent()) {
				opEntry.ifPresent(new Consumer<BufferEntry>() {
					@Override
					public void accept(BufferEntry buffer) {

						buffer.getLocker().lock();
						try {
							if(!buffer.isUpdate()) {return;}

							buffer.setBufferState(BufferState.toREAD(buffer.getBuffer(), buffer.getBufferState()));

							for (
								final Iterator<PacketInfo> itBufferBytes = buffer.getBufferPacketInfo().iterator();
								itBufferBytes.hasNext();
							) {
								final PacketInfo packetInfo = itBufferBytes.next();
								final int bufferLength = packetInfo.getPacketBytes();
								itBufferBytes.remove();

								if (bufferLength <= 0) {
									continue;
								}

								ByteBuffer receivePacket = ByteBuffer.allocate(bufferLength);
								for (int i = 0; i < bufferLength; i++) {
									receivePacket.put(buffer.getBuffer().get());
								}
								BufferState.toREAD(receivePacket, BufferState.WRITE);

								if(log.isTraceEnabled()) {
									StringBuilder sb = new StringBuilder(logTag);
									sb.append(bufferLength);
									sb.append(" bytes received.\n");
									sb.append("    ");
									sb.append("[RemoteHost]:");
									sb.append(buffer.getRemoteAddress());
									sb.append("/");
									sb.append("[LocalHost]:");
									sb.append(buffer.getLocalAddress());
									sb.append("\n");
									sb.append(FormatUtil.byteBufferToHexDump(receivePacket, 4));
									log.trace(sb.toString());

									receivePacket.rewind();
								}

								writeTransparent(receivePacket);
							}

							buffer.setUpdate(false);

						}finally{
							buffer.getLocker().unlock();
						}
					}
				});
			}

			transparentQueueLocker.lock();
			try {
				for(Iterator<ByteBuffer> it = transparentExternalQueue.iterator(); it.hasNext();) {
					final ByteBuffer data = it.next();
					it.remove();

					final InetSocketAddress dst =
						new InetSocketAddress(getTransparentRemoteAddress(), getTransparentRemotePort());

					writeUDPPacket(channel.getKey(), dst, data);
				}
			}finally {transparentQueueLocker.unlock();}

			return ThreadProcessResult.NoErrors;
		}

	}
}
