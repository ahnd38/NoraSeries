package org.jp.illg.dstar.g123.model;

import java.net.InetSocketAddress;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;

import lombok.Getter;
import lombok.Setter;

public class RouteEntry{
	@Getter
	@Setter
	private long createdTimestamp;

	@Getter
	@Setter
	private long activityTimestamp;

	@Getter
	@Setter
	private RouteEntryKey key;

	@Getter
	@Setter
	private DVPacket header;

	@Getter
	@Setter
	private int headerCount;

	@Getter
	@Setter
	private int voiceCount;

	@Getter
	private final CacheTransmitter<G2TransmitPacketEntry> packetTransmitter;

	@Getter
	@Setter
	private boolean headerTransmitted;

	private RouteEntry(){
		super();

		setCreatedTimestamp(System.currentTimeMillis());
		setActivityTimestamp(0);
		setHeaderCount(0);
		setVoiceCount(0);

		packetTransmitter = new CacheTransmitter<>(10, 1);

		setHeaderTransmitted(false);
	}

	public RouteEntry(RouteEntryKey key, DVPacket header) {
		this();

		assert key != null && header != null;
		if(key == null || header == null){throw new IllegalArgumentException();}

		setKey(key);
		setHeader(header);
	}

	public InetSocketAddress getRemoteAddress() {
		return getKey().getRemoteAddress();
	}

	public int getFrameID() {
		return getKey().getFrameID();
	}

	public void updateActivityTimestamp() {
		setActivityTimestamp(System.currentTimeMillis());
	}

	public void incrementHeaderCount() {
		setHeaderCount(getHeaderCount() + 1);
	}

	public void incrementVoiceCount() {
		setVoiceCount(getVoiceCount() + 1);
	}

}
