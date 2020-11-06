package org.jp.illg.util.ambe.dv3k.packet.speech;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.Queue;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class SpeechData extends DV3KSpeechPacketBase {

	private static final int samplesMax = 164;
	private static final int samplesMin = 156;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Queue<Integer> speechData;

	public SpeechData() {
		super(DV3KSpeechPacketType.SpeechData);

		setSpeechData(new LinkedList<Integer>());
	}

	@Override
	public SpeechData clone() {
		SpeechData copy = null;

		copy = (SpeechData)super.clone();

		copy.speechData = new LinkedList<>(getSpeechData());

		return copy;
	}

	@Override
	protected int getRequestSpeechFieldDataLength() {
		return 1 + (getSamples() * 2);
	}

	@Override
	protected boolean assembleSpeechFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < getRequestSpeechFieldDataLength()) {return false;}

		final int samples = getSamples();

		buffer.put((byte)samples);

		for(
			int i = 0; i < samples && !getSpeechData().isEmpty() && buffer.remaining() >= 2; i++
		) {
			final int pcm = getSpeechData().poll();

			buffer.put((byte)((pcm >> 8) & 0xFF));
			buffer.put((byte)(pcm & 0xFF));
		}

		return true;
	}

	@Override
	protected boolean parseSpeechFieldData(ByteBuffer buffer, int fieldLength) {
		if(
			buffer.remaining() < fieldLength ||
			buffer.remaining() < (1 + (samplesMin * 2))
		) {return false;}

		getSpeechData().clear();

		final int samples = (buffer.get() & 0xFF);

		for(int i = 0; i < samples && buffer.remaining() >= 2; i++) {
			final short pcm =
				(short)(((buffer.get() << 8) & 0xFF00) | (buffer.get() & 0x00FF));

			getSpeechData().add((int)pcm);
		}

		return true;
	}

	private int getSamples() {
		return getSpeechData().size() > samplesMax ? samplesMax : getSpeechData().size();
	}
}
