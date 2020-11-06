/**
 *
 */
package org.jp.illg.dstar.repeater.modem.icomap;


import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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
import org.jp.illg.dstar.service.web.handler.WebRemoteControlAccessPointHandler;
import org.jp.illg.dstar.service.web.model.AccessPointStatusData;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.DataSegmentDecoder.DataSegmentDecoderResult;
import org.jp.illg.dstar.util.DataSegmentEncoder;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;
import org.jp.illg.util.uart.UartInterface;
import org.jp.illg.util.uart.UartInterfaceFactory;
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
@Deprecated
@Slf4j
public class AccessPointInterface extends DStarRepeaterModemBase
	implements WebRemoteControlAccessPointHandler
{

	private static final int inetHeaderCacheLimit = 8;

	private static final int retryLimit = 2;

//	private final Object lock = new Object();

/*
	public interface AccessPointInterfaceReceiveEventHandler{
		public void handleAccessPointInterfaceReceiveEvent(List<AccessPointCommand> commands);
	}
*/
	public interface AccessPointInterfaceEventHandler{
		public void handleAccessPointInterfaceEvent(AccessPointInterfaceEvent event);
	}
	public static enum AccessPointInterfaceEvent{
		OPEN_ERROR,
		INITIALIZE_ERROR,
		HB_ERROR,
		SEND_ERROR,
		RECEIVE_ERROR,
		SEND_COMPLETE,
		RECEIVE_COMPLETE,
	}

	private class HeaderCommandCache{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private int frameID;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long activityTimestamp;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private AccessPointCommand voiceHeader;

		private HeaderCommandCache() {
			super();
		}

		public HeaderCommandCache(int frameID, AccessPointCommand voiceHeader) {
			this();
			setFrameID(frameID);
			setCreatedTimestamp(System.currentTimeMillis());
			updateActivityTimestamp();
			setVoiceHeader(voiceHeader);
		}

		public void updateActivityTimestamp() {
			setActivityTimestamp(System.currentTimeMillis());
		}
	}

	/**
	 * 通信状態列挙体
	 * @author AHND
	 *
	 */
	private static enum CommunicationState{
		INITIALIZE,
		PORT_OPEN,
		INITIALIZE_CMD,
		INITIALIZE_CMD_WAIT,
		WAIT_MAIN,
		WAIT_HB_CMD,
//		WAIT_ACK_HEADER_FROM_RIG,
		SEND_VOICE_TO_RIG,
		RECV_VOICE_FROM_RIG,
		PORT_ERROR,
		TIME_WAIT,
		;
	}

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
	private static final int packetSlipLimitDefault = 50;
	public static final String packetSlipLimitPropertyName = "PacketSlipLimit";

	@Getter
	@Setter
	private boolean disableSlowDataToInet;
	private static final boolean disableSlowDataToInetDefault = false;
	public static final String disableSlowDataToInetPropertyName = "DisableSlowDataToInet";

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

	private String logTag;

	private ByteBuffer recvBuffer;
	private BufferState recvBufferState;
	private boolean recvBufferUpdate;
	private long recvTimestamp;

	private final InitializeCommand initializeCommandResponse;
	private final HeartbeatCommand heartbeatCommandResponse;
	private final VoiceDataHeader voiceDataHeaderFromRig;
	private final VoiceData voiceDataFromRig;
	private final VoiceDataHeaderToRigResponse voiceDataHeaderToRigResponse;
	private final VoiceDataToRigResponse voiceDataToRigResponse;

	private List<AccessPointCommand> sendCommands;
	private Queue<AccessPointCommand> sendRequestQueue;
	private List<AccessPointCommand> recvCommands;

	private Map<Integer, HeaderCommandCache> inetHeaderCaches;

	@Getter
	@Setter
	private AccessPointInterfaceEventHandler eventHandler;

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

	private long performanceCounterTimestamp;
	private int packetCounter;

	private int currentFrameID;
//	private boolean currentFrameIDGenerated;

	private int retryCount;

	private UUID loopBlockID;

	private CommunicationState communicationState;
	private CommunicationState callbackState;
	private long communicationTimestamp;

	private static enum GatewayMode{
		Unknown,
		TerminalMode,
		AccessPointMode,
	}
	private GatewayMode gatewayMode;

	private AccessPointCommand lastSendCommand;

	private boolean codeSquelchReceived;
	private byte receivedCodeSquelchCode;

	private DSTARPacket rigHeaderCache;

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
			synchronized(recvBuffer) {
				//受信バッファが古ければ捨てる
				if(recvTimestamp + 5000 < System.currentTimeMillis()) {
					if(recvBufferState == BufferState.WRITE) {
						recvBuffer.flip();
						recvBufferState = BufferState.READ;
					}
					if(log.isDebugEnabled()) {
						recvBuffer.rewind();
						log.debug(
							logTag +
							"[" + getRigPortName() + "]" + " function serialEvent() purged receive cache data..." +
							FormatUtil.byteBufferToHex(recvBuffer)
						);
					}
					recvBuffer.clear();
					recvBufferState = BufferState.INITIALIZE;
					recvTimestamp = System.currentTimeMillis();
				}

				if(recvBufferState == BufferState.READ) {
					recvBuffer.compact();
					recvBufferState = BufferState.WRITE;
				}

				if(event.getReceiveData() != null && event.getReceiveData().length > 0) {
					//バッファをオーバーランするか？
					if(recvBuffer.remaining() < event.getReceiveData().length) {
						if(log.isWarnEnabled())
							log.warn(logTag + "[" + getRigPortName() + "]" + " Buffer overflow detected!");

						//超える分は破棄して入れられるだけ入れる
						recvBuffer.put(event.getReceiveData(),0,recvBuffer.remaining());
						recvBufferState = BufferState.WRITE;
					}else {
						//バッファに余裕があるので、書き込む
						recvBuffer.put(event.getReceiveData());	//受信バッファにコピー
						recvBufferState = BufferState.WRITE;
					}

					if(log.isTraceEnabled()) {
		//				log.trace("function serialEvent() received data..." + FormatUtil.bytesToHex(event.getReceiveData()));
						recvBuffer.flip();
						log.trace(
							logTag +
							"[" + getRigPortName() + "]" + " buffer data updated..." +
							FormatUtil.byteBufferToHex(recvBuffer)
						);
						recvBufferState = BufferState.READ;
					}
				}

				if(recvBufferState == BufferState.WRITE) {
					recvBuffer.flip();
					recvBufferState = BufferState.READ;
				}

				recvBufferUpdate = true;
			}

			wakeupProcessThread();

			return true;
		}
	};


	{
		initializeCommandResponse =
			new InitializeCommand();
		heartbeatCommandResponse =
			new HeartbeatCommand();
		voiceDataHeaderFromRig =
			new VoiceDataHeader();
		voiceDataFromRig =
			new VoiceData();
		voiceDataHeaderToRigResponse =
			new VoiceDataHeaderToRigResponse();
		voiceDataToRigResponse =
			new VoiceDataToRigResponse();

	}

	/**
	 * コンストラクタ
	 */
	public AccessPointInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		this(exceptionListener, workerExecutor, gateway, repeater, eventListener, null);
	}

	public AccessPointInterface(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway, @NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			AccessPointInterface.class.getSimpleName(),
			workerExecutor,
			ModemTypes.getTypeByClassName(AccessPointInterface.class.getName()),
			gateway,
			repeater,
			eventListener,
			socketIO
		);

		logTag = this.getClass().getSimpleName() + " : ";

		this.rigDataPort = UartInterfaceFactory.createUartInterface(exceptionListener);
		this.rigDataPort.addEventListener(uartEventListener);

		this.rigDataPortErrorCount = 0;

		this.enableCodeSquelch = enableCodeSquelchDefault;
		this.codeSquelchCode = codeSquelchCodeDefault;
		this.codeSquelchReceived = false;
		this.receivedCodeSquelchCode = (byte)0x00;

		this.rigHeaderCache = null;

		this.enablePacketSlip = enablePacketSlipDefault;
		this.packetSlipLimit = packetSlipLimitDefault;

		this.recvBuffer = ByteBuffer.allocate(2048);
		this.recvBufferState = BufferState.INITIALIZE;
		this.recvBufferUpdate = false;
		this.recvTimestamp = System.currentTimeMillis();
		this.clearCommunicationTimestamp();

		this.gatewayMode = GatewayMode.Unknown;

		setDataSegmentDecoder(new DataSegmentDecoder());
		setDataSegmentEncoder(new DataSegmentEncoder());

		this.voicePacketCounter = 0;
		this.voicePacketBackboneSequence = 0;

		this.voicePacketSlipCounter = 0;

		this.currentFrameID = 0x0;
