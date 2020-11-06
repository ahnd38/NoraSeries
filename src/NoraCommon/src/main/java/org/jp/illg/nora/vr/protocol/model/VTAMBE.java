package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.nora.vr.model.NoraVRCodecType;

import lombok.NonNull;

public class VTAMBE extends VoiceTransferBase<Byte> {

	public VTAMBE() {
		this(null);
	}

	public VTAMBE(Header header) {
		super(NoraVRCommandType.VTAMBE, header);
	}

	@Override
	public VTAMBE clone() {
		final VTAMBE copy = (VTAMBE)super.clone();

		return copy;
	}

	@Override
	protected boolean assembleVoiceField(@NonNull ByteBuffer buffer) {
		if(
			buffer.remaining() < getVoiceFieldLength() &&
			getAudio().size() > getVoiceFieldLength()
		) {
			return false;
		}

		for(int i = 0; i < getVoiceFieldLength() && !getAudio().isEmpty() && buffer.hasRemaining(); i++) {
			buffer.put(getAudio().poll());
		}

		return true;
	}

	@Override
	protected int getVoiceFieldLength() {
		return 9;
	}

	@Override
	protected boolean parseVoiceField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 9) {return false;}

		getAudio().clear();

		for(int i = 0; i < getVoiceFieldLength() && buffer.hasRemaining(); i++) {
			getAudio().add(buffer.get());
		}

		return true;
	}

	@Override
	public NoraVRCodecType getCodecType() {
		return NoraVRCodecType.AMBE;
	}

}
