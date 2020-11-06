package org.jp.illg.dstar.reflector.protocol.dcs.model;

import java.net.InetSocketAddress;
import java.util.UUID;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorPacket;
import org.jp.illg.util.FormatUtil;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class DCSPacketImpl extends ReflectorPacket implements DCSPacket{

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DCSPacketType dCSPacketType;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DCSPoll poll;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DCSConnect connect;

	@Getter
	@Setter
	private int longSequence;

	@Getter
	@Setter
	private String text;


	public DCSPacketImpl(
		final DCSPacketType dcsPacketType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final DVPacket packet
	) {
		super(DSTARProtocol.DCS, loopBlockID, connectionDirection, remoteAddress, localAddress, packet);

		setDCSPacketType(dcsPacketType);

		setLongSequence(0x0);
		setText("");
	}

	public DCSPacketImpl(final DCSPoll poll) {
		this(
			DCSPacketType.POLL,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			null
		);

		setPoll(poll);
	}

	public DCSPacketImpl(final DCSConnect connect) {
		this(
			DCSPacketType.CONNECT,
			null,
			ConnectionDirectionType.Unknown,
			null,
			null,
			null
		);

		setConnect(connect);
	}

	/*
	 * ------------------------------------------------------
	 */

	public DCSPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			DCSPacketType.HEADERVOICE,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header, voice)
		);
	}

	public DCSPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final Header header,
		final BackBoneHeader backbone
	) {
		this(
			DCSPacketType.HEADERVOICE,
			loopBlockID,
			connectionDirection,
			remoteAddress,
			localAddress,
			new DVPacket(backbone, header)
		);
	}

	public DCSPacketImpl(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress,
		final VoiceData voice,
		final BackBoneHeader backbone
	) {
		this(
			DCSPacketType.HEADERVOICE,
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

	public DCSPacketImpl(
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

	public DCSPacketImpl(
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

	public DCSPacketImpl(
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

	public DCSPacketImpl(
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

	public DCSPacketImpl(
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

	public DCSPacketImpl(
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

	@Override
	public DVPacket getDvPacket() {
		return getDVPacket();
	}

	@Override
	public BackBoneHeader getBackBone() {
		return super.getBackBone();
	}

	@Override
	public Header getRfHeader() {
		return super.getRfHeader();
	}

	@Override
	public VoiceData getVoiceData() {
		return super.getVoiceData();
	}

	@Override
	public char[] getRepeater2Callsign() {
		return super.getRFHeader() != null ?
			super.getRfHeader().getRepeater2Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getRepeater1Callsign() {
		return super.getRFHeader() != null ?
			super.getRfHeader().getRepeater1Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getYourCallsign() {
		return super.getRFHeader() != null ?
			super.getRfHeader().getYourCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsign() {
		return super.getRFHeader() != null ?
			super.getRfHeader().getMyCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsignAdd() {
		return super.getRFHeader() != null ?
			super.getRfHeader().getMyCallsignAdd() : DSTARDefines.EmptyShortCallsignChar;
	}

	@Override
	public DCSPacketImpl clone() {
		DCSPacketImpl copy = null;

		copy = (DCSPacketImpl)super.clone();

		copy.dCSPacketType = this.dCSPacketType;

		if(this.poll != null) {copy.poll = this.poll.clone();}

		if(this.connect != null) {copy.connect = this.connect.clone();}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indentLevel);
		sb.append("DCSPacketType=");
		sb.append(getDCSPacketType());

		sb.append('/');

		sb.append("LongSequence=");
		sb.append(getLongSequence());

		sb.append('/');

		sb.append("Text=");
		sb.append(getText());

		sb.append('\n');

		switch(getDCSPacketType()) {
		case CONNECT:
			sb.append(getConnect().toString(indentLevel + 4));
			break;
		case POLL:
			sb.append(getPoll().toString(indentLevel + 4));
			break;
		case HEADERVOICE:
			sb.append(getRfHeader().toString(indentLevel + 4));
			sb.append("\n");
			sb.append(getVoiceData().toString(indentLevel + 4));
			sb.append("\n");
			sb.append(getBackBone().toString(indentLevel + 4));
			break;
		default:
			break;
		}

		return sb.toString();
	}




}
