package org.jp.illg.util.ambe.dv3k.packet;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.ambe.dv3k.DV3KDefines;
import org.jp.illg.util.ambe.dv3k.DV3KPacket;
import org.jp.illg.util.ambe.dv3k.packet.channel.DV3KChannelPacketType;
import org.jp.illg.util.ambe.dv3k.packet.control.DV3KControlPacketType;
import org.jp.illg.util.ambe.dv3k.packet.speech.DV3KSpeechPacketType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class DV3KPacketBase implements DV3KPacket, Cloneable {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DV3KPacketType packetType;

	@Getter
	@Setter
	private InetSocketAddress remoteAddress;


	protected DV3KPacketBase(@NonNull DV3KPacketType packetType) {
		super();

		setPacketType(packetType);
	}

	@Override
	public DV3KControlPacketType getControlPacketType() {
		return null;
	}

	@Override
	public DV3KSpeechPacketType getSpeechPacketType() {
		return null;
	}

	@Override
	public DV3KChannelPacketType getChannelPacketType() {
		return null;
	}

	@Override
	public DV3KPacketBase clone() {
		DV3KPacketBase copy = null;

		try {
			copy = (DV3KPacketBase)super.clone();

			copy.packetType = this.packetType;
			copy.remoteAddress = this.remoteAddress;

		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public ByteBuffer assemblePacket() {
		final int fieldLength = getRequestFieldDataLength();

		final ByteBuffer buffer = ByteBuffer.allocate(4 + fieldLength);

		buffer.put(DV3KDefines.DV3K_START_BYTE);
		buffer.put((byte)((fieldLength >> 8) & 0xFF));
		buffer.put((byte)(fieldLength & 0xFF));
		buffer.put((byte)getPacketType().getValue());
		if(!assembleFieldData(buffer)) {return null;}

		BufferState.toREAD(buffer, BufferState.WRITE);

		return buffer;
	}

	@Override
	public DV3KPacket parsePacket(@NonNull ByteBuffer buffer) {

		if(buffer.remaining() < 5) {return null;}

		final int savedPos = buffer.position();

		final byte[] header = new byte[4];
		buffer.get(header);

		if(header[0] != DV3KDefines.DV3K_START_BYTE) {
			buffer.position(savedPos);

			return null;
		}

		final int fieldLength =
			((header[1] << 8) & 0xFF00) | (header[2] & 0x00FF);

		DV3KPacketType packetType = DV3KPacketType.getTypeByValue(header[3]);
		if(packetType != getPacketType()) {
			buffer.position(savedPos);

			return null;
		}

		if(buffer.remaining() < fieldLength) {
			buffer.position(savedPos);

			return null;
		}

		if(parseFieldData(buffer, fieldLength)) {
			buffer.compact();
			buffer.limit(buffer.position());
			buffer.position(0);

			return this.clone();
		}
		else {
			buffer.position(savedPos);

			return null;
		}
	}

	protected abstract boolean assembleFieldData(ByteBuffer buffer);
	protected abstract boolean parseFieldData(final ByteBuffer buffer, final int fieldLength);
	protected abstract int getRequestFieldDataLength();
}
