package org.jp.illg.nora.vr;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.jp.illg.nora.vr.model.NoraVRRoute;

import lombok.Getter;
import lombok.NonNull;

public class NoraVRAccessLog {

	@Getter
	private long accessTime;

	@Getter
	private NoraVRRoute route;

	@Getter
	private String yourCallsign;

	@Getter
	private String myCallsignLong;

	@Getter
	private String myCallsignShort;


	public NoraVRAccessLog(
		final long accessTime,
		@NonNull final NoraVRRoute route,
		final String yourCallsign,
		final String myCallsignLong,
		final String myCallsignShort
	) {
		super();

		this.accessTime = accessTime;
		this.route = route;
		this.yourCallsign = yourCallsign;
		this.myCallsignLong = myCallsignLong;
		this.myCallsignShort = myCallsignShort;
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < indentLevel; i++) {sb.append(' ');}

		final SimpleDateFormat format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

		sb.append("AccessTime:");
		sb.append(format.format(new Date(accessTime)));

		sb.append('/');

		sb.append("Route:");
		sb.append(route);

		sb.append('/');

		sb.append("YourCallsign:");
		sb.append(yourCallsign);

		sb.append('/');

		sb.append("MyCallsign:");
		sb.append(myCallsignLong);
		sb.append('_');
		sb.append(myCallsignShort);

		return sb.toString();
	}

	@Override
	public String toString() {
		return toString(0);
	}
}
