package org.jp.illg.util.audio.util.winlinux;

import javax.sound.sampled.AudioFormat;

import lombok.NonNull;


public class AudioPlaybackCapture {

	public interface AudioPlaybackCaptureEventLintener{
		public void audioCaptureEvent(byte[] audioData);
	}

	private final AudioCaptureWorker audioCapture;
	private final AudioPlaybackWorker audioPlayback;

	public AudioPlaybackCapture(
		@NonNull final AudioFormat audioFormat,
		int captureBufferSize,
		AudioPlaybackCaptureEventLintener captureEventListener
	) {
		super();

		audioCapture = new AudioCaptureWorker(audioFormat, captureBufferSize, captureEventListener);
		audioPlayback = new AudioPlaybackWorker(audioFormat);
	}


	public boolean openCapture(final String captureMixerName) {
		return audioCapture.open(captureMixerName);
	}

	public void closeCapture() {
		audioCapture.close();
	}

	public boolean startCapture() {
		return audioCapture.start();
	}

	public void stopCapture() {
		audioCapture.stop();
	}

	public boolean openPlayback(final String captureMixerName) {
		return audioPlayback.open(captureMixerName);
	}

	public void closePlayback() {
		audioPlayback.close();
	}

	public void startPlayback() {
		audioPlayback.start();
	}

	public void stopPlayback() {
		audioPlayback.stop();
	}

	public void close() {
		closeCapture();
		closePlayback();
	}

	public boolean writePlayback(final byte[] buffer, int offset) {
		return audioPlayback.write(buffer, offset);
	}
}