//		this.currentFrameIDGenerated = false;

		this.clearPerformanceCounter();
		this.packetCounter = 0;

		this.recvCommands = new LinkedList<>();
		this.recvCommands.clear();
		this.sendRequestQueue = new ConcurrentLinkedQueue<AccessPointCommand>();
		this.sendRequestQueue.clear();
		this.sendCommands = new LinkedList<>();
		this.sendCommands.clear();

		this.communicationState = CommunicationState.INITIALIZE;
		this.callbackState = CommunicationState.INITIALIZE;

		this.lastSendCommand = null;

		this.inetHeaderCaches = new HashMap<>();

		this.retryCount = 0;

		this.loopBlockID = null;

		this.uartEventQueue = new TaskQueue<>(getWorkerExecutor());
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
		if(this.rigDataPort != null) {this.rigDataPort.closePort();}

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

	}

	@Override
	public boolean initializeWebRemoteControlInt(final WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeModemAccessPoint(this);
	}

	@Override
	protected ThreadProcessResult threadInitialize(){

		if(isDisableSlowDataToInet()) {
			log.info(
				logTag +
				"[" + getRigPortName() + "] " + "Configulation parameter " +
				disableSlowDataToInetPropertyName + " is true."
			);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {

	}

	@Override
	protected ThreadProcessResult processModem() {
		AccessPointCommand command = null;

		//受信バッファにデータがあれば解析
		analyzeReceiveBuffer();

		//送信要求コマンドがあればコピー
		synchronized(this.sendRequestQueue) {
			for(Iterator<AccessPointCommand> it = this.sendRequestQueue.iterator();it.hasNext();) {
				this.sendCommands.add(it.next());
				it.remove();
			}
		}


		switch(this.communicationState) {

		case INITIALIZE: {
			this.rigDataPort.setBaudRate(this.rigBitRate);
			this.rigDataPort.setDataBits(8);
			this.rigDataPort.setStopBitMode(UartStopBitModes.STOPBITS_ONE);
			this.rigDataPort.setParityMode(UartParityModes.PARITY_NONE);
			this.rigDataPort.setFlowControlMode(UartFlowControlModes.FLOWCONTROL_DISABLE);
			this.communicationState = CommunicationState.PORT_OPEN;
			break;
		}

		case PORT_OPEN: {
			if (!this.rigDataPort.openPort(this.rigPortName)) {
				this.callReceiveEventHandler(AccessPointInterfaceEvent.OPEN_ERROR);

				this.rigDataPort.closePort();

				if (this.rigDataPortErrorCount % 60 == 0) {
					if(log.isWarnEnabled()) {log.error(logTag + "[" + getRigPortName() + "]" + " Open failed.");}
				}

				if (this.rigDataPortErrorCount >= Integer.MAX_VALUE)
					this.rigDataPortErrorCount = 0;
				else
					this.rigDataPortErrorCount++;

				this.updateCommunicationTimestamp(TimeUnit.SECONDS.toMillis(10));
				this.callbackState = CommunicationState.INITIALIZE;
				this.communicationState = CommunicationState.TIME_WAIT;
			} else {
				this.communicationState = CommunicationState.INITIALIZE_CMD;
				this.rigDataPortErrorCount = 0;
			}
			break;
		}

		case INITIALIZE_CMD: {
			//受信バッファクリア
			this.receiveBufferClear();

			if (!this.sendCommand(command = new InitializeCommand())) {
				if(log.isErrorEnabled())
					log.error(logTag + "[" + getRigPortName() + "]" + " Could not transmit command, initialize process failed.");

				this.communicationState = CommunicationState.PORT_ERROR;
			} else {
				this.communicationState = CommunicationState.INITIALIZE_CMD_WAIT;
			}
			this.updateCommunicationTimestamp();

			break;
		}

		case INITIALIZE_CMD_WAIT: {
			if (this.isCommunicationTimeouted(TimeUnit.SECONDS.toMillis(10))) {
				this.callReceiveEventHandler(AccessPointInterfaceEvent.INITIALIZE_ERROR);
				this.communicationState = CommunicationState.INITIALIZE_CMD;
			} else if (this.recvCommands.size() > 0) {
				for (Iterator<AccessPointCommand> it = this.recvCommands.iterator(); it.hasNext(); ) {
					command = it.next();
					it.remove();

					if (command != null && command instanceof InitializeCommand) {
						this.communicationState = CommunicationState.WAIT_MAIN;
					}
				}
			}
			break;
		}

		case WAIT_MAIN: {
			//無線機からの受信データがあれば流す
			if (this.recvCommands.size() > 0) {
				for (Iterator<AccessPointCommand> it = this.recvCommands.iterator(); it.hasNext(); ) {
					AccessPointCommand recvCommand = it.next();
					it.remove();

					this.updateCommunicationTimestamp();

					boolean voiceCommand = false;
					if (
							recvCommand != null &&
							(
								recvCommand instanceof VoiceDataHeader ||
								(voiceCommand = recvCommand instanceof VoiceData)
							)
					) {
						boolean foundValidHeader = false;

						// we try sync by slowdata
						if (voiceCommand) {
							if (getDataSegmentDecoder().decode(recvCommand.getDataSegment()) == DataSegmentDecoderResult.Header) {
								AccessPointCommand newHeaderCommand = new VoiceDataHeader();
								newHeaderCommand.setDvHeader(getDataSegmentDecoder().getHeader());
								recvCommand = newHeaderCommand;

								if(log.isDebugEnabled()) {
									log.debug(
										logTag +
										"[" + getRigPortName() + "]" + " Found header information from slow data segment.\n" +
										recvCommand.getDvHeader().toString()
									);
								}

								foundValidHeader = true;
							} else {
								foundValidHeader = false;
							}    // cound not found header from slow data segment
						} else {
							foundValidHeader = true;
						}    // already received header command

						// we have valid header
						if (foundValidHeader) {
							Header receiveHeader = recvCommand.getDvHeader();

							// check header callsigns
							if (
									CallSignValidator.isValidUserCallsign(receiveHeader.getMyCallsign()) &&
									(
											CallSignValidator.isValidRepeaterCallsign(receiveHeader.getRepeater1Callsign()) ||
											DSTARDefines.DIRECT.equals(new String(receiveHeader.getRepeater1Callsign()))
									) &&
									(
										(
											CallSignValidator.isValidRepeaterCallsign(receiveHeader.getRepeater2Callsign()) ||
											CallSignValidator.isValidGatewayCallsign(receiveHeader.getRepeater2Callsign())
										) ||
										DSTARDefines.DIRECT.equals(new String(receiveHeader.getRepeater2Callsign()))
									) &&
										!DSTARDefines.EmptyLongCallsign.equals(new String(receiveHeader.getYourCallsign()))
							) {
/*
								synchronized (this.notifyCommands) {
									this.notifyCommands.add(recvCommand);
								}
*/

								getDataSegmentDecoder().reset();
								getDataSegmentEncoder().reset();

								this.codeSquelchReceived = false;
								this.communicationState = CommunicationState.RECV_VOICE_FROM_RIG;
								this.updateCommunicationTimestamp();
								//フレームID生成
								this.currentFrameID = super.generateFrameID();
//								this.currentFrameIDGenerated = true;

								//ゲートウェイ動作モード判定
								if (
										DSTARDefines.DIRECT.equals(String.valueOf(recvCommand.getRepeater2Callsign())) &&
										DSTARDefines.DIRECT.equals(String.valueOf(recvCommand.getRepeater1Callsign()))
								) {
									this.gatewayMode = GatewayMode.TerminalMode;
								} else {
									this.gatewayMode = GatewayMode.AccessPointMode;
								}

								loopBlockID = UUID.randomUUID();

								final BackBoneHeader headerBackbone =
									new BackBoneHeader(
										BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, currentFrameID
									);

								this.rigHeaderCache = new InternalPacket(
									loopBlockID, ConnectionDirectionType.Unknown, new DVPacket(headerBackbone, recvCommand.getDvHeader())
								);

								if(log.isTraceEnabled()) {
									log.trace(
										logTag +
										"[" + getRigPortName() + "]" + " Receive header packet from radio.\n" +
										recvCommand.toString()
									);
								}
							} else {
								if(log.isTraceEnabled()) {
									log.info(
										logTag +
										"[" + getRigPortName() + "]" + " Reject illegal header.\n" + receiveHeader.toString()
									);
								}
							}
						}
						break;
					}
				}

				//上位側からのヘッダがあれば無線機に送信
//			}else if(this.sendCommands.size() >= 6) {
//			} else if (!this.sendCommands.isEmpty() && this.sendCommands.size() >= 10) {
			} else if (!this.sendCommands.isEmpty()) {
				for (Iterator<AccessPointCommand> it = this.sendCommands.iterator(); it.hasNext(); ) {
					command = it.next();
//					it.remove();

					boolean frameStart = false;

					if (command != null && command instanceof VoiceDataHeader) {
						synchronized (this.inetHeaderCaches) {
							HeaderCommandCache cacheHeader =
									this.inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());
							// new header?
							if (cacheHeader == null) {
								// remove old header cache
								removeOldHeaderCache();

								HeaderCommandCache newHeaderCache =
										new HeaderCommandCache(command.getBackBone().getFrameIDNumber(), command);

								this.inetHeaderCaches.put(newHeaderCache.getFrameID(), newHeaderCache);

							} else {
								cacheHeader.updateActivityTimestamp();
							}
						}

						it.remove();
						frameStart = true;
					} else if (command != null && command instanceof VoiceData) {

						// Resync frame by voice data frame id
						synchronized (this.inetHeaderCaches) {
							HeaderCommandCache cacheHeader =
									this.inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());

							//同じFrameIDかつ、前回のパケット受信から30秒以内ならヘッダをキャッシュからヘッダを補完する
							if (
									cacheHeader != null &&
									(cacheHeader.getActivityTimestamp() + TimeUnit.SECONDS.toMillis(30)) > System.currentTimeMillis()
							) {
								command = cacheHeader.getVoiceHeader();

								if(log.isDebugEnabled()) {
									log.debug(
										logTag +
										"[" + getRigPortName() + "]" + " Resync inet frame from header cache...\n" +
										command.getDvPacket().toString()
									);
								}

								frameStart = true;
							} else {
								//ヘッダキャッシュがヒットせず、ヘッダが不明なので削除
								it.remove();
							}
						}
					} else {
						//ヘッダもしくは音声データ以外は削除
						it.remove();
					}

					if (frameStart) {
						//無線機へヘッダ送信
						if(this.sendCommand(command)) {
							if(log.isDebugEnabled())
								log.debug(logTag + "[" + getRigPortName() + "]" + " Start voice tranmit");

							this.communicationState = CommunicationState.SEND_VOICE_TO_RIG;

							// disable slow data encode, if control flag activated.
							if (
									command.getDvHeader().getFlags()[0] != 0x0 &&
									(command.getDvHeader().getFlags()[0] & RepeaterControlFlag.getMask()) != RepeaterControlFlag.AUTO_REPLY.getValue()
							)
								getDataSegmentEncoder().setEnableEncode(false);
							else
								getDataSegmentEncoder().setEnableEncode(true);

							getDataSegmentEncoder().reset();
							getDataSegmentEncoder().setCodeSquelchCode(this.getCodeSquelchCode());
							getDataSegmentEncoder().setEnableCodeSquelch(this.isEnableCodeSquelch());

							this.voicePacketCounter = 0;
							this.voicePacketBackboneSequence = 0;

							this.lastSendCommand = new VoiceData();
							ArrayUtil.copyOf(this.lastSendCommand.getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);

							this.updatePerformanceCounter();
							this.packetCounter = 0;

							if(log.isTraceEnabled())
								log.trace(logTag + "[" + getRigPortName() + "]" + " Send header packet to radio.\n" + command.toString());
						}else{
							if(log.isErrorEnabled())
								log.error(logTag + "[" + getRigPortName() + "]" + " Could not transmit command, transmit process failed.");

							this.communicationState = CommunicationState.PORT_ERROR;
						}
						this.updateCommunicationTimestamp();
						break;
					}
				}

				//生存確認コマンドを送信
			} else if (this.isCommunicationTimeouted(2000)) {
				this.receiveBufferClear();

				if(this.sendCommand(command = new HeartbeatCommand())){
					this.communicationState = CommunicationState.WAIT_HB_CMD;
				}else{
					if(log.isErrorEnabled()) {
						log.error(
							logTag +
							"[" + getRigPortName() + "]" + " Could not transmit command, heartbeat process failed."
						);
					}

					this.communicationState = CommunicationState.PORT_ERROR;
				}

				this.updateCommunicationTimestamp();

			}
			break;
		}

		case WAIT_HB_CMD: {
			//タイムアウト?
			if (this.isCommunicationTimeouted(10000)) {
				this.callReceiveEventHandler(AccessPointInterfaceEvent.HB_ERROR);
				this.updateCommunicationTimestamp();
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"[" + getRigPortName() + "]" + " Communication state " + this.communicationState.toString() + " timeout occured."
					);
				}
				this.communicationState = CommunicationState.INITIALIZE_CMD;

			} else if (this.recvCommands.size() > 0) {
				for (Iterator<AccessPointCommand> it = this.recvCommands.iterator(); it.hasNext(); ) {
					command = it.next();
					it.remove();

					if (command != null && command instanceof HeartbeatCommand) {
						this.updateCommunicationTimestamp();
						this.communicationState = CommunicationState.WAIT_MAIN;
					}
				}
			}
			break;
		}

		case SEND_VOICE_TO_RIG: {
			onSendVoiceToRig();

			break;
		}

		case RECV_VOICE_FROM_RIG: {
			if (this.isCommunicationTimeouted(500)) {
				this.callReceiveEventHandler(AccessPointInterfaceEvent.RECEIVE_ERROR);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"[" + getRigPortName() + "]" +
						" Communication state " + this.communicationState.toString() + " timeout occured."
					);
				}
				this.communicationState = CommunicationState.WAIT_MAIN;
				this.currentFrameID = 0x00;
