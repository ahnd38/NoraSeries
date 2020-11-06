package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class Nak extends NoraVRPacketBase {

	@Getter
	@Setter
	private String reason;

	public Nak() {
		super(NoraVRCommandType.NAK);

		setReason("");
	}

	@Override
	public Nak clone() {
		Nak copy = null;

		copy = (Nak)super.clone();

		copy.reason = this.reason;

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		for(int i = 0; i < getReason().length(); i++)
			buffer.put((byte)getReason().charAt(i));

		if(!buffer.hasRemaining()) {return false;}

		buffer.put((byte)0x00);

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return limitReason().length() + 1;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 1) {return false;}

		final StringBuilder sb = new StringBuilder(511);

		for(
			int i = 0;
			buffer.hasRemaining() && i < 511;
			i++
		) {
			final char c = (char)buffer.get();

			if(c == 0x00) {break;}

			sb.append(c);
		}

		setReason(sb.toString());

		return true;
	}

	private String limitReason() {
		if(getReason() == null)
			setReason("");

		if(getReason().length() > 511)
			setReason(getReason().substring(0, 511));

		return getReason();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb = new StringBuilder();

		sb.append(super.toString(indentLevel));

		indentLevel += 4;

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		sb.append("\n");

		sb.append(indent);
		sb.append("Reason:");
		sb.append(getReason());

		return sb.toString();
	}
}
