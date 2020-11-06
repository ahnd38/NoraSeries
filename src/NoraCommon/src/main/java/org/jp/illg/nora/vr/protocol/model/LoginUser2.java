package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class LoginUser2 extends NoraVRPacketBase {

	@Getter
	@Setter
	private String loginUserName;

	@Getter
	@Setter
	private byte protocolVersion;

	@Getter
	@Setter
	private String applicationName;

	@Getter
	@Setter
	private String applicationVersion;

	public LoginUser2() {
		super(NoraVRCommandType.LGINUSR2);

		setProtocolVersion((byte)2);
		setApplicationName("");
		setApplicationVersion("");
	}

	@Override
	public LoginUser2 clone() {
		LoginUser2 copy = null;

		copy = (LoginUser2)super.clone();

		copy.loginUserName = this.loginUserName;
		copy.protocolVersion = this.protocolVersion;
		copy.applicationName = this.applicationName;
		copy.applicationVersion = this.applicationVersion;

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

		buffer.put(getProtocolVersion());

		for(int i = 0; i < 3; i++) {buffer.put((byte)0x0);}

		final String appName = getApplicationName();
		for(int i = 0; i < 32; i++) {
			if(appName != null && appName.length() > i)
				buffer.put((byte)appName.charAt(i));
			else
				buffer.put((byte)0x0);
		}

		final String appVersion = getApplicationVersion();
		for(int i = 0; i < 16; i++) {
			if(appVersion != null && appVersion.length() > i)
				buffer.put((byte)appVersion.charAt(i));
			else
				buffer.put((byte)0x0);
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return
			DSTARDefines.CallsignFullLength +	// Login Callsign
			1 +									// Protocol Version
			3 +									// Reserved
			32 +								// Application Name
			16;									// Application Version
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 60)
			return false;

		final StringBuffer userNameBuffer =
			new StringBuffer(DSTARDefines.CallsignFullLength);

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			userNameBuffer.append((char)buffer.get());
		}

		setLoginUserName(
			DSTARUtils.formatFullLengthCallsign(userNameBuffer.toString())
		);

		setProtocolVersion(buffer.get());

		for(int i = 0; i < 3; i++) {buffer.get();}

		final StringBuilder applicationNameBuffer = new StringBuilder(32);
		for(int i = 0; i < 32; i++) {
			final byte d = buffer.get();
			if(d != 0x0) {applicationNameBuffer.append((char)d);}
		}
		setApplicationName(applicationNameBuffer.toString());

		final StringBuilder applicationVersionBuffer = new StringBuilder(16);
		for(int i = 0; i < 16; i++) {
			final byte d = buffer.get();
			if(d != 0x0) {applicationVersionBuffer.append((char)d);}
		}
		setApplicationVersion(applicationVersionBuffer.toString());

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
		sb.append('/');
		sb.append(getProtocolVersion());

		return sb.toString();
	}
}
