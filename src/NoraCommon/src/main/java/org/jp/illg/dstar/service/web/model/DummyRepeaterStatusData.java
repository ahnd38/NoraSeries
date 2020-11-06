package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DummyRepeaterStatusData extends RepeaterStatusData {
	
	public DummyRepeaterStatusData(String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
