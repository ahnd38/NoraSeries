package org.jp.illg.dstar.model;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARPacketType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.SystemUtil;

import lombok.Getter;
import lombok.Setter;

public abstract class DSTARPacketBase implements DSTARPacket{

	@Getter
	private long createTimeNanos;

	@Getter
	@Setter
	private byte[] header;

	@Getter
	@Setter
	private DSTARPacketType packetType;

	@Getter
	@Setter
	private DSTARProtocol protocol;

	private UUID loopBlockID;

	@Getter
	@Setter
	private ConnectionDirectionType connectionDirection;

	@Getter
	@Setter
	private DVPacket dVPacket;

	@Getter
	@Setter
	private DDPacket dDPacket;

	@Getter
	@Setter
	private HeardPacket heardPacket;


	protected DSTARPacketBase() {
		super();

		createTimeNanos = SystemUtil.getNanoTimeCounterValue();

		header = new byte[4];
	}

	private DSTARPacketBase(
		final DSTARPacketType packetType,
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket dvPacket,
		final DDPacket ddPacket,
		final HeardPacket heardPacket
	) {
		this();

		this.packetType = packetType;
		this.protocol = protocol;
		this.loopBlockID = loopBlockID;
		this.connectionDirection = connectionDirection;
		this.dVPacket = dvPacket;
		this.dDPacket = ddPacket;
		this.heardPacket = heardPacket;
	}

	public DSTARPacketBase(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DVPacket dVPacket
	) {
		this(
			DSTARPacketType.DV,
			protocol,
			loopBlockID,
			connectionDirection,
			dVPacket,
			null,
			null
		);
	}

	public DSTARPacketBase(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final DDPacket ddPacket
	) {
		this(
			DSTARPacketType.DD,
			protocol,
			loopBlockID,
			connectionDirection,
			null,
			ddPacket,
			null
		);
	}

	public DSTARPacketBase(
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection,
		final HeardPacket heardPacket
	) {
		this(
			DSTARPacketType.DV,
			protocol,
			loopBlockID,
			connectionDirection,
			null,
			null,
			heardPacket
		);
	}

	public DSTARPacketBase(
		final DSTARPacketType packetType,
		final DSTARProtocol protocol,
		final UUID loopBlockID,
		final ConnectionDirectionType connectionDirection
	) {
		this(
			packetType,
			protocol,
			loopBlockID,
			connectionDirection,
			null,
			null,
			null
		);
	}

	public DSTARPacketBase(
		final DSTARPacketType packetType,
		final DSTARProtocol protocol,
		final UUID loopBlockID
	) {
		this(
			packetType,
			protocol,
			loopBlockID,
			ConnectionDirectionType.Unknown,
			null,
			null,
			null
		);
	}

	@Override
	public long getCreateTimeMillis() {
		return System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(
			SystemUtil.getNanoTimeCounterValue() - createTimeNanos, TimeUnit.NANOSECONDS
		);
	}

	@Override
	public UUID getLoopBlockID() {
		return loopBlockID;
	}

	@Override
	public UUID getLoopblockID() {
		return getLoopBlockID();
	}

	@Override
	public void setLoopBlockID(UUID loopBlockID) {
		this.loopBlockID = loopBlockID;
	}

	@Override
	public void setLoopblockID(UUID loopblockID) {
		setLoopBlockID(loopblockID);
	}

	@Override
	public Header getRFHeader() {
		switch(getPacketType()) {
		case DD:
			final DDPacket ddPacket = getDDPacket();
			return ddPacket != null ? ddPacket.getRfHeader() : null;

		case DV:
			final DVPacket dvPacket = getDVPacket();
			return dvPacket != null ? dvPacket.getRfHeader() : null;

		default:
			return null;
		}
	}

	@Override
	public Header getRfHeader() {
		return getRFHeader();
	}

	@Override
	public BackBoneHeader getBackBoneHeader() {
		switch(getPacketType()) {
		case DD:
			final DDPacket ddPacket = getDDPacket();
			return ddPacket != null ? ddPacket.getBackBone() : null;

		case DV:
			final DVPacket dvPacket = getDVPacket();
			return dvPacket != null ? dvPacket.getBackBone() : null;

		default:
			return null;
		}
	}

