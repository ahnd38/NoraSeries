package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class LoginAck extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private NoraVRConfiguration serverConfiguration;

	@Getter
	@Setter
	private byte protocolVersion;

	@Getter
	@Setter
	private String gatewayCallsign;

	@Getter
	@Setter
	private String repeaterCallsign;


	public LoginAck() {
		super(NoraVRCommandType.LOGINACK);

		setClientCode((long)0x0);
		setServerConfiguration(new NoraVRConfiguration());
		setProtocolVersion((byte)0x0);
	}

	@Override
	public LoginAck clone() {
		LoginAck copy = null;

		copy = (LoginAck)super.clone();

		copy.clientCode = this.clientCode;
		copy.serverConfiguration = this.serverConfiguration.clone();
		copy.protocolVersion = this.protocolVersion;

		return copy;
	}

	@Override
	protected boolean assembleField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		//Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		//Server Configuration
		buffer.put((byte)((getServerConfiguration().getValue() >> 8) & 0xFF));
		buffer.put((byte)(getServerConfiguration().getValue() & 0xFF));

		//Protocol Version
		buffer.put(getProtocolVersion());
		//Reserved
		buffer.put((byte)0x00);

		//Gateway Callsign
		final String gatewayCallsign =
			DSTARUtils.formatFullLengthCallsign(getGatewayCallsign());
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			buffer.put((byte)gatewayCallsign.charAt(i));
		}

		//Repeater Callsign
		final String repeaterCallsign =
			DSTARUtils.formatFullLengthCallsign(getRepeaterCallsign());
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			buffer.put((byte)repeaterCallsign.charAt(i));
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 24;
	}

	@Override
	protected boolean parseField(@NonNull ByteBuffer buffer) {
		if(buffer.remaining() < 24) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		//Server Configuration
		int scVal = (buffer.get() & 0xFF);
		scVal = (scVal << 8) | (buffer.get() & 0xFF);
		getServerConfiguration().setValue(scVal);

		//Protocol Version
		setProtocolVersion(buffer.get());

		//Reserved
		buffer.get();

		//Gateway Callsign
		final StringBuilder gatewayCallsign = new StringBuilder();
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			gatewayCallsign.append((char)buffer.get());
		}
		setGatewayCallsign(DSTARUtils.formatFullLengthCallsign(gatewayCallsign.toString()));

		//Repeater Callsign
		final StringBuilder repeaterCallsign = new StringBuilder();
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			repeaterCallsign.append((char)buffer.get());
		}
		setRepeaterCallsign(DSTARUtils.formatFullLengthCallsign(repeaterCallsign.toString()));

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
		sb.append("ClientCode:");
		sb.append(String.format("0x%08X", getClientCode()));
		sb.append("/");
		sb.append("ProtocolVersion:");
		sb.append(getProtocolVersion());
		sb.append("/");
		sb.append("ServerConfiguration:");
		sb.append(getServerConfiguration());

		return sb.toString();
	}
}
