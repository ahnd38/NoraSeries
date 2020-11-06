package org.jp.illg.util.audio.util.winlinux;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineEvent;
import javax.sound.sampled.LineListener;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AudioPlaybackWorker {

	@Getter
	private final AudioFormat playbackAudioFormat;

	private Mixer.Info playbackMixer;

	private SourceDataLine playbackLine;

	public AudioPlaybackWorker(
		@NonNull AudioFormat playbackAudioFormat
	) {
		super();

		this.playbackAudioFormat = playbackAudioFormat;
	}

	public boolean open(final String captureMixerName) {
		playbackMixer =
			AudioTool.getMixerInfo(false, captureMixerName, getPlaybackAudioFormat());
		if(playbackMixer == null) {
			return false;
		}

		try {
			playbackLine =
				(SourceDataLine) AudioSystem
				.getMixer(playbackMixer)
				.getLine(new DataLine.Info(SourceDataLine.class, playbackAudioFormat));

			playbackLine.addLineListener(new LineListener() {
				@Override
				public void update(LineEvent event) {
					if(log.isTraceEnabled())
						log.trace("Playback line event received = Type:" + event.getType());
				}
			});

			playbackLine.open(getPlaybackAudioFormat());
		} catch (LineUnavailableException ex) {
			return false;
		}

		start();

		return true;
	}

	public void close() {
		if(playbackLine != null && playbackLine.isOpen())
			playbackLine.close();
	}

	public void start() {
		if(playbackLine != null && playbackLine.isOpen())
			playbackLine.start();
	}

	public void stop() {
		if(playbackLine != null && playbackLine.isOpen())
			playbackLine.stop();
	}

	public boolean write(final byte[] buffer, final int offset) {
		if(playbackLine != null && playbackLine.isOpen()) {
//			if(playbackLine.available() < buffer.length)
//				playbackLine.flush();

			int writeBytes = 0;
			while(writeBytes < buffer.length)
				writeBytes += playbackLine.write(buffer, offset + writeBytes, buffer.length - writeBytes);

			return true;
		}
		else {
			return false;
		}
	}
}
