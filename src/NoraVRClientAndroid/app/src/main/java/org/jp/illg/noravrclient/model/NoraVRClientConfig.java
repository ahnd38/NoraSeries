package org.jp.illg.noravrclient.model;

import lombok.Getter;
import lombok.Setter;

public class NoraVRClientConfig {
	
	public NoraVRClientConfig(){
		super();
		
		setServerAddress("");
		setServerPort(52161);
		setLoginCallsign("");
		setMyCallsign("");
		setMyCallsignShort("");
		setYourCallsign("");
		setYourCallsignCQ(false);
		setCodecType(null);
		setUseGateway(false);
		setTransmitCallsignHistory(null);
		setDisableDisplaySleep(false);
		setEnableMicAGC(true);
		setMicGain(0.0D);
		setEnableTransmitShortMessage(false);
		setShortMessage("");
		setEnableTransmitGPS(false);
		setEnablePlayBeepReceiveStart(true);
		setEnablePlayBeepReceiveEnd(true);
		setEnableGPSLocationPopup(true);
	}
	
	@Getter
	@Setter
	private String serverAddress;
	
	@Getter
	@Setter
	private int serverPort;
	
	@Getter
	@Setter
	private String loginCallsign;
	
	@Getter
	@Setter
	private String loginPassword;
	
	@Getter
	@Setter
	private String myCallsign;
	
	@Getter
	@Setter
	private String myCallsignShort;
	
	@Getter
	@Setter
	private String yourCallsign;
	
	@Getter
	@Setter
	private boolean yourCallsignCQ;
	
	@Getter
	@Setter
	private String codecType;
	
	@Getter
	@Setter
	private boolean useGateway;
	
	@Getter
	@Setter
	private String[] transmitCallsignHistory;
	
	@Getter
	@Setter
	private boolean disableDisplaySleep;
	
	@Getter
	@Setter
	private boolean enableMicAGC;
	
	@Getter
	@Setter
	private double micGain;
	
	@Getter
	@Setter
	private boolean enableTransmitShortMessage;
	
	@Getter
	@Setter
	private String shortMessage;
	
	@Getter
	@Setter
	private boolean enableTransmitGPS;
	
	@Getter
	@Setter
	private boolean enablePlayBeepReceiveStart;
	
	@Getter
	@Setter
	private boolean enableGPSLocationPopup;
	
	@Getter
	@Setter
	private boolean enablePlayBeepReceiveEnd;
	
	@Getter
	@Setter
	private String externalPTTType;
	
	@Getter
	@Setter
	private int externalPTTKeycode;
	
	@Getter
	@Setter
	private boolean touchPTTTransmit;
	
	@Getter
	@Setter
	private boolean pttToggleMode;
}
