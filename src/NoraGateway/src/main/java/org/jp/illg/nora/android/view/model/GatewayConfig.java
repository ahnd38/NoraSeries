package org.jp.illg.nora.android.view.model;

import android.os.Bundle;

import org.jp.illg.dstar.DStarDefines;
import org.parceler.Parcel;
import org.parceler.Parcels;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Parcel(Parcel.Serialization.BEAN)
public class GatewayConfig {

	private String gatewayCallsign = "";

	private boolean enableJapanTrust = true;

	private String japanTrustServerAddress = DStarDefines.JpTrustServerAddress;

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
