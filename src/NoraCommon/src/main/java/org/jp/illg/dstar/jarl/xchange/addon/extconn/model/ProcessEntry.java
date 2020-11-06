package org.jp.illg.dstar.jarl.xchange.addon.extconn.model;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.model.HeardInfo;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.ambe.AMBEBERCalculator;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.Setter;

public class ProcessEntry {

	@Getter
	private final Lock locker;

	@Getter
	private final int frameID;

	@Getter
	private final long createTime;

	@Getter
	@Setter
	private ProcessMode processMode;

	@Getter
	private final DSTARRepeater repeater;

	@Getter
	@Setter
	private DSTARPacket headerPacket;

	@Getter
	private final Timer activityTimekeeper;

	@Getter
	@Setter
	private DataSegmentDecoder slowdataDecoder;

	@Getter
	private HeardInfo heardInfo;

	@Getter
	private final AMBEBERCalculator errorDetector;

	public ProcessEntry(
		final int frameID, final ProcessMode processMode, final DSTARRepeater repeater
	) {
		super();

		this.locker = new ReentrantLock();

		this.createTime = System.currentTimeMillis();
		this.frameID = frameID;
		this.processMode = processMode;
		this.repeater = repeater;

		activityTimekeeper = new Timer();

		this.heardInfo = new HeardInfo();

		errorDetector = new AMBEBERCalculator();
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(final int indentLevel) {
		int lvl = indentLevel;
		if(lvl < 0) {lvl = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < lvl; c++) {sb.append(' ');}

		sb.append("FrameID=");
		sb.append(String.format("%04X", this.frameID));

		sb.append('/');

		sb.append("CreateTime=");
		sb.append(FormatUtil.dateFormat(getCreateTime()));

		sb.append('/');

		sb.append("ProcessMode=");
		sb.append(getProcessMode());

		sb.append('/');

		sb.append("Repeater=");
		sb.append(getRepeater().getRepeaterCallsign());

		sb.append('/');

		sb.append("HeaderPacket=\n");
		sb.append(getHeaderPacket() != null ? getHeaderPacket().toString(lvl + 4) : "null");

		return sb.toString();
	}
}
