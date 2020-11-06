package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class VoiceroidAutoReplyRepeaterStatusData extends RepeaterStatusData {
	
	public VoiceroidAutoReplyRepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
