package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.protocol.dcs.DCSPacketTool;

import com.annimon.stream.Optional;

public class DCSHeaderVoice extends DCSPacketImpl implements Cloneable{

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(
			DCSPacketType.HEADERVOICE,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			packet
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);
	}

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, voice)
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			null,
			null,
			header,
			backbone
		);
	}

	public DCSHeaderVoice(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			loopBlockID,
			connectionDirection,
			null,
			null,
			voice,
			backbone
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DCSHeaderVoice(
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			header,
			voice,
			backbone
		);
	}

	public DCSHeaderVoice(
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			header,
			backbone
		);
	}

	public DCSHeaderVoice(
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			voice,
			backbone
		);
	}

	public static Optional<DCSPacket> validPacket(ByteBuffer buffer) {
		return DCSPacketTool.isValidHeaderVoicePacket(buffer);
	}

	public static Optional<byte[]> assemblePacket(DCSPacket packet) {
		return DCSPacketTool.assembleHeaderVoicePacket(packet);
	}
}
