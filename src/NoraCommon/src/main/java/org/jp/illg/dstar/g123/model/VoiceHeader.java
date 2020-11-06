/**
 *
 */
package org.jp.illg.dstar.g123.model;

import java.nio.ByteBuffer;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;

/**
 * @author AHND
 *
 */
public abstract class VoiceHeader
	extends G2PacketBase
	implements Cloneable
{

	protected VoiceHeader(
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

	protected VoiceHeader(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final BackBoneHeader backbone,
		final Header header
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, header)
		);
	}

	@Override
	public abstract G2Packet parseCommandData(ByteBuffer buffer);

	@Override
	public abstract byte[] assembleCommandData();
}
