package org.jp.illg.dstar.reflector.protocol.model;

import java.net.InetSocketAddress;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ConnectionRequest;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIOEntryUDP;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public abstract class ReflectorConnectionEntry<TransmitterPacketType extends TransmitterPacket> {

	@Getter
	private final long createTime;

	@Getter
	private final UUID id;

	@Getter
	private final UUID loopBlockID;

	@Getter
	@Setter
	private SocketIOEntryUDP outgoingChannel;

	@Getter
	@Setter
	private InetSocketAddress remoteAddressPort;

	@Getter
	private final InetSocketAddress localAddressPort;

	@Getter
	private final ConnectionDirectionType connectionDirection;

	@Getter
	@Setter
	private String repeaterCallsign;

	@Getter
	@Setter
	private String reflectorCallsign;

	@Getter
	private final Timer activityTimeKepper;

	@Getter
	private final Timer stateTimeKeeper;

	@Getter
	private final Timer receiveKeepAliveTimeKeeper;

	@Getter
	private final Timer transmitKeepAliveTimeKeeper;

	@Getter
	private final Timer frameSequenceTimeKepper;

	@Getter
	@Setter
	private DSTARRepeater destinationRepeater;

	@Getter
	@Setter
	private int modCode;

	@Getter
	private final CacheTransmitter<TransmitterPacketType> cacheTransmitter;

	@Getter
	@Setter
	private boolean cacheTransmitterUndeflow;

	@Getter
	@Setter
	private int currentFrameID;

	@Getter
	@Setter
	private DSTARPacket currentHeader;

	@Getter
	@Setter
	private ConnectionDirectionType currentFrameDirection;

	@Getter
	@Setter
	private byte currentFrameSequence;

	@Getter
	private final DataSegmentDecoder slowdataDecoder;

	@Getter
	@Setter
	private ConnectionRequest connectionRequest;

	@Getter
	@Setter
	private ReflectorHostInfo outgoingReflectorHostInfo;

	@Getter
	private final List<ReflectorRemoteUserEntry> remoteUsers;


	public ReflectorConnectionEntry(
		@NonNull final UUID loopBlockID,
		final int transmitterCacheSize,
		@NonNull final InetSocketAddress remoteAddressPort,
		@NonNull final InetSocketAddress localAddressPort,
		@NonNull final ConnectionDirectionType connectionDirection
	) {
		if(transmitterCacheSize < 0)
			throw new IllegalArgumentException("transmitterCacheSize must larger than 0.");

		this.createTime = System.currentTimeMillis();
		this.id = UUID.randomUUID();
		this.loopBlockID = loopBlockID;

		this.outgoingChannel = null;
		this.remoteAddressPort = remoteAddressPort;
		this.localAddressPort = localAddressPort;
		this.connectionDirection = connectionDirection;

		this.repeaterCallsign = DSTARDefines.EmptyLongCallsign;
		this.reflectorCallsign = DSTARDefines.EmptyLongCallsign;

		this.activityTimeKepper = new Timer();
		this.stateTimeKeeper = new Timer();
		this.receiveKeepAliveTimeKeeper = new Timer();
		this.transmitKeepAliveTimeKeeper = new Timer();
		this.frameSequenceTimeKepper = new Timer();

		this.destinationRepeater = null;

		this.modCode = 0x0;

		this.cacheTransmitter = new CacheTransmitter<>(transmitterCacheSize);
		this.cacheTransmitterUndeflow = false;

		this.currentFrameID = 0x0;
		this.currentHeader = null;
		this.currentFrameDirection = ConnectionDirectionType.Unknown;
		this.currentFrameSequence = 0x0;

		this.slowdataDecoder = new DataSegmentDecoder();
		this.connectionRequest = ConnectionRequest.Nothing;

		this.outgoingReflectorHostInfo = null;

		this.remoteUsers = new LinkedList<>();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();
		String datePtn = "yyyy/MM/dd HH:mm:ss.SSS";

		sb.append(indent + "[ID]:");
		sb.append(getId());

		sb.append("\n");

		sb.append(indent);
		sb.append("[DIR]:");
		sb.append(getConnectionDirection());

		sb.append("\n");

		sb.append(indent);
		sb.append("[RepeaterCallsign]:");
		sb.append(getRepeaterCallsign());

		sb.append("/");
		sb.append("[ReflectorCallsign]:");
		sb.append(getReflectorCallsign());

		sb.append("\n");

		sb.append(indent);
		sb.append("[CreatedTime]:");
		sb.append(DateFormatUtils.format(getCreateTime(), datePtn));
		sb.append("/");
		sb.append("[ActivityTime]:");
		sb.append(DateFormatUtils.format(getActivityTimeKepper().getTimestampMilis(), datePtn));
		sb.append("/");
		sb.append("[StateTime]:");
		sb.append(DateFormatUtils.format(getStateTimeKeeper().getTimestampMilis(), datePtn));

		sb.append("\n");

		sb.append(indent);
		sb.append("[TransmitKeepAliveTime]:");
		sb.append(DateFormatUtils.format(getTransmitKeepAliveTimeKeeper().getTimestampMilis(), datePtn));
		sb.append("/");
		sb.append("[ReceiveKeepAliveTime]:");
		sb.append(DateFormatUtils.format(getReceiveKeepAliveTimeKeeper().getTimestampMilis(), datePtn));

		sb.append("\n");

		sb.append(indent);
		sb.append("[ConnectionRequest]:");
		sb.append(getConnectionRequest().toString());

		sb.append("\n");

		sb.append(indent);
		sb.append("[LocalAddress]:");
		sb.append(getLocalAddressPort());
		sb.append("/");
		sb.append("[RemoteAddress]:");
		sb.append(getRemoteAddressPort());

		sb.append("\n");

		sb.append(indent);
		sb.append("[FrameID]:");
		sb.append(String.format("0x%04X", getCurrentFrameID()));
		sb.append("/");
		sb.append("[Sequence]:");
		sb.append(String.format("0x%02X", getCurrentFrameSequence()));

		return sb.toString();
	}

	public abstract DSTARProtocol getProtocol();
}
