package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class IrcDDBRoutingServiceStatusData extends RoutingServiceStatusData {
	
	private int userRecords;
	
	private int repeaterRecords;
	
	public IrcDDBRoutingServiceStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
