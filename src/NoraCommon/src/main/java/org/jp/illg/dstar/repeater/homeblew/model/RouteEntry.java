package org.jp.illg.dstar.repeater.homeblew.model;

import java.util.UUID;

import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class RouteEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int frameID;

	@Getter
	@Setter
	private RouteStatus routeStatus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DVPacket headerPacket;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Timer activityTime;
	
	@Getter
	private final UUID loopBlockID;

	private RouteEntry() {
		super();

		setCreateTime(System.currentTimeMillis());
		setActivityTime(new Timer());
		
		loopBlockID = UUID.randomUUID();
	}

	public RouteEntry(
		final int frameID,
		@NonNull final RouteStatus routeStatus,
		@NonNull final DVPacket headerPacket
	) {
		this();

		setFrameID(frameID);
		setRouteStatus(routeStatus);
		setHeaderPacket(headerPacket);
	}
}
