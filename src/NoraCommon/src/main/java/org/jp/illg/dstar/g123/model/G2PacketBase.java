package org.jp.illg.dstar.g123.model;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.UUID;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.DSTARPacketBase;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.FormatUtil;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class G2PacketBase extends DSTARPacketBase implements G2Packet
{
	/**
	 * リモートアドレス
	 */
	@Getter
	@Setter
	private InetSocketAddress remoteAddress;

	/**
	 * タイムスタンプ
	 */
	@Getter
	@Setter
	private long timestamp;


	/**
	 * コンストラクタ
	 */
	public G2PacketBase(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket dvPacket
	) {
		super(
			DSTARProtocol.G123,
			loopBlockID,
			connectionDirection,
			dvPacket
		);
	}

	public G2PacketBase(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		@NonNull final BackBoneHeader backbone,
		@NonNull final Header header
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, header)
		);
	}

	public G2PacketBase(
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		@NonNull final BackBoneHeader backbone,
		@NonNull final VoiceData voice
	) {
		this(
			loopBlockID,
			connectionDirection,
			new DVPacket(backbone, voice)
		);
	}

	public G2PacketBase(
		final DSTARPacketType packetType,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection
	) {
		super(
			packetType,
			DSTARProtocol.G123,
			loopBlockID,
			connectionDirection
		);
	}

	@Override
	public G2PacketBase clone() {
		G2PacketBase copy = null;
		try {
			copy = (G2PacketBase)super.clone();

			clone(copy, this);

		}catch(ClassCastException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public void clear() {

	}

	public abstract byte[] assembleCommandData();

	public abstract G2Packet parseCommandData(ByteBuffer buffer);

	@Override
	public Header getRfHeader() {
		return getRFHeader();
	}

	@Override
	public BackBoneHeader getBackBone() {
		return getBackBoneHeader();
	}

	@Override
	public VoiceData getVoiceData() {
		return getDVData();
	}

	@Override
	public char[] getRepeater2Callsign() {
		final Header header = getRFHeader();
		return header != null ? header.getRepeater2Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getRepeater1Callsign() {
		final Header header = getRFHeader();
		return header != null ? header.getRepeater1Callsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getYourCallsign() {
		final Header header = getRFHeader();
		return header != null ? header.getYourCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsign() {
		final Header header = getRFHeader();
		return header != null ? header.getMyCallsign() : DSTARDefines.EmptyLongCallsignChar;
	}

	@Override
	public char[] getMyCallsignAdd() {
		final Header header = getRFHeader();
		return header != null ? header.getMyCallsignAdd() : DSTARDefines.EmptyLongCallsignChar;
	}

	public void updateTimestamp() {
		timestamp = System.currentTimeMillis();
	}

	public void clearTimestamp() {
		setTimestamp(0);
	}

	@Override
	public String toString(int indentLevel){
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < indentLevel; indentLevel++) {sb.append(' ');}

		sb.append("RemoteAddress=");
		if(getRemoteAddress() != null)
			sb.append(getRemoteAddress().toString());
		else
			sb.append("null");

		sb.append("/Timestamp=");
		sb.append(FormatUtil.dateFormat(getTimestamp()));

		sb.append("\n");
		sb.append(super.toString(indentLevel + 4));

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	private static void clone(final G2PacketBase dst, final G2PacketBase src) {
		/*
		final InetSocketAddress remoteAddress = src.remoteAddress;
		if(remoteAddress != null)
			dst.remoteAddress = SerializationUtils.clone(remoteAddress);
		*/
		dst.remoteAddress = src.remoteAddress;

		dst.timestamp = src.timestamp;
	}
}
