package org.jp.illg.dstar.model.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jp.illg.dstar.DSTARDefines;

import lombok.Getter;
import lombok.Setter;

public class RepeaterProperties {

	@Getter
	@Setter
	private boolean enable;

	@Getter
	@Setter
	private String type;

	@Getter
	@Setter
	private String callsign;

	@Getter
	@Setter
	private String defaultRoutingService;

	@Getter
	@Setter
	private String routingServiceFixed;

	@Getter
	@Setter
	private boolean allowDIRECT;

	private List<String> directMyCallsigns;

	@Getter
	@Setter
	private boolean useRoutingService;

	@Getter
	@Setter
	private boolean allowIncomingConnection;

	@Getter
	@Setter
	private boolean allowOutgoingConnection;

	@Getter
	@Setter
	private boolean autoDisconnectFromReflectorOnTxToG2Route;

	@Getter
	@Setter
	private double frequency;

	@Getter
	@Setter
	private double frequencyOffset;

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
	private double range;

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
	private String scope;

	@Getter
	@Setter
	private int autoDisconnectFromReflectorOutgoingUnusedMinutes;


	private Properties configurationProperties;

	private List<CallsignEntry> accessAllowList;

	private List<ModemProperties> modemProperties;

	public RepeaterProperties() {
		super();

		setType("");
		setCallsign(DSTARDefines.EmptyLongCallsign);
		setDefaultRoutingService("");
		setRoutingServiceFixed("");
		setAllowDIRECT(false);
		setUseRoutingService(true);
		setAutoDisconnectFromReflectorOnTxToG2Route(true);
		setAutoDisconnectFromReflectorOutgoingUnusedMinutes(0);

		modemProperties = new ArrayList<>();
	}

	public boolean addModemProperties(ModemProperties modem) {
		if(modem == null) {return false;}

		synchronized(modemProperties) {
			return modemProperties.add(modem);
		}
	}

	public List<ModemProperties> getModemProperties() {
		synchronized(modemProperties) {
			return new ArrayList<ModemProperties>(modemProperties);
		}
	}

	public synchronized Properties getConfigurationProperties() {
		if(this.configurationProperties != null)
			return configurationProperties;
		else
			return (this.configurationProperties = new Properties());
	}

	public synchronized List<CallsignEntry> getAccessAllowList() {
		if(accessAllowList != null)
			return accessAllowList;
		else
			return accessAllowList = new ArrayList<>();
	}

	public synchronized List<String> getDirectMyCallsigns() {
		if(directMyCallsigns != null)
			return directMyCallsigns;
		else
			return directMyCallsigns = new ArrayList<>();
	}

}
