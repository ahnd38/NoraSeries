package org.jp.illg.dstar.model;

import java.net.InetAddress;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

public class GlobalIPInfo {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long createTime;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private InetAddress globalIP;


	private GlobalIPInfo() {
		super();

		setCreateTime(System.currentTimeMillis());
	}

	public GlobalIPInfo(@NonNull InetAddress globalIP) {
		this();

		setGlobalIP(globalIP);
	}
}
