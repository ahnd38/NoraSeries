package org.jp.illg.dstar.model.config;

import java.util.HashMap;
import java.util.Map;

import org.jp.illg.dstar.g123.G123CommunicationService;
import org.jp.illg.dstar.model.defines.AccessScope;
import org.jp.illg.dstar.model.defines.VoiceCharactors;

import lombok.Getter;
import lombok.Setter;

public class GatewayProperties {

	@Setter
	@Getter
	private String callsign;

	@Setter
	@Getter
	private int port;

	@Setter
	@Getter
	private int g2protocolVersion;
	public static final int g2ProtocolVersionDefault = G123CommunicationService.protocolVersionDefault;
	public static final int g2ProtocolVersionMin = G123CommunicationService.protocolVersionMin;
	public static final int g2ProtocolVersionMax = G123CommunicationService.protocolVersionMax;

	@Setter
	@Getter
	private boolean useProxyGateway;
	private static final boolean useProxyGatewayDefault = false;

	@Setter
	@Getter
	private String proxyGatewayAddress;
	private static final String proxyGatewayAddressDefault = "";

	@Setter
	@Getter
	private int proxyPort;
	private static final int proxyPortDefault = 30001;

	@Setter
	@Getter
	private String hostFileOutputPath;
	public static String hostFileOutputPathDefault = "./hosts.output.txt";

	@Getter
	@Setter
	private double latitude;

	@Getter
	@Setter
	private double longitude;

	@Getter
	@Setter
	private double agl;

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
	private String scope;

	private Map<String, RoutingServiceProperties> routingServices;

	private Map<String, ReflectorProperties> reflectors;

	private RemoteControlProperties remoteControlService;

	private ReflectorLinkManagerProperties reflectorLinkManager;

	@Setter
	@Getter
	private String hostsFile;

	@Setter
	@Getter
	private boolean disableHeardAtReflector;
	public static final boolean disableHeardAtReflectorDefault = true;

	@Setter
	@Getter
	private String announceVoice;
	public static final String announceVoiceDefault = VoiceCharactors.KizunaAkari.getCharactorName();

	@Setter
	@Getter
	private boolean disableWakeupAnnounce;
	public static final boolean disableWakeupAnnounceDefault = false;

	@Getter
	@Setter
	private boolean autoReplaceCQFromReflectorLinkCommand;
	private static final boolean autoReplaceCQFromReflectorLinkCommandDefault = false;

	public GatewayProperties() {
		super();

		clear();
	}

	/**
	 * @return routingServices
	 */
	public synchronized Map<String, RoutingServiceProperties> getRoutingServices() {
		if(this.routingServices != null)
			return routingServices;
		else
			return (this.routingServices = new HashMap<String, RoutingServiceProperties>());
	}

	/**
	 * @return reflectors
	 */
	public synchronized Map<String, ReflectorProperties> getReflectors() {
		if(this.reflectors != null)
			return this.reflectors;
		else
			return (this.reflectors = new HashMap<String, ReflectorProperties>());
	}

	public synchronized RemoteControlProperties getRemoteControlService() {
		if(remoteControlService != null)
			return remoteControlService;
		else
			return (remoteControlService = new RemoteControlProperties());
	}

	public synchronized ReflectorLinkManagerProperties getReflectorLinkManager() {
		if(reflectorLinkManager != null)
			return reflectorLinkManager;
		else
			return (reflectorLinkManager = new ReflectorLinkManagerProperties());
	}

	/**
	 *
	 */
	public synchronized void clear(){
		setCallsign("");
		setPort(0);
		setG2protocolVersion(g2ProtocolVersionDefault);
		getRoutingServices().clear();
		getReflectors().clear();

		setDisableHeardAtReflector(disableHeardAtReflectorDefault);
		setAutoReplaceCQFromReflectorLinkCommand(autoReplaceCQFromReflectorLinkCommandDefault);
		setAnnounceVoice(announceVoiceDefault);
		setDisableWakeupAnnounce(disableWakeupAnnounceDefault);
		setHostFileOutputPath(hostFileOutputPathDefault);
		setUseProxyGateway(useProxyGatewayDefault);
		setProxyGatewayAddress(proxyGatewayAddressDefault);
		setProxyPort(proxyPortDefault);

		setLatitude(0.0d);
		setLongitude(0.0d);
		setAgl(0.0d);
		setDescription1("");
		setDescription2("");
		setUrl("");
		setScope(AccessScope.Unknown.getTypeName());
		setDashboardUrl("");
	}

}
