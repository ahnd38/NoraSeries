package org.jp.illg.dstar.routing.service.ircDDB.model;

import java.util.Date;

import lombok.Data;
import lombok.NonNull;

@Data
public class IRCDDBAppRepeaterIPEntry {

	private String zoneRepeaterCallsign;

	private String ipAddress;

	private Date updateTime;

	public IRCDDBAppRepeaterIPEntry() {
		super();
	}

	public IRCDDBAppRepeaterIPEntry(
		@NonNull Date updateTime,
		@NonNull String zoneRepeaterCallsign,
		@NonNull String ipAddress
	) {
		this();

		setUpdateTime(updateTime);
		setZoneRepeaterCallsign(zoneRepeaterCallsign);
		setIpAddress(ipAddress);
	}

	@Override
	public IRCDDBAppRepeaterIPEntry clone() {
		IRCDDBAppRepeaterIPEntry copy = null;
		try {
			copy = (IRCDDBAppRepeaterIPEntry)super.clone();

			copy.zoneRepeaterCallsign = zoneRepeaterCallsign;
			copy.ipAddress = ipAddress;
			copy.updateTime = updateTime != null ? (Date)updateTime.clone() : null;

			return copy;
		}catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}
}
