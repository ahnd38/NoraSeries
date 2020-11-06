package org.jp.illg.dstar.service.web.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ExternalICOMRepeaterStatusData extends RepeaterStatusData {

	public ExternalICOMRepeaterStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
}
