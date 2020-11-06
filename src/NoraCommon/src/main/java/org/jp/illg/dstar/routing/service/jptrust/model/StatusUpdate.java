package org.jp.illg.dstar.routing.service.jptrust.model;

public class StatusUpdate extends Status {

	public StatusUpdate() {
		super();
	}

	@Override
	public StatusType getStatusType() {
		return StatusType.PTTUpdate;
	}

}
