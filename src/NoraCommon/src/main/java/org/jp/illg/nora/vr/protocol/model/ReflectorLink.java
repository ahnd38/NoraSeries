package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.DSTARUtils;

import lombok.Getter;
import lombok.Setter;

public class ReflectorLink extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private String linkedReflectorCallsign;


	public ReflectorLink() {
		super(NoraVRCommandType.RLINK);

		setClientCode(0x0);
		setLinkedReflectorCallsign(DSTARDefines.EmptyLongCallsign);
	}

	@Override
	public ReflectorLink clone() {
		ReflectorLink copy = null;

		copy = (ReflectorLink)super.clone();

		copy.clientCode = this.clientCode;

		return copy;
	}

	@Override
	protected boolean assembleField(ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		//Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		// Linked Reflector Callsign
		final String reflectorCallsign =
			DSTARUtils.formatFullLengthCallsign(getLinkedReflectorCallsign());
		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			buffer.put((byte)reflectorCallsign.charAt(i));
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 12;
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 12) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		// Linked Reflector Clalsign
		final StringBuffer reflectorCallsignBuffer =
			new StringBuffer(DSTARDefines.CallsignFullLength);

		for(int i = 0; i < DSTARDefines.CallsignFullLength; i++) {
			reflectorCallsignBuffer.append((char)buffer.get());
		}
		setLinkedReflectorCallsign(reflectorCallsignBuffer.toString());

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
		sb.append('/');
		sb.append("LinkedReflectorCallsign:");
		sb.append(getLinkedReflectorCallsign());

		return sb.toString();
	}

}
