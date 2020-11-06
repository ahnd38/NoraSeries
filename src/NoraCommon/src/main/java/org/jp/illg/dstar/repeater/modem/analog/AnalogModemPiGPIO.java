package org.jp.illg.dstar.repeater.modem.analog;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.ModemTransceiverMode;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.VoicePCM;
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
import org.jp.illg.dstar.reporter.model.ModemStatusReport;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.handler.WebRemoteControlAnalogModemPiGPIOHandler;
import org.jp.illg.dstar.service.web.model.AnalogModemPiGPIOHeaderData;
import org.jp.illg.dstar.service.web.model.AnalogModemPiGPIOStatusData;
import org.jp.illg.dstar.service.web.model.ModemStatusData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture.AudioPlaybackCaptureEventLintener;
import org.jp.illg.util.audio.util.winlinux.AudioTool;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPin;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalogModemPiGPIO extends DStarRepeaterModemBase
implements WebRemoteControlAnalogModemPiGPIOHandler {


	private static final String logHeader;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String downlinkEnablePortName;
	private static final String downlinkEnablePortNameDefault = "";
	public static final String downlinkEnablePortNamePropertyName = "DownlinkEnablePortName";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean downlinkEnablePortInvert;
	private static final boolean downlinkEnablePortInvertDefault = false;
	public static final String downlinkEnablePortInvertPropertyName = "DownlinkEnablePortInvert";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String downlinkAudioDeviceName;
	private static final String downlinkAudioDeviceNameDefault = "";
	public static final String downlinkAudioDeviceNamePropertyName = "DownlinkAudioDeviceName";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String uplinkEnablePortName;
	private static final String uplinkEnablePortNameDefault = "";
	public static final String uplinkEnablePortNamePropertyName = "UplinkEnablePortName";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean uplinkEnablePortInvert;
	private static final boolean uplinkEnablePortInvertDefault = false;
	public static final String uplinkEnablePortInvertPropertyName = "UplinkEnablePortInvert";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String uplinkAudioDeviceName;
	private static final String uplinkAudioDeviceNameDefault = "";
	public static final String uplinkAudioDeviceNamePropertyName = "UplinkAudioDeviceName";



	private final int framesPerSeconds;
	private final int frameSampleBytes;

	private final GpioController gpio;
	private GpioPinDigitalOutput downlinkEnablePort;
	private Pin downlinkEnablePin;
	private GpioPinDigitalInput uplinkEnablePort;
	private Pin uplinkEnablePin;

	private boolean uplinkEnable;
	private int uplinkEnablePortCache;
	private int uplinkFrameID;
	private int uplinkSequence;
	private UUID loopBlockID;
	private Header uplinkHeader;
	private final NewDataSegmentEncoder uplinkSlowdataEncoder;

	private int downlinkFrameID;
	private Header downlinkHeader;
	private final Timer downlinkTimekeeper;

	private final ByteBuffer captureBuffer;
	private final Lock captureBufferLocker;

	private final AudioFormat audioFormat = new AudioFormat(8000.0f, 16, 1, true, true);
	private final AudioPlaybackCapture audio;

	private final Queue<DSTARPacket> writePacket;
	private final Lock writePacketLocker;

	private String uplinkYourCallsign, uplinkMyCallsignLong, uplinkMyCallsignShort;
	private boolean uplinkUseGateway;

	static {
		logHeader = AnalogModemPiGPIO.class.getSimpleName() + " : ";
	}

	public AnalogModemPiGPIO(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway,
		@NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener
	) {
		this(exceptionListener, workerExecutor, gateway, repeater, eventListener, null);
	}

	public AnalogModemPiGPIO(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor,
		@NonNull DSTARGateway gateway,
		@NonNull DSTARRepeater repeater,
		final EventListener<DStarRepeaterModemEvent> eventListener,
		SocketIO socketIO
	) {
		super(
			exceptionListener,
			AnalogModemPiGPIO.class.getSimpleName(),
			workerExecutor,
			ModemTypes.getTypeByClassName(AnalogModemPiGPIO.class.getName()),
			gateway,
			repeater,
			eventListener,
			socketIO
		);

		gpio = GpioFactory.getInstance();
		framesPerSeconds = (int)Math.ceil(1.0d / 0.02d);
		frameSampleBytes =
			(int)(audioFormat.getSampleRate() * audioFormat.getSampleSizeInBits() / 8 / framesPerSeconds);

		captureBuffer = ByteBuffer.allocateDirect(frameSampleBytes * 10);
		captureBuffer.limit(0);
		captureBufferLocker = new ReentrantLock();

		audio = new AudioPlaybackCapture(
			audioFormat,
			frameSampleBytes,
			audioEventListener
		);

		uplinkEnable = false;
		uplinkEnablePortCache = 0x0;
		uplinkFrameID = 0x0;
		uplinkSequence = 0x0;
		uplinkHeader = null;
		loopBlockID = null;
		uplinkSlowdataEncoder = new NewDataSegmentEncoder();
		uplinkYourCallsign = DSTARDefines.CQCQCQ;
		uplinkMyCallsignLong = DSTARDefines.EmptyLongCallsign;
		uplinkMyCallsignShort= DSTARDefines.EmptyShortCallsign;
		uplinkUseGateway = true;

		downlinkTimekeeper = new Timer();
		downlinkFrameID = 0x0;
		downlinkHeader = null;

		writePacket = new LinkedList<>();
		writePacketLocker = new ReentrantLock();
	}

	@Override
	public ModemTransceiverMode getDefaultTransceiverMode() {
		return ModemTransceiverMode.FullDuplex;
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

	private final AudioPlaybackCaptureEventLintener audioEventListener = new AudioPlaybackCaptureEventLintener() {
		@Override
		public void audioCaptureEvent(byte[] audioData) {

			captureBufferLocker.lock();
			try {
				captureBuffer.compact();
				final int overflowSamples = audioData.length - captureBuffer.remaining();
				if(overflowSamples > 0) {
					//オーバーフローした場合、既存バッファの古いデータを削除
					if(captureBuffer.capacity() > overflowSamples) {
						captureBuffer.flip();
						captureBuffer.position(overflowSamples);
						captureBuffer.compact();
					}
					else {
						captureBuffer.clear();
					}
				}

				for(int i = 0; i < audioData.length && captureBuffer.hasRemaining(); i++) {
					captureBuffer.put((byte)audioData[i]);
				}

				captureBuffer.flip();

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader +
						String.format(
							Locale.getDefault(),
							"Write capture %dbytes audio data to capture buffer...%s",
							audioData.length, captureBuffer.toString()
						)
					);
				}
			}finally {
				captureBufferLocker.unlock();
			}
		}
	};

	@Override
	public boolean setPropertiesInternal(ModemProperties properties) {

		setDownlinkEnablePortName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				downlinkEnablePortNamePropertyName,
				downlinkEnablePortNameDefault
			)
		);

		setDownlinkEnablePortInvert(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				downlinkEnablePortInvertPropertyName,
				downlinkEnablePortInvertDefault
			)
		);

		setDownlinkAudioDeviceName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				downlinkAudioDeviceNamePropertyName,
				downlinkAudioDeviceNameDefault
			)
		);

		setUplinkEnablePortName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				uplinkEnablePortNamePropertyName,
				uplinkEnablePortNameDefault
			)
		);

		setUplinkEnablePortInvert(
			PropertyUtils.getBoolean(
				properties.getConfigurationProperties(),
				uplinkEnablePortInvertPropertyName,
				uplinkEnablePortInvertDefault
			)
		);

		setUplinkAudioDeviceName(
			PropertyUtils.getString(
				properties.getConfigurationProperties(),
				uplinkAudioDeviceNamePropertyName,
				uplinkAudioDeviceNameDefault
			)
		);

		GpioPin alreadyProvisionedPin = null;

		final String downlinkPinName = getDownlinkEnablePortName().toUpperCase(Locale.ROOT);

		downlinkEnablePin = RaspiPin.getPinByName(downlinkPinName);
		if(downlinkEnablePin == null) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not found DownlinkEnablePort = " + getDownlinkEnablePortName() + ".");

			return false;
		}
		if((alreadyProvisionedPin = gpio.getProvisionedPin(downlinkEnablePin)) != null) {
			gpio.unprovisionPin(alreadyProvisionedPin);
		}

		downlinkEnablePort =
			gpio.provisionDigitalOutputPin(
				downlinkEnablePin,
				"DownlinkEnablePort",
				isDownlinkEnablePortInvert() ? PinState.HIGH : PinState.LOW
			);

		final String uplinkPinName = getUplinkEnablePortName().toUpperCase(Locale.ROOT);

		uplinkEnablePin = RaspiPin.getPinByName(uplinkPinName);
		if(uplinkEnablePin == null) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not found UplinkEnablePort = " + getUplinkEnablePortName() + ".");

			return false;
		}
		if((alreadyProvisionedPin = gpio.getProvisionedPin(uplinkEnablePin)) != null) {
			gpio.unprovisionPin(alreadyProvisionedPin);
		}

		uplinkEnablePort =
			gpio.provisionDigitalInputPin(
				uplinkEnablePin,
				"UplinkEnablePort",
				isUplinkEnablePortInvert() ? PinPullResistance.PULL_UP : PinPullResistance.PULL_DOWN
			);

		return true;
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
	public ModemProperties getProperties(ModemProperties properties) {

		return properties;
	}

	@Override
	public boolean initializeWebRemoteControlInt(final WebRemoteControlService webRemoteControlService) {
		return webRemoteControlService.initializeModemAnalogModemPiGPIO(this);
	}

	@Override
	public boolean writePacketInternal(DSTARPacket packet) {
		if(packet == null)
			return false;

		writePacketLocker.lock();
		try {
			return writePacket.add(packet);
		}finally {
			writePacketLocker.unlock();
		}
	}

	@Override
	public boolean hasWriteSpace() {
		return true;
	}

	@Override
	public VoiceCodecType getCodecType() {
		return VoiceCodecType.DPCM;
	}

	@Override
	protected void threadFinalize() {
		gpio.shutdown();

		if(downlinkEnablePort != null)
			gpio.unprovisionPin(downlinkEnablePort);

		if(uplinkEnablePort != null)
			gpio.unprovisionPin(uplinkEnablePort);

		if(audio != null) {
			audio.stopCapture();
			audio.stopPlayback();

			audio.close();
		}
	}

	@Override
	protected ThreadProcessResult threadInitialize() {

		uplinkUseGateway = true;
		uplinkYourCallsign = DSTARDefines.CQCQCQ;
		uplinkMyCallsignLong = getRepeaterCallsign();
		uplinkMyCallsignShort = DSTARDefines.EmptyShortCallsign;

		if(log.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			sb.append("Detected audio device list...\n");

			for(final Mixer.Info deviceInfo : AudioTool.getCaptureMixers(audioFormat)) {
				if(getUplinkAudioDeviceName().equals(deviceInfo.getName()))
					sb.append("  * ");
				else
					sb.append("  - ");

				sb.append("Name:");
				sb.append(deviceInfo.getName());

				sb.append("/");

				sb.append("Vendor:");
				sb.append(deviceInfo.getVendor());

				sb.append("/");

				sb.append("Version:");
				sb.append(deviceInfo.getVersion());

				sb.append("\n");
			}

			for(final Mixer.Info deviceInfo : AudioTool.getPlaybackMixers(audioFormat)) {
				if(getDownlinkAudioDeviceName().equals(deviceInfo.getName()))
					sb.append("  * ");
				else
					sb.append("  - ");

				sb.append("Name:");
				sb.append(deviceInfo.getName());

				sb.append("/");

				sb.append("Vendor:");
				sb.append(deviceInfo.getVendor());

				sb.append("/");

				sb.append("Version:");
				sb.append(deviceInfo.getVersion());

				sb.append("\n");
			}

			log.info(logHeader + sb.toString());
		}

		if(!audio.openCapture(getUplinkAudioDeviceName())) {
			final String message = "Could not open uplink capture audio device = " + getUplinkAudioDeviceName() + ".";
			if(log.isErrorEnabled())
				log.error(logHeader + message);

			return threadFatalError(message, null);
		}

		if(!audio.openPlayback(getDownlinkAudioDeviceName())) {
			audio.close();

			final String message = "Could not open downlink playback audio device = " + getDownlinkAudioDeviceName() + ".";
			if(log.isErrorEnabled())
				log.error(logHeader + message);

			return threadFatalError(message, null);
		}

		if(log.isInfoEnabled()) {
			log.info(
				logHeader +
				"[Uplink Enable Port] PortName:" + getUplinkEnablePortName() +
				(isUplinkEnablePortInvert() ? "(ActiveGround)" : "")
			);

			log.info(
				logHeader +
				"[Downlink Enable Port] PortName:" + getDownlinkEnablePortName() +
				(isDownlinkEnablePortInvert() ? "(ActiveGround)" : "")
			);
		}


		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected ThreadProcessResult processModem() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;


		if((processResult = processDownlink()) != ThreadProcessResult.NoErrors)
			return processResult;

		if((processResult = processUplink()) != ThreadProcessResult.NoErrors)
			return processResult;

		return processResult;
	}

	@Override
	protected ProcessIntervalMode getProcessLoopIntervalMode() {
		return ProcessIntervalMode.VoiceTransfer;
	}

	@Override
	protected ModemStatusReport getStatusReportInternal(ModemStatusReport report) {

		return report;
	}

	private DSTARPacket readWritePacket() {
		writePacketLocker.lock();
		try {
			return !writePacket.isEmpty() ? writePacket.poll() : null;
		}finally {
			writePacketLocker.unlock();
		}
	}

	private ThreadProcessResult processDownlink() {

		DSTARPacket packet = null;
		while((packet = readWritePacket()) != null) {
			if(packet.getPacketType() != DSTARPacketType.DV) {continue;}

			if(
				downlinkFrameID == 0x0 &&
				packet.getDVPacket().hasPacketType(PacketType.Header)
			) {
				downlinkFrameID = packet.getBackBone().getFrameIDNumber();
				downlinkHeader = packet.getRfHeader();

				if(log.isDebugEnabled())
					log.debug(String.format(Locale.getDefault(), "%s Start downlink frameID=0x%04X", logHeader, downlinkFrameID));

				downlinkTimekeeper.setTimeoutTime(60, TimeUnit.SECONDS);
				downlinkTimekeeper.updateTimestamp();
				downlinkEnablePort.setState(isDownlinkEnablePortInvert() ? PinState.LOW : PinState.HIGH);

				audio.startPlayback();

				notifyStatusChanged();
			}
			else if(
				downlinkFrameID == packet.getBackBone().getFrameIDNumber() &&
				packet.getDVPacket().hasPacketType(PacketType.Voice) &&
				packet.getVoiceData().getVoiceCodecType() == VoiceCodecType.DPCM
			) {
				audio.writePlayback(packet.getVoiceData().getVoiceSegment(), 0);

				if(packet.isEndVoicePacket()) {
					if(log.isDebugEnabled())
						log.debug(String.format(Locale.getDefault(), "%s End downlink frameID=0x%04X", logHeader, downlinkFrameID));

					downlinkFrameID = 0x0;
					downlinkHeader = null;

					downlinkEnablePort.setState(isDownlinkEnablePortInvert() ? PinState.HIGH : PinState.LOW);

					audio.stopPlayback();

					notifyStatusChanged();
				}
				else {
					downlinkTimekeeper.updateTimestamp();
				}
			}

		}

		if(
			downlinkFrameID != 0x0 &&
			downlinkTimekeeper.isTimeout()
		) {
			if(log.isDebugEnabled())
				log.debug(String.format(Locale.getDefault(), "%s Timeout downlink frameID=0x%04X", logHeader, downlinkFrameID));

			downlinkFrameID = 0x0;
			downlinkHeader = null;

			downlinkEnablePort.setState(isDownlinkEnablePortInvert() ? PinState.HIGH : PinState.LOW);

			audio.stopPlayback();

			notifyStatusChanged();
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult processUplink() {

		final boolean uplinkStart = uplinkEnablePortCache == 0xFFF && !uplinkEnable;
		final boolean uplinkEnd = uplinkEnablePortCache == 0x000 && uplinkEnable;


		VoicePCM voice = null;
		do {
			captureBufferLocker.lock();
			try {
				if(captureBuffer.remaining() >= frameSampleBytes) {
					voice = new VoicePCM();

					for(
						int i = 0;
						i < voice.getVoiceSegment().length && i < frameSampleBytes && captureBuffer.hasRemaining();
						i++
					) {
						voice.getVoiceSegment()[i] = captureBuffer.get();
					}

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader +
							String.format(
								Locale.getDefault(),
								"Read capture %dbytes audio data from capture buffer...%s",
								frameSampleBytes, captureBuffer.toString()
							)
						);
					}
				}
				else {
					voice = null;
				}

			}finally {
				captureBufferLocker.unlock();
			}

			if(uplinkStart) {
				uplinkEnable = true;

				audio.startCapture();
			}
			else if(uplinkEnd) {
				uplinkEnable = false;

				audio.stopCapture();
			}

			if(uplinkEnable && uplinkStart) {
				uplinkFrameID = DSTARUtils.generateFrameID();
				uplinkSequence = 0x0;
				loopBlockID = DSTARUtils.generateLoopBlockID();

				final Header header = new Header();
				header.setFlags(new byte[] {(byte)0x00, (byte)0x00, (byte)0x00});
				header.setRepeater1Callsign(getRepeaterCallsign().toCharArray());
				header.setRepeater2Callsign(
					uplinkUseGateway ? getGatewayCallsign().toCharArray() : getRepeaterCallsign().toCharArray()
				);
				header.setYourCallsign(uplinkYourCallsign.toCharArray());
				header.setMyCallsign(uplinkMyCallsignLong.toCharArray());
				header.setMyCallsignAdd(uplinkMyCallsignShort.toCharArray());

				final Header slowdataHeader = header.clone();
				uplinkHeader = header;

				final InternalPacket headerPacket = new InternalPacket(loopBlockID,
					new DVPacket(
						new BackBoneHeader(
							BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, uplinkFrameID
						),
						header.clone()
					)
				);

				addReadPacket(headerPacket);

				uplinkSlowdataEncoder.reset();
				uplinkSlowdataEncoder.setHeader(slowdataHeader);
				uplinkSlowdataEncoder.setEnableHeader(true);
				uplinkSlowdataEncoder.setShortMessage("via NoraVR(" + getModemType().getTypeName() + ")");
				uplinkSlowdataEncoder.setEnableShortMessage(true);

				clearCaptureBuffer();

				if(log.isDebugEnabled())
					log.debug(String.format(Locale.getDefault(), "%s Start uplink frameID=0x%04X", logHeader, uplinkFrameID));

				notifyStatusChanged();
			}

			if((uplinkEnable && voice != null) || uplinkEnd) {
				if(uplinkEnd && voice == null) {
					voice = new VoicePCM();
					for(
						int i = 0;
						i < voice.getVoiceSegment().length && i < frameSampleBytes;
						i++
					) {
						voice.getVoiceSegment()[i] = (byte)0x00;
					}
				}
				uplinkSlowdataEncoder.encode(voice.getDataSegment());

				addReadPacket(new InternalPacket(loopBlockID,
					new DVPacket(
						new BackBoneHeader(
							BackBoneHeaderType.DV,
							uplinkEnd ? BackBoneHeaderFrameType.VoiceDataLastFrame : BackBoneHeaderFrameType.VoiceData,
							uplinkFrameID, (byte)uplinkSequence
						),
						uplinkHeader.clone(), voice
					)
				));

				uplinkSequence = (uplinkSequence + 0x1) % 0x15;

				if(uplinkEnd) {
					if(log.isDebugEnabled())
						log.debug(String.format(Locale.getDefault(), "%s End uplink frameID=0x%04X", logHeader, uplinkFrameID));

					uplinkFrameID = 0x0;
					uplinkSequence = 0x0;
					uplinkHeader = null;

					notifyStatusChanged();

					break;
				}
			}
		}while(voice != null);


		final boolean portEnable =
			isUplinkEnablePortInvert() ? uplinkEnablePort.isLow() : uplinkEnablePort.isHigh();

		uplinkEnablePortCache = (((uplinkEnablePortCache << 1) & 0xFFE) | (portEnable ? 0x1 : 0x0)) & 0xFFF;

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void onReadPacket(final DSTARPacket packet) {
		if(log.isTraceEnabled())
			log.trace(logHeader + "Add uplink(read) packet...\n" + packet.toString(4));
	}

	private void clearCaptureBuffer() {
		captureBufferLocker.lock();
		try {
			captureBuffer.clear();
			captureBuffer.limit(0);

			if(log.isTraceEnabled())
				log.trace(logHeader + "Clear capture buffer.");
		}finally {
			captureBufferLocker.unlock();
		}
	}

	@Override
	public void updateHeaderFromWebRemoteControl(final AnalogModemPiGPIOHeaderData data) {
		final String repeater1Callsign = DSTARUtils.formatFullLengthCallsign(data.getRepeater1Callsign());
		final String repeater2Callsign = DSTARUtils.formatFullLengthCallsign(data.getRepeater2Callsign());
		final String yourCallsign = DSTARUtils.formatFullLengthCallsign(data.getYourCallsign());
		final String myCallsignLong = DSTARUtils.formatFullLengthCallsign(data.getMyCallsignLong());
		final String myCallsignShort = DSTARUtils.formatShortLengthCallsign(data.getMyCallsignShort());

		final boolean repeater1Valid =
			getRepeaterCallsign().equals(repeater1Callsign);

		final boolean repeater2Valid =
			getRepeaterCallsign().equals(repeater2Callsign) ||
			getGatewayCallsign().equals(repeater2Callsign);

		final boolean myCallsignLongValid = CallSignValidator.isValidUserCallsign(myCallsignLong);
		final boolean myCallsignShortValid = CallSignValidator.isValidShortCallsign(myCallsignShort);

		final boolean useGateway =
			getGatewayCallsign().equals(repeater2Callsign);

		if(
			repeater1Valid && repeater2Valid &&
			myCallsignLongValid && myCallsignShortValid
		) {
			uplinkUseGateway = useGateway;
			uplinkYourCallsign = yourCallsign;
			uplinkMyCallsignLong = myCallsignLong;
			uplinkMyCallsignShort = myCallsignShort;
		}

		notifyStatusChanged();
	}

	@Override
	protected ModemStatusData createStatusDataInternal() {
		return createWebControlStatusData();
	}

	@Override
	protected Class<? extends ModemStatusData> getStatusDataTypeInternal() {
		return AnalogModemPiGPIOStatusData.class;
	}

	private AnalogModemPiGPIOStatusData createWebControlStatusData() {
		final AnalogModemPiGPIOStatusData status =
			new AnalogModemPiGPIOStatusData(getWebSocketRoomId());
		status.setModemId(getModemId());
		status.setWebSocketRoomId(getWebSocketRoomId());

		status.setGatewayCallsign(getGatewayCallsign());
		status.setRepeaterCallsign(getRepeaterCallsign());

		final Header uplinkHeader = this.uplinkHeader;
		final Header downlinkHeader = this.downlinkHeader;

		status.setUplinkHeader(uplinkHeader != null ? convertHeader(uplinkHeader) : null);
		status.setDownlinkHeader(downlinkHeader != null ? convertHeader(downlinkHeader) : null);

		status.setUplinkActive(uplinkFrameID != 0x0);
		status.setDownlinkActive(downlinkFrameID != 0x0);

		status.setUplinkConfigUseGateway(uplinkUseGateway);
		status.setUplinkConfigYourCallsign(uplinkYourCallsign);
		status.setUplinkConfigMyCallsign(uplinkMyCallsignLong);

		return status;
	}

	private AnalogModemPiGPIOHeaderData convertHeader(final Header header) {
		final AnalogModemPiGPIOHeaderData convHeader = new AnalogModemPiGPIOHeaderData();

		convHeader.setFlags(Arrays.copyOf(header.getFlags(), header.getFlags().length));
		convHeader.setRepeater1Callsign(String.valueOf(header.getRepeater1Callsign()));
		convHeader.setRepeater2Callsign(String.valueOf(header.getRepeater2Callsign()));
		convHeader.setYourCallsign(String.valueOf(header.getYourCallsign()));
		convHeader.setMyCallsignLong(String.valueOf(header.getMyCallsign()));
		convHeader.setMyCallsignShort(String.valueOf(header.getMyCallsignAdd()));

		return convHeader;
	}
}
