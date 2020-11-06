package org.jp.illg.dstar.model;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.ToStringWithIndent;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class DVPacket implements Cloneable, ToStringWithIndent{

	/**
	 * ID
	 */
	@Getter
	private final byte[] id = new byte[4];

	/**
	 * Flags
	 */
	@Getter
	private byte[] flags = new byte[2];

	/**
	 * RF Header
	 */
	@Getter
	@Setter
	private Header rfHeader;

	/**
	 * Voice Data
	 */
	@Getter
	@Setter
	private VoiceData voiceData;

	/**
	 * BackBone
	 */
	@Getter
	@Setter
	private BackBoneHeader backBone;


	protected DVPacket() {
		super();

		this.rfHeader = null;
		this.voiceData = null;
		this.backBone = null;

		clear();
	}

	protected DVPacket(PacketType... packetTypes) {
		this();

		setPacketType(packetTypes);
	}

	protected DVPacket(List<PacketType> packetTypes) {
		this();

		setPacketType(packetTypes);
	}

	public DVPacket(@NonNull DVPacket packet) {
		this(packet.getPacketType());

		clone(packet, this);
	}

	public DVPacket(
		@NonNull final BackBoneHeader backbone,
		@NonNull final VoiceData voice
	) {
		this(PacketType.Voice);

		setBackBone(backbone);
		setVoiceData(voice);
	}

	public DVPacket(
		@NonNull final BackBoneHeader backbone,
		@NonNull final Header header
	) {
		this(PacketType.Header);

		setBackBone(backbone);
		setRfHeader(header);
	}

	public DVPacket(
		@NonNull final BackBoneHeader backbone,
		@NonNull final Header header,
		@NonNull final VoiceData voice
	) {
		this(PacketType.Header, PacketType.Voice);

		setBackBone(backbone);
		setRfHeader(header);
		setVoiceData(voice);
	}

	public DVPacket(
		@NonNull final BackBoneHeader backbone
	) {
		this();

		setBackBone(backbone);
	}

	public void setPacketType(final PacketType... packetTypes) {
		byte value = 0;
		if(packetTypes != null) {
			for(final PacketType packetType : packetTypes) {
				value |= packetType.getValue();
			}
		}

		flags[0] = (byte)((flags[0] & ~PacketType.getMask()) | value);
	}

	public void setPacketType(final List<PacketType> packetTypes) {
		byte value = 0;
		if(packetTypes != null) {
			for(final PacketType packetType : packetTypes) {
				value |= packetType.getValue();
			}
		}

		flags[0] = (byte)((flags[0] & ~PacketType.getMask()) | value);
	}

	public List<PacketType> getPacketType() {
		return PacketType.getPacketType(flags[0]);
	}

	public boolean hasPacketType(final PacketType packetType) {
		return PacketType.hasPacketType(packetType, flags[0]);
	}

	public boolean hasPacketType(final int value) {
		final List<PacketType> packetTypes = getPacketType();
		final List<PacketType> valuePacketTypes = PacketType.getPacketType(value);
		for(final PacketType t : packetTypes) {
			boolean found = false;
			for(final PacketType v : valuePacketTypes) {
				if(t == v) {
					found = true;
					break;
				}
			}
			if(!found) {return false;}
		}

		return true;
	}

	public void setId(byte[] id) {
		if(id == null || id.length <= 0) {return;}

		ArrayUtil.copyOf(this.id, id);
	}

	public void setFlags(byte[] flags) {
		if(flags == null || flags.length <= 0) {return;}

		ArrayUtil.copyOf(this.flags, flags);
	}

	public boolean isEnableNATTraversalFlag() {
		return (flags[0] & 0x1) != 0x0;
	}

	@Override
	public DVPacket clone() {
		DVPacket copy = null;

		try {
			copy = (DVPacket)super.clone();

			clone(copy, this);
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}

		return copy;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indent) {
		if(indent < 0) {indent = 0;}

		final StringBuilder sb = new StringBuilder();

		FormatUtil.addIndent(sb, indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]");

		sb.append('\n');
		FormatUtil.addIndent(sb, indent + 4);

		sb.append("PacketTypes=");
		final List<PacketType> packetTypes =
			PacketType.getPacketType(flags[0]);
		for(final Iterator<PacketType> it = packetTypes.iterator(); it.hasNext();) {
			sb.append(it.next().getTypeName());
			if(it.hasNext()) {sb.append(',');}
		}
		sb.append('/');

		sb.append("NATTraversal=");
		sb.append(isEnableNATTraversalFlag());

		sb.append("\n");
		sb.append(getBackBone().toString(indent + 4));

		if(hasPacketType(PacketType.Header)) {
			sb.append("\n");
			sb.append(getRfHeader().toString(indent + 4));
		}

		if(hasPacketType(PacketType.Voice)) {
			sb.append("\n");
			sb.append(getVoiceData().toString(indent + 4));
		}

		return sb.toString();
	}

	public void clear() {
		Arrays.fill(this.getId(), (byte)0x00);
		Arrays.fill(this.getFlags(), (byte)0x00);

		final Header header = getRfHeader();
		if(header != null) {header.clear();}

		final VoiceData voiceData = getVoiceData();
		if(voiceData != null) {voiceData.clear();}

		final BackBoneHeader backboneHeader = getBackBone();
		if(backboneHeader != null) {backboneHeader.clear();}
	}

	public boolean isEndVoicePacket() {
		final BackBoneHeader backboneHeader = getBackBone();

		return hasPacketType(PacketType.Voice) &&
			backboneHeader != null && backboneHeader.isEndSequence();
	}

	private static void clone(final DVPacket dst, final DVPacket src) {

		final byte[] id = src.getId();
		if(id != null) {dst.setId(id);}

		dst.setPacketType(src.getPacketType());

		final byte[] flags = src.getFlags();
		if(flags != null)
			dst.setFlags(Arrays.copyOf(flags, flags.length));

		final Header header = src.getRfHeader();
		if(header != null)
			dst.setRfHeader(header.clone());

		final VoiceData voiceData = src.getVoiceData();
		if(voiceData != null)
			dst.setVoiceData(voiceData.clone());

		final BackBoneHeader backboneHeader = src.getBackBone();
		if(backboneHeader != null)
			dst.setBackBone(backboneHeader.clone());
	}
}
