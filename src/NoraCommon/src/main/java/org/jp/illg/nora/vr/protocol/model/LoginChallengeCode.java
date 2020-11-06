package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class LoginChallengeCode extends NoraVRPacketBase {

	@Getter
	@Setter
	private long challengeCode;

	public LoginChallengeCode() {
		super(NoraVRCommandType.LOGIN_CC);

		setChallengeCode(0x0);
	}

	@Override
	public LoginChallengeCode clone() {
		LoginChallengeCode copy = null;

		copy = (LoginChallengeCode)super.clone();

		copy.challengeCode = this.challengeCode;

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		buffer.put((byte)((getChallengeCode() >> 24) & 0xFF));
		buffer.put((byte)((getChallengeCode() >> 16) & 0xFF));
		buffer.put((byte)((getChallengeCode() >> 8) & 0xFF));
		buffer.put((byte)(getChallengeCode() & 0xFF));

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 4;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 4) {return false;}

		long code = (buffer.get() & 0xFF);
		code = (code << 8) | (buffer.get() & 0xFF);
		code = (code << 8) | (buffer.get() & 0xFF);
		code = (code << 8) | (buffer.get() & 0xFF);
		setChallengeCode(code);

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
		sb.append("ChallengeCode:");
		sb.append(String.format("0x%08X", getChallengeCode()));

		return sb.toString();
	}
}
