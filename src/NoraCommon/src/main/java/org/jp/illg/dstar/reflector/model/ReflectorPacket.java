package org.jp.illg.dstar.reflector.model;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacketBase;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class ReflectorPacket extends DSTARPacketBase {

	@Setter
	@Getter
	private InetSocketAddress remoteAddress;

	@Setter
	@Getter
	private InetSocketAddress localAddress;


	public ReflectorPacket(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(
			protocol,
			loopBlockID,
			connectionDirection,
			packet
		);

		setRemoteAddress(remoteAddress);
		setLocalAddress(localAddress);
	}

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		@NonNull DVPacket packet
	) {
		this(
			protocol,
			loopBlockID,
			connectionDirection,
			null,
			null,
			packet
		);
	}

	/*
	 * ------------------------------------------------------
	 */

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);
	}

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
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

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			voice,
			backbone
		);
	}

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			header,
			backbone
		);
	}

	public ReflectorPacket(
		final DSTARProtocol protocol,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			protocol,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			voice,
			backbone
		);
	}

	@Override
	public ReflectorPacket clone() {
		final ReflectorPacket cloneInstance = (ReflectorPacket)super.clone();

		cloneInstance.remoteAddress = remoteAddress;
		cloneInstance.localAddress = localAddress;

		return cloneInstance;
	}

	@Override
	public String toString(int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		FormatUtil.addIndent(sb, indent);

		sb.append("RemoteAddress=");
		sb.append(getRemoteAddress());

		sb.append('/');

		sb.append("LocalAddress=");
		sb.append(getLocalAddress());

		sb.append('\n');

		sb.append(super.toString(indentLevel));

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}
}
