package org.jp.illg.noravrclient;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Criteria;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;


import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.util.DataSegmentDecoder;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.dstar.util.aprs.APRSMessageDecoder;
import org.jp.illg.nora.vr.NoraVRAccessLog;
import org.jp.illg.nora.vr.NoraVRClient;
import org.jp.illg.nora.vr.NoraVREventListener;
import org.jp.illg.nora.vr.NoraVRUser;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.noravrclient.util.BluetoothScoController;
import org.jp.illg.dstar.util.aprs.NMEA2DecLatLonUtil;
import org.parceler.Parcels;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static java.lang.Math.max;
import static org.jp.illg.noravrclient.NoraVRClientDefine.*;
import static org.jp.illg.noravrclient.NoraVRClientUtil.*;

@Slf4j
public class NoraVRClientService extends Service {
	

	@Getter
	@Setter
	public static boolean serviceStarted;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String serverAddress;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private int serverPort;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String loginUser;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String loginPassword;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private NoraVRCodecType codecType;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String myCallsign;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String myCallsignShort;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String yourCallsign;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean useGateway;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableMicAGC;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private double micGain;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableMicNoiseSuppressor;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableTransmitShortMessage;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String shortMessage;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enableTransmitGPS;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enablePlayBeepReceiveStart;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean enablePlayBeepReceiveEnd;
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private boolean disableAudioRecord;
	
	private NotificationManager notificationManager;
	private Notification notification;
	private final int notificationId =
			new Random(System.currentTimeMillis() ^ 0x365de).nextInt(Integer.MAX_VALUE);
	
	private int connectionState;
	private String connectionReason;
	
	private final NoraVRClient noraVRClient;
	
	private AudioRecord recorder;
//	private int recorderFrameSize;
	private short[] recorderBuffer;
	private final NewDataSegmentEncoder transferSlowdataEncoder;
	private boolean requestTransferEnd;
	private Header transferringHeader;
	
	private VoiceTransfer recorderThread;
	
	private AudioTrack speaker;
//	private int speakerFrameSize;
	
	private final DataSegmentDecoder receiverSlowdataDecoder;
	private VoiceReceiver speakerThread;
	
	private NoiseSuppressor noiseSuppressor;
	private AcousticEchoCanceler echoCanceler;
	private AutomaticGainControl automaticGainControl;
	
	private BluetoothScoController bluetoothController;
	
	private AudioManager audioManager;
	
	private boolean usingHeadset;
	
	private AudioFocusRequest audioFocusRequest;
	
	private String linkedReflectorCallsign;
	
	@TargetApi(Build.VERSION_CODES.N)
	private final OnNmeaMessageListener nmeaMessageListenerN;
	private final GpsStatus.NmeaListener nmeaMessageListener;
	private LocationManager locationManager;
	private String nmeaPositionMessage;
	
	private String receivedShortMessage;
	private double receivedGPSPositionLatitude;
	private double receivedGPSPositionLongitude;
	private boolean receivedGPSLocation;
	
	static {
		setServiceStarted(false);
	}
	
