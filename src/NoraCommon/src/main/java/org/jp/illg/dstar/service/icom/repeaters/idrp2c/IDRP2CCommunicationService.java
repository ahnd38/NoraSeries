package org.jp.illg.dstar.service.icom.repeaters.idrp2c;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneManagementData;
import org.jp.illg.dstar.model.BackBonePacket;
import org.jp.illg.dstar.model.BackBonePacketDirectionType;
import org.jp.illg.dstar.model.BackBonePacketType;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.service.icom.IcomPacketTool;
import org.jp.illg.dstar.service.icom.model.ICOMRepeaterType;
import org.jp.illg.dstar.service.icom.repeaters.IcomRepeaterCommunicator;
import org.jp.illg.dstar.service.icom.repeaters.model.CommunicatorEvent;
import org.jp.illg.dstar.service.icom.repeaters.model.FrameEntry;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.socketio.SocketIOEntryUDP;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.napi.SocketIOHandler;
import org.jp.illg.util.socketio.napi.SocketIOHandlerInterface;
import org.jp.illg.util.socketio.napi.define.ChannelProtocol;
import org.jp.illg.util.socketio.napi.define.PacketTracerFunction;
import org.jp.illg.util.socketio.napi.define.ParserFunction;
import org.jp.illg.util.socketio.napi.define.UnknownPacketHandler;
import org.jp.illg.util.socketio.napi.model.BufferEntry;
import org.jp.illg.util.socketio.support.HostIdentType;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.thread.task.TaskQueue;

