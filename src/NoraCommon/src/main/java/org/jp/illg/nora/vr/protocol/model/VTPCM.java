package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.nora.vr.model.NoraVRCodecType;

import lombok.NonNull;

public class VTPCM extends VoiceTransferBase<Short> {

	public VTPCM() {
		this(null);
	}

	public VTPCM(final Header header) {
		super(NoraVRCommandType.VTPCM, header);
	}

	@Override
	public VTPCM clone() {
		final VTPCM copy = (VTPCM)super.clone();

		return copy;
	}

	@Override
	protected boolean assembleVoiceField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getVoiceFieldLength())
			return false;


		for(int i = 0; i < getVoiceFieldLength(); i+= 2) {
			short sample = 0;

			if(!getAudio().isEmpty()) {
				sample = getAudio().poll();
			}
			else {
				sample = 0;
			}

			if(buffer.hasRemaining())
				buffer.put((byte)((sample >> 8) & 0xFF));

			if(buffer.hasRemaining())
				buffer.put((byte)(sample & 0xFF));
		}

		return true;
	}

	@Override
	protected int getVoiceFieldLength() {
		return 320;
	}

	@Override
	protected boolean parseVoiceField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getVoiceFieldLength())
			return false;

		for(int i = 0; i < getVoiceFieldLength() && buffer.hasRemaining(); i += 2) {
			final short sample = (short)(((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF));

			getAudio().add(sample);
		}

		return true;
	}

	@Override
	public NoraVRCodecType getCodecType() {
		return NoraVRCodecType.PCM;
	}

}
