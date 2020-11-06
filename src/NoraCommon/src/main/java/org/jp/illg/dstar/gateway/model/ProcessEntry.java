package org.jp.illg.dstar.gateway.model;

import java.net.InetAddress;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.model.defines.RepeaterControlFlag;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.ambe.AMBEBERCalculator;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ProcessEntry {

	private static final long processEntryTimeoutMillis = TimeUnit.SECONDS.toMillis(2);

	@Getter
	private final Lock locker;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Timer activityTimekeeper;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int frameID;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARRepeater repeater;

	@Getter
	@Setter
	private ProcessModes processMode;

	@Getter
	@Setter
	private ProcessStates processState;

	@Getter
	@Setter
	private UUID routingID;

	private Queue<DSTARPacket> cachePackets;

	@Getter
	@Setter
	private DSTARPacket headerPacket;

	@Getter
	@Setter
	private InetAddress remoteAddress;

	@Getter
	@Setter
	private RoutingService routingService;

	@Getter
	@Setter
	private DataSegmentDecoder slowdataDecoder;

	@Getter
	private HeardInfo heardInfo;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private AMBEBERCalculator errorDetector;


	private ProcessEntry() {
		super();

		locker = new ReentrantLock();

		setCreatedTimestamp(System.currentTimeMillis());
		setActivityTimekeeper(new Timer(processEntryTimeoutMillis));
		updateActivityTimestamp();
		setFrameID(0x0);
		setRepeater(null);
		setProcessMode(ProcessModes.Unknown);
		setProcessState(ProcessStates.Unknown);
		setRoutingID(null);
		setHeaderPacket(null);
		setRoutingService(null);
		setErrorDetector(new AMBEBERCalculator());

		heardInfo = new HeardInfo();
	}

	public ProcessEntry(int frameID, ProcessModes processMode, DSTARRepeater repeater) {
		this();

		setFrameID(frameID);
		setProcessMode(processMode);
		setRepeater(repeater);
		setRoutingService(repeater != null ? repeater.getRoutingService() : null);
	}

	public ProcessEntry(int frameID, ProcessModes processMode) {
		this(frameID, processMode, null);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int indent = indentLevel;
		if(indent < 0) {indent = 0;}

		final StringBuffer sb = new StringBuffer();

		for(int count = 0; count < indent; count++)
			sb.append(' ');

		sb.append("[ProcessEntry]:");

		String datePtn = "yyyy/MM/dd HH:mm:ss.SSS";
		sb.append("CreatedTime=");
		sb.append(DateFormatUtils.format(this.createdTimestamp, datePtn));

		sb.append("/");
		sb.append("ActivityTime=");
		sb.append(DateFormatUtils.format(getActivityTimekeeper().getTimestampMilis(), datePtn));

		sb.append("/");
		sb.append("FrameID=");
		sb.append(String.format("%04X", this.frameID));

		sb.append("/");
		sb.append("Repeater=");
		if (this.repeater != null)
			sb.append(this.repeater.getRepeaterCallsign());
		else
			sb.append("null");

		sb.append("/");
		sb.append("ProcessMode=");
		sb.append(this.processMode.toString());

		sb.append("/");
		sb.append("ProcessState=");
		sb.append(this.processState.toString());

		sb.append("/");
		sb.append("RoutingID=");
		if (this.routingID != null)
			sb.append(this.routingID.toString());
		else
			sb.append("null");

		sb.append("/");
		sb.append("CachePackets=");
		sb.append(getCachePackets().size());
		sb.append("packets");

		sb.append("/");
		sb.append("RemoteAddress=");
		if (this.remoteAddress != null)
			sb.append(this.remoteAddress.toString());
		else
			sb.append("null");

		sb.append("/");
		sb.append("HeaderPacket=\n");
		if (this.headerPacket != null)
			sb.append(this.headerPacket.toString(indent + 4));
		else
			sb.append("null");

		sb.append('\n');
		for(int count = 0; count < indent; count++) {sb.append(' ');}
		sb.append("HeardInfo=\n");
		sb.append(this.heardInfo.toString(indent + 4));

		return sb.toString();
	}

	public void updateActivityTimestamp() {
		getActivityTimekeeper().updateTimestamp();
	}

	public boolean isTimeoutActivity() {
		return getActivityTimekeeper().isTimeout();
	}

	/**
	 * @return cachePackets
	 */
	public Queue<DSTARPacket> getCachePackets() {
		if (this.cachePackets == null)
			setCachePackets(new LinkedList<>());

		return cachePackets;
	}

	/**
	 * @param cachePackets
	 *            セットする cachePackets
	 */
	private void setCachePackets(Queue<DSTARPacket> cachePackets) {
		this.cachePackets = cachePackets;
	}

	public boolean isBusyHeader() {
		final DSTARPacket packet = getHeaderPacket();

		return packet != null &&
			packet.getDVPacket().hasPacketType(PacketType.Header) &&
			packet.getRFHeader().isSetRepeaterControlFlag(RepeaterControlFlag.CANT_REPEAT);
	}
}
