package org.jp.illg.nora;

import android.app.Application;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;


import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.multidex.MultiDex;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.gateway.NoraGatewayUtil;
import org.jp.illg.nora.android.config.ApplicationConfigReaderWriter;
import org.jp.illg.nora.android.reporter.model.NoraGatewayStatusInformation;
import org.jp.illg.nora.android.view.model.ApplicationConfig;
import org.jp.illg.noragateway.NoraGateway;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.android.AndroidHelper;
import org.jp.illg.util.android.StorageUtil;
import org.jp.illg.util.android.service.ServiceUtil;
import org.jp.illg.util.android.usb.USBMonitorUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.logback.LogbackUtil;
import org.jp.illg.util.uart.UartInterface;
import org.jp.illg.util.uart.UartInterfaceFactory;
import org.parceler.Parcels;

import java.io.File;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_ATTACHMENT_ID;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_APPLICATION_ERROR;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_EXCEPTION_ERROR;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_LOG;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_SAVED_LOGS;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_SERVICE_STARTED;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_NOTIFY_STATUS_REPORT;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_REQUEST_CHECK_RUNNING;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_REQUEST_CONNECT;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_REQUEST_GATEWAY_START;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_REQUEST_GATEWAY_STOP;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_RESPONSE_CHECK_RUNNING;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_RESPONSE_CONNECT;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_RESPONSE_GATEWAY_START;
import static org.jp.illg.nora.NoraGatewayForAndroidService.MSG_RESPONSE_GATEWAY_STOP;


@Slf4j
public class NoraGatewayForAndroidApp extends Application {

	private static final int logLimit = 100;

	Messenger serviceMessenger;
	Messenger selfMessenger = new Messenger(new IncommingMessageHandler());

	private static final IntentFilter intentFilter;

	private Intent serviceIntent;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private EventBus applicationEventBus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private ApplicationConfig applicationConfig;

	private USBMonitorUtil usbMonitorUtil;

	private boolean requestingServiceStart = false;
	
	private final LifecycleObserver lifecycleObserver = new DefaultLifecycleObserver() {
		@Override
		public void onStart(LifecycleOwner owner){
			if(log.isTraceEnabled())
				log.trace(this.getClass().getSimpleName() + ".onApplicationForegrounded()");
		}
		
		@Override
		public void onStop(LifecycleOwner owner) {
			if(log.isTraceEnabled())
				log.trace(this.getClass().getSimpleName() + ".onApplicationBackgrounded()");
			
			ApplicationConfigReaderWriter.saveConfig(
					NoraGatewayForAndroidApp.this, getApplicationConfig()
			);
		}
	};

	private final Deque<String> logs = new LinkedList<>();

