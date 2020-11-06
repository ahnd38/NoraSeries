package org.jp.illg.dstar.routing.service.jptrust.model;

import java.util.concurrent.TimeUnit;

import org.jp.illg.util.Timer;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class StatusRepeaterEntry{
	@Getter
	private String repeaterCallsign;

	@Getter
	private Timer watchdogTimekeeper;

	@Getter
	private Timer keepaliveTimekeeper;

	@Getter
	@Setter
	private boolean keepaliveTransmitted;

	@Getter
	@Setter
	private String message;

	public StatusRepeaterEntry(
		@NonNull final String repeaterCallsign,
		final long watchdogTimeoutSeconds,
		final long keepaliveTimePeriodSeconds
	){
		super();

		this.repeaterCallsign = repeaterCallsign;

		watchdogTimekeeper =
			new Timer(watchdogTimeoutSeconds, TimeUnit.SECONDS);
		keepaliveTimekeeper =
			new Timer(keepaliveTimePeriodSeconds, TimeUnit.SECONDS);

		keepaliveTransmitted = false;

		message = "";
	}
}
