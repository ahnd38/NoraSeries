package org.jp.illg.util.audio.vocoder.pcm;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.audio.vocoder.VoiceVocoder;

import lombok.Getter;
import lombok.Setter;

/**
 * PCM dummy vocoder
 * @author AHND
 *
 */
public class PCMVocoder implements VoiceVocoder<ShortBuffer> {

	@Getter
	private int encodeMaxPacketSize;

	@Getter
	private int encodeMinPacketSize;

	@Getter
	private int encodeAveragePacketSize;

	@Getter
	@Setter
	private String vocoderType;

	private final Lock locker;

	private final Queue<ByteBuffer> encodedAudio;

	private final Queue<ShortBuffer> decodedAudio;


	public PCMVocoder(final String vocoderType, final boolean useFEC) {
		super();

		setVocoderType(vocoderType);

		locker = new ReentrantLock();

		encodedAudio = new LinkedList<ByteBuffer>();
		decodedAudio = new LinkedList<ShortBuffer>();
	}

	@Override
	public boolean init(int sampleRate, int channel, int bitRate) {
		return true;
	}

	@Override
	public void dispose() {

	}

	@Override
	public boolean encodeInput(ShortBuffer pcm) {
		if(pcm == null) {return false;}

		locker.lock();
		try {
			final ByteBuffer encoded = ByteBuffer.allocate(pcm.remaining() * 2);
			while(pcm.hasRemaining()) {
				final short sample = pcm.get();

				encoded.put((byte)((sample >> 8) & 0xFF));
				encoded.put((byte)(sample & 0xFF));
			}
			encoded.flip();

			final int packetSize = encoded.remaining();

			if(
				(
					encodeMinPacketSize == 0 && packetSize > 0
				) ||
				encodeMinPacketSize > packetSize
			) {encodeMinPacketSize = packetSize;}

			if(encodeMaxPacketSize < packetSize) {encodeMaxPacketSize = packetSize;}

			encodeAveragePacketSize = (encodeAveragePacketSize + packetSize) >> 1;

			return encodedAudio.add(encoded);

		}finally {locker.unlock();}
	}

	@Override
	public byte[] encodeOutput() {
		locker.lock();
		try {
			if(encodedAudio.isEmpty()) {return null;}

			final ByteBuffer encoded = encodedAudio.poll();

			final byte[] converted = new byte[encoded.remaining()];
			for(int i = 0; i < converted.length && encoded.hasRemaining(); i++) {
				converted[i] = encoded.get();
			}

			return converted;
		}finally {locker.unlock();}
	}

	@Override
	public boolean decodeInput(byte[] audio, boolean packetLoss) {
		if(audio == null) {return false;}

		locker.lock();
		try {
			final int samples = audio.length / 2;
			final int length = (audio.length / 2) << 1;
			final ShortBuffer decoded = ShortBuffer.allocate(samples);

			for(int i = 0; i < length && decoded.hasRemaining(); i+=2) {
				final short sample =
					(short)(((audio[i] << 8) & 0xFF00) | (audio[i + 1] & 0x00FF));

				decoded.put(sample);
			}
			decoded.flip();

			return decodedAudio.add(decoded);
		}finally {locker.unlock();}
	}

	@Override
	public ShortBuffer decodeOutput() {
		locker.lock();
		try {
			if(decodedAudio.isEmpty()) {return null;}

			return decodedAudio.poll();
		}finally {locker.unlock();}
	}
}
