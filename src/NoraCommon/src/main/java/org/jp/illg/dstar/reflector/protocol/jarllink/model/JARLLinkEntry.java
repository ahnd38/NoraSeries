package org.jp.illg.dstar.reflector.protocol.jarllink.model;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.ReflectorRemoteUserEntry;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionEntry;
import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class JARLLinkEntry extends ReflectorConnectionEntry<JARLLinkTransmitPacketEntry>{

	@Getter
	@Setter
	private InetSocketAddress connectionObserverAddressPort;

	@Getter
	@Setter
	private JARLLinkInternalState connectionState;

	@Getter
	private Timer connectionStateTimeKeeper;

	@Getter
	@Setter
	private int transmitLongSequence;

	@Getter
	private Timer receiveRepeaterKeepAliveTimeKeeper;

	@Getter
	@Setter
	private boolean repeaterKeepAliveReceived;

	@Getter
	@Setter
	private boolean repeaterConnectAnnounceOutputed;

//	@Getter
//	private Timer receiveServerKeepAliveTimeKeeper;

//	@Getter
//	@Setter
//	private long receiveServerKeepAliveLastTime;

	@Getter
	private final List<ReflectorRemoteUserEntry> loginUsersCache;

	@Getter
	@Setter
	private boolean loginUsersReceiving;

	@Getter
	@Setter
	private boolean loginUsersReceived;

	@Getter
	private final Timer loginUsersTimekeeper;

	@Getter
	@Setter
	private DSTARPacket transmitHeader;

	@Getter
	@Setter
	private int protocolVersion;

	@Getter
	@Setter
	private String serverSoftware;


	public JARLLinkEntry(
		@NonNull final UUID loopBlockID,
		final int transmitterCacheSize,
		@NonNull final InetSocketAddress remoteAddressPort,
		@NonNull final InetSocketAddress localAddressPort,
		@NonNull final ConnectionDirectionType connectionDirection
	) {
		super(
			loopBlockID,
			transmitterCacheSize,
			remoteAddressPort,
			localAddressPort,
			connectionDirection
		);

		setConnectionState(JARLLinkInternalState.Unknown);
		connectionStateTimeKeeper = new Timer();

		setTransmitLongSequence(0);


		receiveRepeaterKeepAliveTimeKeeper = new Timer();
//		receiveServerKeepAliveTimeKeeper = new Timer();
//		receiveServerKeepAliveLastTime = 0L;

		repeaterKeepAliveReceived = false;
		repeaterConnectAnnounceOutputed = false;

		loginUsersCache = new ArrayList<>(64);
		loginUsersReceiving = false;
		loginUsersReceived = false;
		loginUsersTimekeeper = new Timer();

		serverSoftware = "";

		setTransmitHeader(null);
		setProtocolVersion(1);

		setConnectionObserverAddressPort(null);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	@Override
	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		final StringBuilder sb = new StringBuilder();
		final String datePtn = "yyyy/MM/dd HH:mm:ss.SSS";

		sb.append(super.toString(indentLevel));

		sb.append("\n");

		sb.append(indent);
		sb.append("[State]:");
		sb.append(getConnectionState().toString());

		sb.append("\n");

		sb.append(indent);
		sb.append("[StateTime]:");
		sb.append(DateFormatUtils.format(getConnectionStateTimeKeeper().getTimestampMilis(), datePtn));

		sb.append("\n");

		sb.append(indent);
//		sb.append("[ReceiveServerKeepAliveTime]:");
//		sb.append(DateFormatUtils.format(getReceiveServerKeepAliveTimeKeeper().getTimestamp(), datePtn));
//		sb.append("/");
		sb.append("[ReceiveRepeaterKeepAliveTime]:");
		sb.append(DateFormatUtils.format(getReceiveRepeaterKeepAliveTimeKeeper().getTimestampMilis(), datePtn));

		sb.append("\n");

		sb.append(indent);
		sb.append("[ProtocolVersion]:");
		sb.append(getProtocolVersion());

		sb.append("\n");

		sb.append(indent);
		sb.append("[ConnectionObserver]:");
		sb.append(getConnectionObserverAddressPort());

		sb.append("\n");

		sb.append(indent);
		sb.append("[LongSequence]:");
		sb.append(String.format("0x%04X", getTransmitLongSequence()));

		return sb.toString();
	}

	@Override
	public DSTARProtocol getProtocol() {
		return DSTARProtocol.JARLLink;
	}
}
