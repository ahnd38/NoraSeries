package org.jp.illg.dstar.g123.model;

import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.dvpacket2.CacheTransmitter;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class G2Entry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	private final Timer activityTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private ConnectionDirectionType direction;

	@Getter
	private UUID loopblockID;

	@Getter
	@Setter
	private InetSocketAddress remoteAddressPort;

	@Getter
	@Setter
	private int protocolVersion;

	@Getter
	@Setter
	private G2RouteStatus routeStatus;

	@Getter
	@Setter
	private int frameID;

	@Getter
	@Setter
	private int sequence;

	@Getter
	@Setter
	private DSTARPacket header;

	@Getter
	@Setter
	private boolean headerTransmitted;

	@Getter
	private final DataSegmentDecoder slowdataDecoder;

	@Getter
	private final CacheTransmitter<G2TransmitPacketEntry> transmitter;


	private G2Entry() {
		super();

		setCreatedTimestamp(System.currentTimeMillis());

		activityTimestamp = new Timer(500, TimeUnit.MILLISECONDS);

		transmitter = new CacheTransmitter<>(10, 1);

		loopblockID = DSTARUtils.generateLoopBlockID();

		setDirection(ConnectionDirectionType.Unknown);
		setProtocolVersion(0);

		setRemoteAddressPort(null);

		setRouteStatus(G2RouteStatus.Invalid);

		setFrameID(0x0000);
		setSequence(0x0);

		slowdataDecoder = new DataSegmentDecoder();

		setHeader(null);
		setHeaderTransmitted(false);
	}

	public G2Entry(ConnectionDirectionType direction, int protocolVersion, int frameID) {
		this();

		setDirection(direction);
		setProtocolVersion(protocolVersion);
		setFrameID(frameID);
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

		sb.append(indent);

		sb.append("[Direction]:");
		sb.append(getDirection());

		sb.append("/");

		sb.append("[ProtocolVersion]:");
		sb.append(getProtocolVersion());

		sb.append("/");

		sb.append("[RouteStatus]:");
		sb.append(getRouteStatus());

		sb.append("\n");
		sb.append(indent);

		sb.append("[CreateTime]:");
		sb.append(FormatUtil.dateFormat(datePtn, getCreatedTimestamp()));

		sb.append("/");

		sb.append("[RemoteAddress]:");
		sb.append(getRemoteAddressPort());

		sb.append("\n");
		sb.append(indent);

		sb.append("[FrameID]:");
		sb.append(String.format("0x%04X", getFrameID()));

		sb.append("/");

		sb.append("[Sequence]:");
		sb.append(String.format("0x%02X", getSequence()));

		return sb.toString();
	}

}
