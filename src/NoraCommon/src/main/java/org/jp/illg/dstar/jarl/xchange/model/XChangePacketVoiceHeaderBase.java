package org.jp.illg.dstar.jarl.xchange.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.model.DVPacket;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class XChangePacketVoiceHeaderBase extends XChangePacketBase {

	@SuppressWarnings("unused")
	private static final String logTag =
		XChangePacketVoiceHeaderBase.class.getSimpleName() + " : ";

	private static final int backboneLength = 7;

	@Getter
	@Setter
	private DVPacket dvPacket;


	public XChangePacketVoiceHeaderBase(
		@NonNull final DVPacket packet,
		XChangePacketDirection direction,
		XChangeRouteFlagData routeFlags
	) {
		super();

		dvPacket = packet;

		setDirection(direction);
		setRouteFlags(routeFlags);
	}

	@Override
	public final XChangePacketType getType() {
		return XChangePacketType.Voice;
	}

	@Override
	protected int getDataFieldTransmitDataLength() {
		return backboneLength + getDataSubFieldTransmitDataLength();
	}

	@Override
	protected int getDataFieldReceiveDataLength() {
		final int subFieldLength = getDataSubFieldReceiveDataLength();

		return subFieldLength < 0 ? -1 : (backboneLength + subFieldLength);
	}

	@Override
	protected final boolean parseDataField(ByteBuffer buffer) {
		if(buffer.remaining() < backboneLength) {return false;}

		getDvPacket().getBackBone().setId(buffer.get());
		getDvPacket().getBackBone().setDestinationRepeaterID(buffer.get());
		getDvPacket().getBackBone().setSendRepeaterID(buffer.get());
		getDvPacket().getBackBone().setSendTerminalID(buffer.get());
		getDvPacket().getBackBone().setFrameIDNumber(
			((buffer.get() & 0xFF) << 8) | (buffer.get() & 0xFF)
		);
		getDvPacket().getBackBone().setManagementInformation(buffer.get());

		return parseDataSubField(buffer);
	}

	@Override
	protected final boolean assembleDataField(ByteBuffer buffer) {
		if(buffer.remaining() < backboneLength) {return false;}

		if(isLastVoice()) {getDvPacket().getBackBone().setEndSequence();}

		buffer.put(getDvPacket().getBackBone().getId());
		buffer.put(getDvPacket().getBackBone().getDestinationRepeaterID());
		buffer.put(getDvPacket().getBackBone().getSendRepeaterID());
		buffer.put(getDvPacket().getBackBone().getSendTerminalID());
		buffer.put(getDvPacket().getBackBone().getFrameID()[0]);
		buffer.put(getDvPacket().getBackBone().getFrameID()[1]);
		buffer.put(getDvPacket().getBackBone().getManagementInformation());

		return assembleDataSubField(buffer);
	}

	@Override
	public XChangePacketVoiceHeaderBase clone() {
		XChangePacketVoiceHeaderBase copy = (XChangePacketVoiceHeaderBase)super.clone();

		if(dvPacket != null) {copy.dvPacket = dvPacket.clone();}

		return copy;
	}

	public String toString(final int indentLevel) {
		final int lvl = indentLevel < 0 ? 0 : indentLevel;
		final StringBuilder sb = new StringBuilder(super.toString(lvl));

		sb.append("\n");

		if(getDvPacket() != null)
			sb.append(getDvPacket().toString(lvl + 4));
		else {
			for(int c = 0; c < lvl + 4; c++) {sb.append(' ');}

			sb.append("[DvPacket]:null");
		}

		return sb.toString();
	}

	protected abstract int getDataSubFieldTransmitDataLength();
	protected abstract boolean parseDataSubField(final ByteBuffer buffer);

	protected abstract int getDataSubFieldReceiveDataLength();
	protected abstract boolean assembleDataSubField(final ByteBuffer buffer);

	protected abstract boolean isLastVoice();
}
