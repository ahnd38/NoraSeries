package org.jp.illg.dstar.reflector.protocol.jarllink.model;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorPacket;

import lombok.Getter;
import lombok.Setter;

public class JARLLinkPacket extends ReflectorPacket {

	@Getter
	@Setter
	private JARLLinkPacketType jARLLinkPacketType;

	@Getter
	@Setter
	private JARLLinkTransmitType jARLLinkTransmitType;


	public JARLLinkPacket(
		final JARLLinkPacketType jarkLinkPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(DSTARProtocol.DCS, loopBlockID, connectionDirection, remoteAddress, localAddress, packet);

		setJARLLinkPacketType(jarkLinkPacketType);
	}

	/*
	 * ------------------------------------------------------
	 */

	public JARLLinkPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			JARLLinkPacketType.DVPacket,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);
	}

	public JARLLinkPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			JARLLinkPacketType.DVPacket,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public JARLLinkPacket(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			JARLLinkPacketType.DVPacket,
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

	public JARLLinkPacket(
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

	public JARLLinkPacket(
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

	public JARLLinkPacket(
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

	public JARLLinkPacket(
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public JARLLinkPacket(
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			backbone
		);
	}

	public JARLLinkPacket(
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			voice,
			backbone
		);
	}


	public String toString() {return toString(0);}

	public String toString(int indent) {
		if(indent < 0) {indent = 0;}
		StringBuilder sb = new StringBuilder();

		for(int c = 0; c < indent; c++) {sb.append(' ');}

		sb.append("JARLLinkPacketType=");
		sb.append(getJARLLinkPacketType());
		sb.append("/");
		sb.append("JARLLinkTransmitType=");
		sb.append(getJARLLinkTransmitType());

		sb.append("\n");

		sb.append(super.toString(indent + 4));

		return sb.toString();
	}
}
