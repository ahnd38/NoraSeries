package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EchoAutoReplyRepeaterStatusData extends RepeaterStatusData {
	
	public EchoAutoReplyRepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
