package org.jp.illg.dvr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.util.ObjectWrapper;
import org.jp.illg.util.TimestampWithTimeout;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture.AudioCaptureEventLintener;
import org.jp.illg.util.audio.vocoder.VoiceVocoder;
import org.jp.illg.util.audio.vocoder.opus.OpusVocoderFactory;
import org.jp.illg.util.audio.vocoder.pcm.PCMVocoder;
import org.jp.illg.util.logback.LogbackUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpusTest {

	private OpusTest() {

	}

	public static void main(String[] args) {
		InputStream logConfig = null;
		try {
			logConfig = OpusTest.class.getClassLoader().getResourceAsStream("logback_stdconsole.xml");

			if (!LogbackUtil.initializeLogger(logConfig, true))
				log.warn("Could not debug log configuration !");
		} finally {
			try {
				logConfig.close();
			} catch (IOException ex) {
			}
		}

		VoiceVocoder<ShortBuffer> opus =
			OpusVocoderFactory.createOpusVocoder(NoraVRCodecType.Opus8k.getTypeName(), true);
		if(opus == null) {
			log.error("Could not create opus instance.");
			return;
		}
		else if(!opus.init(8000, 1, 8000)) {
			log.error("Could not init opus.");
			return;
		}

		AudioFormat audioFormat =
			new AudioFormat(8000.0f, 16, 1, true, true);

		DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, audioFormat);

		SourceDataLine dl = null;
		try {
			dl = (SourceDataLine) AudioSystem.getLine(sourceInfo);

			dl.open(audioFormat);
		} catch (LineUnavailableException ex) {
			log.error("Failed line open.", ex);
			return;
		}
		final SourceDataLine sourceDataline = dl;

		sourceDataline.start();

		final TimestampWithTimeout timer = new TimestampWithTimeout(1000);
		final ObjectWrapper<Integer> samples = new ObjectWrapper<Integer>();
		samples.setObject(0);

		final PCMVocoder pcmVocoder = new PCMVocoder(NoraVRCodecType.PCM.getTypeName(), false);

		AudioPlaybackCapture inputWorker =
			new AudioPlaybackCapture(audioFormat, 320,
				new AudioCaptureEventLintener() {
					@Override
					public void handleAudioCaptureEvent(byte[] audioData) {
						{
							final ShortBuffer buf = ShortBuffer.allocate(audioData.length / 2);
							short sample = 0;
							for(int i = 0; i < audioData.length; i += 2) {
								sample = (short)(((audioData[i] << 8) & 0xFF00) | (audioData[i + 1] & 0x00FF));

								buf.put((short)sample);
							}
							buf.flip();

							pcmVocoder.encodeInput(buf);
						}

						byte[] encoded = null;
						while((encoded = pcmVocoder.encodeOutput()) != null) {
							pcmVocoder.decodeInput(encoded, false);
						}

						ShortBuffer pcmDecoded = null;
						while((pcmDecoded = pcmVocoder.decodeOutput()) != null) {
							opus.encodeInput(pcmDecoded);

							byte[] encodedData = null;
							while((encodedData = opus.encodeOutput()) != null) {
								opus.decodeInput(encodedData, false);
							}

							ShortBuffer decodedData = null;
							while((decodedData = opus.decodeOutput()) != null) {
								final byte[] outputBuffer = new byte[decodedData.remaining() * 2];
								for(int i = 0; i < outputBuffer.length && decodedData.hasRemaining(); i += 2) {
									final short sample = decodedData.get();

									outputBuffer[i] = (byte)((sample >> 8) & 0xFF);
									outputBuffer[i + 1] = (byte)(sample & 0xFF);
								}

								sourceDataline.write(outputBuffer, 0, outputBuffer.length);

								samples.setObject(samples.getObject() + (outputBuffer.length / 2));

							}
						}


						if(timer.isTimeout()) {
							timer.updateTimestamp();

							log.info("Output sample rate : " + samples.getObject() + "/s" +
								", PacketSize : Ave=" + (opus.getEncodeAveragePacketSize() * 8) +
								"|Max=" + (opus.getEncodeMaxPacketSize() * 8) + "|Min=" + (opus.getEncodeMinPacketSize() * 8) + "."
							);
							samples.setObject(0);;
						}
					}
				}
			);



		inputWorker.openCapture(null);

		while(true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				break;
			}
		}


		inputWorker.close();
	}

}
