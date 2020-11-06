package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import java.util.Calendar;

import lombok.Getter;

public class TimeBasedEntry {

	@Getter
	private final int dayOfWeek;

	@Getter
	private final Calendar startTime;

	@Getter
	private final Calendar endTime;

	@Getter
	private final String linkReflectorCallsign;

	public TimeBasedEntry(
		int dayOfWeek,
		Calendar startTime,
		Calendar endTime,
		String linkReflectorCallsign
	) {
		super();

		this.dayOfWeek = dayOfWeek;
		this.startTime = startTime;
		this.endTime = endTime;
		this.linkReflectorCallsign = linkReflectorCallsign;
	}



}
