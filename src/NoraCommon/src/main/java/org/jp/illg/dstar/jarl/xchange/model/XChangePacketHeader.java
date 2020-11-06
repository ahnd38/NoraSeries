package org.jp.illg.dstar.jarl.xchange.model;

import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.NonNull;

public class XChangePacketHeader extends XChangePacketVoiceHeaderBase {

	private static final int fieldLength = 41;

	public XChangePacketHeader() {
		super(
			new DVPacket(
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader),
				new Header()
			),
			null,
			null
		);

		getDvPacket().setPacketType(PacketType.Header);
	}

	public XChangePacketHeader(
		@NonNull final DVPacket packet,
		@NonNull XChangePacketDirection direction,
		@NonNull XChangeRouteFlagData routeFlags
	) {
		super(packet, direction, routeFlags);

		if(packet.hasPacketType(PacketType.Header))
			throw new IllegalArgumentException("Packet does not have Header.");
	}

	@Override
	protected int getDataSubFieldTransmitDataLength() {
		return fieldLength;
	}

	@Override
	protected boolean parseDataSubField(ByteBuffer buffer) {
		if(buffer.remaining() != fieldLength) {return false;}

		copyFromBuffer(getDvPacket().getRfHeader().getFlags(), buffer, 3);
		copyFromBuffer(
			getDvPacket().getRfHeader().getRepeater2Callsign(), buffer,
			DSTARDefines.CallsignFullLength
		);
		copyFromBuffer(
			getDvPacket().getRfHeader().getRepeater1Callsign(), buffer,
			DSTARDefines.CallsignFullLength
		);
		copyFromBuffer(
			getDvPacket().getRfHeader().getYourCallsign(), buffer,
			DSTARDefines.CallsignFullLength
		);
		copyFromBuffer(
			getDvPacket().getRfHeader().getMyCallsign(), buffer,
			DSTARDefines.CallsignFullLength
		);
		copyFromBuffer(
			getDvPacket().getRfHeader().getMyCallsignAdd(), buffer,
			DSTARDefines.CallsignShortLength
		);

		copyFromBuffer(
			getDvPacket().getRfHeader().getCrc(), buffer,
			2
		);

		return true;
	}

	@Override
	protected int getDataSubFieldReceiveDataLength() {
		return fieldLength;
	}

	@Override
	protected boolean assembleDataSubField(ByteBuffer buffer) {
		if(buffer.remaining() != fieldLength) {return false;}

		getDvPacket().getRfHeader().calcCRC();

		copyToBuffer(buffer, getDvPacket().getRfHeader().getFlags(), 3);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getRepeater2Callsign(), DSTARDefines.CallsignFullLength
		);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getRepeater1Callsign(), DSTARDefines.CallsignFullLength
		);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getYourCallsign(), DSTARDefines.CallsignFullLength
		);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getMyCallsign(), DSTARDefines.CallsignFullLength
		);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getMyCallsignAdd(), DSTARDefines.CallsignShortLength
		);
		copyToBuffer(
			buffer, getDvPacket().getRfHeader().getCrc(), 2
		);

		return true;
	}

	@Override
	protected final boolean isLastVoice() {return false;}
}
