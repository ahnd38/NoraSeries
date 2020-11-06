package org.jp.illg.dstar.routing.service.jptrust.model;

public class StatusPTTOff extends Status {

	public StatusPTTOff() {
		super();
	}

	@Override
	public StatusType getStatusType() {
		return StatusType.PTTOff;
	}

}
