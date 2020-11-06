package org.jp.illg.nora;

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
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import org.jp.illg.dstar.DStarDefines;
import org.jp.illg.dstar.model.config.GatewayProperties;
import org.jp.illg.dstar.model.config.ModemProperties;
import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadServiceProperties;
import org.jp.illg.dstar.model.config.ReflectorHostFileDownloadURLEntry;
import org.jp.illg.dstar.model.config.ReflectorProperties;
import org.jp.illg.dstar.model.config.RoutingServiceProperties;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.model.defines.ReflectorProtocolProcessorTypes;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.dstar.model.defines.RoutingServiceTypes;
import org.jp.illg.dstar.reflector.protocol.dplus.DPlusCommunicationService;
import org.jp.illg.dstar.reflector.protocol.jarllink.JARLLinkCommunicationService;
import org.jp.illg.dstar.repeater.echo.EchoAutoReplyRepeater;
import org.jp.illg.dstar.repeater.homeblew.HomeblewRepeater;
import org.jp.illg.dstar.repeater.modem.icomap.AccessPointInterface;
import org.jp.illg.dstar.repeater.modem.icomap.NewAccessPointInterface;
import org.jp.illg.dstar.repeater.modem.mmdvm.MMDVMInterface;
import org.jp.illg.dstar.repeater.voiceroid.VoiceroidAutoReplyRepeater;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.routing.service.ircDDB.IrcDDBRoutingService;
import org.jp.illg.dstar.routing.service.jptrust.JpTrustClientService;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DStarUtils;
import org.jp.illg.nora.android.reporter.model.NoraGatewayStatusInformation;
import org.jp.illg.nora.android.view.model.ApplicationConfig;
import org.jp.illg.nora.android.view.model.RepeaterModuleConfig;
import org.jp.illg.nora.gateway.NoraGatewayConfiguration;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.noragateway.MainActivity;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.AndroidHelper;
import org.jp.illg.util.logback.appender.NotifyAppender;
import org.jp.illg.util.logback.appender.NotifyAppenderListener;
import org.jp.illg.util.logback.appender.NotifyLogEvent;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.uart.UartInterfaceType;
import org.parceler.Parcels;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGatewayForAndroidService extends Service
		implements NotifyAppenderListener, NoraGatewayStatusReportListener
{

//	private static final int logLimit = 100;

	public static final int MSG_NOTIFY_EXCEPTION_ERROR = -1;
	public static final int MSG_NOTIFY_APPLICATION_ERROR = 0x0001;
	public static final int MSG_REQUEST_CONNECT = 0x0010;
	public static final int MSG_RESPONSE_CONNECT = 0x0011;
	public static final int MSG_REQUEST_CHECK_RUNNING = 0x001A;
	public static final int MSG_RESPONSE_CHECK_RUNNING = 0x001B;
	public static final int MSG_REQUEST_PORTLIST = 0x0020;
	public static final int MSG_RESPONSE_PORTLIST = 0x0021;
	public static final int MSG_REQUEST_SET_PORT = 0x0022;
	public static final int MSG_RESPONSE_SET_PORT = 0x0023;
	public static final int MSG_REQUEST_GATEWAY_START = 0x0100;
	public static final int MSG_RESPONSE_GATEWAY_START = 0x0101;
	public static final int MSG_REQUEST_GATEWAY_STOP = 0x0200;
	public static final int MSG_RESPONSE_GATEWAY_STOP = 0x0201;
	public static final int MSG_NOTIFY_LOG = 0x1000;
	public static final int MSG_NOTIFY_SAVED_LOGS = 0x1001;
	public static final int MSG_NOTIFY_STATUS_REPORT = 0x1010;
	public static final int MSG_NOTIFY_SERVICE_STARTED = 0x1100;
	public static final String MSG_ATTACHMENT_ID;


	private final Messenger selfMessenger = new Messenger(new IncommingMessageHandler());
	private Messenger hostMessenger;
	private static final IntentFilter intentFilter;

	private NoraGatewayForAndroid gateway;

	private Exception gatewayUncaughtExceotion = null;
	private String gatewayApplicationErrorMessage = null;

	private NotificationManager notificationManager;
	private Notification notification;
	private final int notificationId = new Random().nextInt(Integer.MAX_VALUE);

//	private final Queue<String> logs;

	private final BroadcastReceiver broadcastReceiver =
			new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					switch (Integer.valueOf(intent.getAction())){
						case MSG_REQUEST_CONNECT:
							onMsgRequestConnect(context, intent);
							break;

						case MSG_REQUEST_CHECK_RUNNING:
							onMsgRequestCheckRunning(context, intent);
							break;

						case MSG_REQUEST_PORTLIST:
							onMsgRequestPortList(context, intent);
							break;

						case MSG_REQUEST_GATEWAY_START:
							onMsgRequestGatewayStart(context, intent);
							break;

						case MSG_REQUEST_GATEWAY_STOP:
							onMsgRequestGatewayStop(context, intent);
							break;

						default:
							break;
					}
				}
			};

	private ThreadUncaughtExceptionListener gatewayExceptionListener =
			new ThreadUncaughtExceptionListener() {
				@Override
				public void threadUncaughtExceptionEvent(Exception ex, ThreadBase thread) {
					gatewayUncaughtExceotion = ex;

					Message msg = Message.obtain(null, MSG_NOTIFY_EXCEPTION_ERROR, ex);
					sendMessageToHost(msg);

					Intent intent = new Intent(String.valueOf(MSG_NOTIFY_EXCEPTION_ERROR));
					intent.putExtra(MSG_ATTACHMENT_ID, ex);
					sendMessageToHost(intent);

					NoraGatewayForAndroidService.this.gateway.stop();
				}

				@Override
				public void threadFatalApplicationErrorEvent(String message, Exception ex, ThreadBase thread) {
					gatewayUncaughtExceotion = ex;
					gatewayApplicationErrorMessage = message;

					Message msg = Message.obtain(null, MSG_NOTIFY_APPLICATION_ERROR, message);
					sendMessageToHost(msg);

					Intent intent = new Intent(String.valueOf(MSG_NOTIFY_APPLICATION_ERROR));
					intent.putExtra(MSG_ATTACHMENT_ID, message);
					sendMessageToHost(intent);

					NoraGatewayForAndroidService.this.gateway.stop();
				}
			};


