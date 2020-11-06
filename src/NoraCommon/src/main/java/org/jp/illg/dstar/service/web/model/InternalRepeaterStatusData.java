package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class InternalRepeaterStatusData extends RepeaterStatusData {
	
	public InternalRepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
}
