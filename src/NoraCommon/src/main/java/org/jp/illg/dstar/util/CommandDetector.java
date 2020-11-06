package org.jp.illg.dstar.util;

import java.util.regex.Pattern;

import lombok.NonNull;

public class CommandDetector {

	private static final Pattern routingServiceChangePattern = Pattern.compile(
		"^[ _]{4}G2R[GIJ]$"
	);

	private static final Pattern routingServiceInformationPattern = Pattern.compile(
		"^[ _]{7}[R]$"
	);

	private static final Pattern reflectorLinkInformationPatterm = Pattern.compile(
		"^[ _]{7}[I]$"
	);

	private static final Pattern reflectorLinkControlPattern = Pattern.compile(
		"^(((([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z ]*[A-FH-RT-Z][L])|" +
		"(((XRF)|(XLX)|(DCS)|(REF))[0-9]{3}[A-Z][L]))$"
	);

	private static final Pattern reflectorLinkManagerAutoControlPattern = Pattern.compile(
		"^[ _]{2}RLMAC[DE]$"
	);


	private CommandDetector() {}

	public static CommandType getCommandType(@NonNull final String yourCallsign) {

		//____G2RG/____G2RI/____G2RJ
		if(routingServiceChangePattern.matcher(yourCallsign).matches())
			return CommandType.RoutingServiceChange;

		//_______R
		else if(routingServiceInformationPattern.matcher(yourCallsign).matches())
			return CommandType.RoutingServiceInformation;

		//_______I
		else if(reflectorLinkInformationPatterm.matcher(yourCallsign).matches())
			return CommandType.ReflectorLinkInformation;

		//JQ1ZYCAL/XRF380AL/XLX380AL
		else if(reflectorLinkControlPattern.matcher(yourCallsign).matches())
			return CommandType.ReflectorLinkControl;

		//__RLMACE/__RLMACD
		else if(reflectorLinkManagerAutoControlPattern.matcher(yourCallsign).matches())
			return CommandType.ReflectorLinkManagerAutoControl;

		//CQCQCQ
		else if(CallSignValidator.isValidCQCQCQ(yourCallsign))
			return CommandType.CQCQCQ;

		//JQ1ZYC
		else if(CallSignValidator.isValidUserCallsign(yourCallsign))
			return CommandType.G123;

		return CommandType.Unknown;
	}
}
