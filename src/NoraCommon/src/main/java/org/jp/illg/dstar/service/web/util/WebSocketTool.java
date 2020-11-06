package org.jp.illg.dstar.service.web.util;

import java.util.regex.Pattern;

import lombok.NonNull;

public class WebSocketTool {

	private static final Pattern roomIdPattern = Pattern.compile("[^a-zA-Z0-9]");

	private WebSocketTool() {
		super();
	}


	public static String formatRoomId(@NonNull final String...strings) {
		final StringBuilder sb = new StringBuilder();

		for(int i = 0; i < strings.length; i++) {
			final String str = strings[i];

			if(str == null || "".equals(str)) {continue;}

			sb.append(roomIdPattern.matcher(str).replaceAll("_").toLowerCase());

			if((i + 1) < strings.length) {sb.append(".");}
		}

		return sb.toString();
	}
}
