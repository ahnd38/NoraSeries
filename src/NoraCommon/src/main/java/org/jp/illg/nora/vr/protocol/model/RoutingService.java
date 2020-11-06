package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class RoutingService extends NoraVRPacketBase {

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private String routingServiceName;

	public RoutingService() {
		super(NoraVRCommandType.RSRV);

		setClientCode(0x0);
		setRoutingServiceName("");
	}

	@Override
	public RoutingService clone() {
		RoutingService copy = null;

		copy = (RoutingService)super.clone();

		copy.clientCode = this.clientCode;
		copy.routingServiceName = this.routingServiceName;

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

		// Routing Service Name
		final String routingService = getRoutingServiceName();
		for(int i = 0; i < 16; i++) {
			if(routingService != null && routingService.length() > i)
				buffer.put((byte)routingService.charAt(i));
			else
				buffer.put((byte)0x0);
		}

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return 4 + 16;
	}

	@Override
	protected boolean parseField(ByteBuffer buffer) {
		if(buffer.remaining() < 20) {return false;}

		//Client Code
		long ccode = (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		ccode = (ccode << 8) | (buffer.get() & 0xFF);
		setClientCode(ccode);

		// Routing Service Name
		final StringBuffer routingService =
			new StringBuffer(16);

		for(int i = 0; i < 16; i++) {
			routingService.append((char)buffer.get());
		}
		setRoutingServiceName(routingService.toString().trim());

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
		sb.append("RoutingServiceName");
		sb.append(getRoutingServiceName());

		return sb.toString();
	}
}
