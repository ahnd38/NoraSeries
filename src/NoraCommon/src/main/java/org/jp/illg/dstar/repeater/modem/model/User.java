package org.jp.illg.dstar.repeater.modem.model;

import lombok.Getter;
import lombok.NonNull;

public class User {

	@Getter
	private UserLocationType locationType;

	@Getter
	private String callsign;

	@Getter
	private String callsignShort;

	public User(
		@NonNull final UserLocationType locationType,
		@NonNull final String callsign,
		@NonNull final String callsignShort
	) {
		super();

		this.locationType = locationType;
		this.callsign = callsign;
		this.callsignShort = callsign;
	}

}
