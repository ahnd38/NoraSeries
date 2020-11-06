package org.jp.illg.dstar.routing.service.ircDDB;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;
import java.util.TimeZone;

public class IRCUtils {

	private static final DateFormat ircDateTimeFormat;

	static {
		ircDateTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		ircDateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	private IRCUtils() {
		super();
	}

	public static String getCurrentTime() {

		Date now = new Date(System.currentTimeMillis());

		String formatedDate;
		synchronized(ircDateTimeFormat) {
			formatedDate = ircDateTimeFormat.format(now);
		}

		return formatedDate;
	}

	public static String getRemainTokens(Scanner src) {
		if(src == null) {return "";}

		StringBuffer sb = new StringBuffer("");

		while (src.hasNext()) {
			sb.append(src.next());
			if (src.hasNext()) {sb.append(" ");}
		}

		return sb.toString();
	}

}
