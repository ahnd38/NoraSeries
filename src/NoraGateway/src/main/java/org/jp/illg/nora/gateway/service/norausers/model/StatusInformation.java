package org.jp.illg.nora.gateway.service.norausers.model;

import java.net.InetSocketAddress;

import org.jp.illg.dstar.model.defines.AccessScope;

import lombok.Data;

@Data
public class StatusInformation {

	private String id;

	private long uptimeSeconds;

	private String applicationName;

	private String applicationVersion;

	private String applicationRunningOS;

	private String builderName;
	private String builderEMail;
	private String buildTime;

	private String gitBranchName;
	private String gitCommitID;
	private String gitCommitTime;
	private String gitCommitterName;
	private String gitCommitterEMail;
	private boolean gitDirty;

	private GatewayInformation gatewayInformation;

	private RepeaterInformation[] repeaterInformation;

	private ReflectorInformation[] reflectorInformation;

	private String scope;

	private InetSocketAddress remoteHost;

	public StatusInformation() {
		super();

		id = "";
		uptimeSeconds = 0;
		applicationName = "";
		applicationVersion = "";
		applicationRunningOS = "";

		builderName = "";
		builderEMail = "";
		buildTime = "";

		gitBranchName = "";
		gitCommitID = "";
		gitCommitTime = "";
		gitCommitterName = "";
		gitCommitterEMail = "";
		gitDirty = false;

		gatewayInformation = null;
		repeaterInformation = null;
		reflectorInformation = null;

		scope = AccessScope.Unknown.getTypeName();
		remoteHost = null;
	}

}
