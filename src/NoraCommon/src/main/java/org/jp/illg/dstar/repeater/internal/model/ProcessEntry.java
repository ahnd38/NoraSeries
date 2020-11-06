package org.jp.illg.dstar.repeater.internal.model;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.RepeaterModem;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class ProcessEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UUID id;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	private final Timer activityTimestamp;

	@Getter
	@Setter
	private int frameID;

	@Getter
	@Setter
	private ProcessMode processMode;

	@Getter
	@Setter
	private DSTARPacket headerPacket;

	@Getter
	@Setter
	private boolean busyReceived;

	@Getter
	@Setter
	private RepeaterModem sourceModem;

	@Getter
	private Timer announceStartTime;

	@Getter
	private Queue<DSTARPacket> announcePackets;

	@Getter
	private RateAdjuster<TransmitterPacket> announcePacketRateAdjuster;

	@Getter
	/**
	 * アップリンクルート用最低送信時間タイマ
	 */
	private final Timer minimumTransmissionTimer;

	@Getter
	@Setter
	/**
	 * アップリンクルート用最低送信時間タイマ有効無効
	 */
	private boolean enableMinimumTransmissionTimer;

	@Getter
	@Setter
	/**
	 * アップリンクルートの際にモデムに対しての送信が存在するか
	 */
	private boolean destinationModemRoute;

	@Getter
	@Setter
	private boolean replyFlagTranmitted;

	private ProcessEntry() {
		super();

		this.activityTimestamp = new Timer();

		setId(UUID.randomUUID());
		setCreatedTimestamp(System.currentTimeMillis());
		updateActivityTimestamp();

		setBusyReceived(false);
		setDestinationModemRoute(false);
		setReplyFlagTranmitted(false);

		this.minimumTransmissionTimer = new Timer();
		setEnableMinimumTransmissionTimer(false);
	}

	public ProcessEntry(int frameID, @NonNull DSTARPacket header, ProcessMode processMode) {
		this();

		setFrameID(frameID);
		setHeaderPacket(header);
		setProcessMode(processMode);
	}

	public ProcessEntry(
		int frameID, @NonNull DSTARPacket header, ProcessMode processMode, RepeaterModem sourceModem
	) {
		this(frameID, header, processMode);

		setSourceModem(sourceModem);
	}

	public ProcessEntry(
		final int frameID,
		@NonNull DSTARPacket header,
		final long announceStartTime, @NonNull final TimeUnit announceStartTimeUnit
	) {
		this(frameID, header, ProcessMode.FlagReply);

		this.announceStartTime = new Timer(announceStartTime, announceStartTimeUnit);
		this.announceStartTime.updateTimestamp();

		announcePackets = new LinkedList<>();
	}

	public void updateActivityTimestamp() {
		getActivityTimestamp().updateTimestamp();
	}

	public boolean isTimeoutActivity(final long processEntryTimeoutMillis) {
		return getActivityTimestamp().isTimeout(processEntryTimeoutMillis, TimeUnit.MILLISECONDS);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder();
		sb.append(indent);

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]:");

		sb.append("ID=");
		sb.append(getId().toString());

		sb.append("/");

		sb.append("FrameID=");
		sb.append(String.format("0x%04X", getFrameID()));

		sb.append("/");

		sb.append("ProcessMode=");
		sb.append(getProcessMode().toString());

		sb.append("/");

		sb.append("BusyReceived=");
		sb.append(isBusyReceived());

		sb.append("/");

		sb.append("CreatedTime=");
		sb.append(FormatUtil.dateFormat(getCreatedTimestamp()));

		sb.append("/");

		sb.append("ActivityTime=");
		sb.append(FormatUtil.dateFormat(getActivityTimestamp().getTimeoutMillis()));

		if(getHeaderPacket() != null) {
			sb.append("/");
			sb.append(getHeaderPacket().getRFHeader().toString());
		}

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}
}
