package org.jp.illg.nora.vr.protocol.model;

import java.nio.ByteBuffer;

import lombok.Getter;
import lombok.Setter;

public class UserListGet extends NoraVRPacketBase {

	public static class UserListFlag {

		private static final int localUser = 0x80;
		private static final int remoteUser = 0x40;

		@Getter
		@Setter
		private int value;

		public UserListFlag() {
			super();

			setValue(0x0);
		}

		public boolean isLocalUser() {
			return (getValue() & localUser) != 0x0;
		}

		public void setLocalUser(final boolean enable) {
			if(enable)
				setValue(getValue() | localUser);
			else
				setValue(getValue() & ~localUser);
		}

		public boolean isRemoteUser() {
			return (getValue() | remoteUser) != 0x0;
		}

		public void setRemoteUser(final boolean enable) {
			if(enable)
				setValue(getValue() | remoteUser);
			else
				setValue(getValue() & ~remoteUser);
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indentLevel) {
			if(indentLevel < 0) {indentLevel = 0;}

			StringBuilder sb = new StringBuilder();

			indentLevel += 4;

			String indent = "";
			for(int i = 0; i < indentLevel; i++) {indent += " ";}

			sb.append("\n");

			sb.append(indent);
			sb.append("LocalUser:");
			sb.append(isLocalUser());
			sb.append('/');
			sb.append("RemoteUser:");
			sb.append(isRemoteUser());

			return sb.toString();
		}
	}

	@Getter
	@Setter
	private long clientCode;

	@Getter
	@Setter
	private UserListFlag flag;

	@Getter
	@Setter
	private long requestNo;

	public UserListGet() {
		super(NoraVRCommandType.USLSTGET);

		setClientCode(0x0);
		setFlag(new UserListFlag());
		setRequestNo(0x0);
	}

	@Override
	protected boolean assembleField(ByteBuffer buffer) {
		if(buffer.remaining() < getAssembleFieldLength())
			return false;

		// Client Code
		buffer.put((byte)((getClientCode() >> 24) & 0xFF));
		buffer.put((byte)((getClientCode() >> 16) & 0xFF));
		buffer.put((byte)((getClientCode() >> 8) & 0xFF));
		buffer.put((byte)(getClientCode() & 0xFF));

		// Flag
		buffer.put((byte)getFlag().getValue());

		// Reserved
		for(int i = 0; i < 3; i++) {buffer.put((byte)0x0);}

		// Request No
		buffer.put((byte)((getRequestNo() >> 24) & 0xFF));
		buffer.put((byte)((getRequestNo() >> 16) & 0xFF));
		buffer.put((byte)((getRequestNo() >> 8) & 0xFF));
		buffer.put((byte)(getRequestNo() & 0xFF));

		return true;
	}

	@Override
	protected int getAssembleFieldLength() {
		return
			4 +	// Client Code
			1 +	// Flag
			3 +	// Reserved
			4;	// Request No
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

		// Flag
		getFlag().setValue(buffer.get());

		// Reserved
		for(int i = 0; i < 3; i++) {buffer.get();}

		// Request No
		long requestNo = (buffer.get() & 0xFF);
		requestNo = (requestNo << 8) | (buffer.get() & 0xFF);
		requestNo = (requestNo << 8) | (buffer.get() & 0xFF);
		requestNo = (requestNo << 8) | (buffer.get() & 0xFF);
		setRequestNo(requestNo);

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
		sb.append("Flags:(");
		sb.append(getFlag());
		sb.append(")");
		sb.append("Timestamp:");
		sb.append(String.format("0x%04X", getRequestNo()));

		return sb.toString();
	}
}
