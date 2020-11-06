package org.jp.illg.dstar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

@Data
@AllArgsConstructor
public class BackBonePositionUpdateData {

	@Getter
	private String terminalCallsign;

	@Getter
	private String areaRepeaterCallsign;

}
