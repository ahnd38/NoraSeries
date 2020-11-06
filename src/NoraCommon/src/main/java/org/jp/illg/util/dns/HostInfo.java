package org.jp.illg.util.dns;

import java.net.InetAddress;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class HostInfo {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetAddress hostAddress;

	@Getter
	@Setter
	private int point;


	public HostInfo(InetAddress hostAddress) {
		super();

		setHostAddress(hostAddress);

		setPoint(0);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		String indent = "";
		for(int i = 0; i < indentLevel; i++) {indent += " ";}

		StringBuilder sb = new StringBuilder(indent);
		sb.append("[HostAddress]:");
		sb.append(getHostAddress());

		sb.append("/");

		sb.append("[Point]:");
		sb.append(getPoint());

		return sb.toString();
	}
}
