package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DPlusStatusData extends ReflectorStatusData {
	
	public DPlusStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
