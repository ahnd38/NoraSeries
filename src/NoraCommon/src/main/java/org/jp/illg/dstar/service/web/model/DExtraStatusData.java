package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DExtraStatusData extends ReflectorStatusData {
	
	public DExtraStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
