package org.jp.illg.util.ambe.dv3k;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.Timer;
import org.jp.illg.util.ambe.dv3k.model.DV3KControllerEvent;
import org.jp.illg.util.ambe.dv3k.model.DV3KProcessState;
import org.jp.illg.util.ambe.dv3k.network.DV3KNetworkController;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.DV3KControlPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.PRODID;
import org.jp.illg.util.ambe.dv3k.packet.control.RATEP;
import org.jp.illg.util.ambe.dv3k.packet.control.RESETSOFTCFG;
import org.jp.illg.util.ambe.dv3k.packet.control.VERSTRING;
import org.jp.illg.util.ambe.dv3k.uart.DV3KUartController;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.google.common.util.concurrent.RateLimiter;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DV3KController {

	private String logHeader;
	private ThreadUncaughtExceptionListener exceptionListener;

	private DV3KInterface dv3k;

	private final EventListener<DV3KEvent> eventListener;

	private Timer stateTimeKeeper;

	private DV3KProcessState currentState;
	private DV3KProcessState nextState;
	private DV3KProcessState callbackState;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;

	private final Queue<ByteBuffer> encodedVoice;
	private final Queue<ShortBuffer> encodeInputVoice;
	private final Queue<ShortBuffer> decodedVoice;
	private final Queue<ByteBuffer> decodeInputVoice;
	private final Lock voiceQueueLocker;

	private final ByteBuffer encodeBuffer;
	private final ShortBuffer decodeBuffer;

	private final Timer timestampSetRateP;

	private final RateLimiter encodeRateLimiter;
	private final RateLimiter decodeRateLimiter;

	private final ExecutorService workerExecutor;

	public DV3KController(
		final ExecutorService workerExecutor,
		final ThreadUncaughtExceptionListener exceptionListener,
		final EventListener<DV3KEvent> eventListener
	) {
		super();

		logHeader = DV3KController.class.getSimpleName() + " : ";

		if(workerExecutor != null) {
			this.workerExecutor = workerExecutor;
		}
		else {
			this.workerExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					final Thread t = new Thread(r);
					t.setName(DV3KController.class.getSimpleName() + "Worker_" + t.getId());

					return t;
				}
			});
		}

		this.eventListener = eventListener;

		this.exceptionListener = exceptionListener;

		stateTimeKeeper = new Timer();

		currentState = DV3KProcessState.Initialize;
		nextState = DV3KProcessState.Initialize;
		callbackState = DV3KProcessState.Initialize;

		encodedVoice = new LinkedList<ByteBuffer>();
		encodeInputVoice = new LinkedList<ShortBuffer>();
		decodedVoice = new LinkedList<ShortBuffer>();
		decodeInputVoice = new LinkedList<ByteBuffer>();

		encodeBuffer = ByteBuffer.allocateDirect(DV3KDefines.DV3K_AMBE_BLOCKSIZE);
		decodeBuffer = ShortBuffer.allocate(DV3KDefines.DV3K_PCM_BLOCKSIZE);
		voiceQueueLocker = new ReentrantLock();

		timestampSetRateP = new Timer();

		encodeRateLimiter = RateLimiter.create(50);
		decodeRateLimiter = RateLimiter.create(50);
	}

	public boolean setProperties(Properties properties) {
		String type =
			properties.getProperty("DV3KInterfaceType");

		if("Network".equalsIgnoreCase(type)) {
			dv3k = new DV3KNetworkController(workerExecutor, exceptionListener, controllerEventListener);
		}
		else if(
			"Uart".equalsIgnoreCase(type) ||
			"Serial".equalsIgnoreCase(type) ||
			"SerialPort".equalsIgnoreCase(type)
		) {
			dv3k = new DV3KUartController(workerExecutor, exceptionListener, controllerEventListener);
		}
		else {
			if(log.isErrorEnabled())
				log.error(logHeader + "Unknown DV3K interface type " + type + ".");

			return false;
		}

		final boolean interfaceConfigSuccess = dv3k.setProperties(properties);

		logHeader =
			DV3KController.class.getSimpleName() +
			"(" + dv3k.getClass().getSimpleName() + "/" + dv3k.getPortName() + ") : ";

		return interfaceConfigSuccess;
	}

	private final EventListener<DV3KControllerEvent> controllerEventListener =
		new EventListener<DV3KControllerEvent>() {
			@Override
			public void event(DV3KControllerEvent event, Object attachment) {
				switch(event) {
				case ReceivePacket:
					if(eventListener != null) {
						final DV3KPacket packet = (DV3KPacket)attachment;
						if(
							packet.getPacketType() == DV3KPacketType.ChannelPacket ||
							packet.getPacketType() == DV3KPacketType.SpeechPacket
						) {eventListener.event(DV3KEvent.ReceivePacket, packet);}
					}

					break;
				}
			}
		};

	public boolean start() {
		return dv3k != null && dv3k.open();
	}

	public void stop() {
		if(dv3k != null) {dv3k.close();}
		dv3k = null;
	}

	public ThreadProcessResult process() {

		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		boolean reProcess;
		do {
			reProcess = false;

			setStateChanged(currentState != nextState);
			if(isStateChanged() && log.isTraceEnabled()) {
				log.trace(logHeader + "State changed " + currentState + " -> " + nextState + ".");
			}
			currentState = nextState;

			switch(currentState) {
			case Initialize:
				processResult = onStateInitialize();
				break;

			case SoftReset:
				processResult = onStateSoftReset();
				break;

			case ReadProductionID:
				processResult = onStateReadProductionID();
				break;

			case ReadVerstionString:
				processResult = onStateReadVersionString();
				break;

			case SetRATEP:
				processResult = onStateSetRATEP();
				break;

			case MainState:
				processResult = onStateMainState();
				break;

			case Wait:
				processResult = onStateWait();
				break;

			default:
				break;
			}

			if(
				currentState != nextState &&
				processResult == ThreadProcessResult.NoErrors
			) {reProcess = true;}

		}while(reProcess);

		return processResult;
	}

	private ThreadProcessResult onStateInitialize() {
		toWaitState(1, TimeUnit.SECONDS, DV3KProcessState.SoftReset);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSoftReset() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(1, TimeUnit.SECONDS);
			stateTimeKeeper.updateTimestamp();

			final RESETSOFTCFG packet = new RESETSOFTCFG();
			packet.setCfg0((byte)0x05);
			packet.setCfg1((byte)0x00);
			packet.setCfg2((byte)0x00);

			packet.setMask0((byte)0x0F);
			packet.setMask1((byte)0x00);
			packet.setMask2((byte)0x00);

			dv3k.writeDV3KPacket(packet);
		}
		else if(stateTimeKeeper.isTimeout()) {
			nextState = DV3KProcessState.ReadProductionID;
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateReadProductionID() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(2, TimeUnit.SECONDS);
			stateTimeKeeper.updateTimestamp();

			final PRODID packet = new PRODID();

			dv3k.writeDV3KPacket(packet);
		}
		else if(stateTimeKeeper.isTimeout()) {
			log.warn(logHeader + "State timeout " + currentState + ".");

			nextState = DV3KProcessState.Initialize;
		}
		else {
			while(dv3k.hasReadableDV3KPacket()) {
				final DV3KPacket packet = dv3k.readDV3KPacket();

				if(
					packet.getPacketType() == DV3KPacketType.ControlPacket &&
					packet.getControlPacketType() == DV3KControlPacketType.PRODID
				) {
					final PRODID productionID = (PRODID)packet;

					log.info(logHeader + "ProductionID = " + productionID.getProductionID() + ".");

					nextState = DV3KProcessState.ReadVerstionString;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateReadVersionString() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(2, TimeUnit.SECONDS);
			stateTimeKeeper.updateTimestamp();

			final VERSTRING packet = new VERSTRING();

			dv3k.writeDV3KPacket(packet);
		}
		else if(stateTimeKeeper.isTimeout()) {
			log.warn(logHeader + "State timeout " + currentState + ".");

			nextState = DV3KProcessState.Initialize;
		}
		else {
			while(dv3k.hasReadableDV3KPacket()) {
				final DV3KPacket packet = dv3k.readDV3KPacket();

				if(
					packet.getPacketType() == DV3KPacketType.ControlPacket &&
					packet.getControlPacketType() == DV3KControlPacketType.VERSTRING
				) {
					final VERSTRING versionString = (VERSTRING)packet;

					log.info(logHeader + "Version String = " + versionString.getVersionString() + ".");

					nextState = DV3KProcessState.SetRATEP;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateSetRATEP() {
		if(isStateChanged()) {
			stateTimeKeeper.setTimeoutTime(2, TimeUnit.SECONDS);
			stateTimeKeeper.updateTimestamp();

			final RATEP packet = new RATEP();
			packet.setRcw0(0x0130);
			packet.setRcw1(0x0763);
			packet.setRcw2(0x4000);
			packet.setRcw3(0x0000);
			packet.setRcw4(0x0000);
			packet.setRcw5(0x0048);

			dv3k.writeDV3KPacket(packet);
		}
		else if(stateTimeKeeper.isTimeout()) {
			log.warn(logHeader + "State timeout " + currentState + ".");

			nextState = DV3KProcessState.Initialize;
		}
		else {
			while(dv3k.hasReadableDV3KPacket()) {
				final DV3KPacket packet = dv3k.readDV3KPacket();

				if(
					packet.getPacketType() == DV3KPacketType.ControlPacket &&
					packet.getControlPacketType() == DV3KControlPacketType.RATEP
				) {
					timestampSetRateP.updateTimestamp();

					nextState = DV3KProcessState.MainState;
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateMainState() {
		if(isStateChanged()) {
//			stateTimeKeeper.setTimeoutTime(30, TimeUnit.SECONDS);
//			stateTimeKeeper.updateTimestamp();
		}
//		else if(stateTimeKeeper.isTimeout()) {

//			nextState = DV3KProcessState.SetRATEP;
//		}
		else {
			voiceQueueLocker.lock();
			try {
				if(
					timestampSetRateP.isTimeout(10, TimeUnit.SECONDS) &&
					(
						!encodeInputVoice.isEmpty() ||
						!decodeInputVoice.isEmpty()
					)
				) {
					nextState = DV3KProcessState.SetRATEP;
				}
				else {
					for(Iterator<ShortBuffer> it = encodeInputVoice.iterator(); it.hasNext();) {
						final ShortBuffer pcmVoice = it.next();

						if(encodeRateLimiter.tryAcquire()) {
							it.remove();

							dv3k.encodePCM2AMBEInput(pcmVoice);
						}
						else {
							break;
						}
					}
					for(Iterator<ByteBuffer> it = decodeInputVoice.iterator(); it.hasNext();) {
						final ByteBuffer ambeVoice = it.next();

						if(decodeRateLimiter.tryAcquire()) {
							it.remove();

							dv3k.decodeAMBE2PCMInput(ambeVoice);
						}
						else {
							break;
						}
					}

					encodeBuffer.clear();
					while(dv3k.encodePCM2AMBEOutput(encodeBuffer)) {
						final ByteBuffer buffer = ByteBuffer.allocate(encodeBuffer.remaining());
						buffer.put(encodeBuffer);
						buffer.flip();

						voiceQueueLocker.lock();
						try {
							while(encodedVoice.size() >= 100) {encodedVoice.poll();}

							encodedVoice.add(buffer);
						}finally {voiceQueueLocker.unlock();}

						encodeBuffer.clear();

//						stateTimeKeeper.updateTimestamp();
						timestampSetRateP.updateTimestamp();
					}

					decodeBuffer.clear();
					while(dv3k.decodeAMBE2PCMOutput(decodeBuffer)) {
						final ShortBuffer buffer = ShortBuffer.allocate(decodeBuffer.remaining());
						buffer.put(decodeBuffer);
						buffer.flip();

						voiceQueueLocker.lock();
						try {
							while(decodedVoice.size() >= 100) {decodedVoice.poll();}

							decodedVoice.add(buffer);
						}finally {voiceQueueLocker.unlock();}

						decodeBuffer.clear();

//						stateTimeKeeper.updateTimestamp();
						timestampSetRateP.updateTimestamp();
					}
				}
			}finally {voiceQueueLocker.unlock();}
		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(long waitTime, TimeUnit timeUnit, DV3KProcessState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = DV3KProcessState.Wait;
		this.callbackState = callbackState;
	}

	public boolean encodeInput(@NonNull final ShortBuffer pcmBuffer) {
		if(pcmBuffer.remaining() < DV3KDefines.DV3K_PCM_BLOCKSIZE) {return false;}

		voiceQueueLocker.lock();
		try {
			while(encodeInputVoice.size() >= 100) {encodeInputVoice.poll();}

			return encodeInputVoice.add(pcmBuffer);
		}finally {voiceQueueLocker.unlock();}
	}
	public ByteBuffer encodeOutput() {
		voiceQueueLocker.lock();
		try {
			if(encodedVoice.isEmpty()) {return null;}

			return encodedVoice.poll();
		}finally {voiceQueueLocker.unlock();}
	}

	public boolean decodeInput(final ByteBuffer ambeBuffer) {
		if(ambeBuffer.remaining() < DV3KDefines.DV3K_AMBE_BLOCKSIZE) {return false;}

		voiceQueueLocker.lock();
		try {
			while(decodeInputVoice.size() >= 100) {decodeInputVoice.poll();}

			return decodeInputVoice.add(ambeBuffer);
		}finally {voiceQueueLocker.unlock();}
	}

	public ShortBuffer decodeOutput() {
		voiceQueueLocker.lock();
		try {
			if(decodedVoice.isEmpty()) {return null;}

			return decodedVoice.poll();
		}finally {voiceQueueLocker.unlock();}
	}
}
