package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class RepeaterInfoGet extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	public RepeaterInfoGet() {
		super(NoraVRCommandType.RINFOGET);

		clientCode = 0x0;
	}

	@Override
	public RepeaterInfoGet clone() {
		RepeaterInfoGet copy = (RepeaterInfoGet)super.clone();

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

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 4;
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 4) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

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

		return sb.toString();
	}
}