//	private NoraGatewayConfiguration gatewayConfiguration;

	static {
		MSG_ATTACHMENT_ID = UUID.randomUUID().toString();

		intentFilter = new IntentFilter();
		intentFilter.addAction(String.valueOf(MSG_REQUEST_CONNECT));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_PORTLIST));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_GATEWAY_START));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_GATEWAY_STOP));
		intentFilter.addAction(String.valueOf(MSG_REQUEST_CHECK_RUNNING));
	}

	{

	}


	public boolean initialize(Context context){
		AndroidHelper.setApplicationContext(this);
		if(
			(gateway = NoraGatewayForAndroid.getInstance(gatewayExceptionListener, this, this)) != null
//			NoraGatewayForAndroid.readGatewayConfiguration(R.raw.noragateway)
		)
			return true;
		else
			return false;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		log.trace(this.getClass().getSimpleName() + ".onStartCommand()");

		return Service.START_NOT_STICKY;
	}

	@Override
	public void onCreate(){
		log.trace(this.getClass().getSimpleName() + ".onCreate()");

		getLBM().registerReceiver(broadcastReceiver, intentFilter);

		if(!NotifyAppender.isListenerRegisterd(this))
			NotifyAppender.addListener(this);

		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		Intent serviceStartedNotifyIntent = new Intent(String.valueOf(MSG_NOTIFY_SERVICE_STARTED));
		sendMessageToHost(serviceStartedNotifyIntent);
	}

	@Override
	public IBinder onBind(Intent i) {
		log.trace(this.getClass().getSimpleName() + ".onBind()");

		return selfMessenger.getBinder();
	}

	@Override
	public void onRebind(Intent intent)
	{
		log.trace(this.getClass().getSimpleName() + ".onRebind()");
	}

	@Override
	public boolean onUnbind(Intent intent) {
		log.trace(this.getClass().getSimpleName() + ".onUnbind()");

		notificationManager.cancelAll();

		return true;
	}

	@Override
	public void onDestroy(){
		log.trace(this.getClass().getSimpleName() + ".onDestroy()");

		notificationManager.cancel(notificationId);

		getLBM().unregisterReceiver(broadcastReceiver);

		NotifyAppender.removeListener(this);
	}

	@Override
	public void onTaskRemoved(Intent rootIntent) {

		log.info(this.getClass().getSimpleName() + ".onTaskRemoved()");

		super.onTaskRemoved(rootIntent);
	}

	private class IncommingMessageHandler extends Handler {

		@Override
		public void handleMessage(Message msg){
			switch (msg.what){
				case MSG_REQUEST_CONNECT:
					onMsgRequestConnect(msg);
					break;

				case MSG_REQUEST_CHECK_RUNNING:
					onMsgRequestCheckRunning(msg);
					break;

				case MSG_REQUEST_PORTLIST:
					onMsgRequestPortList(msg);
					break;

				case MSG_REQUEST_GATEWAY_START:
					onMsgRequestGatewayStart(msg);
					break;

				case MSG_REQUEST_GATEWAY_STOP:
					onMsgRequestGatewayStop(msg);
					break;

				case MSG_REQUEST_SET_PORT:
					onMsgRequestSetPort(msg);
					break;

				default:
					break;
			}
		}
	}

	@Override
	public void notifyLog(String msg){
		Message message = Message.obtain(null, MSG_NOTIFY_LOG, msg);
		sendMessageToHost(message);

		Intent logIntent = new Intent(String.valueOf(MSG_NOTIFY_LOG));
		logIntent.putExtra(MSG_ATTACHMENT_ID, msg);
		sendMessageToHost(logIntent);
	}
	
	@Override
	public void notifyLogEvent(NotifyLogEvent event){
	
	}
	
	@Override
	public void listenerProcess(){
	
	}

	@Override
	public void report(BasicStatusInformation info){
		Intent reportIntent = new Intent(String.valueOf(MSG_NOTIFY_STATUS_REPORT));
		reportIntent.putExtra(MSG_ATTACHMENT_ID, Parcels.wrap(new NoraGatewayStatusInformation(info)));
		sendMessageToHost(reportIntent);
	}

	private void onMsgRequestConnect(Message receiveMessage){
		assert  receiveMessage != null;

		boolean success = false;
		if (
				receiveMessage.obj != null && receiveMessage.obj instanceof Context &&
				receiveMessage.replyTo != null
		) {

			Context context = (Context) receiveMessage.obj;
			hostMessenger = receiveMessage.replyTo;

			success = onMsgRequestConnect(context);
		}

		Message msg = Message.obtain(null, MSG_RESPONSE_CONNECT, success);
		sendMessageToHost(msg);
	}

	private void onMsgRequestConnect(Context context, Intent intent){
		assert  intent != null && context != null;

		boolean success = onMsgRequestConnect(context);

		// return connect response
		Message msg = Message.obtain(null, MSG_RESPONSE_CONNECT, success);
		sendMessageToHost(msg);

		Intent sendIntent = new Intent(String.valueOf(MSG_RESPONSE_CONNECT));
		sendIntent.putExtra(MSG_ATTACHMENT_ID, success);
		sendMessageToHost(sendIntent);
	}

	private boolean onMsgRequestConnect(Context context){
		assert context != null;

		boolean initializeSuccess = initialize(context);

		return initializeSuccess;
	}

	private void onMsgRequestCheckRunning(Message receiveMessage){
		assert receiveMessage != null;

		Message msg = Message.obtain(null, MSG_RESPONSE_CHECK_RUNNING, true);
		sendMessageToHost(msg);
	}

	private void onMsgRequestCheckRunning(Context context, Intent intent){
		assert  intent != null && context != null;

		Intent sendIntent = new Intent(String.valueOf(MSG_RESPONSE_CHECK_RUNNING));
		sendIntent.putExtra(MSG_ATTACHMENT_ID, true);
		sendMessageToHost(sendIntent);
	}

	private void onMsgRequestPortList(Message receiveMessage){
		assert receiveMessage != null;

		List<String> ports = onMsgRequestPortList();

		Message msg = Message.obtain(null, MSG_RESPONSE_PORTLIST, ports);
		sendMessageToHost(msg);
	}

	private void onMsgRequestPortList(Context context, Intent intent){
		assert intent != null;

		List<String> ports = onMsgRequestPortList();

		Intent sendIntent = new Intent(String.valueOf(MSG_RESPONSE_PORTLIST));
		sendIntent.putExtra(MSG_ATTACHMENT_ID, ports.toArray(new String[ports.size()]));
		sendMessageToHost(sendIntent);
	}

	private List<String> onMsgRequestPortList(){
		List<String> ports = new ArrayList<>();

		if(gateway != null)
			ports.addAll(gateway.getUartPortList());

		return ports;
	}


	private void onMsgRequestGatewayStart(Message receiveMessage){
		assert  receiveMessage != null;

		boolean success = false;

		if(receiveMessage.obj != null && receiveMessage.obj instanceof ApplicationConfig)
			success = onMsgRequestGatewayStart((ApplicationConfig)receiveMessage.obj);

		Message msg = Message.obtain(null, MSG_RESPONSE_GATEWAY_START, success);
		sendMessageToHost(msg);
	}

	private void onMsgRequestGatewayStart(Context context, Intent intent){
		assert  intent != null;

		boolean success = false;

		ApplicationConfig config = Parcels.unwrap(intent.getParcelableExtra(MSG_ATTACHMENT_ID));

		if(config != null)
			success = onMsgRequestGatewayStart(config);
/*
		Intent sendIntent = new Intent(
				String.valueOf(MSG_RESPONSE_GATEWAY_START),
				null, context,
				NoraGatewayForAndroidApp.AppBroadcastReceiver.class
		);
*/
		Intent sendIntent = new Intent(
				String.valueOf(MSG_RESPONSE_GATEWAY_START)
		);
		sendIntent.putExtra(MSG_ATTACHMENT_ID, success);
		sendMessageToHost(sendIntent);
	}

	private boolean onMsgRequestGatewayStart(ApplicationConfig config){

		boolean success = false;
		if(
				!gateway.isRunning() &&
				gateway.start(convertAppConfigToNoraGatewayConfig(config))
		){success = true;}

		if(success){
			String channelId =
					Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ?
							createNotificationChannel() : "";

			Intent activityIntent = new Intent(this, MainActivity.class);
			PendingIntent pendingIntent =
					PendingIntent.getActivity(this, 0, activityIntent, 0);

			NotificationCompat.Builder notifnicationBuilder =
					new NotificationCompat.Builder(this, channelId);

			notification =
					notifnicationBuilder
							.setContentTitle(NoraGatewayForAndroid.getApplicationName())
							.setContentText("Running...")
							.setSubText(NoraGatewayForAndroid.getApplicationVersion())
							.setContentIntent(pendingIntent)
							.setSmallIcon(R.drawable.ic_swap_vert)
							.setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.drawable.ic_launcher))
							.setOngoing(true)
							.build();

