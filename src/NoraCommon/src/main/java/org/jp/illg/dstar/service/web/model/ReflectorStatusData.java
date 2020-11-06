package org.jp.illg.dstar.service.web.model;

import java.util.List;

import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReflectorStatusData extends StatusData{
	
	private ReflectorProtocolProcessorTypes reflectorType;
	
	private List<ReflectorConnectionData> connections;
	
	public ReflectorStatusData(final String webSocketRoomId) {
		super(webSocketRoomId);
	}
	
}
