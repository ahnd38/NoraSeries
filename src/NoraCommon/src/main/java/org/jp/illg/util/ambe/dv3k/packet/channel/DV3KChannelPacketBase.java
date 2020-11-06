package org.jp.illg.util.ambe.dv3k.packet.channel;

import java.nio.ByteBuffer;

import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketBase;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;

import lombok.Getter;
import lombok.Setter;

public abstract class DV3KChannelPacketBase extends DV3KPacketBase {


	@Getter
	@Setter
	private DV3KChannelPacketType channelPacketType;

	public DV3KChannelPacketBase(DV3KChannelPacketType channelPacketType) {
		super(DV3KPacketType.ChannelPacket);

		setChannelPacketType(channelPacketType);
	}

	@Override
	public DV3KChannelPacketBase clone() {
		DV3KChannelPacketBase copy = null;

		copy = (DV3KChannelPacketBase)super.clone();

		copy.channelPacketType = this.channelPacketType;

		return copy;
	}

	@Override
	protected boolean assembleFieldData(ByteBuffer buffer) {
		if(buffer.remaining() < 1) {return false;}

		buffer.put((byte)getChannelPacketType().getValue());

		if(getRequestChannelFieldDataLength() >= 1)
			return assembleChannelFieldData(buffer);
		else
			return true;
	}

	@Override
	protected boolean parseFieldData(ByteBuffer buffer, int fieldLength) {
		if(
			buffer.remaining() < fieldLength ||
			fieldLength < 1
		) {return false;}

		DV3KChannelPacketType channelPacketType =
			DV3KChannelPacketType.getTypeByValue(buffer.get());
		if(
			channelPacketType == null ||
			channelPacketType != getChannelPacketType() ||
			channelPacketType.getDataLengthResponse() > buffer.remaining()
		) {return false;}

		if(fieldLength > 1)
			return parseChannelFieldData(buffer, fieldLength - 1);
		else
			return true;
	}

	@Override
	protected int getRequestFieldDataLength() {
		return getChannelPacketType().getDataLengthRequest() != -1 ?
			getChannelPacketType().getDataLengthRequest() : getRequestChannelFieldDataLength() + 1;
	}

	protected abstract int getRequestChannelFieldDataLength();
	protected abstract boolean assembleChannelFieldData(ByteBuffer buffer);
	protected abstract boolean parseChannelFieldData(ByteBuffer buffer, int fieldLength);

}
