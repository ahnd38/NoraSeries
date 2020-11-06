package org.jp.illg.dstar.model.config;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.defines.DSTARProtocol;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class ReflectorLinkManagerProperties {

	@Getter
	private Lock locker;

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private AutoConnectProperties autoConnectProperties;
	
	@Getter
	private Map<String, ReflectorBlackListEntry> reflectorBlackList;
	
	@Getter
	private List<DSTARProtocol> defaultReflectorPreferredProtocols;
	
	@Getter
	private Map<String, DSTARProtocol> reflectorPreferredProtocols;

	public ReflectorLinkManagerProperties() {
		super();
		
		locker = new ReentrantLock();

		setEnable(true);
		setAutoConnectProperties(new AutoConnectProperties());
		
		reflectorBlackList = new ConcurrentHashMap<>(8);
		
		defaultReflectorPreferredProtocols = new LinkedList<>();
		reflectorPreferredProtocols = new ConcurrentHashMap<>();
	}

}
