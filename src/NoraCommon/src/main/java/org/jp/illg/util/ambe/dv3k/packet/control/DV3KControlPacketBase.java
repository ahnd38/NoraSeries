package org.jp.illg.util.ambe.dv3k.packet.control;

import java.nio.ByteBuffer;

import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketBase;
import org.jp.illg.util.ambe.dv3k.packet.DV3KPacketType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class DV3KControlPacketBase extends DV3KPacketBase{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DV3KControlPacketType controlPacketType;

	public DV3KControlPacketBase(@NonNull DV3KControlPacketType controlPacketType) {
		super(DV3KPacketType.ControlPacket);

		setControlPacketType(controlPacketType);
	}

	@Override
	public DV3KControlPacketBase clone() {
		DV3KControlPacketBase copy = null;

		copy = (DV3KControlPacketBase)super.clone();

		copy.controlPacketType = this.controlPacketType;

		return copy;
	}

	@Override
	protected boolean assembleFieldData(@NonNull ByteBuffer buffer) {

		if(buffer.remaining() < 1) {return false;}

		buffer.put((byte)getControlPacketType().getValue());

		if(getRequestControlFieldDataLength() >= 1)
			return assembleControlFieldData(buffer);
		else
			return true;
	}

	@Override
	protected int getRequestFieldDataLength() {
		return 	getControlPacketType().getDataLengthRequest() != -1 ?
			getControlPacketType().getDataLengthRequest() + 1 : getRequestControlFieldDataLength() + 1;
	}

	@Override
	protected boolean parseFieldData(@NonNull ByteBuffer buffer, int fieldLength) {
		if(
			buffer.remaining() < fieldLength ||
			fieldLength < 1
		) {return false;}

		DV3KControlPacketType controlPacketType =
			DV3KControlPacketType.getTypeByValue(buffer.get());
		if(
			controlPacketType == null ||
			controlPacketType != getControlPacketType() ||
			controlPacketType.getDataLengthResponse() > buffer.remaining()
		) {return false;}

		if(fieldLength > 1)
			return parseControlFieldData(buffer, fieldLength - 1);
		else
			return true;
	}

	protected abstract int getRequestControlFieldDataLength();
	protected abstract boolean assembleControlFieldData(ByteBuffer buffer);
	protected abstract boolean parseControlFieldData(ByteBuffer buffer, int fieldLength);
}