	public NoraVRClientService(){
		super();
		
		intentFilter = new IntentFilter();
		intentFilter.addAction(String.valueOf(MSG_REQUEST_DISCONNECT));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_TRANSMITVOICE_START));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_TRANSMITVOICE_END));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_CONNECTIONSTATE_GET));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_CHANGEECHOBACK));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_CONFIG_SET));
		
		noraVRClient = new NoraVRClient(
			noraVREventListener, false,
			org.jp.illg.noravrclient.NoraVRClient.getApplicationInformation().getApplicationName(),
			org.jp.illg.noravrclient.NoraVRClient.getApplicationInformation().getApplicationVersion()
		);
		
		connectionState = 0;
		
		transferSlowdataEncoder = new NewDataSegmentEncoder();
		receiverSlowdataDecoder = new DataSegmentDecoder();
		
		usingHeadset = false;
		
		audioFocusRequest = null;
		
		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		
		setEnableMicAGC(true);
		setMicGain(0.0D);
		setEnableMicNoiseSuppressor(true);
		
		setEnableTransmitShortMessage(false);
		setShortMessage("");
		setEnableTransmitGPS(false);
		setEnablePlayBeepReceiveStart(true);
		setEnablePlayBeepReceiveEnd(true);
		setDisableAudioRecord(false);
		
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
			nmeaMessageListener =
					new GpsStatus.NmeaListener() {
						@Override
						public void onNmeaReceived(long timestamp, String message) {
							onNmeaMessageReceived(message, timestamp);
						}
					};
			nmeaMessageListenerN = null;
		} else {
			nmeaMessageListenerN =
					new OnNmeaMessageListener() {
						@Override
						public void onNmeaMessage(String message, long timestamp) {
							onNmeaMessageReceived(message, timestamp);
						}
					};
			nmeaMessageListener = null;
		}
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onStartCommand()");
		
		String channelId =
				Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
						createNotificationChannel() : "";
		
		Intent activityIntent = new Intent(this, MainActivity.class);
		PendingIntent pendingIntent =
				PendingIntent.getActivity(this, 0, activityIntent, 0);
		
		NotificationCompat.Builder notificationBuilder =
				new NotificationCompat.Builder(this, channelId);
		
		notification =
				notificationBuilder
						.setContentTitle("NoraVRClient")
						.setContentText("Running...")
						.setSubText("")
						.setContentIntent(pendingIntent)
						.setSmallIcon(R.drawable.ic_swap_vert)
						.setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_swap_vert))
						.setOngoing(true)
						.build();
		
		startForeground(notificationId, notification);
		
		setConfig(intent);
		
		createAudioRecord(isEnableMicAGC());
		
		createAudioTrack(false);
		
		bluetoothController.start();
		
		startLocationService();
		
		connect(intent);
		
		return Service.START_NOT_STICKY;
	}
	
	@Override
	public void onCreate(){
		setServiceStarted(true);
		
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onCreate()");
		
		getLBM(getApplicationContext()).registerReceiver(broadcastReceiver, intentFilter);
		
		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		
		audioManager = (AudioManager) getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
		locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);
		
		bluetoothController = new BluetoothScoController(getApplicationContext(), bluetoothEventListener);
		

	}

	@Override
	public IBinder onBind(Intent i) {
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onBind()");
		
		return null;
	}

	
	@Override
	public void onRebind(Intent intent)
	{
		log.trace(this.getClass().getSimpleName() + ".onRebind()");
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onUnbind()");
		
		notificationManager.cancelAll();
		
		return true;
	}
	
	@Override
	public void onDestroy(){
		setServiceStarted(false);
		
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onDestroy()");
		
		notificationManager.cancel(notificationId);
		
		getLBM(getApplicationContext()).unregisterReceiver(broadcastReceiver);
		
		if(recorderThread != null && recorderThread.isRunning())
			recorderThread.stopTransfer();
		
		stopAudioTrack();
		
		stopAudioRecord();
		
		bluetoothController.stop();
		
		stopLocationService();
	}
	
	@Override
	public void onTaskRemoved(Intent rootIntent) {
		if(log.isTraceEnabled())
			log.info(this.getClass().getSimpleName() + ".onTaskRemoved()");
		
		super.onTaskRemoved(rootIntent);
	}
	
	private final BroadcastReceiver broadcastReceiver =
			new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					switch (Integer.valueOf(intent.getAction())){
							
						case MSG_REQUEST_DISCONNECT:
							onReceiveMsgRequestDisconnect(intent);
							break;
							
						case MSG_REQUEST_TRANSMITVOICE_START:
							onReceiveMsgRequestTransmitStart(intent);
							break;
							
						case MSG_REQUEST_TRANSMITVOICE_END:
							onReceiveMsgRequestTransmitEnd(intent);
							break;
							
						case MSG_REQUEST_CONNECTIONSTATE_GET:
							onReceiveMsgConnectionStateGet(intent);
							break;
							
						case MSG_REQUEST_CHANGEECHOBACK:
							onReceiveMsgRequestChangeEchoback(intent);
							break;
							
						case MSG_REQUEST_CONFIG_SET:
							onReceiveMsgRequestConfigSet(intent);
							break;
						
						default:
							break;
					}
				}
			};
	private final IntentFilter intentFilter;
	
	private final LocationListener locationListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
		
		}
		
		@Override
		public void onStatusChanged(String s, int i, Bundle bundle) {
		
		}
		
		@Override
		public void onProviderEnabled(String s) {
		
		}
		
		@Override
		public void onProviderDisabled(String s) {
		
		}
	};
	
	private boolean startLocationService() {
		if(
				isEnableTransmitGPS() &&
				locationManager != null
		){
			final Criteria criteria = new Criteria();
			criteria.setAccuracy(Criteria.ACCURACY_LOW);
			criteria.setPowerRequirement(Criteria.POWER_LOW);
			criteria.setAltitudeRequired(false);
			criteria.setSpeedRequired(false);
			criteria.setBearingRequired(false);
			criteria.setCostAllowed(false);
			
			final String providerName = locationManager.getBestProvider(criteria, true);
			
			if(providerName != null) {
				if(log.isDebugEnabled())
					log.debug("GPS provider is " + providerName + ".");
				
				try {
					locationManager.requestLocationUpdates(
							providerName, 10000, 20, locationListener
					);
					if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
						locationManager.addNmeaListener(nmeaMessageListener);
					else
						locationManager.addNmeaListener(nmeaMessageListenerN);
					
					return true;
				}catch(SecurityException ex){
					log.warn("Could not use GPS location.", ex);
				}
			}
		}
		
		return false;
	}
	
	private void stopLocationService() {
		if(locationManager != null){
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
				locationManager.removeNmeaListener(nmeaMessageListener);
			} else {
				locationManager.removeNmeaListener(nmeaMessageListenerN);
			}
			locationManager.removeUpdates(locationListener);
		}
	}
	
	private void onNmeaMessageReceived(final String message, final long timestamp) {
		if(log.isTraceEnabled())
			log.trace("NMEA Message received.\n    " + message);
		
		if(message.startsWith("$GPRMC")) {
			String[] splitedMessage = message.split(",");
			if(splitedMessage.length > 3 && "A".equals(splitedMessage[2])) {
				if(log.isDebugEnabled())
					log.debug("GPS location changed.\n    " + message);
				
				nmeaPositionMessage = message;
			}
		}
	}
	
	private void connect(final Intent intent){
		noraVRClient.disconnect();
		
		final String serverAddress =
			intent.getStringExtra(ID_SERVERADDRESS);
		setServerAddress(serverAddress);
		
		final int serverPort =
			intent.getIntExtra(ID_SERVERPORT, -1);
		if(serverPort <= 0){return;}
		setServerPort(serverPort);
		
		final String loginUser =
				intent.getStringExtra(ID_LOGINUSER);
		setLoginUser(loginUser);
		
		final String loginPassword =
				intent.getStringExtra(ID_LOGINPASSWORD);
		setLoginPassword(loginPassword);
		
		final NoraVRCodecType codecType =
				NoraVRCodecType.getTypeByTypeName(
					intent.getStringExtra(NoraVRClientDefine.ID_CODECTYPE)
				);
		if(codecType == null){return;}
		setCodecType(codecType);
		
		noraVRClient.connect(
				loginUser, loginPassword,
				serverAddress, serverPort,
				codecType
		);
		
		final Intent msg =
				new Intent(String.valueOf(MSG_RESPONSE_CONNECT));
		sendMessageToHost(getApplicationContext(), msg);
	}
	
	private void onReceiveMsgRequestDisconnect(final Intent intent){
		noraVRClient.disconnect();
		
		final Intent msg =
				new Intent(String.valueOf(MSG_RESPONSE_DISCONNECT));
		sendMessageToHost(getApplicationContext(), msg);
		
		stopSelf();
	}
	
	private void onReceiveMsgRequestTransmitStart(final Intent intent){
		if(recorderThread != null && recorderThread.isRunning())
			return;
		
		final String myCallsign =
				NoraVRClientUtil.formatCallsignFullLength(
					intent.getStringExtra(ID_MYCALLSIGN_LONG)
				);
		setMyCallsign(myCallsign);
		
		final String myCallsignShort =
				NoraVRClientUtil.formatCallsignShortLength(
						intent.getStringExtra(ID_MYCALLSIGN_SHORT)
				);
		setMyCallsignShort(myCallsignShort);
		
		final String yourCallsign =
				NoraVRClientUtil.formatCallsignFullLength(
					intent.getStringExtra(ID_YOURCALLSIGN)
				);
		setYourCallsign(yourCallsign);
		
		final boolean useGateway =
				intent.getBooleanExtra(ID_USE_GATEWAY, false);
		setUseGateway(useGateway);

		transferringHeader = null;
		requestTransferEnd = false;
		recorder.startRecording();
		recorderThread = new VoiceTransfer();
		recorderThread.startTransfer();
		
		final Intent msg = new Intent(String.valueOf(MSG_RESPONSE_TRANSMITVOICE_START));
		sendMessageToHost(getApplicationContext(), msg);
	}
	
	private void onReceiveMsgRequestTransmitEnd(final Intent intent){
		if(recorder != null && recorder.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
			return;
		
		requestTransferEnd = true;
		
		final Intent msg = new Intent(String.valueOf(MSG_RESPONSE_TRANSMITVOICE_END));
		sendMessageToHost(getApplicationContext(), msg);
	}

	private void onReceiveMsgConnectionStateGet(final Intent intent){
		sendConnectionStateMessage(false);
	}
	
	private void onReceiveMsgRequestChangeEchoback(final Intent intent){
		final boolean echoback = intent.getBooleanExtra(ID_ECHOBACK, false);
		
		if(noraVRClient.isConnected()){
			noraVRClient.changeEcho(echoback);
		}

		final Intent msg = new Intent(String.valueOf(MSG_RESPONSE_CHANGEECHOBACK));
		sendMessageToHost(getApplicationContext(), msg);
	}
	
	private void onReceiveMsgRequestConfigSet(final Intent intent){
		setConfig(intent);
		
		final Intent response = new Intent(String.valueOf(MSG_RESPONSE_CONFIG_SET));
		sendMessageToHost(getApplicationContext(), response);
	}
	
	private void sendConnectionStateMessage(final boolean isNotify){
		final Intent msg =
				new Intent(
						isNotify ?
								String.valueOf(MSG_NOTIFY_CONNECTIONSTATE_CHANGE) :
								String.valueOf(MSG_RESPONSE_CONNECTIONSTATE_GET)
				);
		
		msg.putExtra(ID_CONNECTION_STATE, connectionState);
		msg.putExtra(ID_CONNECTION_REASON, connectionReason);
		msg.putExtra(ID_REFLECTORCALLSIGN, linkedReflectorCallsign);
		
		sendMessageToHost(getApplicationContext(), msg);
	}
	
	private final NoraVREventListener noraVREventListener = new NoraVREventListener() {
		@Override
		public boolean loginFailed(String reason) {
			connectionState = CONNECTIONSTATE_LOGINFAILED;
			connectionReason = reason != null ? reason : "?";
			
			sendConnectionStateMessage(true);
			
			return false;
		}
		
		@Override
		public void loginSuccess(final int protocolVersion) {
			connectionState = CONNECTIONSTATE_SUCCESS;
			connectionReason = "";
			
			sendConnectionStateMessage(true);
		}
		
		@Override
		public boolean connectionFailed(String reason) {
			connectionState = CONNECTIONSTATE_CONNECTIONFAILED;
			connectionReason = reason != null ? reason : "?";
			
			sendConnectionStateMessage(true);
			
			return true;
		}
		
		@Override
		public void configurationSet(NoraVRConfiguration configuration) {
		
		}
		
		@Override
		public void receiveVoice() {
		
		}
		
		@Override
		public void reflectorLink(String linkedReflectorCallsign) {
			NoraVRClientService.this.linkedReflectorCallsign = linkedReflectorCallsign;
			
			final Intent msg = new Intent(String.valueOf(MSG_NOTIFY_LINKEDREFLECTOR_CHANGE));
			msg.putExtra(ID_REFLECTORCALLSIGN, linkedReflectorCallsign);
			
			sendMessageToHost(getApplicationContext(), msg);
		}
		
		@Override
		public void transmitTimeout(final int frameID) {
			final Intent msg = new Intent(String.valueOf(MSG_NOTIFY_TRANSMITVOICE_TIMEOUT));
			
			sendMessageToHost(getApplicationContext(), msg);
		}

		@Override
		public void repeaterInformation(
			String callsign,
			String name,
			String location,
			double frequencyMHz,
			double frequencyOffsetMHz,
			double serviceRangeKm,
			double agl,
			String url,
			String description1,
			String description2
		) {

		}

		@Override
		public void routingService(String routingServiceName) {

		}

		@Override
		public void userList(List<NoraVRUser> users) {

		}

		@Override
		public void accessLog(List<NoraVRAccessLog> logs) {

		}
	};
	
	private class VoiceTransfer extends Thread {
		
		VoiceTransfer(){
			super();
			
			this.setPriority(Thread.MAX_PRIORITY);
		}
		
		public boolean isRunning(){
			return this.isAlive();
		}
		
		public void startTransfer(){
			this.start();
		}
		
		public void stopTransfer(){
			if(!this.isAlive()){return;}
			
			this.interrupt();
			try {
				this.join();
			}catch(InterruptedException ex){}
		}
		
		@Override
		public void run() {
			
			while(!Thread.interrupted() && recorder != null) {
				recorder.read(recorderBuffer, 0, recorderBuffer.length);
				if(getMicGain() != 0.0D){
					for(int i = 0; i < recorderBuffer.length; i++){
						final double sample = recorderBuffer[i];
						final double boostedSample =
								sample * Math.pow(10, getMicGain() / (double)20);
						
						if(boostedSample < -32768)
							recorderBuffer[i] = -32768;
						else if(boostedSample > 32767)
							recorderBuffer[i] = 32767;
						else
							recorderBuffer[i] = (short)boostedSample;
					}
				}
				
				final ShortBuffer voice = ShortBuffer.wrap(recorderBuffer);
				final byte[] slowdata = new byte[3];
				
				if(transferringHeader == null) {
					transferSlowdataEncoder.reset();
					final Header header = new Header();
					header.setFlags(new byte[]{(byte)0x00, (byte)0x00, (byte)0x00});
					header.setRepeater2Callsign("DIRECT  ".toCharArray());
					header.setRepeater1Callsign("DIRECT  ".toCharArray());
					header.setYourCallsign(getYourCallsign().toCharArray());
					header.setMyCallsign(getMyCallsign().toCharArray());
					header.setMyCallsignAdd(getMyCallsignShort().toCharArray());
					transferSlowdataEncoder.setHeader(header);
					transferSlowdataEncoder.setEnableHeader(true);
					transferSlowdataEncoder.setShortMessage(getShortMessage());
					transferSlowdataEncoder.setEnableShortMessage(isEnableTransmitShortMessage());
					if(isEnableTransmitGPS() && nmeaPositionMessage != null){
						final String info = getMyCallsign() + "," + "BE  ";
						int checksum = 0x0;
						for(int i = 0; i < info.length(); i++) { checksum ^= (int)info.charAt(i); }
						final String aprsMessage =
								nmeaPositionMessage.replaceAll("[\r\n]", "") + "\r\n" +
								info + "*" + String.format(Locale.getDefault(), "%02X", checksum) +
								"             \r\n";
						transferSlowdataEncoder.setAprsMessage(aprsMessage);
						transferSlowdataEncoder.setEnableAprsMessage(true);
						
						if(log.isDebugEnabled())
							log.debug("APRS Message set to data segment encoder = " + aprsMessage);
					}
					
					transferSlowdataEncoder.encode(slowdata);
					
					final boolean isWriteSuccess =
							noraVRClient.writeVoice(
									isUseGateway(),
									getMyCallsign(),
									getMyCallsignShort(),
									getYourCallsign(),
									voice,
									slowdata,
									false
							);
					if(!isWriteSuccess){
						recorder.stop();
					}
					else{
						transferringHeader = header;

						log.info(
								String.format(
										Locale.getDefault(),
										"START Voice Transfer (MicGain=%+.1fdB)", getMicGain()
								)
						);
						
						requestAudioFocus(
								usingHeadset ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC,
								AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
						);
					}
				}
				else if(!requestTransferEnd){
					transferSlowdataEncoder.encode(slowdata);
					
					noraVRClient.writeVoice(
						isUseGateway(),
						getMyCallsign(),
						getMyCallsignShort(),
						getYourCallsign(),
						voice,
						slowdata,
						false
					);
				}
				else{
					transferSlowdataEncoder.encode(slowdata);
					
					noraVRClient.writeVoice(
						isUseGateway(),
						getMyCallsign(),
						getMyCallsignShort(),
						getYourCallsign(),
						voice,
						slowdata,
						true
					);
				}
				
				final Intent msg =
						new Intent(String.valueOf(MSG_NOTIFY_MICVOICE));
				msg.putExtra(ID_MICVOICE, Arrays.copyOf(recorderBuffer, recorderBuffer.length));
				sendMessageToHost(getApplicationContext(), msg);
				
				if(requestTransferEnd){
					log.info(String.format("END Voice Transfer"));
					requestTransferEnd = false;
					transferSlowdataEncoder.reset();
					transferringHeader = null;
					
					recorder.stop();
					
					releaseAudioFocus(audioFocusRequest);
					
					break;
				}
			}
		}
	}
	
	private class VoiceReceiver extends Thread {
		
		private long activityLastTime;
		
		public VoiceReceiver(){
			super();
			
			this.setPriority(Thread.MAX_PRIORITY);
			
			activityLastTime = System.nanoTime();
		}
		
		@Override
		public void run(){
			
			while(speaker != null){
				final NoraVRClient.NoraVRDownlinkAudioPacket<?> packet = noraVRClient.readVoice();
				if(packet != null) {
					if(packet.codec == NoraVRCodecType.AMBE){continue;}
					
					final ShortBuffer audioBuffer = (ShortBuffer)packet.audio;
					final short[] audio = new short[audioBuffer.remaining()];
					for(int i = 0; i < audio.length && audioBuffer.hasRemaining(); i++)
						audio[i] = audioBuffer.get();
					
					if(packet.frameStart) {
						receiverSlowdataDecoder.reset();
						receivedShortMessage = null;
						
						receivedGPSLocation = false;
						receivedGPSPositionLatitude = 0.0D;
						receivedGPSPositionLongitude = 0.0D;
						
						addLastHeardEntry(packet.myCallsignLong,
								packet.myCallsignShort,
								packet.yourCallsign,
								packet.repeater1Callsign,
								packet.repeater2Callsign
						);
						
						if(
								isEnablePlayBeepReceiveStart() &&
								(packet.flags[0] & 0x7) == 0x0
						){ playBeep(speaker);}
						
						speaker.write(audio, 0, audio.length);
					}
					else if(packet.frameEnd) {
						speaker.write(audio, 0, audio.length);
						
						if(
								isEnablePlayBeepReceiveEnd() &&
								(packet.flags[0] & 0x7) == 0x0
						){ playBeep(speaker);}
					}
					else{
						speaker.write(audio, 0, audio.length);
					}
					
					
					switch(receiverSlowdataDecoder.decode(packet.slowdata)) {
						case ShortMessage:
							receivedShortMessage =
									String.valueOf(receiverSlowdataDecoder.getShortMessage());
							
							if(log.isDebugEnabled())
								log.debug("Short Message received = " + receivedShortMessage);
							break;
							
						case APRS:
							if(log.isDebugEnabled())
								log.debug("APRS Message received = " + receiverSlowdataDecoder.getAprsMessage());
							
							APRSMessageDecoder.APRSMessageDecoderResult aprsResult = null;
							if(
								(aprsResult = APRSMessageDecoder.decodeDPRS(receiverSlowdataDecoder.getAprsMessage())) != null
							) {
								receivedGPSLocation = true;
								receivedGPSPositionLatitude = aprsResult.getLatitude();
								receivedGPSPositionLongitude = aprsResult.getLongitude();
							}
							
							break;
							
						default:
							break;
					}
					
					final Intent msg = new Intent(String.valueOf(MSG_NOTIFY_RECEIVEVOICE));
					msg.putExtra(ID_FRAMEID, packet.frameID);
					msg.putExtra(ID_RECEIVEVOICE, audio);
					msg.putExtra(ID_MYCALLSIGN_LONG, packet.myCallsignLong);
					msg.putExtra(ID_MYCALLSIGN_SHORT, packet.myCallsignShort);
					msg.putExtra(ID_YOURCALLSIGN, packet.yourCallsign);
					msg.putExtra(ID_RPT1CALLSIGN, packet.repeater1Callsign);
					msg.putExtra(ID_RPT2CALLSIGN, packet.repeater2Callsign);
					msg.putExtra(ID_DV_FLAGS, packet.flags);
					msg.putExtra(ID_FRAMESTART, packet.frameStart);
					msg.putExtra(ID_FRAMEEND, packet.frameEnd);
					msg.putExtra(
							ID_SHORTMESSAGE, receivedShortMessage != null ? receivedShortMessage : ""
					);
					msg.putExtra(ID_LATITUDE, receivedGPSPositionLatitude);
					msg.putExtra(ID_LONGITUDE, receivedGPSPositionLongitude);
					msg.putExtra(ID_GPSLOCATION_RECEIVED, receivedGPSLocation);
					sendMessageToHost(getApplicationContext(), msg);
					
					activityLastTime = System.nanoTime();
					
					if(speaker.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
						speaker.flush();
						speaker.play();
					}
				}
				
				if(
						(activityLastTime + TimeUnit.MILLISECONDS.toNanos(1000)) <= System.nanoTime()
				){
					if(speaker.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
						speaker.pause();
						speaker.flush();
					}
				}
				
				try {
					Thread.sleep(10);
				}catch(InterruptedException ex){break;}
			}

		}
	}
	
	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel()
	{
		final String channelId = UUID.randomUUID().toString();
		final String channelName = "NoraVRClient Background Service";
		final NotificationChannel chan = new NotificationChannel(
				channelId, channelName, NotificationManager.IMPORTANCE_NONE
		);
		chan.setLightColor(Color.BLUE);
		chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
		
		final NotificationManager service =
				(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		
		if(service != null)
			service.createNotificationChannel(chan);
		
		return channelId;
	}
	
	private final BluetoothScoController.BluetoothEventListener bluetoothEventListener =
			new BluetoothScoController.BluetoothEventListener() {
				@Override
				public void onHeadsetDisconnected() {
					log.info("Headset disconnected.");
					
//					createAudioRecord(isEnableMicAGC());
//					createAudioTrack(false);
					
					audioManager.setSpeakerphoneOn(true);
					
//					usingHeadset = false;
				}
				
				@Override
				public void onHeadsetConnected() {
					log.info("Headset connected.");
					
//					createAudioRecord(isEnableMicAGC());
//					createAudioTrack(true);
					
					audioManager.setSpeakerphoneOn(false);
					
//					usingHeadset = true;
				}
				
				@Override
				public void onScoAudioDisconnected() {
					log.info("Bluetooth headset disconnected.");
					
					createAudioRecord(isEnableMicAGC());
					createAudioTrack(false);
					
					audioManager.setSpeakerphoneOn(true);
					
					usingHeadset = false;
				}
				
				@Override
				public void onScoAudioConnected() {
					log.info("Bluetooth headset connected.");
					
					createAudioRecord(isEnableMicAGC());
					createAudioTrack(true);
					
					audioManager.setSpeakerphoneOn(false);
					
					usingHeadset = true;
				}
			};
	
	private boolean createAudioRecord(final boolean isEnableAGC) {
		stopAudioRecord();
		
		if(isDisableAudioRecord()){return true;}
		
		final int frameSize = 8000 / 50 * 2;
		
		final int transferMinBufferSize =
				AudioRecord.getMinBufferSize(
						8000,
						AudioFormat.CHANNEL_IN_MONO,
						AudioFormat.ENCODING_PCM_16BIT
				);
		
		final int recorderFrameSize = max(frameSize, transferMinBufferSize);
		
		recorderBuffer = new short[frameSize >> 1];
		
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
			recorder = new AudioRecord(
					MediaRecorder.AudioSource.VOICE_COMMUNICATION,
					8000,
					AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					recorderFrameSize
			);
		}
		else{
			recorder = new AudioRecord.Builder()
					.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
					.setAudioFormat(
						new AudioFormat.Builder()
						.setSampleRate(8000)
						.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
						.setChannelMask(AudioFormat.CHANNEL_IN_MONO)
						.build()
					)
					.setBufferSizeInBytes(recorderFrameSize)
					.build();
		}


		if(recorder.getState() != AudioRecord.STATE_INITIALIZED){
			log.error("Could not initialize AudioRecord.");
			recorder = null;
			return false;
		}
		
		noiseSuppressor =
				NoiseSuppressor.isAvailable() ?
						NoiseSuppressor.create(recorder.getAudioSessionId()) : null;
		if(noiseSuppressor != null) {
			if(noiseSuppressor.getEnabled() != isEnableMicNoiseSuppressor()) {
				noiseSuppressor.setEnabled(isEnableMicNoiseSuppressor());
			}
			if(noiseSuppressor.getEnabled() != isEnableMicNoiseSuppressor()){
				noiseSuppressor.release();
				noiseSuppressor = null;
				
				log.warn(
						"Could not " + (isEnableMicNoiseSuppressor() ? "enable" : "disable") + " NoiseSuppressor."
				);
			}
		}else {
			log.warn("Could not NoiseSuppressor instance.");
		}
		
		echoCanceler =
				AcousticEchoCanceler.isAvailable() ?
						AcousticEchoCanceler.create(recorder.getAudioSessionId()) : null;
		if(echoCanceler != null) {
			if(!echoCanceler.getEnabled()){echoCanceler.setEnabled(true);}
			if(!echoCanceler.getEnabled()){
				echoCanceler.release();
				echoCanceler = null;
				
				log.warn("Could not enabled AcousticEchoCanceler.");
			}
		}
		else{
			log.warn("Could not create AcousticEchoCanceler instance.");
		}

		automaticGainControl =
				AutomaticGainControl.isAvailable() ?
						AutomaticGainControl.create(recorder.getAudioSessionId()) : null;
		if(automaticGainControl != null){
			if(automaticGainControl.getEnabled() != isEnableAGC){automaticGainControl.setEnabled(isEnableAGC);}
			if(automaticGainControl.getEnabled() != isEnableAGC){
				automaticGainControl.release();
				automaticGainControl = null;
				
				log.warn("Could not " + (isEnableAGC ? "enabled" : "disabled") + " AutomaticGainControl.");
			}
		}
		else{
			log.warn("Could not create AutomaticGainControl instance.");
		}
		
		return true;
	}
	
	private void stopAudioRecord() {
		if(recorderThread != null && recorderThread.isRunning()) {
			recorderThread.interrupt();
			
			try {
				recorderThread.join();
			}catch(InterruptedException ex){}
			
			recorderThread = null;
		}
		
		if(noiseSuppressor != null) {
			noiseSuppressor.release();
			noiseSuppressor = null;
		}
		
		if(echoCanceler != null) {
			echoCanceler.release();
			echoCanceler = null;
		}
		
		if(automaticGainControl != null){
			automaticGainControl.release();
			automaticGainControl = null;
		}
		
		if(recorder != null) {
			if(recorder.getState() == AudioRecord.STATE_INITIALIZED) {
				recorder.stop();
				recorder.release();
			}
			recorder = null;
		}
		
	}
	
	private boolean createAudioTrack(final boolean isHeadset) {
		
		stopAudioTrack();
		
		final int frameSize = 8000 / 50 * 2;
		
		final int speakerMinBufferSize =
				AudioTrack.getMinBufferSize(
						8000,
						AudioFormat.CHANNEL_IN_STEREO,
						AudioFormat.ENCODING_PCM_16BIT
				);
		
		final int speakerFrameSize = max(frameSize * 25, speakerMinBufferSize);
		
		if(Build.VERSION.SDK_INT < Build.VERSION_CODES.M){
			speaker = new AudioTrack(
					isHeadset ? AudioManager.STREAM_VOICE_CALL : AudioManager.STREAM_MUSIC,
					8000,
					AudioFormat.CHANNEL_OUT_MONO,
					AudioFormat.ENCODING_PCM_16BIT,
					speakerFrameSize,
					AudioTrack.MODE_STREAM
			);
		}
		else{
			speaker = new AudioTrack.Builder()
					.setAudioAttributes(
							new AudioAttributes.Builder()
							.setUsage(isHeadset ?
									AudioAttributes.USAGE_VOICE_COMMUNICATION :
									AudioAttributes.USAGE_MEDIA
							)
							.setContentType(isHeadset ?
									AudioAttributes.CONTENT_TYPE_SPEECH :
									AudioAttributes.CONTENT_TYPE_MUSIC
							)
							.build()
					)
					.setAudioFormat(
							new AudioFormat.Builder()
							.setSampleRate(8000)
							.setEncoding(AudioFormat.ENCODING_PCM_16BIT)
							.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
							.build()
					)
					.setBufferSizeInBytes(speakerFrameSize)
					.build();
		}

		if(speaker.getState() != AudioTrack.STATE_INITIALIZED){
			log.error("Could not initialize AudioRecord.");
			speaker = null;
			return false;
		}
		
		speakerThread = new VoiceReceiver();
		speakerThread.setName(VoiceReceiver.class.getSimpleName() + "_" + speakerThread.getId());
		speakerThread.start();
		
		return true;
	}
	
	private void stopAudioTrack(){
		if(speakerThread != null && speakerThread.isAlive()) {
			speakerThread.interrupt();
			try {
				speakerThread.join();
			}catch(InterruptedException ex){}
		}
		
		if(speaker != null){
			speaker.release();
			speaker = null;
		}
	}
	
	private final AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener =
			new AudioManager.OnAudioFocusChangeListener() {
				@Override
				public void onAudioFocusChange(int i) {
				
				}
			};
	
	public boolean requestAudioFocus(
			int streamType, int audioFocusGain
	) {
		if(audioFocusRequest != null)
			releaseAudioFocus(audioFocusRequest);
		
		int r;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			audioFocusRequest =
					new AudioFocusRequest.Builder(audioFocusGain)
							.setAudioAttributes(
									new AudioAttributes.Builder()
											.setLegacyStreamType(streamType)
											.build())
							.setOnAudioFocusChangeListener(onAudioFocusChangeListener)
							.build();
			
			r = audioManager.requestAudioFocus(audioFocusRequest);
		} else {
			r = audioManager.requestAudioFocus(onAudioFocusChangeListener, streamType, audioFocusGain);
		}
		
		return r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}
	
	public void releaseAudioFocus(final AudioFocusRequest audioFocusRequest) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			if(audioFocusRequest != null)
				audioManager.abandonAudioFocusRequest(audioFocusRequest);
		}
		else{
			audioManager.abandonAudioFocus(onAudioFocusChangeListener);
		}
	}
	
	private void addLastHeardEntry(
			final String myCallsign, final String myCallsignShort,
			final String yourCallsign,
			final String repeater1Callsign, final String repeater2Callsign
	) {
		final HeardEntry newEntry = new HeardEntry();
		newEntry.heardTime = System.currentTimeMillis();
		newEntry.myCallsign = myCallsign;
		newEntry.myCallsignShort = myCallsignShort;
		newEntry.yourCallsign = yourCallsign;
		newEntry.repeater1Callsign = repeater1Callsign;
		newEntry.repeater2Callsign = repeater2Callsign;
		
		final Intent intent = new Intent(String.valueOf(MSG_REQUEST_ADDHEARD));
		intent.putExtra(String.valueOf(ID_HEARDENTRY), Parcels.wrap(newEntry));
		sendMessageToHost(getApplicationContext(), intent);
	}
	
	private boolean playBeep(final AudioTrack audioTrack){
		if(audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
			return false;
		
		byte[] audioData = null;
		try(final InputStream in = getResources().openRawResource(R.raw.s1)){
			audioData = new byte[in.available()];
			int readBytes = 0;
			while(readBytes < audioData.length) {
				readBytes += in.read(audioData, readBytes, audioData.length - readBytes);
			}
		}catch(IOException ex){
			log.warn("Could not read beep sound.", ex);
			return false;
		}
		
		if(audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
			audioTrack.play();
		}
		
		final int writeOffset = 44;
		int writeBytes = 0;
		final int dataSize = (audioData[0x29] << 8) | audioData[0x28];
		final int fileSize = audioData.length - writeOffset;
		int writeLength = 0;
		if(fileSize < dataSize)
			writeLength = fileSize;
		else
			writeLength = dataSize;
		
		while(writeBytes < writeLength) {
			writeBytes += audioTrack.write(
					audioData,
					writeOffset + writeBytes,
					writeLength - writeBytes
			);
		}
		
		return true;
	}
	
	private void setConfig(final Intent intent){
		final boolean micAGC = intent.getBooleanExtra(ID_ENABLE_MICAGC, true);
		if(micAGC != isEnableMicAGC()){
			setEnableMicAGC(micAGC);
			
			if(isServiceStarted()){
				stopAudioRecord();
				createAudioRecord(isEnableMicAGC());
			}
		}
		
		setMicGain(intent.getDoubleExtra(ID_MICGAIN, 0.0D));
		
		setEnableTransmitShortMessage(
				intent.getBooleanExtra(ID_ENABLETRANSMITSHORTMESSAGE, false)
		);
		setShortMessage(
				intent.getStringExtra(ID_SHORTMESSAGE)
		);
		setEnableTransmitGPS(
				intent.getBooleanExtra(ID_ENABLETRANSMITGPS, false)
		);
		setEnablePlayBeepReceiveStart(
				intent.getBooleanExtra(ID_ENABLEBEEPONRECEIVESTART, false)
		);
		setEnablePlayBeepReceiveEnd(
				intent.getBooleanExtra(ID_ENABLEBEEPONRECEIVEEND, false)
		);
		
		setDisableAudioRecord(
				intent.getBooleanExtra(ID_DISABLE_AUDIORECORD, false)
		);
	}
/*
	private boolean decodeAPRS(final String aprsMessage) {
		boolean result = false;
		
		if(!(result = decodeDPRS(aprsMessage))) {
			result = decodeGPRMCGPGGA(aprsMessage);
		}
		
		return result;
	}
	
	private boolean decodeDPRS(final String aprsMessage) {
		//$$CRCABF4,JH1RDA-A>API51,DSTAR*:!3552.43N/14000.85E>/
		final String message = aprsMessage.replaceAll("[\r\n]", "");
		
		if(
				!message.startsWith("$$CRC")
		){return false;}
		
		final Pattern regex =
				Pattern.compile("[!]([0-9]+([.][0-9]+){0,1})([N]|[S])[/]([0-9]+([.][0-9]+){0,1})([E]|[W])[>]");
		final Matcher matcher = regex.matcher(message);
		if(!matcher.find() || matcher.groupCount() != 6)
			return false;
		
		final String latitudeString = matcher.group(1);
		double latitude = 0;
		try{
			latitude = Double.valueOf(latitudeString);
		}catch(NumberFormatException ex){
			return false;
		}
		final String latitudePos = matcher.group(3);
		if(
				latitudePos.length() != 1 ||
				(latitudePos.charAt(0) != 'N' && latitudePos.charAt(0) != 'S')
		){return false;}
		
		final String longitudeString = matcher.group(4);
		double longitude = 0;
		try{
			longitude = Double.valueOf(longitudeString);
		}catch(NumberFormatException ex){
			return false;
		}
		
		final String longitudePos = matcher.group(6);
		if(
				longitudePos.length() != 1 ||
				(longitudePos.charAt(0) != 'E' && longitudePos.charAt(0) != 'W')
		){return false;}
		
		final NMEA2DecLatLonUtil converter =
				new NMEA2DecLatLonUtil(
						latitude, latitudePos.charAt(0),
						longitude, longitudePos.charAt(0)
				);
		
		receivedGPSPositionLatitude = converter.getDecimalLatitude();
		receivedGPSPositionLongitude = converter.getDecimalLongitude();
		
		return true;
	}
	
	private boolean decodeGPRMCGPGGA(final String aprsMessage) {
		//$GPRMC,131921.00,A,3552.4427,N,14000.8616,E,0.000,-0.000,050419,,E,A*3E
		
		final String message = aprsMessage.replaceAll("[\r\n]", "");
		
		if(
				!message.startsWith("$GPRMC") && !message.startsWith("$GPGGA")
		){return false;}
		
		final Pattern regex =
				Pattern.compile("[,]([0-9]+([.][0-9]+){0,1})[,]([N]|[S])[,]([0-9]+([.][0-9]+){0,1})[,]([E]|[W])[,]");
		final Matcher matcher = regex.matcher(message);
		if(!matcher.find() || matcher.groupCount() != 6)
			return false;
		
		final String latitudeString = matcher.group(1);
		double latitude = 0;
		try{
			latitude = Double.valueOf(latitudeString);
		}catch(NumberFormatException ex){
			return false;
		}
		final String latitudePos = matcher.group(3);
		if(
				latitudePos.length() != 1 ||
				(latitudePos.charAt(0) != 'N' && latitudePos.charAt(0) != 'S')
		){return false;}
		
		final String longitudeString = matcher.group(4);
		double longitude = 0;
		try{
			longitude = Double.valueOf(longitudeString);
		}catch(NumberFormatException ex){
			return false;
		}
		
		final String longitudePos = matcher.group(6);
		if(
				longitudePos.length() != 1 ||
				(longitudePos.charAt(0) != 'E' && longitudePos.charAt(0) != 'W')
		){return false;}
		
		final NMEA2DecLatLonUtil converter =
				new NMEA2DecLatLonUtil(
						latitude, latitudePos.charAt(0),
						longitude, longitudePos.charAt(0)
				);
		
		receivedGPSPositionLatitude = converter.getDecimalLatitude();
		receivedGPSPositionLongitude = converter.getDecimalLongitude();
		
		return true;
	}
*/
}
