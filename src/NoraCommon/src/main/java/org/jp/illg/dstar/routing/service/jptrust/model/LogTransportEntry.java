package org.jp.illg.dstar.routing.service.jptrust.model;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.model.Header;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class LogTransportEntry {

	@Getter
	private final UUID id;

	@Getter
	private final long createdTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private JpTrustCommand queryResponse;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Header queryHeader;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Timer activityTimeKeeper;

	public LogTransportEntry(
		final Header queryHeader, final JpTrustCommand queryResponse,
		final long lifeTimeSeconds
	) {
		super();

		this.id = UUID.randomUUID();

		this.createdTime = System.currentTimeMillis();

		setQueryHeader(queryHeader);
		setQueryResponse(queryResponse);
		setActivityTimeKeeper(new Timer(lifeTimeSeconds, TimeUnit.SECONDS));
	}



}
