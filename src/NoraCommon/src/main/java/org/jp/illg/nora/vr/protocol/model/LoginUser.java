package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class LoginUser extends NoraVRPacketBase {

	@Getter
	@Setter
	private String loginUserName;

	public LoginUser() {
		super(NoraVRCommandType.LOGINUSR);

		setLoginUserName(DSTARDefines.EmptyLongCallsign);
	}

	@Override
	public LoginUser clone() {
		LoginUser copy = null;

		copy = (LoginUser)super.clone();

		copy.loginUserName = this.loginUserName;

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		final String userName =
			DSTARUtils.formatFullLengthCallsign(getLoginUserName());

		for(int i = 0; i < userName.length() && i < DSTARDefines.CallsignFullLength; i++) {
			buffer.put((byte)userName.charAt(i));
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return DSTARDefines.CallsignFullLength;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < DSTARDefines.CallsignFullLength)
			return false;

		final StringBuffer userNameBuffer =
			new StringBuffer(DSTARDefines.CallsignFullLength);

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			userNameBuffer.append((char)buffer.get());
		}

		setLoginUserName(
			DSTARUtils.formatFullLengthCallsign(userNameBuffer.toString())
		);

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
		sb.append("LoginUserName:");
		sb.append(getLoginUserName());

		return sb.toString();
	}
}