	@Override
	public BackBoneHeader getBackBone() {
		return getBackBoneHeader();
	}

	@Override
	public int getFrameID() {
		final BackBoneHeader backbone = getBackBoneHeader();

		return backbone != null ? backbone.getFrameIDNumber() : -1;
	}

	@Override
	public int getSequenceNumber() {
		final BackBoneHeader backbone = getBackBoneHeader();

		return backbone != null ? backbone.getSequenceNumber() : -1;
	}

	@Override
	public boolean isLastFrame() {
		final BackBoneHeader backbone = getBackBoneHeader();

		return backbone != null ? backbone.isEndSequence() : false;
	}

	@Override
	public boolean isEndVoicePacket() {
		return isLastFrame();
	}

	@Override
	public VoiceData getDVData() {
		return getPacketType() == DSTARPacketType.DV ? getDVPacket().getVoiceData() : null;
	}

	@Override
	public VoiceData getVoiceData() {
		return getDVData();
	}

	@Override
	public byte[] getDDData() {
		return getPacketType() == DSTARPacketType.DD ? getDDPacket().getData() : null;
	}

	@Override
	public DSTARPacketBase clone() {
		DSTARPacketBase copy = null;

		try {
			copy = (DSTARPacketBase)super.clone();

			clone(copy, this);

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException();
		}
	}

	@Override
	public String toString(final int indentLevel) {
		final int indent = indentLevel > 0 ? indentLevel : 0;

		final StringBuilder sb = new StringBuilder();
		FormatUtil.addIndent(sb, indent);

		sb.append("CreateTime=");
		sb.append(FormatUtil.dateFormat(getCreateTimeMillis()));

		sb.append('/');

		sb.append("PacketType=");
		sb.append(getPacketType());

		sb.append('/');

		sb.append("Protocol=");
		sb.append(getProtocol());

		sb.append('/');

		sb.append("LoopBlockID=");
		sb.append(getLoopBlockID());

		sb.append('/');

		sb.append("ConnectionDirection=");
		sb.append(getConnectionDirection());

		sb.append('\n');

		FormatUtil.addIndent(sb, indent);
		sb.append("PacketContents=");

		sb.append('\n');

		switch(getPacketType()) {
		case DV:
			final DVPacket dvPacket = getDVPacket();

			if(dvPacket != null)
				sb.append(dvPacket.toString(indent + 4));
			else {
				FormatUtil.addIndent(sb, indent + 4);
				sb.append("NULL");
			}
			break;

		case DD:
			final DDPacket ddPacket = getDDPacket();

			if(ddPacket != null)
				sb.append(ddPacket.toString(indent + 4));
			else {
				FormatUtil.addIndent(sb, indent + 4);
				sb.append("NULL");
			}
			break;

		case UpdateHeard:
			final HeardPacket heardPacket = getHeardPacket();

			if(heardPacket != null)
				sb.append(heardPacket.toString(indent + 4));
			else {
				FormatUtil.addIndent(sb, indentLevel + 4);
				sb.append("NULL");
			}
			break;

		default:
			break;
		}

		return sb.toString();
	}

	private static void clone(final DSTARPacketBase dst, final DSTARPacketBase src) {
		if(src.header != null) {dst.header = Arrays.copyOf(src.header, src.header.length);}
		dst.createTimeNanos = src.createTimeNanos;
		dst.packetType = src.packetType;
		dst.protocol = src.protocol;
		dst.loopBlockID = src.loopBlockID;
		dst.connectionDirection = src.connectionDirection;

		final DVPacket dVPacket = src.dVPacket;
		if(dVPacket != null) {dst.dVPacket = dVPacket.clone();}

		final DDPacket dDPacket = src.dDPacket;
		if(dDPacket != null) {dst.dDPacket = dDPacket.clone();}

		final HeardPacket heardPacket = src.heardPacket;
		if(heardPacket != null) {dst.heardPacket = heardPacket.clone();}
	}

	public abstract String toString();
}
