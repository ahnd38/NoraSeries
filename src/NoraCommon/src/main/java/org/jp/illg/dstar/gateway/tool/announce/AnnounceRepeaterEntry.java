package org.jp.illg.dstar.gateway.tool.announce;

import java.util.LinkedList;
import java.util.Queue;

import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.util.dvpacket2.RateAdjuster;
import org.jp.illg.dstar.util.dvpacket2.TransmitterPacket;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class AnnounceRepeaterEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTime;

	@Getter
	@Setter
	private long lastActivityTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARRepeater repeater;

	@Getter
	@Setter
	private AnnounceRepeaterProcessState processState;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Queue<AnnounceTask> announceTasks;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private RateAdjuster<TransmitterPacket> rateMatcher;


	private AnnounceRepeaterEntry() {
		super();

		setCreatedTime(System.currentTimeMillis());

		setProcessState(AnnounceRepeaterProcessState.Initialize);

		setAnnounceTasks(new LinkedList<AnnounceTask>());
		setRateMatcher(new RateAdjuster<>(2, false));
	}

	public AnnounceRepeaterEntry(DSTARRepeater repeater) {
		this();

		if(repeater == null)
			throw new IllegalArgumentException();

		setRepeater(repeater);
	}

	public void updateLastActivityTime() {
		setLastActivityTime(System.currentTimeMillis());
	}
}
