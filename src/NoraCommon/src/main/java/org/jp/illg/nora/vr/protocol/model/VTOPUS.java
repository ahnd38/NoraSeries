package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.nora.vr.model.NoraVRCodecType;

import lombok.NonNull;

public class VTOPUS extends VoiceTransferBase<Byte> {

	private final NoraVRCodecType codecType;

	public VTOPUS() {
		this(NoraVRCodecType.Opus);
	}

	public VTOPUS(NoraVRCodecType codecType) {
		this(codecType, null);
	}

	public VTOPUS(NoraVRCodecType codecType, Header header) {
		super(NoraVRCommandType.VTOPUS, header);

		if(codecType != null && !codecType.getTypeName().startsWith("Opus"))
			throw new IllegalArgumentException();

		this.codecType = codecType;
	}

	@Override
	public VTOPUS clone() {
		final VTOPUS copy = (VTOPUS)super.clone();

		return copy;
	}

	@Override
	protected boolean assembleVoiceField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getVoiceFieldLength())
			return false;

		while(buffer.hasRemaining() && !getAudio().isEmpty()) {
			buffer.put(getAudio().poll());
		}

		return true;
	}

	@Override
	protected int getVoiceFieldLength() {
		return getAudio().size();
	}

	@Override
	protected boolean parseVoiceField(@NonNull ByteBuffer buffer) {
		getAudio().clear();

		while(buffer.hasRemaining()) {
			getAudio().add(buffer.get());
		}

		return true;
	}

	@Override
	public NoraVRCodecType getCodecType() {
		return codecType;
	}
}
