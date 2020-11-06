package org.jp.illg.dstar.jarl.xchange.model;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.defines.PacketType;

import lombok.NonNull;

public class XChangePacketVoice extends XChangePacketVoiceHeaderBase {


	private static final int terminateFieldLength = 15;
	private static final int normalFieldLength = 12;

	public XChangePacketVoice() {
		super(
			new DVPacket(
				new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader),
				new VoiceAMBE()
			),
			null,
			null
		);

		getDvPacket().setPacketType(PacketType.Voice);
	}

	public XChangePacketVoice(
		@NonNull final DVPacket packet,
		@NonNull final XChangePacketDirection direction,
		@NonNull XChangeRouteFlagData routeFlags
	) {
		super(packet, direction, routeFlags);

		if(packet.hasPacketType(PacketType.Voice))
			throw new IllegalArgumentException("Packet does not have type Voice.");
	}

	@Override
	protected int getDataSubFieldTransmitDataLength() {
		return normalFieldLength;
	}

	@Override
	protected boolean parseDataSubField(final ByteBuffer buffer) {
		if(
			buffer.remaining() != normalFieldLength &&
			buffer.remaining() != terminateFieldLength
		) {return false;}

		copyFromBuffer(
			getDvPacket().getVoiceData().getVoiceSegment(), buffer,
			DSTARDefines.VoiceSegmentLength
		);
		copyFromBuffer(
			getDvPacket().getVoiceData().getDataSegment(), buffer,
			DSTARDefines.DataSegmentLength
		);

		if(buffer.remaining() == (terminateFieldLength - normalFieldLength)) {
			for(int c = 0; c < (terminateFieldLength - normalFieldLength); c++) {
				buffer.get();
			}

			getDvPacket().getBackBone().setEndSequence();
		}

		return true;
	}

	@Override
	protected int getDataSubFieldReceiveDataLength() {
		return -1;
	}

	@Override
	protected boolean assembleDataSubField(final ByteBuffer buffer) {
		if(buffer.remaining() != normalFieldLength) {return false;}

		if(!isLastVoice()) {
			copyToBuffer(
				buffer, getDvPacket().getVoiceData().getVoiceSegment(),
				DSTARDefines.VoiceSegmentLength
			);
			copyToBuffer(
				buffer, getDvPacket().getVoiceData().getDataSegment(),
				DSTARDefines.DataSegmentLength
			);
		}
		else {
			copyToBuffer(
				buffer, DSTARDefines.VoiceSegmentLastBytes,
				DSTARDefines.VoiceSegmentLength
			);
			copyToBuffer(
				buffer, DSTARDefines.SlowdataLastBytes,
				DSTARDefines.DataSegmentLength
			);
		}

		return true;
	}

	@Override
	protected final boolean isLastVoice() {
		return getDvPacket().isEndVoicePacket() ||
			Arrays.equals(
				DSTARDefines.VoiceSegmentLastBytes, getDvPacket().getVoiceData().getVoiceSegment()
			);
	}
}
