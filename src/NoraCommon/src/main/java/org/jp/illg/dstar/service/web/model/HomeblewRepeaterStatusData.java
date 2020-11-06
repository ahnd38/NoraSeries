package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HomeblewRepeaterStatusData extends RepeaterStatusData {
	
	public HomeblewRepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
