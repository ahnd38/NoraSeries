package org.jp.illg.dstar.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jp.illg.dstar.DSTARDefines;

public class CallSignValidator {



	private static final Pattern repeaterPattern =
		Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,}[A-FH-Z]$");
//			Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z][ ]{1,}[A-FH-RT-Z]$");

	private static final Pattern gatewayPattern =
		Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,}[G]$");
//			Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z][ ]{1,}[G]$");

	private static final Pattern areaRepeaterPattern =
		Pattern.compile("^[/](([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,}[A-FH-Z]$");
//			Pattern.compile("^[/](([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z ]*[A-FH-RT-Z]$");

	private static final Pattern userPattern =
		Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,}[ A-FH-Z]$");
//			Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[A-Z][ ]{1,}[ A-Z]$");

	private static final Pattern shortCallsignPattern =
			Pattern.compile("^[ /A-Z0-9]{4}$");

	private static final Pattern japanUserPattern =
		Pattern.compile(
			"^((7[J-N][0-9]([A-Z]{2,4}[ ]{0,4}))|(8J[0-9]([A-Z]{2,4}[ ]{0,4}))|(J[AD-SX][0-9]([A-Z]{2,4}[ ]{0,4})))[ A-Z]$"
		);
//		Pattern.compile(
//			"^(([7][J-N][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([8][J][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([J][AD-S][0-9]([A-Z]{3}|[A-Z]{2}[ ])))[ ][ A-Z]$"
//		);

	private static final Pattern japanRepeaterPattern =
		Pattern.compile(
			"^((7[J-N][0-9]([A-Z]{2,4}[ ]{0,4}))|(8J[0-9]([A-Z]{2,4}[ ]{0,4}))|(J[AD-SX][0-9]([A-Z]{2,4}[ ]{0,4})))[A-Z]$"
		);
//		Pattern.compile(
//			"^(([7][J-N][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([8][J][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([J][AD-S][0-9]([A-Z]{3}|[A-Z]{2}[ ])))[ ][A-FH-RT-Z]$"
//		);

	private static final Pattern japanGatewayPattern =
		Pattern.compile(
			"^((7[J-N][0-9]([A-Z]{2,4}[ ]{0,4}))|(8J[0-9]([A-Z]{2,4}[ ]{0,4}))|(J[AD-SX][0-9]([A-Z]{2,4}[ ]{0,4})))[G]$"
		);
//		Pattern.compile(
//			"^(([7][J-N][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([8][J][0-9]([A-Z]{3}|[A-Z]{2}[ ]))|([J][AD-S][0-9]([A-Z]{3}|[A-Z]{2}[ ])))[ ][G]$"
//		);

	private static final Pattern jarlRepeaterPattern =
		Pattern.compile(
			"^J((R[0-9][A-Z]{2}[ ])|(P[0-9][Y][A-Z]{2}[ ])|(Q6[Y][AB][A])).*$"
		);

	private static final Pattern reflecterPattern =
			Pattern.compile("^(XRF|DCS|REF|XLX)[0-9]{3}[ ][ A-Z]$");

	private static final Pattern cqPattern =
			Pattern.compile("^CQ(CQ|[ ]{2})(CQ|[ ]{2})[ ]{2}$");

	private static final Pattern jarlLinkReflectorPattern =
			Pattern.compile("^J[A-Z][0-9](([A-Z]{2}[ ])|[A-Z]{3})[ ][A-FH-RT-Z]$");


	public static boolean isValidShortCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(DSTARDefines.CallsignShortLength, replaceUnderbarToSpace(callsign), shortCallsignPattern);
	}

	public static boolean isValidShortCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidShortCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidUserCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), userPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidUserCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidUserCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidGatewayCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), gatewayPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidGatewayCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidGatewayCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidRepeaterCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), repeaterPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidRepeaterCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidRepeaterCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidAreaRepeaterCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), areaRepeaterPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidAreaRepeaterCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidAreaRepeaterCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidReflectorCallsign(String callsign) {
		if(callsign == null) {return false;}

		return
			isValid(replaceUnderbarToSpace(callsign), reflecterPattern) ||
			isValid(replaceUnderbarToSpace(callsign), repeaterPattern) ||
			isValid(replaceUnderbarToSpace(callsign), jarlLinkReflectorPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidReflectorCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidReflectorCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidCQCQCQ(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), cqPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidCQCQCQ(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidCQCQCQ(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJARLLinkReflectorCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), jarlLinkReflectorPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJARLLinkReflectorCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidJARLLinkReflectorCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanUserCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), japanUserPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanUserCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidJapanUserCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanRepeaterCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), japanRepeaterPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanRepeaterCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidJapanRepeaterCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanGatewayCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), japanGatewayPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJapanGatewayCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidJapanGatewayCallsign(String.valueOf(callsign));
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJARLRepeaterCallsign(String callsign) {
		if(callsign == null) {return false;}

		return isValid(replaceUnderbarToSpace(callsign), jarlRepeaterPattern);
	}

	/**
	 *
	 * @param callsign
	 * @return
	 */
	public static boolean isValidJARLRepeaterCallsign(char[] callsign) {
		if(callsign == null) {return false;}

		return isValidJARLRepeaterCallsign(String.valueOf(callsign));
	}

	public static String replaceUnderbarToSpace(String target) {
		if(target == null) {return "        ";}

		return target.replace("_", " ");
	}

	public static String replaceUnderbarToSpace(char[] target) {
		if(target == null) {return "        ";}

		String src = String.valueOf(target);

		return src.replace("_", " ");
	}

	private static boolean isValid(String target, Pattern pattern) {
		return isValid(DSTARDefines.CallsignFullLength, target, pattern);
	}

	private static boolean isValid(int length, String target, Pattern pattern) {
		assert target != null && pattern != null;

		if(target.length() != length) {return false;}

		Matcher m = pattern.matcher(target);

		return m.matches();
	}


}
