package org.jp.illg.dstar.jarl.xchange.addon.extconn.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.config.ReflectorLinkManagerProperties;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.AuthType;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class ExternalConnectorProperties {

	private final Lock locker;

	@Setter
	@Getter
	private String gatewayCallsign;

	@Setter
	@Getter
	private String scope;

	@Getter
	@Setter
	private double latitude;

	@Getter
	@Setter
	private double longitude;

	@Getter
	@Setter
	private String description1;

	@Getter
	@Setter
	private String description2;

	@Getter
	@Setter
	private String url;

	@Getter
	@Setter
	private String name;

	@Getter
	@Setter
	private String location;

	@Getter
	@Setter
	private String dashboardUrl;

	@Getter
	@Setter
	private String announceVoice;

	@Getter
	@Setter
	private int localPort;
	public static final int localPortDefault = 50100;

	@Getter
	@Setter
	private AuthType authMode;
	public static final AuthType authModeDefault = AuthType.INCOMING;

	@Getter
	@Setter
	private boolean enableAuthOutgoingLink;
	public static final boolean enableAuthOutgoingLinkDefault = false;

	@Setter
	@Getter
	private String hostFileOutputPath;
	public static String hostFileOutputPathDefault = "./hosts.output.txt";

	private Map<RoutingServiceTypes, RoutingServiceProperties> routingServices;

	private Map<String, ReflectorProperties> reflectors;

	private Map<String, ExternalConnectorRepeaterProperties> repeaters;

	private ReflectorLinkManagerProperties reflectorLinkManager;

	@Setter
	@Getter
	private String hostsFile;
	private static final String hostsFileDefault = "config/hosts.txt";

	public ExternalConnectorProperties() {
		super();

		locker = new ReentrantLock();

		clear();
	}

	public Map<String, ReflectorProperties> getReflectors() {
		locker.lock();
		try {
			if(reflectors != null)
				return reflectors;
			else
				return (reflectors = new ConcurrentHashMap<>());
		}finally {
			locker.unlock();
		}
	}

	public Map<String, ExternalConnectorRepeaterProperties> getRepeaters() {
		locker.lock();
		try {
			if(repeaters != null)
				return repeaters;
			else
				return (repeaters = new ConcurrentHashMap<>());
		}finally {
			locker.unlock();
		}
	}

	public Map<RoutingServiceTypes, RoutingServiceProperties> getRoutingServices() {
		locker.lock();
		try {
			if(routingServices != null)
				return routingServices;
			else
				return (routingServices = new ConcurrentHashMap<>());
		}finally {
			locker.unlock();
		}
	}

	public ReflectorLinkManagerProperties getReflectorLinkManager() {
		locker.lock();
		try {
			if(reflectorLinkManager != null)
				return reflectorLinkManager;
			else
				return (reflectorLinkManager = new ReflectorLinkManagerProperties());
		}finally {
			locker.unlock();
		}
	}

	public synchronized void clear(){
		locker.lock();
		try {
			setGatewayCallsign(DSTARDefines.EmptyLongCallsign);
			setScope(AccessScope.Unknown.getTypeName());
			setLocalPort(localPortDefault);
			setHostFileOutputPath(hostFileOutputPathDefault);
			setHostsFile(hostsFileDefault);
			setAuthMode(AuthType.INCOMING);
			setEnableAuthOutgoingLink(enableAuthOutgoingLinkDefault);

			getRoutingServices().clear();
			getReflectors().clear();
			getRepeaters().clear();

		}finally {
			locker.unlock();
		}
	}
}
