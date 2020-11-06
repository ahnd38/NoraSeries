package org.jp.illg.nora.vr.protocol.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.jp.illg.util.BufferState;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class NoraVRPacketBase implements NoraVRPacket, Cloneable {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private NoraVRCommandType commandType;

	@Getter
	@Setter
	private InetSocketAddress remoteHostAddress;

	@Getter
	@Setter
	private InetSocketAddress localHostAddress;

	private NoraVRPacketBase() {
		super();
	}

	protected NoraVRPacketBase(@NonNull final NoraVRCommandType commandType) {
		this();

		setCommandType(commandType);
		setRemoteHostAddress(null);
	}

	@Override
	public NoraVRPacketBase clone() {
		NoraVRPacketBase copy = null;

		try {
			copy = (NoraVRPacketBase)super.clone();

			copy.commandType = this.commandType;
			copy.remoteHostAddress = this.remoteHostAddress;
			copy.localHostAddress = this.localHostAddress;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public ByteBuffer assemblePacket() {
		int fieldLength = getAssembleFieldLength();
		if(fieldLength < 0)
			new IllegalStateException("fieldLength must > 0.");

		final ByteBuffer buffer = ByteBuffer.allocate(16 + fieldLength);

		fieldLength += 8;

		//Header
		buffer.put("NRVR".getBytes(Charset.forName("US-ASCII")));
		//Reserved
		buffer.put((byte)0x00);
		buffer.put((byte)0x00);
		//Length
		buffer.put((byte)((fieldLength >> 8) & 0xFF));
		buffer.put((byte)(fieldLength & 0xFF));
		//Command type
		buffer.put(getCommandType().getCommandString().getBytes(Charset.forName("US-ASCII")));

		if(!assembleField(buffer)) {return null;}

		BufferState.toREAD(buffer, BufferState.WRITE);

		return buffer;
	}

	@Override
	public NoraVRPacket parsePacket(final ByteBuffer buffer) {
		buffer.mark();

		if(buffer.remaining() < 16) {return null;}

		byte[] header = new byte[16];
		buffer.get(header);

		if(
			header[0] != 'N' || header[1] != 'R' ||
			header[2] != 'V' || header[3] != 'R'
		) {
			buffer.reset();
			return null;
		}

		final int fieldLength =
			((header[6] << 8) & 0xFF00) | (header[7] & 0x00FF);

		if(fieldLength > (buffer.remaining() + 8)) {
			buffer.reset();
			return null;
		}

		final char[] commandTypeChar = new char[8];
		for(int i = 0; i < commandTypeChar.length; i++)
			commandTypeChar[i] = (char)header[8 + i];

		NoraVRCommandType commandType =
			NoraVRCommandType.getTypeByCommandString(String.valueOf(commandTypeChar));
		if(commandType != getCommandType()) {
			buffer.reset();
			return null;
		}

		if(parseField(buffer)) {
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.position(0);

			return this.clone();
		}
		else {
			buffer.reset();
			return null;
		}
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();

		sb.append(indent);
		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("] ");

		sb.append("CommandType:");
		sb.append(getCommandType());
		sb.append("/");
		sb.append("RemoteHostAddress:");
		sb.append(getRemoteHostAddress());
		sb.append("/");
		sb.append("LocalHostAddress:");
		sb.append(getLocalHostAddress());

		return sb.toString();
	}

	protected abstract boolean assembleField(@NonNull final ByteBuffer buffer);
	protected abstract int getAssembleFieldLength();
	protected abstract boolean parseField(@NonNull final ByteBuffer buffer);
}
