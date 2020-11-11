package org.jp.illg.noravrclient;

public class NoraVRClientDefine {
	
	public static final String EmptyLongCallsign = "        ";
	public static final String EmptyShortCallsign = "    ";
	
	public static final String CQCQCQ = "CQCQCQ  ";
	public static final String DIRECT = "DIRECT  ";
	
//	public static final int MSG_NOTIFY_SERVICE_STARTED = 10;
	public static final int MSG_REQUEST_CONFIG_SET = 20;
	public static final int MSG_RESPONSE_CONFIG_SET = 21;
	
//	public static final int MSG_REQUEST_CONNECT = 100;
	public static final int MSG_RESPONSE_CONNECT = 101;
	public static final int MSG_REQUEST_DISCONNECT = 200;
	public static final int MSG_RESPONSE_DISCONNECT = 201;
	
	public static final int MSG_REQUEST_CONNECTIONSTATE_GET = 500;
	public static final int MSG_RESPONSE_CONNECTIONSTATE_GET = 501;
	public static final int MSG_NOTIFY_CONNECTIONSTATE_CHANGE = 550;
	
	public static final int MSG_REQUEST_CHANGEECHOBACK = 800;
	public static final int MSG_RESPONSE_CHANGEECHOBACK = 801;
	
	public static final int MSG_NOTIFY_MICVOICE = 1000;
	public static final int MSG_NOTIFY_RECEIVEVOICE = 1001;
	
	public static final int MSG_NOTIFY_LINKEDREFLECTOR_CHANGE = 1500;
	
	public static final int MSG_REQUEST_LASTHEARDLIST = 1700;
	public static final int MSG_RESPONSE_LASTHEARDLIST = 1701;
	public static final int MSG_NOTIFY_LASTHEARDLIST_CHANGE = 1702;
	public static final int MSG_REQUEST_ADDHEARD = 1750;
	public static final int MSG_RESPONSE_ADDHEARD = 1751;
	
	public static final int MSG_REQUEST_TRANSMITVOICE_START = 2000;
	public static final int MSG_RESPONSE_TRANSMITVOICE_START = 2001;
	public static final int MSG_REQUEST_TRANSMITVOICE_END = 2100;
	public static final int MSG_RESPONSE_TRANSMITVOICE_END = 2101;
	public static final int MSG_NOTIFY_TRANSMITVOICE_TIMEOUT = 2200;
	
	
	private static final String ID_PREFIX = BuildConfig.APPLICATION_ID + ".";
	public static final String ID_SERVERADDRESS = ID_PREFIX + "ServerAddress";
	public static final String ID_SERVERPORT = ID_PREFIX + "ServerPort";
	public static final String ID_LOGINUSER = ID_PREFIX + "LoginUser";
	public static final String ID_LOGINPASSWORD = ID_PREFIX + "LoginPassword";
	public static final String ID_CODECTYPE = ID_PREFIX + "CodecType";
	public static final String ID_MYCALLSIGN_LONG = ID_PREFIX + "MyCallsignLong";
	public static final String ID_MYCALLSIGN_SHORT = ID_PREFIX + "MyCallsignShort";
	public static final String ID_YOURCALLSIGN = ID_PREFIX + "YourCallsign";
	public static final String ID_RPT1CALLSIGN = ID_PREFIX + "Repeater1Callsign";
	public static final String ID_RPT2CALLSIGN = ID_PREFIX + "Repeater2Callsign";
	public static final String ID_FRAMEID = ID_PREFIX + "FrameID";
	public static final String ID_FRAMESTART = ID_PREFIX + "FrameStart";
	public static final String ID_FRAMEEND = ID_PREFIX + "FrameEnd";
	public static final String ID_REFLECTORCALLSIGN = ID_PREFIX + "ReflectorCallsign";
	public static final String ID_USE_GATEWAY = ID_PREFIX + "UseGateway";
	public static final String ID_CONNECTION_STATE = ID_PREFIX + "ConnectionState";
	public static final String ID_CONNECTION_REASON = ID_PREFIX + "ConnectionReason";
	public static final String ID_MICVOICE = ID_PREFIX + "MicVoice";
	public static final String ID_RECEIVEVOICE = ID_PREFIX + "ReceiveVoice";
	public static final String ID_ECHOBACK = ID_PREFIX + "Echoback";
	public static final String ID_DV_FLAGS = ID_PREFIX + "DvFlags";
	public static final String ID_HEARDENTRY = ID_PREFIX + "HeardEntry";
	public static final String ID_HEARDENTRYLIST = ID_PREFIX + "HeardEntryList";
	public static final String ID_ENABLE_MICAGC = ID_PREFIX + "EnableMicAGC";
	public static final String ID_MICGAIN = ID_PREFIX + "MicGain";
	public static final String ID_ENABLETRANSMITSHORTMESSAGE = ID_PREFIX + "EnableTransmitShortMessage";
	public static final String ID_SHORTMESSAGE = ID_PREFIX + "ShortMessage";
	public static final String ID_ENABLETRANSMITGPS = ID_PREFIX + "EnableTransmitGPS";
	public static final String ID_ENABLEBEEPONRECEIVESTART = ID_PREFIX + "EnableBeepOnReceiveStart";
	public static final String ID_ENABLEBEEPONRECEIVEEND = ID_PREFIX + "EnableBeepOnReceiveEnd";
	public static final String ID_DISABLE_AUDIORECORD = ID_PREFIX + "DisableAudioRecord";
	public static final String ID_LATITUDE = ID_PREFIX + "Latitude";
	public static final String ID_LONGITUDE = ID_PREFIX + "Longitude";
	public static final String ID_GPSLOCATION_RECEIVED = ID_PREFIX + "GPSLocationReceived";
	public static final String ID_DISABLE_DISPLAYSLEEP = ID_PREFIX + "DisableDisplaySleep";
	
	public static final int CONNECTIONSTATE_SUCCESS = 1;
	public static final int CONNECTIONSTATE_UNKNOWN = 0;
	public static final int CONNECTIONSTATE_LOGINFAILED = -1;
	public static final int CONNECTIONSTATE_CONNECTIONFAILED = -2;
}
