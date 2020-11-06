package org.jp.illg.dstar.model.defines;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.NonNull;

public enum PacketType{
	Unknown((byte)0x00),
	Header((byte)0x10),
	Voice((byte)0x20),
	Poll((byte)0x81),
	;

	@Getter
	private static final int mask = 0xF0;

	private final byte val;

	PacketType(final byte val) {
		this.val = val;
	}

	public byte getValue() {
		return this.val;
	}

	public String getTypeName() {
		return this.toString();
	}

	public static List<PacketType> getPacketType(final int value) {
		final List<PacketType> result = new ArrayList<>(values().length);

		for(final PacketType t : values()) {
			if(hasPacketType(t, value)) {result.add(t);}
		}

		return result;
	}

	public static boolean hasPacketType(
		@NonNull final PacketType packetType, final int value
	) {
		return ((packetType.getValue() & getMask()) & (getMask() & value)) != 0x0;
	}

	public static PacketType getTypeByValue(byte value) {
		for(PacketType v : values()) {
			if(v.getValue() == value) {return v;}
		}
		return PacketType.Unknown;
	}
}
