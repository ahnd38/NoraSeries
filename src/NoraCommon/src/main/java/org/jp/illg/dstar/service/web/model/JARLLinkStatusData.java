package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class JARLLinkStatusData extends ReflectorStatusData {
	
	public JARLLinkStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
}
