package org.jp.illg.util.ambe.dv3k.uart;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.ambe.dv3k.DV3KDefines;
import org.jp.illg.util.ambe.dv3k.DV3KInterface;
import org.jp.illg.util.ambe.dv3k.DV3KPacket;
import org.jp.illg.util.ambe.dv3k.model.DV3KControllerEvent;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;
import org.jp.illg.util.ambe.dv3k.packet.channel.ChannelData;
import org.jp.illg.util.ambe.dv3k.packet.channel.DV3KChannelPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.PRODID;
import org.jp.illg.util.ambe.dv3k.packet.control.RATEP;
import org.jp.illg.util.ambe.dv3k.packet.control.READY;
import org.jp.illg.util.ambe.dv3k.packet.control.VERSTRING;
import org.jp.illg.util.ambe.dv3k.packet.speech.DV3KSpeechPacketType;
import org.jp.illg.util.ambe.dv3k.packet.speech.SpeechData;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.ThreadBase;
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

import com.annimon.stream.function.Consumer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DV3KUartController implements DV3KInterface {

	private static enum ProcessState {
		INITIALIZE,
		IDLE,
		WAIT,
		;
	}

	private class ProcessThread extends ThreadBase{

		protected ProcessThread(
			ThreadUncaughtExceptionListener exceptionListener,
			String workerThreadName,
			long processLoopPeriodMillis,
			boolean manualControlThreadTerminate
		) {
			super(
				exceptionListener, workerThreadName,
				processLoopPeriodMillis, manualControlThreadTerminate
			);
		}

		@Override
		protected ThreadProcessResult threadInitialize() {
			return ThreadProcessResult.NoErrors;
		}

		@Override
		protected void threadFinalize() {
		}

		@Override
		protected ThreadProcessResult process() {
			super.setWorkerThreadTerminateRequest(
				!internalProcess(!super.isWorkerThreadAvailable())
			);

			return ThreadProcessResult.NoErrors;
		}

		@Override
		public void wakeupProcessThread() {
			super.wakeupProcessThread();
		}
	}

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String DV3KPortName;
	public static final String DV3KPortNamePropertyName = "DV3KPortName";
	private static final String DV3KPortNameDefault = "";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int DV3KBaudRate;
	public static final String DV3KBaudRatePropertyName = "DV3KBaudRate";
	private static final int DV3KBaudRateDefault = 460800;

	private String logTag;

	private final ExecutorService workerExecutor;

	private final EventListener<DV3KControllerEvent> eventListener;

	private final Lock locker;

	private final ThreadUncaughtExceptionListener exceptionListener;

	private ProcessThread processThread;

	private UartInterface uart;

	private final TaskQueue<UartEvent, Boolean> uartReceiveEventQueue;

	private final Timer uartPortInactivityTimer;

	private final Lock uartReceiveBufferLocker;
	private final ByteBuffer uartReceiveBuffer;
	private BufferState uartReceiveBufferState;
	private final Timer uartReceiveBufferTimekeeper;
	private final Queue<DV3KPacket> uartReceivePackets;

	private final Queue<DV3KPacket> uartReceiveChannelPackets;
	private final Queue<DV3KPacket> uartReceiveSpeechPackets;
	private final Queue<DV3KPacket> uartReceiveOtherPackets;

	private final Queue<DV3KPacket> writePackets;
	private byte[] writeBuffer;

	@SuppressWarnings("unused")
	private boolean isStateChanged;
	private ProcessState currentState, nextState, callbackState;
	private final Timer stateTimekeeper;

	private final PRODID dv3kPRODID = new PRODID();
	private final RATEP dv3kRATEP = new RATEP();
	private final VERSTRING dv3kVERSTRING = new VERSTRING();
	private final SpeechData dv3kSpeechData = new SpeechData();
	private final ChannelData dv3kChannelData = new ChannelData();
	private final READY dv3kReady = new READY();

	private final Consumer<UartEvent> uartReceiveEventDispatcher =
		new Consumer<UartEvent>() {
			@Override
			public void accept(UartEvent uartEvent) {
				uartReceiveBufferLocker.lock();
				try {
					final BufferUtilObject putResult =
						BufferUtil.putBuffer(
							logTag,
							uartReceiveBuffer, uartReceiveBufferState, uartReceiveBufferTimekeeper,
							uartEvent.getReceiveData()
						);
					uartReceiveBufferState = putResult.getBufferState();

					if(log.isTraceEnabled()) {
						uartReceiveBufferState = BufferState.toREAD(uartReceiveBuffer, uartReceiveBufferState);
						final int savedPos = uartReceiveBuffer.position();
						log.trace(
							logTag + "Receive from " + getDV3KPortName() + " " +
							uartEvent.getReceiveData().length + "bytes\n" +
							FormatUtil.byteBufferToHexDump(uartReceiveBuffer, 4)
						);
						uartReceiveBuffer.position(savedPos);
					}

				}catch(Exception ex) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Failed to put receive buffer = " + getDV3KPortName());
				}finally {
					uartReceiveBufferLocker.unlock();
				}

				parseUartDV3KPacket();

				locker.lock();
				try {
					uartPortInactivityTimer.updateTimestamp();

//					if(processThread != null)
//						processThread.wakeupProcessThread();
				}finally {
					locker.unlock();
				}
			}
	};

	private final UartEventListener uartEvent = new UartEventListener() {
		@Override
		public void uartEvent(UartEvent uartEvent) {
			if(uartEvent.getReceiveData().length <= 0) {return;}

			if(log.isTraceEnabled()) {
				log.trace(
					logTag + "Receive data dump from " + getDV3KPortName() + "\n" +
					FormatUtil.bytesToHexDump(uartEvent.getReceiveData(), 4)
				);
			}

			uartReceiveEventQueue.addEventQueue(
				uartReceiveEventDispatcher, uartEvent, exceptionListener
			);
		}

		@Override
		public UartEventType getLinteningEventType() {
			return UartEventType.DATA_AVAILABLE;
		}
	};


	public DV3KUartController(
		@NonNull final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		final EventListener<DV3KControllerEvent> eventListener
	) {
		super();

		logTag = this.getClass().getSimpleName() + " : ";

		this.exceptionListener = exceptionListener;
		this.eventListener = eventListener;
		this.workerExecutor = workerExecutor;

		locker = new ReentrantLock();

		uartReceiveEventQueue = new TaskQueue<>(this.workerExecutor);

		uartPortInactivityTimer = new Timer();

		stateTimekeeper = new Timer();
		isStateChanged = false;
		currentState = nextState = callbackState = ProcessState.INITIALIZE;

		uartReceiveBufferLocker = new ReentrantLock();
		uartReceiveBuffer = ByteBuffer.allocateDirect(4096);
		uartReceiveBufferState = BufferState.INITIALIZE;
		uartReceiveBufferTimekeeper = new Timer();
		uartReceiveBufferTimekeeper.setTimeoutTime(1, TimeUnit.SECONDS);
		uartReceivePackets = new LinkedList<>();

		uartReceiveChannelPackets = new LinkedList<>();
		uartReceiveSpeechPackets = new LinkedList<>();
		uartReceiveOtherPackets = new LinkedList<>();

		writePackets = new LinkedList<>();

		setDV3KPortName(DV3KPortNameDefault);
		setDV3KBaudRate(DV3KBaudRateDefault);
	}

	@Override
	public boolean open() {
		locker.lock();
		try {
			close();

			processThread = new ProcessThread(
				exceptionListener,
				this.getClass().getSimpleName() + "Worker",
				TimeUnit.MILLISECONDS.convert(100, TimeUnit.MILLISECONDS),
				true
			);

			processThread.start();

		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public boolean isOpen() {
		boolean isOpen = false;

		locker.lock();
		try {
			isOpen =
				processThread != null && processThread.isRunning() &&
				uart != null && uart.isOpen();
		}finally {
			locker.unlock();
		}

		return isOpen;
	}

	@Override
	public void close() {
		if(processThread != null && processThread.isRunning()) {
			processThread.stop();

			processThread = null;
		}

		locker.lock();
		try {
			closeUart();
		}finally {
			locker.unlock();
		}
	}

	@Override
	public String getPortName() {
		return getDV3KPortName();
	}

	@Override
	public boolean setProperties(Properties properties) {
		locker.lock();
		try {
			setDV3KPortName(
				PropertyUtils.getString(
					properties,
					DV3KPortNamePropertyName, DV3KPortNameDefault
				)
			);

			setDV3KBaudRate(
				PropertyUtils.getInteger(
					properties,
					DV3KBaudRatePropertyName, DV3KBaudRateDefault
				)
			);

			logTag = this.getClass().getSimpleName() + "(" + getDV3KPortName() + ") : ";

		}finally {
			locker.unlock();
		}

		return true;
	}

	@Override
	public Properties getProperties(Properties properties) {
		return properties;
	}

	@Override
	public boolean writeDV3KPacket(@NonNull DV3KPacket packet) {
		return addWritePacket(packet);
	}

	@Override
	public boolean hasReadableDV3KPacket() {
		synchronized(uartReceiveOtherPackets) {
			return !uartReceiveOtherPackets.isEmpty();
		}
	}

	@Override
	public DV3KPacket readDV3KPacket() {
		DV3KPacket packet = null;
		synchronized(uartReceiveOtherPackets) {
			packet = uartReceiveOtherPackets.poll();
		}

		return packet;
	}

	@Override
	public boolean encodePCM2AMBEInput(@NonNull ShortBuffer pcmBuffer) {
		boolean isSuccess = true;

		while(pcmBuffer.remaining() >= DV3KDefines.DV3K_PCM_BLOCKSIZE) {
			final SpeechData packet = new SpeechData();

			for(int i = 0; i < DV3KDefines.DV3K_PCM_BLOCKSIZE && pcmBuffer.hasRemaining(); i++)
				packet.getSpeechData().add((int)pcmBuffer.get());

			if(!addWritePacket(packet)) {isSuccess = false;}
		}

		locker.lock();
		try {
			processThread.wakeupProcessThread();
		}finally {
			locker.unlock();
		}

		return isSuccess;
	}

	@Override
	public boolean encodePCM2AMBEOutput(ByteBuffer ambeBuffer) {
		DV3KPacket packet = null;
		synchronized(uartReceiveChannelPackets) {
			packet = uartReceiveChannelPackets.poll();
		}
		if(packet == null) {return false;}

		final ChannelData channelData = (ChannelData)packet;

		while(!channelData.getChannelData().isEmpty() && ambeBuffer.hasRemaining()) {
			ambeBuffer.put(channelData.getChannelData().poll());
		}
		ambeBuffer.flip();

		return true;
	}

	@Override
	public boolean decodeAMBE2PCMInput(ByteBuffer ambeBuffer) {
		boolean isSuccess = true;

		while(ambeBuffer.remaining() >= DV3KDefines.DV3K_AMBE_BLOCKSIZE) {
			final ChannelData packet = new ChannelData();

			for(int i = 0; i < DV3KDefines.DV3K_AMBE_BLOCKSIZE && ambeBuffer.hasRemaining(); i++)
				packet.getChannelData().add(ambeBuffer.get());

			if(!addWritePacket(packet)) {isSuccess = false;}
		}

		locker.lock();
		try {
			processThread.wakeupProcessThread();
		}finally {
			locker.unlock();
		}

		return isSuccess;
	}

	@Override
	public boolean decodeAMBE2PCMOutput(ShortBuffer pcmBuffer) {
		DV3KPacket packet = null;
		synchronized (uartReceiveSpeechPackets) {
			packet = uartReceiveSpeechPackets.poll();
		}
		if(packet == null) {return false;}

		final SpeechData speechData = (SpeechData)packet;

		while(!speechData.getSpeechData().isEmpty() && pcmBuffer.hasRemaining()) {
			final int sample = speechData.getSpeechData().poll();
			pcmBuffer.put((short)sample);
		}
		pcmBuffer.flip();

		return true;
	}

	private boolean internalProcess(final boolean threadTerminateRequest) {

		boolean terminateRequest = false;

		locker.lock();
		try {

			boolean reProcess;
			do {
				reProcess = false;

				isStateChanged = nextState != currentState;
				currentState = nextState;

				switch(currentState) {
				case INITIALIZE:
					terminateRequest = !onStateInitialize(threadTerminateRequest);
					break;

				case IDLE:
					terminateRequest = !onStateIdle(threadTerminateRequest);
					break;

				case WAIT:
					terminateRequest = !onStateWait(threadTerminateRequest);
					break;

				default:
					break;
				}

			}while(reProcess && !terminateRequest);
		}finally {
			locker.unlock();
		}

		return !terminateRequest;
	}

	private boolean onStateInitialize(final boolean threadTerminateRequest) {
		nextState = ProcessState.IDLE;

		return !threadTerminateRequest;
	}

	private boolean onStateIdle(final boolean threadTerminateRequest) {
		if(threadTerminateRequest) {
			closeUart();
		}
		else {
			locker.lock();
			try {
				boolean isAvailableWritePackets = false;
				synchronized(writePackets) {
					isAvailableWritePackets = !writePackets.isEmpty();
				}
				if(isAvailableWritePackets) {
					if(uart == null || !uart.isOpen()) {
						uartPortInactivityTimer.updateTimestamp();

						if(!openPort()) {
							toWaitState(ProcessState.INITIALIZE, 10, TimeUnit.SECONDS);

							if(log.isWarnEnabled())
								log.warn(logTag + "Could not open uart port = " + getDV3KPortName() + ", will going initialize state.");
						}
						else {
							writePackets();
						}
					}
					else {
						writePackets();
					}
				}
/*
				if(uartPortInactivityTimer.isTimeout(5, TimeUnit.SECONDS) && uart != null  && uart.isOpen()) {
					closeUart();

					if(log.isTraceEnabled())
						log.trace(logTag + "Uart port inactivity timer timeout, will close uart port = " + getDV3KPortName());
				}
*/
			}catch(InterruptedException ex) {

			}finally {
				locker.unlock();
			}
		}

		return !threadTerminateRequest;
	}

	private boolean onStateWait(final boolean threadTerminateRequest) {
		if(stateTimekeeper.isTimeout()) {nextState = callbackState;}

		return !threadTerminateRequest;
	}

	private void toWaitState(final ProcessState callbackState, final long time, final TimeUnit timeUnit) {
		this.callbackState = callbackState;
		nextState = ProcessState.WAIT;
		stateTimekeeper.setTimeoutTime(time, timeUnit);
		stateTimekeeper.updateTimestamp();
	}

	private void writePackets() throws InterruptedException {
		locker.lock();
		try {
			if(writeBuffer != null) {
				if(!writePacket()) {openPort();}

				if(writeBuffer != null) {return;}
			}

			DV3KPacket packet = null;
			do {
				synchronized(writePackets) {
					packet = writePackets.poll();
				}
				if(packet == null) {break;}

				final ByteBuffer buffer = packet.assemblePacket();
				if(buffer == null) {
					if(log.isWarnEnabled())
						log.warn(logTag + "DV3K packet assemble error = " + packet);

					continue;
				}
				else if(buffer.remaining() <= 0) {continue;}

				writeBuffer = new byte[buffer.remaining()];
				for(int i = 0; i < writeBuffer.length && buffer.hasRemaining(); i++)
					writeBuffer[i] = buffer.get();

				if(!writePacket()) {openPort();}

			}while(packet != null && writeBuffer == null);
		}finally {
			locker.unlock();
		}
	}

	private boolean writePacket() throws InterruptedException{
		boolean success = false;

		locker.lock();
		try {
			if(writeBuffer == null || writeBuffer.length <= 0) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Write buffer is empty.");

				return false;
			}

			final Timer timekeeper = new Timer();
			timekeeper.updateTimestamp();
			do {
				final int writeBytes = uart.writeBytes(writeBuffer);

				if(writeBytes < 0) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Failed to write to uart port = " + getDV3KPortName() + "/Code = " + writeBytes);

					closeUart();

					if(!openPort()) {

						writeBuffer = null;
						success = false;

						if(log.isWarnEnabled())
							log.warn(logTag + "Could not open uart port = " + getDV3KPortName());

						break;
					}
				}
				else if(writeBytes < writeBuffer.length) {
					if(log.isTraceEnabled()) {
						log.trace(
							logTag + "Write to uart port = " + getDV3KPortName() + "\n" +
							"    [*]" + writeBytes + "/" + writeBuffer.length + "bytes\n" +
							FormatUtil.bytesToHexDump(writeBuffer, writeBytes, 4)
						);
					}

					writeBuffer =
						Arrays.copyOfRange(writeBuffer, writeBuffer.length - writeBytes, writeBuffer.length);
				}
				else {
					if(log.isTraceEnabled()) {
						log.trace(
							logTag + "Write to uart port = " + getDV3KPortName() + "\n" +
							"    " + writeBytes + "/" + writeBuffer.length + "bytes\n" +
							FormatUtil.bytesToHexDump(writeBuffer, writeBytes, 4)
						);
					}
					writeBuffer = null;
				}

				uartPortInactivityTimer.updateTimestamp();

				if(writeBuffer != null) {
					if(!timekeeper.isTimeout(500, TimeUnit.MILLISECONDS)) {
						try {
							Thread.sleep(10);
						}catch(InterruptedException ex) {
							writeBuffer = null;

							throw ex;
						}
					}
					else {
						success = false;
						break;
					}
				}
				else {
					success = true;
					break;
				}
			}while(writeBuffer != null);

		}finally {
			locker.unlock();
		}

		return success;
	}

	private void parseUartDV3KPacket() {
		DV3KPacket packet = null;

		do {
			packet = null;

			uartReceiveBufferLocker.lock();
			try {
				uartReceiveBufferState = BufferState.toREAD(uartReceiveBuffer, uartReceiveBufferState);
				parseUartDV3KPacket(uartReceiveBuffer, uartReceivePackets);

				packet = uartReceivePackets.poll();
			}finally {
				uartReceiveBufferLocker.unlock();
			}

			if(packet != null) {
				if(
					packet.getPacketType() == DV3KPacketType.ChannelPacket &&
					packet.getChannelPacketType() == DV3KChannelPacketType.ChannelData
				) {
					synchronized(uartReceiveChannelPackets) {
						while(uartReceiveChannelPackets.size() >= 100)
							uartReceiveChannelPackets.poll();

						uartReceiveChannelPackets.add(packet);
					}
				}
				else if(
					packet.getPacketType() == DV3KPacketType.SpeechPacket &&
					packet.getSpeechPacketType() == DV3KSpeechPacketType.SpeechData
				) {
					synchronized(uartReceiveSpeechPackets) {
						while(uartReceiveSpeechPackets.size() >= 100)
							uartReceiveSpeechPackets.poll();

						uartReceiveSpeechPackets.add(packet);
					}
				}
				else if(packet.getPacketType() == DV3KPacketType.ControlPacket){
					synchronized(uartReceiveOtherPackets) {
						while(uartReceiveOtherPackets.size() >= 100)
							uartReceiveOtherPackets.poll();

						uartReceiveOtherPackets.add(packet);
					}
				}

				if(eventListener != null)
					eventListener.event(DV3KControllerEvent.ReceivePacket, packet);
			}
		}while(packet != null);
	}

	private boolean parseUartDV3KPacket(
		ByteBuffer receiveBuffer,
		Queue<DV3KPacket> receivePackets
	) {
		boolean hasValidPacket = false;
		boolean match = false;
		DV3KPacket parsedCommand = null;
		do {
			if(log.isTraceEnabled()) {
				final int savedPos = receiveBuffer.position();
				log.trace(
					logTag + "Parse uart receive buffer.\n" +
					FormatUtil.byteBufferToHexDump(receiveBuffer, 4)
				);
				receiveBuffer.position(savedPos);
			}
			if (
				(parsedCommand = dv3kPRODID.parsePacket(receiveBuffer)) != null ||
				(parsedCommand = dv3kVERSTRING.parsePacket(receiveBuffer)) != null ||
				(parsedCommand = dv3kRATEP.parsePacket(receiveBuffer)) != null ||
				(parsedCommand = dv3kSpeechData.parsePacket(receiveBuffer)) != null ||
				(parsedCommand = dv3kChannelData.parsePacket(receiveBuffer)) != null ||
				(parsedCommand = dv3kReady.parsePacket(receiveBuffer)) != null
			) {
				parsedCommand.setRemoteAddress(new InetSocketAddress(0));

				while(receivePackets.size() >= 100) {receivePackets.poll();}
				if(receivePackets.add(parsedCommand)) {hasValidPacket = true;}

				if(log.isTraceEnabled())
					log.trace(logTag + "Receive DV3K packet from " + getDV3KPortName() + ".\n    " + parsedCommand);

				match = true;
			} else if(parseUnknownPacket(receiveBuffer)) {
				match = true;
			} else {
				match = false;
			}
		} while (match);

		return hasValidPacket;
	}

	private boolean parseUnknownPacket(final ByteBuffer buffer) {
		if(buffer.remaining() < 4) {return false;}

		final int savedPos = buffer.position();

		final byte[] header = new byte[4];
		buffer.get(header);

		if(header[0] != DV3KDefines.DV3K_START_BYTE) {
			buffer.position(savedPos);

			return false;
		}

		final int fieldLength =
			((header[1] << 8) & 0xFF00) | (header[2] & 0x00FF);

		if(buffer.remaining() < fieldLength) {
			buffer.position(savedPos);

			return false;
		}

		final ByteBuffer packet = ByteBuffer.allocate(fieldLength + 4);
		packet.put(header);
		for(int i = 0; i < fieldLength && buffer.hasRemaining(); i++) {packet.put(buffer.get());}
		packet.flip();

		buffer.compact();
		buffer.limit(buffer.position());
		buffer.position(0);

		if(log.isInfoEnabled())
			log.info(logTag + "Unknown packet received.\n" + FormatUtil.byteBufferToHexDump(packet, 4));

		return true;
	}

	private boolean addWritePacket(final DV3KPacket packet) {
		boolean success = false;

		locker.lock();
		try {
			synchronized(writePackets) {
				while(writePackets.size() >= 100) {writePackets.poll();}

				success = writePackets.add(packet);
			}
		}finally {
			locker.unlock();
		}

		return success;
	}

	private boolean openPort() {
		closeUart();

		locker.lock();
		try {
			uart = UartInterfaceFactory.createUartInterface(exceptionListener);

			uart.addEventListener(uartEvent);
			uart.setBaudRate(getDV3KBaudRate());
			uart.setDataBits(8);
			uart.setStopBitMode(UartStopBitModes.STOPBITS_ONE);
			uart.setParityMode(UartParityModes.PARITY_NONE);
			uart.setFlowControlMode(UartFlowControlModes.FLOWCONTROL_DISABLE);

			final boolean isOpenSuccess = uart.openPort(getDV3KPortName());

			if(log.isDebugEnabled())
				log.debug("Open uart = " + uart);

			return isOpenSuccess;
		}finally {
			locker.unlock();
		}
	}

	private void closeUart() {
		locker.lock();
		try {
			if(uart != null) {
				uart.closePort();

				if(log.isDebugEnabled())
					log.debug("Close uart = " + uart);
			}

			uart = null;
		}finally {
			locker.unlock();
		}
	}
}
