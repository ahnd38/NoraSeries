package org.jp.illg.dstar.g123.model;

import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

public abstract class VoiceData extends G2PacketBase implements Cloneable
{
	protected VoiceData(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket packet
	) {
		super(
			loopBlockID,
			connectionDirection,
			packet
		);

		getDVPacket().getId()[0] = 'D';
		getDVPacket().getId()[1] = 'S';
		getDVPacket().getId()[2] = 'V';
		getDVPacket().getId()[3] = 'T';
	}

	protected VoiceData(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final BackBoneHeader backbone,
		final org.jp.illg.dstar.model.VoiceData voice
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, voice)
		);
	}
}
