package org.jp.illg.dstar.routing.service.jptrust.model;

import java.net.InetAddress;

import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class PositionCacheEntry {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private Timer timeKeeper;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String yourCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String repeater1Callsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String repeater2Callsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetAddress gatewayIP;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private JpTrustResult queryResult;


	private PositionCacheEntry() {
		super();

		setCreateTime(System.currentTimeMillis());
		setTimeKeeper(new Timer());
	}

	public PositionCacheEntry(
		@NonNull final String yourCallsign,
		@NonNull final String repeater1Callsign,
		@NonNull final String repeater2Callsign,
		@NonNull final InetAddress gatewayIP,
		@NonNull final JpTrustResult queryResult,
		final long timelimitMillis
	) {
		this();

		setYourCallsign(yourCallsign);
		setRepeater1Callsign(repeater1Callsign);
		setRepeater2Callsign(repeater2Callsign);
		setGatewayIP(gatewayIP);
		setQueryResult(queryResult);

		getTimeKeeper().setTimeoutMillis(timelimitMillis);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		StringBuffer sb = new StringBuffer();

		for(int count = 0; count < indentLevel; count++) {sb.append(' ');}

		sb.append("[");
		sb.append(this.getClass().getSimpleName());
		sb.append("]");

		sb.append("CreateTime:");
		sb.append(FormatUtil.dateFormat(getCreateTime()));
		sb.append("/");
		sb.append("UR:");
		sb.append(getYourCallsign());
		sb.append("/");
		sb.append("RPT1:");
		sb.append(getRepeater1Callsign());
		sb.append("/");
		sb.append("RPT2:");
		sb.append(getRepeater2Callsign());
		sb.append("/");
		sb.append("GWIP:");
		sb.append(getGatewayIP());
		sb.append("/");
		sb.append("QueryResult:");
		sb.append(getQueryResult());

		return sb.toString();
	}

}
