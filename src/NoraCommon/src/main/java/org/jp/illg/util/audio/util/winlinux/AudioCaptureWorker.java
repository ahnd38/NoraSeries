package org.jp.illg.util.audio.util.winlinux;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture.AudioPlaybackCaptureEventLintener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioCaptureWorker implements Runnable {

	private Thread workerThread;
	private boolean workerThreadAvailable;

	private TargetDataLine captureLine;
	private boolean captureLineRunning;
	private final Lock captureLineLocker;
	private final Condition captureLineOpened;

	@Getter
	private final AudioPlaybackCaptureEventLintener captureEventListener;

	@Getter
	private final AudioFormat captureAudioFormat;

	private final int captureBufferSize;

	private Mixer.Info captureMixer;

	public AudioCaptureWorker(
		@NonNull AudioFormat captureAudioFormat,
		int captureBufferSize,
		@NonNull AudioPlaybackCaptureEventLintener captureEventListener
	) {
		super();

		this.captureAudioFormat = captureAudioFormat;
		if(captureBufferSize < 1) {throw new IllegalArgumentException();}
		this.captureBufferSize = captureBufferSize;
		this.captureEventListener = captureEventListener;

		workerThreadAvailable = false;

		captureLineLocker = new ReentrantLock();
		captureLineOpened = captureLineLocker.newCondition();
		captureLineRunning = false;
	}

	public boolean open(final String captureMixerName) {
		close();

		captureMixer = AudioTool.getMixerInfo(true, captureMixerName, getCaptureAudioFormat());
		if(captureMixer == null) {return false;}

		try {
			captureLine =
				(TargetDataLine) AudioSystem
				.getMixer(captureMixer)
				.getLine(new DataLine.Info(TargetDataLine.class, getCaptureAudioFormat()));
		} catch (LineUnavailableException ex) {
			if(log.isErrorEnabled())
				log.error("Could not create capture mixer device instance = " + captureMixer.getName() + ".", ex);

			return false;
		}
		if(captureLine == null) {
			if(log.isErrorEnabled())
				log.error("Capture mixer not found.");

			return false;
		}

		captureLine.addLineListener(new LineListener() {

			@Override
			public void update(LineEvent event) {
				if(log.isTraceEnabled())
					log.trace("Capture line event received = Type:" + event.getType());

				if(event.getType() == LineEvent.Type.OPEN) {
					captureLineLocker.lock();
					try {
						captureLineOpened.signalAll();
					}finally {
						captureLineLocker.unlock();
					}
//					captureLine.start();
				}
			}
		});

		try {
			captureLine.open(getCaptureAudioFormat());
		}catch(LineUnavailableException ex) {
			if(log.isErrorEnabled())
				log.error("Could not open capture line.", ex);

			return false;
		}

		if(log.isInfoEnabled())
			log.info("Open capture device " + captureMixer.getName());

		return true;
	}

	public void close() {
		stop();

		if(captureLine != null && captureLine.isOpen())
			captureLine.close();

		captureLine = null;
	}

	public boolean start() {
		if(
			workerThreadAvailable &&
			workerThread != null && workerThread.isAlive()
		) {stop();}

		if(captureLine == null)
			return false;

		if(!captureLine.isOpen()) {
			captureLineLocker.lock();
			try {
				captureLineOpened.await(5, TimeUnit.SECONDS);
			}catch(InterruptedException ex) {
				return false;
			}finally {
				captureLineLocker.unlock();
			}
		}

		captureLine.start();
		captureLineRunning = true;

		workerThread = new Thread(this);
		workerThread.setName(this.getClass().getSimpleName() + "_" + workerThread.getId());
		workerThreadAvailable = true;
		workerThread.start();

		return true;
	}

	public void stop() {
		workerThreadAvailable = false;
		if(
			workerThread != null && workerThread.isAlive() &&
			workerThread.getId() != Thread.currentThread().getId()
		) {
			workerThread.interrupt();
			try {
				if(captureLine != null && captureLine.isOpen()) {
					captureLine.flush();
					captureLine.stop();
				}
				captureLineRunning = false;

				workerThread.join();
			} catch (InterruptedException ex) {
//				if(log.isDebugEnabled()) {log.debug("function stop() received interrupt.",ex);}
			}
		}
	}

	@Override
	public void run() {
		if(workerThread == null || workerThread.getId() != Thread.currentThread().getId()) {
			log.error("Thread ID not matched.");

			return;
		}

		try {
			long savedTimestamp = System.currentTimeMillis();
			int rateCounter = 0;
			do {
				byte[] buffer = new byte[captureBufferSize];
				Arrays.fill(buffer, (byte)0x00);

				if(
					read(buffer) &&		// semi block 20ms
					getCaptureEventListener() != null &&
					getCaptureEventListener() instanceof AudioPlaybackCaptureEventLintener
				) {
					try {
						getCaptureEventListener().audioCaptureEvent(buffer);
					}catch(Exception ex) {
						if(log.isWarnEnabled())
							log.warn("Exception occurred on audio capture event.", ex);
					}
				}else {
					Thread.sleep(10);
				}

				if(rateCounter < 500) {
					rateCounter++;
				}else {
					rateCounter = 0;
					long diffTimeMillis = (System.currentTimeMillis() - savedTimestamp) / 10;
					if(log.isTraceEnabled())
						log.trace("Audio input rate is 1000/" + diffTimeMillis);

					savedTimestamp = System.currentTimeMillis();
				}
			}while(this.workerThreadAvailable && !this.workerThread.isInterrupted());

		}catch(InterruptedException ex) {

		}
	}

	private boolean read(byte[] buffer) {
		if(buffer == null){
			throw new IllegalArgumentException();
		}

		if(captureLine == null)
			return false;

		int bytesToRead = 0;
		while(bytesToRead < buffer.length && captureLine.isOpen() && captureLineRunning) {
			int numBytesRead =
					captureLine.read(buffer, bytesToRead, buffer.length - bytesToRead);

			if(numBytesRead == -1) {return false;}

			bytesToRead += numBytesRead;
		}

		return bytesToRead >= buffer.length;
	}

}
