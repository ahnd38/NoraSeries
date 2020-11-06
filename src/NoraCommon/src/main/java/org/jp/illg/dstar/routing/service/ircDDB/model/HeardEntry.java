package org.jp.illg.dstar.routing.service.ircDDB.model;

import org.jp.illg.util.Timer;

import lombok.Getter;

public class HeardEntry {

	@Getter
	private final int frameID;

	@Getter
	private final Timer inactivityTimer;

	public HeardEntry(final int frameID) {
		super();

		this.frameID = frameID;
		inactivityTimer = new Timer();
		inactivityTimer.updateTimestamp();
	}

}
