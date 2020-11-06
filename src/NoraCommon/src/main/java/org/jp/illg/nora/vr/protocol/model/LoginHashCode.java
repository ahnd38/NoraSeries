package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.util.FormatUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class LoginHashCode extends NoraVRPacketBase {

	private final int hashCodeLength = 32;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private byte[] hashCode;

	public LoginHashCode() {
		super(NoraVRCommandType.LOGIN_HS);

		setHashCode(new byte[hashCodeLength]);
	}

	@Override
	public LoginHashCode clone() {
		LoginHashCode copy = null;

		copy = (LoginHashCode)super.clone();

		copy.hashCode = Arrays.copyOf(this.hashCode, hashCodeLength);

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < hashCodeLength)
			return false;

		for(int i = 0; i < getHashCode().length && buffer.hasRemaining(); i++) {
			buffer.put(getHashCode()[i]);
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return hashCodeLength;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < hashCodeLength)
			return false;

		for(int i = 0; i < getHashCode().length && buffer.hasRemaining(); i++) {
			getHashCode()[i] = buffer.get();
		}

		return true;
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
		sb.append("HashCode:");
		sb.append(FormatUtil.bytesToHex(getHashCode()));

		return sb.toString();
	}
}
