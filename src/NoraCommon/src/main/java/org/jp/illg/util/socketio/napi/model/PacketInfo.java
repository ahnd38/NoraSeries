package org.jp.illg.util.socketio.napi.model;

import lombok.Getter;
import lombok.Setter;

public class PacketInfo {

	@Getter
	@Setter
	private int packetBytes;

	@Getter
	private final boolean packetCombine;


	public PacketInfo(final int packetBytes, final boolean packetCombine) {
		super();

		this.packetBytes = packetBytes;
		this.packetCombine = packetCombine;
	}

	public PacketInfo(final int packetBytes) {
		this(packetBytes, false);
	}

}