import com.annimon.stream.function.Consumer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IDRP2CCommunicationService extends IcomRepeaterCommunicator{

	/**
	 * コントローラに送信したパケットがタイムアウトするまでの時間(ms)
	 */
	private static final int controllerTransmitTimeoutMillis = 200;

	/**
	 * コントローラに送信するパケットの再送制限回数
	 */
	private static final int controllerTransmitRetryLimit = 5;

	/**
	 * フレームタイムアウト時間(秒)
	 */
	private static final int frameTimeoutSeconds = 1;

	/**
	 * DVフレーム中にヘッダを挿入する
	 *
	 * ※2020年現在では仕様書通りに挿入すると,何故かレピータから正常なRF出力が得られないので、
	 * falseとすること
	 */
	private static final boolean headerInsertDVFrameDefault = false;



	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int gatewayLocalPort;
	@Getter
	private static final int gatewayLocalPortDefault = 20000;
	private static final String gatewayLocalPortPropertyName = "GatewayLocalPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int monitorLocalPort;
	@Getter
	private static final int monitorLocalPortDefault = 20001;
	private static final String monitorLocalPortPropertyName = "MonitorLocalPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String controllerAddress;
	@Getter
	private static final String controllerAddressDefault = null;
	private static final String controllerAddressPropertyName = "ControllerAddress";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int controllerPort;
	@Getter
	private static final int controllerPortDefault = 20000;
	private static final String controllerPortPropertyName = "ControllerPort";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean headerInsertDVFrame;
	private static final String headerInsertDVFramePropertyName = "HeaderInsertDVFrame";


	private String logTag = createLogTag();

	private final ExecutorService workerExecutor;

	private final Timer controllerKeepaliveTimekeeper;

	private final TaskQueue<Object, Boolean> receivePacketEventQueue;

	private boolean isControllerAddressPresent;
	private InetSocketAddress controllerAddressPort;

	private final Queue<BackBonePacket> toControllerPackets;
	private final Timer controllerTransferTimekeeper;
	private int controllerTransferRetryCount;
	private BackBonePacket controllerTransmittingPacket;


	private int magicNumber;

	private final Map<Integer, FrameEntry> downlinkFrameEntries;
	private final Map<Integer, FrameEntry> uplinkFrameEntries;

	private SocketIOEntryUDP gatewayLocalPortSocket;
	private SocketIOHandler<BufferEntry> gatewayLocalPortHandler;
	private SocketIOEntryUDP monitorLocalPortSocket;
	private SocketIOHandler<BufferEntry> monitorLocalPortHandler;

	private final SocketIOHandlerInterface socketHandler =
		new SocketIOHandlerInterface() {
			@Override
			public void updateReceiveBuffer(InetSocketAddress remoteAddress, int receiveBytes) {
				receivePacketEventQueue.addEventQueue(receivePacketEventDispacher, getExceptionListener());
			}

			@Override
			public OperationRequest readEvent(
				SelectionKey key,
				ChannelProtocol protocol,
				InetSocketAddress localAddress,
				InetSocketAddress remoteAddress
			) {
				return null;
			}

			@Override
			public void errorEvent(
				SelectionKey key,
				ChannelProtocol protocol,
				InetSocketAddress localAddress,
				InetSocketAddress remoteAddress,
				Exception ex
			) {

			}

			@Override
			public void disconnectedEvent(
				SelectionKey key,
				ChannelProtocol protocol,
				InetSocketAddress localAddress,
				InetSocketAddress remoteAddress
			) {

			}

			@Override
			public OperationRequest connectedEvent(
				SelectionKey key,
				ChannelProtocol protocol,
				InetSocketAddress localAddress,
				InetSocketAddress remoteAddress
			) {
				return null;
			}

			@Override
			public OperationRequest acceptedEvent(
				SelectionKey key,
				ChannelProtocol protocol,
				InetSocketAddress localAddress,
				InetSocketAddress remoteAddress
			) {
				return null;
			}
		};

	private final Consumer<Object> receivePacketEventDispacher = new Consumer<Object>() {
		@Override
		public void accept(Object t) {
			parseControllerPacket();

			serviceProcess();
		}
	};

	private final PacketTracerFunction controllerPacketTracer = new PacketTracerFunction() {
		@Override
		public void accept(
			final ByteBuffer buffer,
			final InetSocketAddress remoteAddress,
			final InetSocketAddress localAddress
		) {
			if(log.isTraceEnabled()) {
				final StringBuilder sb = new StringBuilder(logTag);
				sb.append(buffer.remaining());
				sb.append(" bytes received from ");
				sb.append(remoteAddress);
				sb.append(".\n");
				sb.append(FormatUtil.byteBufferToHexDump(buffer, 4));

				log.trace(sb.toString());
			}
		}
	};

	private final ParserFunction controllerPacketParser = new ParserFunction() {
		@Override
		public Boolean apply(
			final ByteBuffer buffer,
			final Integer packetSize,
			final InetSocketAddress remoteAddress,
			final InetSocketAddress localAddress
		) {
			BackBonePacket packet = null;
			if (
				(packet = IcomPacketTool.parseDummyPacket(buffer)) != null ||
				(packet = IcomPacketTool.parseDDPacket(buffer)) != null ||
				(packet = IcomPacketTool.parseDVPacket(buffer)) != null ||
				(packet = IcomPacketTool.parseErrorPacket(buffer)) != null ||
				(packet = IcomPacketTool.parsePositionUpdatePacket(buffer)) != null ||
				(packet = IcomPacketTool.parseInitPacket(buffer))!= null
			) {
				packet.setRemoteAddress(remoteAddress);
				packet.setLocalAddress(localAddress);

				if(log.isTraceEnabled())
					log.trace(logTag + "Receive packet from " + remoteAddress + "\n" + packet.toString(4));

				processControllerReceivePacket(packet);

				return true;
			}

			return false;
		}
	};

	private final UnknownPacketHandler controllerUnknownPacketHandler = new UnknownPacketHandler() {
		@Override
		public void accept(
			final ByteBuffer buffer,
			final InetSocketAddress remoteAddress,
			final InetSocketAddress localAddress
		) {
			if(log.isDebugEnabled()) {
				log.debug(
					logTag +
					"Unknown packet received(length:" + buffer.remaining() + "bytes)\n" +
					FormatUtil.byteBufferToHexDump(buffer, 4)
				);
			}
		}
	};

	public IDRP2CCommunicationService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor,
		final SocketIO socketio,
		final EventListener<CommunicatorEvent> eventListener
	) {
		super(
			IDRP2CCommunicationService.class,
			systemID,
			exceptionListener,
			workerExecutor, socketio,
			eventListener,
			100, TimeUnit.MILLISECONDS
		);

		this.workerExecutor = workerExecutor;

		receivePacketEventQueue = new TaskQueue<>(this.workerExecutor);

		controllerKeepaliveTimekeeper = new Timer();

		toControllerPackets = new ConcurrentLinkedQueue<>();

		controllerTransferTimekeeper = new Timer();
		controllerTransferRetryCount = 0;
		controllerTransmittingPacket = null;

		downlinkFrameEntries = new ConcurrentHashMap<>();
		uplinkFrameEntries = new ConcurrentHashMap<>();

		setGatewayLocalPort(gatewayLocalPortDefault);
		setMonitorLocalPort(monitorLocalPortDefault);
		setControllerAddress(controllerAddressDefault);
		setControllerPort(controllerPortDefault);
		setHeaderInsertDVFrame(headerInsertDVFrameDefault);
	}

	@Override
	public ICOMRepeaterType getRepeaterControllerType() {
		return ICOMRepeaterType.ID_RP2C;
	}

	@Override
	public boolean start() {
		getLocker().lock();
		try {
			gatewayLocalPortSocket =
				getSocketio().registUDP(
					new InetSocketAddress(getGatewayLocalPort()),
					gatewayLocalPortHandler = new SocketIOHandler<>(
						socketHandler, getSocketio(),
						getWorkerExecutor(),
						getExceptionListener(),
						BufferEntry.class, HostIdentType.RemoteLocalAddressPort
					),
					this.getClass().getSimpleName() + "(" + gatewayLocalPortPropertyName + ")@" + getGatewayLocalPort()
				);
			if(gatewayLocalPortSocket == null) {
				if(log.isErrorEnabled()) {
					log.error(
						logTag +
						"Could not regist " + gatewayLocalPortPropertyName +
						", port number = " + getGatewayLocalPort()
					);
				}

				closeSocket();

				return false;
			}

			monitorLocalPortSocket =
				getSocketio().registUDP(
					new InetSocketAddress(getMonitorLocalPort()),
					monitorLocalPortHandler = new SocketIOHandler<>(
						socketHandler, getSocketio(),
						getWorkerExecutor(),
						getExceptionListener(),
						BufferEntry.class, HostIdentType.RemoteLocalAddressPort
					),
					this.getClass().getSimpleName() + "(" + monitorLocalPortPropertyName + ")@" + getMonitorLocalPort()
				);
			if(monitorLocalPortSocket == null) {
				if(log.isErrorEnabled()) {
					log.error(
						logTag +
						"Could not regist " + monitorLocalPortPropertyName +
						", port number = " + getMonitorLocalPort()
					);
				}

				closeSocket();

				return false;
			}
		}finally {
			getLocker().unlock();
		}

		return true;
	}

	@Override
	public void stop() {
		closeSocket();
	}

	@Override
	public boolean isRunning() {
		return true;
	}

	@Override
	public boolean isReady() {
		return controllerAddressPort != null;
	}

	@Override
	public boolean setProperties(Properties prop) {
		setGatewayLocalPort(PropertyUtils.getInteger(
			prop,
			gatewayLocalPortPropertyName,
			getGatewayLocalPortDefault()
		));
		if(getGatewayLocalPort() <= 0 || getGatewayLocalPort() > 65535) {
			if(log.isErrorEnabled())
				log.error(logTag + "Invalid GatewayLocalPort " + getGatewayLocalPort());

			closeSocket();

			return false;
		}

		setMonitorLocalPort(PropertyUtils.getInteger(
			prop,
			monitorLocalPortPropertyName,
			getMonitorLocalPortDefault()
		));
		if(getMonitorLocalPort() <= 0 || getMonitorLocalPort() > 65535) {
			if(log.isErrorEnabled())
				log.error(logTag + "Invalid MonitorLocalPort " + getMonitorLocalPort());

			closeSocket();

			return false;
		}

		setControllerAddress(PropertyUtils.getString(
			prop,
			controllerAddressPropertyName,
			getControllerAddressDefault()
		));

		setControllerPort(PropertyUtils.getInteger(
			prop,
			controllerPortPropertyName,
			getControllerPortDefault()
		));

		if(prop.contains(controllerAddressPropertyName)) {
			try {
				controllerAddressPort =
					new InetSocketAddress(getControllerAddress(), getControllerPort());
			}catch(IllegalArgumentException ex) {
				if(log.isErrorEnabled()) {
					log.error(
						logTag +
						"Invalid controller address = " + getControllerAddress() + ":" + getControllerPort()
					);
				}

				return false;
			}

			isControllerAddressPresent = true;

			logTag = createLogTag(controllerAddressPort);
		}
		else
			isControllerAddressPresent = false;


		setHeaderInsertDVFrame(PropertyUtils.getBoolean(
			prop,
			headerInsertDVFramePropertyName,
			headerInsertDVFrameDefault
		));

		return true;
	}

	@Override
	public Properties getProperties() {
		return getProperties();
	}

	@Override
	public boolean serviceInitialize() {
		return true;
	}

	@Override
	public void serviceFinalize() {
		closeSocket();
	}

	@Override
	public ThreadProcessResult serviceProcess() {

		processController();

		processFrameEntry();

		return ThreadProcessResult.NoErrors;
	}

	private void processController() {
		boolean isControllerDead = false;

		getLocker().lock();
		try {
			processControllerTransferPackets();

			isControllerDead = controllerAddressPort != null &&
				controllerKeepaliveTimekeeper.isTimeout(10, TimeUnit.MINUTES);

			if(isControllerDead){
				if(log.isWarnEnabled())
					log.warn(logTag + "Controller keep alive timeout! ID-RP2C(" + controllerAddressPort + ") is dead.");

				if(!isControllerAddressPresent) {controllerAddressPort = null;}
				magicNumber = 0;

				logTag = createLogTag();
			}
			else if(
				//コントローラのアドレスが外部から設定されている状態で、3分間無通信状態が続いた場合には
				//初期化パケットを送信する
				isControllerAddressPresent && controllerAddressPort != null &&
				controllerKeepaliveTimekeeper.isTimeout(3, TimeUnit.MINUTES)
			) {
				sendINIT(controllerAddressPort);

				magicNumber = 0;
			}

		}finally {
			getLocker().unlock();
		}

		if(isControllerDead) {clearManagementRepeaterCallsigns();}
	}

	private void processFrameEntry() {
		getLocker().lock();
		try {
			processFrameEntry(downlinkFrameEntries);

			processFrameEntry(uplinkFrameEntries);
		}finally {
			getLocker().unlock();
		}
	}

	private void processFrameEntry(final Map<Integer, FrameEntry> entries) {
		getLocker().lock();
		try {
			for(final Iterator<FrameEntry> it = entries.values().iterator(); it.hasNext();) {
				final FrameEntry entry = it.next();

				if(entry.getActivityTimekeeper().isTimeout(frameTimeoutSeconds, TimeUnit.SECONDS)) {
					it.remove();

					if(log.isDebugEnabled())
						log.debug(logTag + "Frame timeout = " + String.format("0x%04X", entry.getFrameID()));
				}
			}
		}finally {
			getLocker().unlock();
		}
	}

	private void processControllerTransferPackets() {
		getLocker().lock();
		try {
			while(hasReadablePacketFromSystem()) {
				final DSTARPacket packet = readPacketFromSystem();
				if(packet == null) {break;}

				FrameEntry entry = downlinkFrameEntries.get(packet.getFrameID());

				final boolean isNewFrame = entry == null;
				if(isNewFrame) {
					if(packet.getRFHeader() == null) {continue;}

					entry = new FrameEntry(packet.getFrameID(), packet);
					downlinkFrameEntries.put(packet.getFrameID(), entry);

					if(log.isDebugEnabled())
						log.debug(logTag + "Start downlink new frame = " + String.format("0x%04X", packet.getFrameID()));
				}

				entry.getActivityTimekeeper().updateTimestamp();

				while(toControllerPackets.size() > 1000) {toControllerPackets.poll();}

				final BackBonePacket bbPacket = convertDSTARPacketToBackBonePacket(packet, isNewFrame);
				if(bbPacket != null) {toControllerPackets.add(bbPacket);}

				if(
					isHeaderInsertDVFrame() &&
					packet.getPacketType() == DSTARPacketType.DV &&
					packet.getDVPacket() != null && packet.getDVPacket().hasPacketType(PacketType.Voice) &&
					!packet.isLastFrame() &&
					packet.getBackBoneHeader() != null && packet.getBackBoneHeader().getSequenceNumber() == DSTARDefines.MaxSequenceNumber
				) {
					final BackBonePacket headerPacket = convertDSTARPacketToBackBonePacket(entry.getHeaderPacket(), true);
					if(headerPacket != null) {toControllerPackets.add(headerPacket);}
				}

				if(packet.isLastFrame()) {
					downlinkFrameEntries.remove(packet.getFrameID());

					if(log.isDebugEnabled())
						log.debug(logTag + "End downlink frame = " + String.format("0x%04X", packet.getFrameID()));
				}
			}

			//送信中であるか
			if(controllerTransmittingPacket == null && !toControllerPackets.isEmpty()) {
				//新規送信
				controllerTransmittingPacket = toControllerPackets.poll();
				controllerTransmittingPacket.getManagementData().setMagicNumber(magicNumber);
				controllerTransferTimekeeper.updateTimestamp();
				controllerTransferRetryCount = 0;

				sendDVDDPacketToController(controllerTransmittingPacket);
			}
			else if(controllerTransmittingPacket != null){
				//送信中
				if(controllerTransferTimekeeper.isTimeout(controllerTransmitTimeoutMillis, TimeUnit.MILLISECONDS)) {
					//タイムアウト
					if(controllerTransferRetryCount < controllerTransmitRetryLimit) {
						controllerTransferTimekeeper.updateTimestamp();

						sendDVDDPacketToController(controllerTransmittingPacket);

						controllerTransferRetryCount++;
					}
					else {
						//再試行制限回数に到達したので諦める
						if(log.isWarnEnabled())
							log.warn(logTag + "Send failed to controller, no response returned.");

						controllerTransferRetryCount = 0;
						controllerTransmittingPacket = null;
					}
				}
			}
		}finally {
			getLocker().unlock();
		}
	}

	private BackBonePacket convertDSTARPacketToBackBonePacket(
		final DSTARPacket packet,
		final boolean isNewFrame
	) {
		BackBonePacket backbonePacket = null;

		if(
			packet.getPacketType() == DSTARPacketType.DD &&
			packet.getDDPacket() != null
		) {
			backbonePacket = new BackBonePacket(
				packet.getLoopblockID(),
				null,
				new BackBoneManagementData(
					0,
					BackBonePacketDirectionType.Send,
					BackBonePacketType.DDPacket,
					0
				),
				null,
				null,
				null,
				packet.getDDPacket()
			);
		}
		else if(
			packet.getPacketType() == DSTARPacketType.DV &&
			packet.getDVPacket() != null
		) {
			if(isNewFrame && packet.getDVPacket().hasPacketType(PacketType.Header)) {
				packet.getDVPacket().setPacketType(PacketType.Header);

				backbonePacket = new BackBonePacket(
					packet.getLoopblockID(),
					null,
					new BackBoneManagementData(
						0,
						BackBonePacketDirectionType.Send,
						BackBonePacketType.DVPacket,
						0
					),
					null,
					null,
					null,
					packet.getDVPacket()
				);
			}
			else if(packet.getDVPacket().hasPacketType(PacketType.Voice)){
				packet.getDVPacket().setPacketType(PacketType.Voice);

				backbonePacket = new BackBonePacket(
					packet.getLoopblockID(),
					null,
					new BackBoneManagementData(
						0,
						BackBonePacketDirectionType.Send,
						BackBonePacketType.DVPacket,
						0
					),
					null,
					null,
					null,
					packet.getDVPacket()
				);
			}
		}

		return backbonePacket;
	}

	private void processControllerReceivePacket(final BackBonePacket packet) {
		final int frameID = packet.getFrameID();

		getLocker().lock();
		try {
			if(
				packet.getPacketType() == DSTARPacketType.DD ||
				packet.getPacketType() == DSTARPacketType.DV
			) {
				FrameEntry entry = uplinkFrameEntries.get(frameID);
				final boolean isNewFrame = entry == null;
				if(isNewFrame) {
					if(
						packet.getRFHeader() == null ||
						(	//モニターポートからのゲートウェイ向けパケットは無視する
							!packet.getRemoteAddress().equals(controllerAddressPort) &&
							packet.getRFHeader().getRepeater2Callsign()[DSTARDefines.CallsignFullLength - 1] == 'G'
						)
					) {return;}
					else if(!packet.getRFHeader().isValidCRC()) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Header CRC ERROR\n" + packet.toString(4));
					}

					entry = new FrameEntry(frameID, packet.clone());
					uplinkFrameEntries.put(frameID, entry);

					if(log.isDebugEnabled()) {
						log.debug(
							logTag +
							"Start uplink frame = " + String.format("0x%04X", packet.getFrameID()) +
							(packet.getRFHeader() != null ? ("\n" + packet.getRFHeader()) : "")
						);
					}
				}

				if(	//モニターポートからのゲートウェイ向けパケットは無視する
					!packet.getRemoteAddress().equals(controllerAddressPort) &&
					entry.getHeaderPacket().getRFHeader() != null &&
					entry.getHeaderPacket().getRFHeader().getRepeater2Callsign()[DSTARDefines.CallsignFullLength - 1] == 'G'
				) {
					return;
				}

				entry.getActivityTimekeeper().updateTimestamp();

				if(
					packet.getPacketType() == DSTARPacketType.DV &&
					packet.getDVPacket() != null &&
					packet.getDVPacket().hasPacketType(PacketType.Voice)
				) {
					packet.getDVPacket().setRfHeader(entry.getHeaderPacket().getRFHeader().clone());
					packet.getDVPacket().setPacketType(PacketType.Header, PacketType.Voice);
				}

				if(
					packet.isLastFrame() &&
					(
						packet.getManagementData().getType() == BackBonePacketType.DDPacket ||
						packet.getManagementData().getType() == BackBonePacketType.DVPacket
					)
				) {
					uplinkFrameEntries.remove(frameID);

					if(log.isDebugEnabled())
						log.debug(logTag + "End uplink frame = " + String.format("0x%04X", packet.getFrameID()));
				}
			}


			//生存確認
			if(
				packet.getManagementData().getDirectionType() == BackBonePacketDirectionType.Send &&
				packet.getManagementData().getType() == BackBonePacketType.Check
			) {
				sendResponseToController(packet);

				controllerKeepaliveTimekeeper.updateTimestamp();

				//コントローラのアドレスが確定していない場合にはINITを送信
				if(controllerAddressPort == null)
					sendINIT(packet.getRemoteAddress());

				return;
			}
			//初期化
			else if(
				packet.getManagementData().getDirectionType() == BackBonePacketDirectionType.Ack &&
				Arrays.equals("INIT".getBytes(StandardCharsets.US_ASCII), packet.getHeader())
			) {
				onReceiveINIT(packet);
				return;
			}
			//応答
			else if(packet.getManagementData().getDirectionType() == BackBonePacketDirectionType.Ack) {
				onReceiveAck(packet);
				return;
			}
			else if(packet.getManagementData().getDirectionType() != BackBonePacketDirectionType.Send) {
				return;
			}
			//IPフィルタ
			else if(!packet.getRemoteAddress().getAddress().equals(controllerAddressPort.getAddress())
			) {
				return;
			}

			//ゲートウェイポートからの場合にはACKを返答
			if(packet.getRemoteAddress().equals(controllerAddressPort)) {
				controllerKeepaliveTimekeeper.updateTimestamp();

				sendResponseToController(packet);
			}

			if(
				packet.getManagementData().getType() == BackBonePacketType.DDPacket ||
				packet.getManagementData().getType() == BackBonePacketType.DVPacket ||
				packet.getManagementData().getType() == BackBonePacketType.PositionUpdate
			) {
				writePacketToSystem(packet);
			}
			else {
				if(log.isInfoEnabled())
					log.info(logTag + "Notification packet received\n" + packet.toString(4));
			}
		}finally {
			getLocker().unlock();
		}
	}

	private void onReceiveINIT(final BackBonePacket packet) {
		magicNumber = incrementMagicNumber(packet.getManagementData().getMagicNumber());

		if(isControllerAddressPresent) {
			InetSocketAddress configControllerAddress = null;
			try {
				configControllerAddress = new InetSocketAddress(
					getControllerAddress(), getControllerPort()
				);
			}catch(IllegalArgumentException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "Illegal controller address = " + getControllerAddress() + ":" + getControllerPort());
			}

			if(
				configControllerAddress != null &&
				!configControllerAddress.isUnresolved() &&
				packet.getRemoteAddress().equals(configControllerAddress)
			) {
				controllerAddressPort = configControllerAddress;

				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Controller connection established = " + packet.getRemoteAddress()
					);
				}
			}
			else {
				if(log.isWarnEnabled()) {
					log.warn(
						logTag +
						"Controller address mismatch!(Config:" +
						getControllerAddress() + ":" + getControllerPort() + "<->" + packet.getRemoteAddress() +
						")"
					);
				}
			}
		}
		else {
			//設定にてコントローラのアドレスが設定されていなければ、
			//受信したパケットから反映する
			if(controllerAddressPort != null && !controllerAddressPort.equals(packet.getRemoteAddress())) {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Controller address is changed(" +
						packet.getRemoteAddress() + " <- " + controllerAddressPort +
						")"
					);
				}
			}
			else {
				if(log.isInfoEnabled()) {
					log.info(
						logTag +
						"Controller detected = " + packet.getRemoteAddress()
					);
				}
			}

			controllerAddressPort = packet.getRemoteAddress();

			logTag = createLogTag(controllerAddressPort);
		}

		if(log.isDebugEnabled())
			log.debug(logTag + "Receive INIT packet from controller(MagicNumber:" + magicNumber + ")");
	}

	private void onReceiveAck(final BackBonePacket packet) {
		//マジックナンバーをチェックし、
		//コントローラ側の番号がズレていればズレた値を反映する
		if(packet.getManagementData().getMagicNumber() != magicNumber) {
			if(log.isWarnEnabled()) {
				log.warn(
					logTag +
					"Magic number mismatch with controller!(Current:" + magicNumber +
					"/Receive:" + packet.getManagementData().getMagicNumber() +
					")"
				);
			}

			magicNumber = packet.getManagementData().getMagicNumber();
		}

		incrementMagicNumber();

		//送信中のパケットがあれば完了とする
		if(
			controllerTransmittingPacket != null &&
			controllerTransmittingPacket.getManagementData().getMagicNumber() ==
				packet.getManagementData().getMagicNumber()
		) {
			if(log.isTraceEnabled()) {
				log.trace(
					logTag + "Transmit complete number = " +
					controllerTransmittingPacket.getManagementData().getMagicNumber()
				);
			}

			controllerTransmittingPacket = null;
			controllerTransferRetryCount = 0;

			processControllerTransferPackets();
		}
	}

	private boolean sendDVDDPacketToController(final BackBonePacket packet) {
		byte[] buffer = null;
		if(
			packet.getPacketType() == DSTARPacketType.DD &&
			packet.getDDPacket() != null
		) {
			buffer = IcomPacketTool.assembleDDPacket(packet);
		}
		else if(
			packet.getPacketType() == DSTARPacketType.DV &&
			packet.getDVPacket() != null &&
			(
				(packet.getDVPacket().hasPacketType(PacketType.Header) && packet.getRFHeader() != null) ||
				(packet.getDVPacket().hasPacketType(PacketType.Voice) && packet.getVoiceData() != null)
			)
		){
			buffer = IcomPacketTool.assembleDVPacket(
				packet.getDVPacket().hasPacketType(PacketType.Header) ? PacketType.Header : PacketType.Voice,
				packet
			);
		}

		return buffer != null && sendPacketToController(packet, buffer);
	}

	private boolean sendResponseToController(final BackBonePacket packet) {
		final BackBonePacket respPacket = new BackBonePacket(new BackBoneManagementData(
			packet.getManagementData().getMagicNumber(), BackBonePacketDirectionType.Ack, BackBonePacketType.Check, 0
		));

		final byte[] buffer = IcomPacketTool.assembleDummyPacket(respPacket);
		if(buffer == null) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not assemble check packet\n" + packet.toString(4));

			return false;
		}

		return sendPacketToController(respPacket, buffer);
	}

	private boolean sendPacketToController(
		final BackBonePacket packet, final byte[] buffer
	) {
		if(!gatewayLocalPortSocket.getChannel().isOpen() || controllerAddressPort == null)
			return false;

		return sendPacket(packet, buffer, controllerAddressPort);
	}

	private boolean sendPacket(
		final BackBonePacket packet, final byte[] buffer,
		final InetSocketAddress destination
	) {
		if(!gatewayLocalPortSocket.getChannel().isOpen() || destination == null)
			return false;

		final boolean isSuccess = gatewayLocalPortHandler.writeUDPPacket(
			gatewayLocalPortSocket.getKey(),
			destination, ByteBuffer.wrap(buffer)
		);

		if(log.isTraceEnabled()) {
			log.trace(
				logTag +
				"Send packet to controller " + destination + "\n" +
					packet.toString(4) + "\n" +
				"    [Dump]\n" +
					FormatUtil.bytesToHexDump(buffer, 8)
			);
		}

		return isSuccess;
	}

	private boolean sendINIT(final InetSocketAddress destinationAddress) {
		final BackBonePacket initPacket = new BackBonePacket(new BackBoneManagementData(
			magicNumber, BackBonePacketDirectionType.Send, BackBonePacketType.Check, 0
		));
		final byte[] buffer = IcomPacketTool.assembleInitPacket(initPacket);

		return sendPacket(initPacket, buffer, destinationAddress);
	}

	private void incrementMagicNumber() {
		magicNumber = incrementMagicNumber(magicNumber);
	}

	private boolean parseControllerPacket() {
		return parsePacket(monitorLocalPortHandler) || parsePacket(gatewayLocalPortHandler);
	}

	private boolean parsePacket(
		final SocketIOHandler<BufferEntry> ioHandler
	) {
		return ioHandler.hasReceivedReadBuffer() &&
			ioHandler.parseReceivedReadBuffer(
				controllerPacketTracer,
				controllerPacketParser,
				controllerUnknownPacketHandler
			);
	}

	private static int incrementMagicNumber(final int currentMagicNumber) {
		return (currentMagicNumber + 1) % 65536;
	}

	private void closeSocket() {
		getLocker().lock();
		try {
			if(gatewayLocalPortSocket != null)
				SocketIOHandler.closeChannel(gatewayLocalPortSocket);

			gatewayLocalPortSocket = null;

			if(monitorLocalPortSocket != null)
				SocketIOHandler.closeChannel(monitorLocalPortSocket);

			monitorLocalPortSocket = null;
		}finally {
			getLocker().unlock();
		}
	}

	private static String createLogTag(final InetSocketAddress controllerAddress) {
		return IDRP2CCommunicationService.class.getSimpleName() +
			"(" + (controllerAddress != null ? controllerAddress : "NO CONTROLLER") +  ") : ";
	}

	private static String createLogTag() {
		return createLogTag(null);
	}
}