//		notificationManager.notify(notificationId, notification);
			startForeground(notificationId, notification);
		}

		return success;
	}

	private void onMsgRequestGatewayStop(Message receiveMessage){
		assert receiveMessage != null;

		onMsgRequestGatewayStop();

		Message msg = Message.obtain(null, MSG_RESPONSE_GATEWAY_STOP);
		sendMessageToHost(msg);
	}

	private void onMsgRequestGatewayStop(Context context, Intent intent){
		assert intent != null;

		onMsgRequestGatewayStop();

		Intent sendIntent = new Intent(String.valueOf(MSG_RESPONSE_GATEWAY_STOP));
		sendMessageToHost(sendIntent);
	}

	private void onMsgRequestGatewayStop(){
		if(gateway.isRunning()){gateway.stop();}
	}


	public void onMsgRequestSetPort(Message receiveMessage){
		assert receiveMessage != null;

		boolean success = false;

		String portName = null;

		if(receiveMessage.obj != null && receiveMessage.obj instanceof String){
			Pattern pattern = Pattern.compile("\\[(.*?)\\],");
			Matcher matcher = pattern.matcher(String.valueOf(receiveMessage.obj));

			if(matcher.find() && matcher.groupCount() >= 1){portName = matcher.group(1);}
		}

		log.info("Set port request received..." + (portName != null ? portName : String.valueOf(receiveMessage.obj)) + ".");

		NoraGatewayConfiguration gatewayConfiguration = NoraGatewayForAndroid.getGatewayConfiguration();

		if(portName != null && gatewayConfiguration != null){
			for(RepeaterProperties repeater : gatewayConfiguration.getRepeaterProperties().values()){
				for(ModemProperties modem : repeater.getModemProperties()){
					if(ModemTypes.AccessPoint.getTypeName().equals(modem.getType())){
						Properties modemProperties = modem.getConfigurationProperties();

						if(modemProperties.containsKey(AccessPointInterface.rigPortNamePropertyName))
							modemProperties.remove(AccessPointInterface.rigPortNamePropertyName);

						modem.getConfigurationProperties().setProperty(AccessPointInterface.rigPortNamePropertyName, portName);

						success = true;
					}
				}
			}
		}

		Message msg = Message.obtain(null, MSG_RESPONSE_SET_PORT, success);
		sendMessageToHost(msg);
	}


	private boolean sendMessageToHost(Message msg){
		assert msg != null;

		try{
			if(hostMessenger != null){
				synchronized (hostMessenger){hostMessenger.send(msg);}
			}

			return true;
		}catch(RemoteException ex){
			log.warn("Could not send message.", ex);
		}

		return false;
	}

	private boolean sendMessageToHost(Intent msg){
		assert msg != null;

		return sendMessageToHost(msg, false);
	}

	private boolean sendMessageToHost(Intent msg, boolean sync){
		assert msg != null;

		if(sync)
			getLBM().sendBroadcastSync(msg);
		else
			getLBM().sendBroadcast(msg);

		return true;
	}

	private LocalBroadcastManager getLBM(){
		return LocalBroadcastManager.getInstance(getApplicationContext());
	}

	private NoraGatewayConfiguration convertAppConfigToNoraGatewayConfig(
			ApplicationConfig applicationConfig
	){
		assert applicationConfig != null;

		if(log.isTraceEnabled())
			log.trace("Converting application config to gateway config...\n" + applicationConfig.toString());

		NoraGatewayConfiguration noraGatewayConfiguration = new NoraGatewayConfiguration();

		//ゲートウェイ
		GatewayProperties gatewayProperties = noraGatewayConfiguration.getGatewayProperties();
		final String gatewayCallsign = applicationConfig.getGatewayConfig().getGatewayCallsign();
		if(CallSignValidator.isValidJARLRepeaterCallsign(gatewayCallsign)){
			if(log.isWarnEnabled())
				log.warn("DO NOT USE JARL REPEATER CALLSIGN = " + gatewayCallsign + ".");
			
			return null;
		}
		gatewayProperties.setCallsign(
				DStarUtils.formatFullCallsign(
				gatewayCallsign,
				'G'
			)
		);
		gatewayProperties.setHostsFile("hosts.txt");
		gatewayProperties.setPort(40000);
		gatewayProperties.setDisableHeardAtReflector(true);
		
		File hostsOutputDir = null;
		if((hostsOutputDir = this.getApplicationContext().getExternalFilesDir(null)) == null){
			hostsOutputDir = this.getApplicationContext().getFilesDir();
		}
		if(hostsOutputDir != null){
			gatewayProperties.setHostFileOutputPath(
					hostsOutputDir + File.separator + "hosts.output.txt"
			);
		}
		
		gatewayProperties.setUseProxyGateway(applicationConfig.getGatewayConfig().isUseProxyGateway());
		gatewayProperties.setProxyGatewayAddress(applicationConfig.getGatewayConfig().getProxyGatewayAddress());
		gatewayProperties.setProxyPort(applicationConfig.getGatewayConfig().getProxyGatewayPort());

		if(applicationConfig.getGatewayConfig().isEnableJapanTrust()){
			RoutingServiceProperties routingServiceProperties = new RoutingServiceProperties();
			routingServiceProperties.setEnable(true);

			routingServiceProperties.setType(RoutingServiceTypes.JapanTrust.getTypeName());
			routingServiceProperties.getConfigurationProperties().put(
					JpTrustClientService.trustAddressPropertyName,
					applicationConfig.getGatewayConfig().getJapanTrustServerAddress()
			);
			routingServiceProperties.getConfigurationProperties().put(
					JpTrustClientService.trustPortPropertyName,
					30001
			);

			routingServiceProperties.getConfigurationProperties().setProperty(
					JpTrustClientService.useProxyGatewayPropertyName,
					String.valueOf(applicationConfig.getGatewayConfig().isUseProxyGateway())
			);
			routingServiceProperties.getConfigurationProperties().setProperty(
					JpTrustClientService.proxyGatewayAddressPropertyName,
					applicationConfig.getGatewayConfig().getProxyGatewayAddress()
			);
			routingServiceProperties.getConfigurationProperties().setProperty(
					JpTrustClientService.proxyPortPropertyName,
					String.valueOf(applicationConfig.getGatewayConfig().getProxyGatewayPort())
			);
			
			routingServiceProperties.getConfigurationProperties().setProperty(
					JpTrustClientService.queryIDPropertyName,
					String.valueOf(NoraDefines.NoraGatewayQueryIDForJapanTrust)
			);

			gatewayProperties.getRoutingServices().put(routingServiceProperties.getType(), routingServiceProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableIrcDDB()){
			RoutingServiceProperties routingServiceProperties = new RoutingServiceProperties();
			routingServiceProperties.setEnable(true);

			routingServiceProperties.setType(RoutingServiceTypes.ircDDB.getTypeName());
			routingServiceProperties.getConfigurationProperties().put(
					IrcDDBRoutingService.getIrcDDBServerAddressPropertyName(),
					applicationConfig.getGatewayConfig().getIrcDDBServerAddress()
			);
			routingServiceProperties.getConfigurationProperties().put(
					IrcDDBRoutingService.getIrcDDBServerPasswordPropertyName(),
					applicationConfig.getGatewayConfig().getIrcDDBPassword()
			);
			routingServiceProperties.getConfigurationProperties().put(
					IrcDDBRoutingService.getIrcDDBCallsignPropertyName(),
					DStarUtils.formatFullLengthCallsign(
							applicationConfig.getGatewayConfig().getGatewayCallsign()
					).substring(0, DStarDefines.CallsignFullLength - 2).trim()
			);

			gatewayProperties.getRoutingServices().put(routingServiceProperties.getType(), routingServiceProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableDExtra()){
			ReflectorProperties reflectorProperties = new ReflectorProperties();
			reflectorProperties.setEnable(true);

			reflectorProperties.setType(ReflectorProtocolProcessorTypes.DExtra.getTypeName());

			gatewayProperties.getReflectors().put(reflectorProperties.getType(), reflectorProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableDCS()){
			ReflectorProperties reflectorProperties = new ReflectorProperties();
			reflectorProperties.setEnable(true);

			reflectorProperties.setType(ReflectorProtocolProcessorTypes.DCS.getTypeName());

			gatewayProperties.getReflectors().put(reflectorProperties.getType(), reflectorProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableDPlus()){
			ReflectorProperties reflectorProperties = new ReflectorProperties();
			reflectorProperties.setEnable(true);

			reflectorProperties.getConfigurationProperties().put(
				DPlusCommunicationService.loginCallsignPropertyName,
				applicationConfig.getGatewayConfig().getGatewayCallsign()
			);

			reflectorProperties.setType(ReflectorProtocolProcessorTypes.DPlus.getTypeName());

			gatewayProperties.getReflectors().put(reflectorProperties.getType(), reflectorProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableJARLMultiForward()){
			ReflectorProperties reflectorProperties = new ReflectorProperties();
			reflectorProperties.setEnable(true);

			reflectorProperties.setType(ReflectorProtocolProcessorTypes.JARLLink.getTypeName());

			reflectorProperties.getConfigurationProperties().put(
					JARLLinkCommunicationService.getLoginCallsignPropertyName(),
					DStarUtils.formatFullCallsign(gatewayCallsign, ' ')
			);
			
			reflectorProperties.getConfigurationProperties().put(
					JARLLinkCommunicationService.getConnectionObserverAddressPropertyName(),
					"hole-punchd.d-star.info"
			);
			reflectorProperties.getConfigurationProperties().put(
					JARLLinkCommunicationService.getRepeaterHostnameServerAddressPropertyName(),
					"k-dk.net"
			);

			gatewayProperties.getReflectors().put(reflectorProperties.getType(), reflectorProperties);
		}

		if(applicationConfig.getGatewayConfig().isEnableRemoteControl()){
			gatewayProperties.getRemoteControlService().setEnable(true);
			gatewayProperties.getRemoteControlService().setPassword("NoraRemotePass");
			gatewayProperties.getRemoteControlService().setPort(62115);
		}
		
		final ReflectorHostFileDownloadServiceProperties reflectorHostFileDownloadServiceProperties =
			noraGatewayConfiguration.getReflectorHostFileDownloadServiceProperties();
		
		reflectorHostFileDownloadServiceProperties.setEnable(true);
		reflectorHostFileDownloadServiceProperties.getUrlEntries().add(
			new ReflectorHostFileDownloadURLEntry(
				true, 360, "https://kdk.ddns.net/norahosts/hosts.txt"
			)
		);
		reflectorHostFileDownloadServiceProperties.getUrlEntries().add(
			new ReflectorHostFileDownloadURLEntry(
				true, 30, "https://kdk.ddns.net/nora_hosts.php"
			)
		);

		for(
				RepeaterModuleConfig repeaterModuleConfig :
				applicationConfig.getRepeaterConfig().getRepeaterModules().values()
		){
			if(!repeaterModuleConfig.isRepeaterEnabled()){continue;}

			RepeaterProperties repeaterProperties = new RepeaterProperties();

			repeaterProperties.setEnable(true);

			repeaterProperties.setCallsign(
					DStarUtils.formatFullCallsign(
						gatewayProperties.getCallsign(),
						repeaterModuleConfig.getRepeaterModule()
					)
			);
			RepeaterTypes repeaterType =
					RepeaterTypes.getTypeByTypeName(repeaterModuleConfig.getRepeaterType());
			repeaterProperties.setType(repeaterType.getTypeName());
			//XXX 現段階ではJapanTrust固定
			repeaterProperties.setDefaultRoutingService(RoutingServiceTypes.JapanTrust.getTypeName());
			repeaterProperties.setRoutingServiceFixed("false");

			repeaterProperties.setAllowIncomingConnection(false);
			repeaterProperties.setAllowOutgoingConnection(true);

			boolean allowDIRECT = false;

			switch(repeaterType){
				case Internal:{
					final String directMyCallsignsConfig =
						repeaterModuleConfig.getInternalRepeaterConfig().getDirectMyCallsigns();
					if(directMyCallsignsConfig != null && !"".equals(directMyCallsignsConfig)){
						for(final String callsign : directMyCallsignsConfig.split(",")){
							final String directMyCallsign =
								DStarUtils.formatFullCallsign(callsign.toUpperCase(Locale.ENGLISH), ' ');

							if(
								CallSignValidator.isValidUserCallsign(directMyCallsign) &&
									!CallSignValidator.isValidJARLRepeaterCallsign(directMyCallsign)
							){
								repeaterProperties.getDirectMyCallsigns().add(directMyCallsign);
							}
						}
					}

					ModemProperties modemProperties = new ModemProperties();
					modemProperties.setEnable(true);
					ModemTypes modemType =
							ModemTypes.getTypeByTypeName(
									repeaterModuleConfig.getInternalRepeaterConfig().getModemType()
							);
					modemProperties.setType(modemType.getTypeName());
					switch (modemType){
						case AccessPoint:{
							modemProperties.getConfigurationProperties().put(
									AccessPointInterface.rigPortNamePropertyName,
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemAccessPointConfig().getPortName()
							);
							modemProperties.getConfigurationProperties().put(
									AccessPointInterface.disableSlowDataToInetPropertyName,
									Boolean.FALSE.toString()
							);
							if(
								repeaterModuleConfig.getInternalRepeaterConfig()
										.getModemAccessPointConfig().isTerminalMode()
							){
								allowDIRECT = true;
								modemProperties.setAllowDIRECT(true);
							}
							else{
								modemProperties.setAllowDIRECT(false);
							}
							break;
						}
						case NewAccessPoint:{
							modemProperties.getConfigurationProperties().put(
									NewAccessPointInterface.rigPortNamePropertyName,
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemAccessPointConfig().getPortName()
							);
							if(
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemAccessPointConfig().isTerminalMode()
							){
								allowDIRECT = true;
								modemProperties.setAllowDIRECT(true);
							}
							else{
								modemProperties.setAllowDIRECT(false);
							}
							break;
						}
						case MMDVM: {
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.portNamePropertyName,
									repeaterModuleConfig.getInternalRepeaterConfig()
										.getModemMMDVMConfig().getPortName()
							);
							
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.duplexPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemMMDVMConfig().isDuplex()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemMMDVMConfig().isRxInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().isTxInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.pttInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().isPttInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txDelayPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getTxDelay()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxFrequencyPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getRxFrequency()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxFrequencyOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getRxFrequencyOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txFrequencyPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getTxFrequency()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txFrequencyOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getTxFrequencyOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxDCOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getRxDCOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txDCOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getTxDCOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rfLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getRfLevel()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getRxLevel()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMConfig().getTxLevel()
									)
							);
							
							if(
									repeaterModuleConfig.getInternalRepeaterConfig()
										.getModemMMDVMConfig().isAllowDIRECT()
							){
								allowDIRECT = true;
								modemProperties.setAllowDIRECT(true);
							}
							else{
								modemProperties.setAllowDIRECT(false);
							}
							break;
						}
						case MMDVMBluetooth:{
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.uartTypePropertyName,
									UartInterfaceType.BluetoothSPP.getTypeName()
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.portNamePropertyName,
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemMMDVMBluetoothConfig().getPortName()
							);
							
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.duplexPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().isDuplex()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().isRxInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().isTxInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.pttInvertPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().isPttInvert()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txDelayPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getTxDelay()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxFrequencyPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getRxFrequency()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxFrequencyOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getRxFrequencyOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txFrequencyPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getTxFrequency()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txFrequencyOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getTxFrequencyOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxDCOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getRxDCOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txDCOffsetPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getTxDCOffset()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rfLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getRfLevel()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.rxLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getRxLevel()
									)
							);
							modemProperties.getConfigurationProperties().put(
									MMDVMInterface.txLevelPropertyName,
									String.valueOf(
											repeaterModuleConfig.getInternalRepeaterConfig()
													.getModemMMDVMBluetoothConfig().getTxLevel()
									)
							);
							
							if(
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemMMDVMBluetoothConfig().isAllowDIRECT()
							){
								allowDIRECT = true;
								modemProperties.setAllowDIRECT(true);
							}
							else{
								modemProperties.setAllowDIRECT(false);
							}
							break;
							
						}
						
						case NewAccessPointBluetooth:{
							modemProperties.getConfigurationProperties().put(
									NewAccessPointInterface.rigPortNamePropertyName,
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemNewAccessPointBluetoothConfig().getPortName()
							);
							modemProperties.getConfigurationProperties().put(
									NewAccessPointInterface.uartTypePropertyName,
									UartInterfaceType.BluetoothSPP.getTypeName()
							);
							
							if(
									repeaterModuleConfig.getInternalRepeaterConfig()
											.getModemNewAccessPointBluetoothConfig().isAllowDVSimplex()
							){
								allowDIRECT = true;
								modemProperties.setAllowDIRECT(true);
							}
							else{
								modemProperties.setAllowDIRECT(false);
							}
						}
					}
					repeaterProperties.addModemProperties(modemProperties);
					repeaterProperties.setAllowDIRECT(allowDIRECT);
					break;
				}
/*
				case ExternalHomebrew:{
					repeaterProperties.getConfigurationProperties().put(
							HomeblewRepeater.remoteRepeaterAddressPropertyName,
							repeaterModuleConfig.getExternalHomebrewRepeaterConfig().getRemoteRepeaterAddress()
					);
					repeaterProperties.getConfigurationProperties().put(
							HomeblewRepeater.remoteRepeaterPortPropertyName,
							String.valueOf(
								repeaterModuleConfig.getExternalHomebrewRepeaterConfig().getRemoteRepeaterPort()
							)
					);
					repeaterProperties.getConfigurationProperties().put(
							HomeblewRepeater.localPortPropertyName,
							String.valueOf(
								repeaterModuleConfig.getExternalHomebrewRepeaterConfig().getLocalPort()
							)
					);
					break;
				}
				case VoiceroidAutoReply:{
					repeaterProperties.getConfigurationProperties().put(
							VoiceroidAutoReplyRepeater.autoReplyOperatorCallsignPropertyName,
							DStarUtils.formatFullCallsign(
								repeaterModuleConfig.getVoiceroidAutoReplyRepeaterConfig().getAutoReplyOperatorCallsign()
							)
					);
					break;
				}
				case EchoAutoReply:{
					repeaterProperties.getConfigurationProperties().put(
							EchoAutoReplyRepeater.autoReplyOperatorCallsignPropertyName,
							DStarUtils.formatFullCallsign(
									repeaterModuleConfig.getEchoAutoReplyRepeaterConfig().getAutoReplyOperatorCallsign()
							)
					);
					break;
				}
 */
			}

			noraGatewayConfiguration.getRepeaterProperties().put(
					repeaterProperties.getCallsign(),
					repeaterProperties
			);
		}

		return noraGatewayConfiguration;
	}

	@RequiresApi(Build.VERSION_CODES.O)
	private String createNotificationChannel()
	{
		String channelId = UUID.randomUUID().toString();
		String channelName = "NoraGateway Background Service";
		NotificationChannel chan = new NotificationChannel(channelId,
				channelName, NotificationManager.IMPORTANCE_NONE);
		chan.setLightColor(Color.BLUE);
		chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
		NotificationManager service = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		service.createNotificationChannel(chan);

		return channelId;
	}
}