	static {
		intentFilter = new IntentFilter();
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_EXCEPTION_ERROR));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_APPLICATION_ERROR));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_CONNECT));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_CHECK_RUNNING));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_GATEWAY_START));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_GATEWAY_STOP));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_LOG));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_STATUS_REPORT));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_SAVED_LOGS));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_SERVICE_STARTED));
	}

	public class AppBroadcastReceiver extends BroadcastReceiver{
		@Override
		public void onReceive(Context context, Intent intent) {
			switch (Integer.valueOf(intent.getAction())){
				case MSG_RESPONSE_CONNECT:{
					onResponseConnect(context, intent);
					break;
				}

				case MSG_RESPONSE_CHECK_RUNNING:{
					onResponseCheckRunning(context, intent);
					break;
				}

				case MSG_NOTIFY_LOG: {
					onMsgNotifyLog(context, intent);
					break;
				}

				case MSG_NOTIFY_STATUS_REPORT:{
					onMsgNotifyStatusReport(context, intent);
					break;
				}

				case MSG_NOTIFY_SAVED_LOGS: {
					onMsgNotifySavedLogs(context, intent);
					break;
				}

				case MSG_NOTIFY_SERVICE_STARTED: {
					Intent connectIntent = new Intent(String.valueOf(MSG_REQUEST_CONNECT));
					sendMessageToService(connectIntent);
					break;
				}

				case MSG_RESPONSE_GATEWAY_START:{
					onMsgResponseGatewayStart(context, intent);
					break;
				}

				case MSG_RESPONSE_GATEWAY_STOP:{
					onMsgResponseGatewayStop(context, intent);
					break;
				}

				case MSG_NOTIFY_EXCEPTION_ERROR:{
					onMsgNotifyExceptionError(context, intent);
					break;
				}

				case MSG_NOTIFY_APPLICATION_ERROR:{
					onMsgNotifyApplicationError(context, intent);
					break;
				}

			}
		}
	}

	@Getter
	private final BroadcastReceiver broadcastReceiver = new AppBroadcastReceiver();

	public static class MainActivityEvent extends EventBusEvent<MainActivityEventType>{
		public MainActivityEvent(MainActivityEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum MainActivityEventType{
		MainActivityCreated{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				if(attachment != null && attachment instanceof MainActivity){

				}
			}
		},
		MainActivityOnPause {
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){

			}
		},
		RequestCheckRunningGateway{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				Intent sendIntent = new Intent(String.valueOf(MSG_REQUEST_CHECK_RUNNING));
				application.sendMessageToService(sendIntent);
			}
		},
		RequestStartGateway{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				application.requestingServiceStart = true;

				application.startService();
			}
		},
		RequestStopGateway{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				Message msg = Message.obtain(null, MSG_REQUEST_GATEWAY_STOP);
				application.sendMessageToService(msg);

				Intent sendIntent = new Intent(String.valueOf(MSG_REQUEST_GATEWAY_STOP));
				application.sendMessageToService(sendIntent);
			}
		},
		RequestPortList{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				List<String> ports = application.getUartPorts();

				application.getApplicationEventBus().post(
						new MainActivity.ApplicationEvent(
								MainActivity.ApplicationEventType.ResponseUartPort,
								ports.toArray(new String[ports.size()])
						)
				);
			}
		},
		RequestSetPort{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				Message msg = Message.obtain(null, NoraGatewayForAndroidService.MSG_REQUEST_SET_PORT, attachment);
				application.sendMessageToService(msg);
			}
		},
		UpdateConfig{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				if(attachment != null && attachment instanceof ApplicationConfig){
					application.setApplicationConfig((ApplicationConfig)attachment);
					
					ApplicationConfigReaderWriter.saveConfig(
							application, application.getApplicationConfig()
					);
				}
			}
		},
		RequestConfig{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				application.getApplicationEventBus().post(
						new MainActivity.ApplicationEvent(
								MainActivity.ApplicationEventType.ResponseConfig,
								application.getApplicationConfig()
						)
				);
			}
		},
		RequestSavedLog{
			@Override
			void apply(NoraGatewayForAndroidApp application, Object attachment){
				String[] logArray;
				synchronized (application.logs){
					logArray = application.logs.toArray(new String[application.logs.size()]);
				}

				application.getApplicationEventBus().post(
						new MainActivity.ApplicationEvent(
								MainActivity.ApplicationEventType.NotifySavedLogs,
								logArray
						)
				);
			}
		},
		;

		abstract void apply(NoraGatewayForAndroidApp application, Object attachment);
	}

	public static ApplicationInformation<?> getApplicationInformation() {
		return NoraGateway.getApplicationInformation();
	}

	@Subscribe
	public void onApplicationEvent(MainActivityEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}

	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		MultiDex.install(this);
	}

	@Override
	public void onCreate() {
		super.onCreate();

		File logDir = null;
		if(
				StorageUtil.isExternalStorageReadable() &&
				StorageUtil.isExternalStorageWritable() &&
				(logDir = getApplicationContext().getExternalFilesDir(null)) != null
		){
			LogbackUtil.addRollingFileAppender(
					logDir.getAbsolutePath(),
					NoraGatewayUtil.getApplicationName(),
					10
			);
		}
		else {
			if(log.isWarnEnabled())
				log.warn("Logging service initialize failed, External dir not available.");
		}

		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onCreate()");

		AndroidHelper.setApplicationContext(this);


		getLBM().registerReceiver(broadcastReceiver, intentFilter);

		ProcessLifecycleOwner.get().getLifecycle().addObserver(lifecycleObserver);

		setApplicationEventBus(EventBus.builder().build());
		getApplicationEventBus().register(this);

		usbMonitorUtil = new USBMonitorUtil(this, usbMonitorOnDeviceConnectListener);
		usbMonitorUtil.register();
		List<UsbDevice> usbDevices = usbMonitorUtil.getDeviceList();
		if(usbDevices != null && usbDevices.size() >= 1){
			for(UsbDevice usbDevice : usbDevices){
				if(
						usbDevice.getVendorId() == 0x0403 &&
						!usbMonitorUtil.hasPermission(usbDevice)
				){
					usbMonitorUtil.requestPermission(usbDevice);}
			}
		}

		ApplicationConfigReaderWriter.loadConfig(this).ifPresent(
				new Consumer<ApplicationConfig>() {
					@Override
					public void accept(ApplicationConfig applicationConfig) {
						setApplicationConfig(applicationConfig);
					}
				}
		);

//		startService(new Intent(getBaseContext(), OnClearFromRecentService.class));
	}

	@Override
	public void onTerminate(){
		log.trace(this.getClass().getSimpleName() + ".onTerminate()");

		terminate();

		super.onTerminate();
	}

	private ServiceConnection serviceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			serviceMessenger = new Messenger(iBinder);

			Message msg = Message.obtain(null, NoraGatewayForAndroidService.MSG_REQUEST_CONNECT, getApplicationContext());
			msg.replyTo = selfMessenger;
			sendMessageToService(msg);
		}

		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			log.error("Service disconnected from " + componentName + ".");
		}
	};

	private final USBMonitorUtil.OnDeviceConnectListener usbMonitorOnDeviceConnectListener =
			new USBMonitorUtil.OnDeviceConnectListener() {
				@Override
				public void onAttach(UsbDevice device) {
					log.trace(this.getClass().getSimpleName() + ".usbMonitorOnDeviceConnectListener.onAttach()");

					if(!usbMonitorUtil.hasPermission(device)){
						usbMonitorUtil.requestPermission(device);
					}
				}

				@Override
				public void onDettach(UsbDevice device) {
					log.trace(this.getClass().getSimpleName() + ".usbMonitorOnDeviceConnectListener.onDettach()");

				}

				@Override
				public void onConnect(UsbDevice device, USBMonitorUtil.UsbControlBlock ctrlBlock, boolean createNew) {
					log.trace(this.getClass().getSimpleName() + ".usbMonitorOnDeviceConnectListener.onConnect()");
				}

				@Override
				public void onDisconnect(UsbDevice device, USBMonitorUtil.UsbControlBlock ctrlBlock) {
					log.trace(this.getClass().getSimpleName() + ".usbMonitorOnDeviceConnectListener.onDisconnect()");


				}
			};

	private class IncommingMessageHandler extends Handler {

		@Override
		public void handleMessage(Message msg){
			switch (msg.what){
				case MSG_RESPONSE_CONNECT:{
//					onResponseConnect(msg);
					break;
				}

				case MSG_RESPONSE_CHECK_RUNNING:{
//					onResponseCheckRunning(msg);
					break;
				}

				case MSG_NOTIFY_LOG: {
//					onMsgNotifyLog(msg);
					break;
				}

				case MSG_NOTIFY_SAVED_LOGS:{
//					onMsgNotifySavedLogs(msg);
					break;
				}

				case MSG_RESPONSE_GATEWAY_START:{
//					onMsgResponseGatewayStart(msg);
					break;
				}

				case MSG_RESPONSE_GATEWAY_STOP:{
//					onMsgResponseGatewayStop(msg);
					break;
				}
			}

		}
	};

	private void onResponseConnect(Message receiveMessage){
		assert receiveMessage != null;

		if(requestingServiceStart) {
			Message msg = Message.obtain(null, NoraGatewayForAndroidService.MSG_REQUEST_GATEWAY_START);
			sendMessageToService(msg);

			requestingServiceStart = false;
		}
	}

	private void onResponseConnect(Context context, Intent intent){
		assert context != null && intent != null;

		if(requestingServiceStart) {
			Intent sendIntent = new Intent(String.valueOf(MSG_REQUEST_GATEWAY_START));
			sendIntent.putExtra(MSG_ATTACHMENT_ID, Parcels.wrap(getApplicationConfig()));
			sendMessageToService(sendIntent);

			requestingServiceStart = false;
		}
	}

	private void onResponseCheckRunning(Message receiveMessage){
		assert receiveMessage != null;

		boolean running = false;
		if(receiveMessage.obj != null && receiveMessage.obj instanceof Boolean)
			running = (Boolean)receiveMessage.obj;

		onResponseCheckRunning(running);
	}

	private void onResponseCheckRunning(Context context, Intent intent){
		assert context != null && intent != null;

		boolean running = intent.getBooleanExtra(MSG_ATTACHMENT_ID, false);

		onResponseCheckRunning(running);
	}

	private void onResponseCheckRunning(boolean running){
		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.ResponseCheckRunningGateway, running)
		);
	}

	private void onMsgNotifyLog(Message receiveMessage){
		assert receiveMessage != null;

		if(receiveMessage.obj != null && receiveMessage.obj instanceof String){
			String logMessage = (String)receiveMessage.obj;

			onMsgNotifyLog(logMessage);
		}
	}

	private void onMsgNotifyLog(Context context, Intent intent){
		assert context != null && intent != null;

		String logMessage = intent.getStringExtra(MSG_ATTACHMENT_ID);

		if(logMessage != null)
			onMsgNotifyLog(logMessage);
	}

	private void onMsgNotifyLog(String logMessage){
		assert logMessage != null;

		synchronized (logs){
			while(logs.size() > logLimit){logs.poll();}
			logs.add(logMessage);
		}

		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.NotifyLog, logMessage)
		);
	}

	private void onMsgNotifyStatusReport(Context context, Intent intent){
		assert context != null && intent != null;

		NoraGatewayStatusInformation statusInfo =
				Parcels.unwrap(intent.getParcelableExtra(MSG_ATTACHMENT_ID));

		onMsgNotifyStatusReport(statusInfo);
	}

	private void onMsgNotifyStatusReport(NoraGatewayStatusInformation statusInfo){
		assert statusInfo != null;

		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.NotifyStatusReport, statusInfo)
		);
	}

	private void onMsgNotifySavedLogs(Message receiveMessage){
		assert receiveMessage != null;

		if(receiveMessage.obj != null && receiveMessage.obj instanceof String[]){
			String[] savedLogs = (String[])receiveMessage.obj;

			onMsgNotifySavedLogs(savedLogs);
		}
	}

	private void onMsgNotifySavedLogs(Context context, Intent intent){
		assert context != null && intent != null;

		String[] savedLogs = intent.getStringArrayExtra(MSG_ATTACHMENT_ID);

		if(savedLogs != null)
			onMsgNotifySavedLogs(savedLogs);
	}

	private void onMsgNotifySavedLogs(String[] savedLogs){
		assert savedLogs != null;

		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.NotifySavedLogs, savedLogs)
		);
	}

	private void onMsgResponseGatewayStart(Message receiveMessage){
		assert receiveMessage != null;

		if(receiveMessage.obj != null && receiveMessage.obj instanceof Boolean){
			Boolean success = (Boolean)receiveMessage.obj;

			onMsgResponseGatewayStart(success);
		}
	}

	private void onMsgResponseGatewayStart(Context context, Intent intent){
		assert context != null && intent != null;

		Boolean success = intent.getBooleanExtra(MSG_ATTACHMENT_ID, false);

		if(success != null)
			onMsgResponseGatewayStart(success);
	}

	private void onMsgResponseGatewayStart(boolean success){
		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.ResponseGatewayStart, success)
		);
	}

	private void onMsgResponseGatewayStop(Message receiveMessage){
		assert receiveMessage != null;

		onMsgResponseGatewayStop();
	}

	private void onMsgResponseGatewayStop(Context context, Intent intent){
		assert context != null && intent != null;

		onMsgResponseGatewayStop();
	}

	private void onMsgResponseGatewayStop(){
		getApplicationEventBus().post(
				new MainActivity.ApplicationEvent(MainActivity.ApplicationEventType.ResponseGatewayStop, null)
		);

		stopService();
	}

	private void onMsgNotifyExceptionError(Context context, Intent intent){

	}

	private void onMsgNotifyApplicationError(Context context, Intent intent){

	}

	private boolean startService(){
		serviceIntent = new Intent(this, NoraGatewayForAndroidService.class);
//		bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		if(!isServiceRunning()){startService(serviceIntent);}

		return true;
	}

	private void stopService(){
//		unbindService(serviceConnection);
		if(isServiceRunning()){stopService(serviceIntent);}
	}

	private void terminate(){
		usbMonitorUtil.unregister();

		if(getApplicationEventBus().isRegistered(this))
			getApplicationEventBus().unregister(this);
	}

	private boolean isServiceRunning(){
		return ServiceUtil.isServiceWorking(getApplicationContext(), NoraGatewayForAndroidService.class);
	}

	private boolean sendMessageToService(Message msg){
		assert msg != null;

		try{
			if(serviceMessenger != null){
				msg.replyTo = selfMessenger;
				serviceMessenger.send(msg);
			}

			return true;
		}catch(RemoteException ex){
			log.warn("Could not send message.", ex);
		}

		return false;
	}

	private boolean sendMessageToService(Intent msg){
		assert msg != null;

		return sendMessageToService(msg, false);
	}

	private boolean sendMessageToService(Intent msg, boolean sync){
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

	private List<String> getUartPorts(){
		List<String> ports = new LinkedList<>();

		UartInterface uart = UartInterfaceFactory.createUartInterface(null);

		List<String> getPorts = uart.getUartPortList();

		if(getPorts != null)
			ports.addAll(getPorts);

		return ports;
	}
}
