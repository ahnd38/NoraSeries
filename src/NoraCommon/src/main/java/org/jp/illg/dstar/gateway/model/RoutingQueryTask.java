package org.jp.illg.dstar.gateway.model;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.gateway.define.QueryRequestSource;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.RoutingService;
import org.jp.illg.dstar.routing.model.RoutingInfo;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.Callback;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;

@ToString
public class RoutingQueryTask{
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private UUID taskID;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private DSTARRepeater repeater;

	@Setter
	@Getter
	private RoutingService routingService;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private QueryRequestSource requestSource;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createdTimestamp;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Callback<RoutingInfo> callback;

	private final Timer queryLimitTimer;


	private RoutingQueryTask(
		@NonNull UUID taskID, @NonNull QueryRequestSource requestSource,
		@NonNull RoutingService routingService
	) {
		super();

		this.taskID = taskID;
		this.requestSource = requestSource;
		this.routingService = routingService;

		queryLimitTimer = new Timer();
		queryLimitTimer.updateTimestamp();

		setCreatedTimestamp(System.currentTimeMillis());
	}

	public RoutingQueryTask(
		@NonNull UUID taskID,
		@NonNull RoutingService routingService, @NonNull DSTARRepeater repeater
	) {
		this(taskID, QueryRequestSource.Repeater, routingService);

		setRepeater(repeater);
	}

	public RoutingQueryTask(
		@NonNull UUID taskID,
		@NonNull RoutingService routingService, @NonNull Callback<RoutingInfo> callback
	) {
		this(taskID, QueryRequestSource.Callback, routingService);

		setCallback(callback);
	}

	public boolean isTimeout(long duration, TimeUnit timeUnit) {
		return queryLimitTimer.isTimeout(duration, timeUnit);
	}
}
