package org.jp.illg.nora.android.view.model;

import org.jp.illg.dstar.DSTARDefines;
import org.parceler.Parcel;

import lombok.Data;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class GatewayConfig {

	private String gatewayCallsign = "";

	private boolean enableJapanTrust = true;

	private String japanTrustServerAddress = DSTARDefines.JpTrustServerAddress;

	private boolean enableIrcDDB = false;

	private String ircDDBServerAddress = "";

	private String ircDDBUserName = "";

	private String ircDDBPassword = "";

	private boolean ircDDBAnonymous = false;

	private boolean enableDExtra = false;

	private boolean enableDPlus = false;

	private boolean enableJARLMultiForward = false;

	private boolean enableDCS = true;

	private boolean enableRemoteControl = false;

	private boolean useProxyGateway = false;

	private String proxyGatewayAddress = "140.227.227.11";

	private int proxyGatewayPort = 56513;
}
