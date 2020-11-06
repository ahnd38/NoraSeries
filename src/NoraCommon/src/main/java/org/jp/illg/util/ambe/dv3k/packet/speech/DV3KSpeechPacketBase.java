package org.jp.illg.util.ambe.dv3k.packet.speech;

import java.nio.ByteBuffer;

import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketBase;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class DV3KSpeechPacketBase extends DV3KPacketBase {

	@Getter
	@Setter
	private DV3KSpeechPacketType speechPacketType;

	protected DV3KSpeechPacketBase(@NonNull final DV3KSpeechPacketType speechPacketType) {
		super(DV3KPacketType.SpeechPacket);

		setSpeechPacketType(speechPacketType);
	}

	@Override
	public DV3KSpeechPacketBase clone() {
		DV3KSpeechPacketBase copy = null;

		copy = (DV3KSpeechPacketBase)super.clone();

		copy.speechPacketType = this.speechPacketType;

		return copy;
	}

	@Override
	protected boolean assembleFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < 1) {return false;}

//		buffer.put((byte)0x40);
		buffer.put((byte)getSpeechPacketType().getValue());

		if(getRequestSpeechFieldDataLength() >= 1)
			return assembleSpeechFieldData(buffer);
		else
			return true;
	}

	@Override
	protected boolean parseFieldData(ByteBuffer buffer, int fieldLength) {
		if(
			buffer.remaining() < fieldLength ||
			fieldLength < 2
		) {return false;}


		DV3KSpeechPacketType speechPacketType =
			DV3KSpeechPacketType.getTypeByValue(buffer.get());
		if(
			speechPacketType == null ||
			speechPacketType != getSpeechPacketType() ||
			speechPacketType.getDataLengthResponse() > buffer.remaining()
		) {return false;}

		if(fieldLength > 1)
			return parseSpeechFieldData(buffer, fieldLength - 1);
		else
			return true;
	}

	@Override
	protected int getRequestFieldDataLength() {
		return getSpeechPacketType().getDataLengthRequest() != -1 ?
			getSpeechPacketType().getDataLengthRequest() : getRequestSpeechFieldDataLength() + 1;
	}

	protected abstract int getRequestSpeechFieldDataLength();
	protected abstract boolean assembleSpeechFieldData(ByteBuffer buffer);
	protected abstract boolean parseSpeechFieldData(ByteBuffer buffer, int fieldLength);

}
