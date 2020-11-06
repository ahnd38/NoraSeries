package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DCSStatusData extends ReflectorStatusData {
	
	public DCSStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
