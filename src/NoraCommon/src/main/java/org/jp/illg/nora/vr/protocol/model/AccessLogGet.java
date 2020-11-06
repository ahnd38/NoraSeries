package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class AccessLogGet extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private long requestID;


	public AccessLogGet() {
		super(NoraVRCommandType.ACLOGGET);

		setClientCode(0x0);
		setRequestID(0x0);
	}

	@Override
	public AccessLogGet clone() {
		AccessLogGet copy = null;

		copy = (AccessLogGet)super.clone();

		copy.clientCode = this.clientCode;
		copy.requestID = this.requestID;

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

		//Reserved
		for(int i = 0; i < 4; i++) {buffer.put((byte)0x00);}

		//Request ID
		buffer.put((byte)((getRequestID() >> 24) & 0xFF));
		buffer.put((byte)((getRequestID() >> 16) & 0xFF));
		buffer.put((byte)((getRequestID() >> 8) & 0xFF));
		buffer.put((byte)(getRequestID() & 0xFF));

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

		//Reserved
		for(int i = 0; i < 4; i++) {buffer.get();}

		//Request ID
		long requestID = (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		requestID = (requestID << 8) | (buffer.get() & 0xFF);
		setRequestID(requestID);

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
		sb.append(String.format("0x%04X", getClientCode()));

		sb.append('/');

		sb.append("RequestID:");
		sb.append(String.format("0x%04X", getRequestID()));

		return sb.toString();
	}
}
