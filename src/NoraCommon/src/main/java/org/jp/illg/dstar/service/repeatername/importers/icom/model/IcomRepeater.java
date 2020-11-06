package org.jp.illg.dstar.service.repeatername.importers.icom.model;

import org.jp.illg.dstar.service.repeatername.model.RepeaterData;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper=true)
public class IcomRepeater extends RepeaterData{

	private int groupNo;

	private String groupName;

	private String subName;

	private String duplexMode;

	private String mode;

	private String tone;

	private String repeaterTone;

	private boolean repeater;

	private boolean repeater1Use;

	private String position;

	private int utcOffset;


	public IcomRepeater(
		final int groupNo,
		final String groupName,
		final String name,
		final String subName,
		final String repeaterCallsign,
		final String gatewayCallsign,
		final double frequencyMHz,
		final String duplexMode,
		final double frequencyOffsetMHz,
		final String mode,
		final String tone,
		final String repeaterTone,
		final boolean isRepeater,
		final boolean isRepeater1Use,
		final String position,
		final double latitude,
		final double longitude,
		final int utcOffset
	) {
		super(
			name,
			repeaterCallsign, gatewayCallsign,
			frequencyMHz, frequencyOffsetMHz,
			latitude, longitude, utcOffset
		);

		this.groupNo = groupNo;
		this.groupName = groupName;
		this.subName = subName;
		this.duplexMode = duplexMode;
		this.mode = mode;
		this.tone = tone;
		this.repeaterTone = repeaterTone;
		this.repeater = isRepeater;
		this.repeater1Use = isRepeater1Use;
		this.position = position;
	}
}