//				this.currentFrameIDGenerated = false;
			} else {
				onReceiveVoiceFromRig();
			}
			break;
		}

		case TIME_WAIT:{
			if(isCommunicationTimeouted(0)){
				this.communicationState = this.callbackState;
				this.updateCommunicationTimestamp();
			}
			break;
		}

		case PORT_ERROR:{
			this.rigDataPort.closePort();
			this.communicationState = CommunicationState.INITIALIZE;
			break;
		}

		default: {
			if (this.rigDataPort != null) { this.rigDataPort.closePort(); }
			this.communicationState = CommunicationState.INITIALIZE;
			this.receiveBufferClear();
			break;
		}
		}

		// Clean old inet header caches
		synchronized(this.inetHeaderCaches) {
			for(Iterator<HeaderCommandCache> it = this.inetHeaderCaches.values().iterator(); it.hasNext();) {
				HeaderCommandCache cacheHeader = it.next();
				if((cacheHeader.getActivityTimestamp() + TimeUnit.MINUTES.toMillis(5)) < System.currentTimeMillis()) {
					it.remove();
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ModemStatusReport getStatusReportInternal(ModemStatusReport report) {

		return report;
	}

	@Override
	protected ModemStatusData createStatusDataInternal() {
		final AccessPointStatusData status =
			new AccessPointStatusData(getWebSocketRoomId());

		return status;
	}

	@Override
	protected Class<? extends ModemStatusData> getStatusDataTypeInternal() {
		return AccessPointStatusData.class;
	}

	@Override
	protected ProcessIntervalMode getProcessLoopIntervalMode() {
		return ProcessIntervalMode.Normal;
	}

	private void onSendVoiceToRig() {

		boolean timeout = this.isCommunicationTimeouted(TimeUnit.MILLISECONDS.toMillis(750));
		boolean retry = (retryCount < retryLimit) && timeout;

		//応答が返って来ていない場合
		if (timeout) {
			//再試行回数制限到達か？
			if(!retry) {
				//再試行回数制限に達したので諦めて、初期化ステートに以降
				this.callReceiveEventHandler(AccessPointInterfaceEvent.SEND_ERROR);

				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"[" + getRigPortName() + "]" + " Communication state " + this.communicationState.toString() +
						" timeout occured."
					);
				}

				this.communicationState = CommunicationState.INITIALIZE_CMD;
				retryCount = 0;
			}else {
				retryCount++;
				if(this.lastSendCommand != null){sendVoiceToRig(this.lastSendCommand);}
			}
			return;
		}


		if(this.recvCommands.isEmpty()) {return;}

		AccessPointCommand command = null;

		boolean responseFound = false;

		//ボイスデータ応答を探してボイスデータを送信
		for(Iterator<AccessPointCommand> it = this.recvCommands.iterator();it.hasNext();) {
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
			}else{continue;}
		}

		//音声パケットの応答があったか？
		if(responseFound){

			//スリップパケット挿入有効で、上位側からのコマンドが何も無い場合にはスライドパケットを挿入する
			if(this.isEnablePacketSlip() && sendCommands.isEmpty()) {

				if(this.voicePacketSlipCounter == 0) {
					if(log.isDebugEnabled())
						log.debug(logTag + "[" + getRigPortName() + "]" + " [Underflow detected!]");
				}

				//スリップパケット挿入が連続していないか？
				if(this.voicePacketSlipCounter < this.getPacketSlipLimit()) {

					if(this.lastSendCommand != null && this.lastSendCommand instanceof VoiceData) {
						this.sendCommands.add(this.lastSendCommand);
					}
					else {
						AccessPointCommand nullVoice = new VoiceData();
						nullVoice.setBackBone(this.lastSendCommand.getBackBone());
						ArrayUtil.copyOf(nullVoice.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentNullBytes);

						this.sendCommands.add(nullVoice);
					}

					this.voicePacketSlipCounter++;
				}
				else {
					//パケットスリップリミットへ到達したので、終端パケットを挿入する
					ArrayUtil.copyOf(this.lastSendCommand.getVoiceSegment(), VoiceAMBE.lastVoiceSegment);
					this.lastSendCommand.getBackBone().setEndSequence();

					this.sendCommands.add(this.lastSendCommand);

					if(log.isDebugEnabled())
						log.debug(logTag + "[" + getRigPortName() + "]" + " Inserted end packet, limit of packet slip.");

					this.voicePacketSlipCounter = 0;

					this.communicationState = CommunicationState.INITIALIZE_CMD;

				}
			}
			//パケットスリップが無効で、上位側からのコマンドが無い場合には、終端コマンドを挿入する
			else if(!this.isEnablePacketSlip() && sendCommands.isEmpty()) {
				ArrayUtil.copyOf(this.lastSendCommand.getVoiceSegment(), VoiceAMBE.lastVoiceSegment);
				this.lastSendCommand.getBackBone().setEndSequence();

				this.sendCommands.add(this.lastSendCommand);
			}
			//上位側からのコマンドがある場合
			else if(!sendCommands.isEmpty()){
				if(this.voicePacketSlipCounter != 0) {
					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"[" + getRigPortName() + "]" + " add slide packet because underflow detected...slip count:" +
							(this.voicePacketSlipCounter + 1)
						);
					}
				}

				this.voicePacketSlipCounter = 0;
			}


			boolean foundSendCommand = false;

			for(Iterator<AccessPointCommand> comIt = this.sendCommands.iterator();comIt.hasNext();) {
				command = comIt.next();
				comIt.remove();

				if(command != null && command instanceof VoiceData) {
					foundSendCommand = true;
					break;
				}else {continue;}
			}

			if(!foundSendCommand){
				if(log.isWarnEnabled())
					log.warn("Could not found command from inet command queue.");

				AccessPointCommand endVoice = new VoiceData();
				endVoice.setBackBone(this.lastSendCommand.getBackBone());
				endVoice.getBackBone().setEndSequence();
				ArrayUtil.copyOf(endVoice.getVoiceData().getVoiceSegment(), DSTARDefines.VoiceSegmentLastBytes);
				ArrayUtil.copyOf(endVoice.getVoiceData().getDataSegment(), DSTARDefines.SlowdataLastBytes);

				command = endVoice;
			}

			// update header cache last activity
			synchronized (this.inetHeaderCaches) {
				HeaderCommandCache cacheHeader =
						this.inetHeaderCaches.get(command.getBackBone().getFrameIDNumber());
				if(cacheHeader != null) {cacheHeader.updateActivityTimestamp();}
			}

			//パケットカウンタセット
			((VoiceData)command).setPacketCounter(
				this.voicePacketCounter,
				!command.isEndPacket() ?
						this.voicePacketBackboneSequence : this.voicePacketBackboneSequence | 0x40
			);

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


			//送信
			sendVoiceToRig(command);

			if(this.voicePacketCounter >= 0xFF)
				this.voicePacketCounter = 0;
			else
				this.voicePacketCounter++;

			if(this.voicePacketBackboneSequence >= 0x14)
				this.voicePacketBackboneSequence = 0x00;
			else
				this.voicePacketBackboneSequence++;


		}
	}

	private void onReceiveVoiceFromRig() {
		for(Iterator<AccessPointCommand> it = this.recvCommands.iterator();it.hasNext();) {
			AccessPointCommand recvCommand = it.next();
			it.remove();

			if(!(recvCommand instanceof VoiceData)) {continue;}

//			this.notifyCommands.add(recvCommand);

			//コードスケルチ検出処理
			DataSegmentDecoderResult decoderResult =
					getDataSegmentDecoder().decode(recvCommand.getDataSegment());

			switch(decoderResult) {
			case CSQL:{
				this.codeSquelchReceived = true;
				this.receivedCodeSquelchCode = (byte)getDataSegmentDecoder().getCsqlCode();
				break;
			}
			default:
				break;
			}

			//SlowData除去処理
			if(isDisableSlowDataToInet())
				getDataSegmentEncoder().encode(recvCommand.getDataSegment());


			if(
				this.gatewayMode == GatewayMode.TerminalMode ||
				!this.enableCodeSquelch ||
				(
					this.enableCodeSquelch && this.codeSquelchReceived &&
					this.receivedCodeSquelchCode == this.codeSquelchCode
				)
			) {
				if(this.rigHeaderCache != null) {
					addReadPacket(rigHeaderCache);
					this.rigHeaderCache = null;
				}

				final DSTARPacket receivePacket =
					new InternalPacket(loopBlockID, ConnectionDirectionType.Unknown, recvCommand.getDvPacket());
				receivePacket.getBackBone().setFrameIDNumber(this.currentFrameID);
				addReadPacket(receivePacket);

				if(log.isTraceEnabled())
					log.trace(logTag + "[" + getRigPortName() + "]" + " Receive voice packet from radio.\n" + recvCommand.toString());
			}

			//パケット終端か？
			if(recvCommand.isEndPacket()) {
				this.callReceiveEventHandler(AccessPointInterfaceEvent.RECEIVE_COMPLETE);
				this.communicationState = CommunicationState.WAIT_MAIN;
				this.currentFrameID = 0x0;
				break;
			}

			this.updateCommunicationTimestamp();
		}
	}

	private void updateCommunicationTimestamp() {
		this.communicationTimestamp = System.currentTimeMillis();
	}

	private void updateCommunicationTimestamp(long timeLimitMillis) {
		this.communicationTimestamp = System.currentTimeMillis() + timeLimitMillis;
	}

	private void clearCommunicationTimestamp() {
		this.communicationTimestamp = 0;
	}

	private boolean isCommunicationTimeouted(long timeoutMillis) {
		return this.communicationTimestamp + timeoutMillis < System.currentTimeMillis()?true:false;
	}

	private void updatePerformanceCounter() {
		this.performanceCounterTimestamp = System.currentTimeMillis();
	}

	private void clearPerformanceCounter() {
		this.performanceCounterTimestamp = 0;
	}

	private boolean isPersormanceCounterTimeout(long timeoutMillis) {
		return this.performanceCounterTimestamp + timeoutMillis < System.currentTimeMillis()?true:false;
	}

	private void callReceiveEventHandler(AccessPointInterfaceEvent event) {
		if(
				this.getEventHandler()!= null &&
				this.getEventHandler() instanceof AccessPointInterfaceEventHandler
		) {this.getEventHandler().handleAccessPointInterfaceEvent(event);}
	}

	private void receiveBufferClear() {
		synchronized(this.recvBuffer) {
			this.recvBuffer.clear();
			this.recvBufferState = BufferState.INITIALIZE;
			this.recvBufferUpdate = false;
		}
		this.recvTimestamp = System.currentTimeMillis();
	}

	private void analyzeReceiveBuffer() {
		AccessPointCommand command = null;
		boolean match = false;

		synchronized(this.recvBuffer) {

			if(!this.recvBufferUpdate) {return;}

			do {
				if(
						//初期化コマンド応答か？
						(command = initializeCommandResponse.analyzeCommandData(this.recvBuffer)) != null ||
						//生存確認コマンドか？
						(command = heartbeatCommandResponse.analyzeCommandData(this.recvBuffer)) != null ||
						//ボイスヘッダか？
						(command = voiceDataHeaderFromRig.analyzeCommandData(this.recvBuffer)) != null ||
						//ボイス&データか？
						(command = voiceDataFromRig.analyzeCommandData(this.recvBuffer)) != null ||
						//ボイスヘッダに対する応答か？
						(command = voiceDataHeaderToRigResponse.analyzeCommandData(this.recvBuffer)) != null ||
						//ボイスデータに対する応答か？
						(command = voiceDataToRigResponse.analyzeCommandData(this.recvBuffer)) != null
				) {
					//受信コマンドキューへ追加
					this.recvCommands.add(command.clone());
					this.recvTimestamp = System.currentTimeMillis();

					match = true;
				}else {
					match = false;
				}
			}while(match);

			this.recvBufferUpdate = false;
		}

		return;
	}

	/**
	 * 送信コマンドをキューに追加する
	 * @param command 送信コマンド
	 * @return キューへ正常に追加出来ればtrue
	 */
	public boolean addSendCommand(AccessPointCommand command) {
		if(command == null || !(command instanceof AccessPointCommand))
			throw new IllegalArgumentException();

		if(!super.isWorkerThreadAvailable()) {return false;}

		synchronized(this.sendRequestQueue) {
			this.sendRequestQueue.add(command);
		}

		super.wakeupProcessThread();

		return true;
	}

	@Override
	public boolean writePacketInternal(@NonNull DSTARPacket packet) {
		boolean success = true;

		if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
			final AccessPointCommand command = new VoiceDataHeader();
			command.setDvHeader(packet.getRfHeader().clone());
			command.setBackBone(packet.getBackBone().clone());

			success &= addSendCommand(command);
		}

		if(packet.getDVPacket().hasPacketType(PacketType.Header)) {
			final AccessPointCommand command = new VoiceData();
			command.setVoiceData(packet.getVoiceData().clone());
			command.setBackBone(packet.getBackBone().clone());

			success &= addSendCommand(command);
		}

		return success;
	}

	private boolean sendVoiceToRig(AccessPointCommand command) {

		//ボイスデータ送信
		boolean portError = !this.sendCommand(command);

		if(!portError) {
			if(log.isTraceEnabled())
				log.trace(logTag + "[" + getRigPortName() + "]" + " Send voice packet to radio.\n" + command.toString());
		}
		else {
			if(log.isErrorEnabled())
				log.error(logTag + "[" + getRigPortName() + "]" + " Could not transmit command, voice transmit process failed.");
		}

		//
		this.lastSendCommand = command;

		if(portError){
			this.communicationState = CommunicationState.PORT_ERROR;
		}
		//ボイスデータ終端か？
		else if(command.isEndPacket()) {
			this.callReceiveEventHandler(AccessPointInterfaceEvent.SEND_COMPLETE);
			this.communicationState = CommunicationState.WAIT_MAIN;

			if(log.isDebugEnabled())
				log.debug(logTag + "[" + getRigPortName() + "]" + " End of voice tranmit");
		}


		this.updateCommunicationTimestamp();

		if(this.isPersormanceCounterTimeout(5000)) {
			this.updatePerformanceCounter();

			if(log.isTraceEnabled()) {
				log.trace(
					logTag +
					"[" + getRigPortName() + "]" + " Voice packet send rate ..." + this.packetCounter / 5 + "packets/sec"
				);
			}

			this.packetCounter = 0;
		}else {
			this.packetCounter++;
		}

		return portError;
	}

	private boolean sendCommand(AccessPointCommand command) {
		assert command != null;

		// AccessPoint/Terminalモードの場合、識別符号は「G」限定?
		if(command instanceof VoiceDataHeader) {
			command.getDvHeader().getFlags()[0] = (byte)(
					(
						((command.getDvHeader().getFlags()[0] & ~RepeaterRoute.getMask()) | RepeaterRoute.TO_TERMINAL.getValue())
					));

//					(byte)(RepeaterRoute.TO_REPEATER.getValue() | RepeaterFlags.NOTHING_NULL.getValue());

//			command.getDvHeader().getRepeater1Callsign()[DStarDefines.CallsignFullLength - 1] = 'G';
//			command.getDvHeader().getRepeater2Callsign()[DStarDefines.CallsignFullLength - 1] = 'G';
		}

		byte[] sendBytes = command.assembleCommandData();

		return this.rigDataPort.writeBytes(sendBytes, sendBytes.length) > 0;
	}

	@Override
	public boolean setPropertiesInternal(ModemProperties properties) {
		if(properties == null) {return false;}

		setRigPortName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				rigPortNamePropertyName,
				rigPortNameDefault
			)
		);

		logTag = this.getClass().getSimpleName() + "[" + getRigPortName() + "] : ";

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
					"Illegal CSQL code = " + csqlCode + ", replace to default code = " + codeSquelchCodeDefault + "."
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

		return properties;
	}

	@Override
	public boolean hasWriteSpace() {
		return true;
	}

	/**
	 * 古いヘッダキャッシュ(上位側)を削除する
	 */
	private void removeOldHeaderCache() {
		synchronized(this.inetHeaderCaches) {
			while (this.inetHeaderCaches.size() >= inetHeaderCacheLimit) {
				Optional<HeaderCommandCache> pollCacheHeader =
						Stream.of(this.inetHeaderCaches)
						.min(
								ComparatorCompat.comparingLong(
										new ToLongFunction<Map.Entry<Integer, HeaderCommandCache>>() {
												@Override
												public long applyAsLong(Map.Entry<Integer, HeaderCommandCache> integerHeaderCommandCacheEntry) {
													return integerHeaderCommandCacheEntry.getValue().getActivityTimestamp();
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
						);
				pollCacheHeader.ifPresent(
						new Consumer<HeaderCommandCache>() {
							@Override
							public void accept(HeaderCommandCache headerCommandCache) {
								inetHeaderCaches.remove(headerCommandCache.getFrameID());
							}
						}
				);
			}
		}
	}
}
