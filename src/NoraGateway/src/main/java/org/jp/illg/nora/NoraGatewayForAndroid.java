package org.jp.illg.nora;

import android.content.Context;

import com.ftdi.j2xx.D2xxManager;


import org.jp.illg.dstar.gateway.DStarGatewayImpl;
import org.jp.illg.dstar.model.DStarGateway;
import org.jp.illg.dstar.model.DStarRepeater;
import org.jp.illg.dstar.model.config.RepeaterProperties;
import org.jp.illg.dstar.repeater.DStarRepeaterManager;
import org.jp.illg.dstar.service.hfdownloader.ReflectorHostFileDownloadService;
import org.jp.illg.nora.gateway.NoraGatewayConfiguration;
import org.jp.illg.nora.gateway.NoraGatewayConfigurator;
import org.jp.illg.nora.gateway.NoraGatewayUtil;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporterAndroid;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.nora.gateway.service.norausers.NoraUsersStatusReporter;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGatewayForAndroid extends ThreadBase {


//	private static final String applicationName = NoraGatewayForAndroid.class.getSimpleName();
//	private static final String applicationVersion = "0.0.5alpha";

	private static NoraGatewayForAndroid instance;
	private static final Lock instanceLocker = new ReentrantLock();

	private final Timer hostsFileLoadPeriodTimeKeeper;
	private long hostFileLastModified;

	/**
	 * メインスレッド
	 */
	@SuppressWarnings("unused")
	private Thread mainThread;

	/**
	 * アプリケーション設定ファイルインスタンス
	 */
	@Getter
	@Setter
	private static NoraGatewayConfiguration gatewayConfiguration = new NoraGatewayConfiguration();

	/**
	 * 例外捕捉リスナ
	 */
	private static ThreadUncaughtExceptionListener exceptionListener =
			new ThreadUncaughtExceptionListener() {
				@Override
				public void threadUncaughtExceptionEvent(
						Exception ex, ThreadBase thread
				) {
					uncaughtException = ex;
				}

				@Override
				public void threadFatalApplicationErrorEvent(
						String message, Exception ex, ThreadBase thread
				) {
					if (message != null)
						applicationError = message;
					else
						applicationError = "";

					uncaughtException = ex;
				}
			};

	/**
	 * 未捕捉例外
	 */
	private static Exception uncaughtException = null;

	/**
	 * アプリケーションエラー
	 */
	private static String applicationError = null;

	/**
	 * デバッグフラグ
	 */
	private static boolean debug = false;

	private NoraGatewayStatusReportListener statusReportListener;

	private NoraGatewayStatusReporterAndroid statusReporter;
	
	private NoraUsersStatusReporter noraUsersStatusReporter;
	
	private SocketIO localSocketIO;

	private static Context context;
	
	private ReflectorHostFileDownloadService reflectorHostFileDownloadService;

	private ExecutorService workerExecutor;


	public static NoraGatewayForAndroid getInstance(
		ThreadUncaughtExceptionListener exceptionListener,
		Context context,
		NoraGatewayStatusReportListener statusReportListener
	) {
		synchronized (instanceLocker) {
			if (instance != null)
				return instance;
			else
				return (instance = new NoraGatewayForAndroid(exceptionListener, context, statusReportListener));
		}
	}

	private NoraGatewayForAndroid(
		ThreadUncaughtExceptionListener exceptionListener,
		Context context,
		NoraGatewayStatusReportListener statusReportListener
	) {
		super(exceptionListener, NoraGatewayForAndroid.class.getSimpleName());

		if (context == null) {
			throw new IllegalArgumentException("Context must not null.");
		}

		NoraGatewayForAndroid.context = context;
		this.statusReportListener = statusReportListener;
		mainThread = Thread.currentThread();

		hostsFileLoadPeriodTimeKeeper = new Timer();
		hostFileLastModified = 0;
		
		reflectorHostFileDownloadService = null;

		workerExecutor = null;
	}

	public static String getApplicationName() {
		return NoraGatewayUtil.getApplicationName();
	}

	public static String getApplicationVersion() {
		return NoraGatewayUtil.getApplicationVersion();
	}

	public static boolean readGatewayConfiguration(int configResource) {
		if (context == null || configResource <= 0) {
			return false;
		}

		InputStream config = null;
		try {
			config = context.getResources().openRawResource(configResource);

			return NoraGatewayConfigurator.readConfiguration(gatewayConfiguration, config);
		} finally {
			try {
				if (config != null) {
					config.close();
				}
			} catch (IOException ex) {
			}
		}
	}

	public List<String> getUartPortList() {
		List<String> ports = new ArrayList<>();

		if (context != null) {
			D2xxManager manager = null;
			try {
				manager = D2xxManager.getInstance(context);
			} catch (D2xxManager.D2xxException ex) {
				log.warn("Could not get D2xx instance.", ex);
			}
			if (manager != null) {
				int devices = manager.createDeviceInfoList(context);

				D2xxManager.FtDeviceInfoListNode deviceInfo[] =
						new D2xxManager.FtDeviceInfoListNode[devices];
				manager.getDeviceInfoList(devices, deviceInfo);

				for (D2xxManager.FtDeviceInfoListNode node : deviceInfo) {
					ports.add("[" + node.serialNumber + "]" + "," + node.description);
				}
			}
		}

		return ports;
	}

	public boolean loadReflectorHosts(String filePath){
		if(filePath == null || "".equals(filePath)){return false;}

		DStarGateway gateway = DStarGatewayImpl.getCreatedGateway();
		if(gateway == null){return false;}

		return gateway.loadReflectorHosts(filePath, true);
	}

	public boolean start(NoraGatewayConfiguration configuration) {
		if (configuration == null) {
			return false;
		}

		gatewayConfiguration = configuration;

		return this.start();
	}

	@Override
	protected ThreadProcessResult threadInitialize() {
		if(log.isInfoEnabled())
			log.info(getApplicationName() + " Version" + getApplicationVersion());

		if(workerExecutor != null){ workerExecutor.shutdown(); }
		workerExecutor = Executors.newFixedThreadPool(2, new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				final Thread t = new Thread(r);
				t.setName("NoraWorker_" + t.getId());

				return t;
			}
		});

		if (!startGatewayRepeaters()) {
			return ThreadProcessResult.FatalError;
		}
		
		if(localSocketIO != null){
			localSocketIO.stop();
			localSocketIO = null;
		}
		localSocketIO = new SocketIO(exceptionListener, workerExecutor);
		if(!localSocketIO.start()){
			if(log.isErrorEnabled())
				log.error("Could not start socket io thread.");
			
			return ThreadProcessResult.FatalError;
		}

		if (statusReportListener != null) {
			statusReporter = new NoraGatewayStatusReporterAndroid(
					getExceptionListener(),
					statusReportListener,
					TimeUnit.MILLISECONDS.toMillis(500)
			);
			statusReporter.start();
		} else {
			statusReporter = null;
			
			if(log.isDebugEnabled())
				log.debug("Status report listener is not set, Status reporter is disable.");
		}
		
		if(noraUsersStatusReporter != null){
			if(statusReporter != null)
				statusReporter.removeListener(noraUsersStatusReporter);
			
			noraUsersStatusReporter = null;
		}
		noraUsersStatusReporter =
			new NoraUsersStatusReporter(
					exceptionListener,
					localSocketIO,
					"k-dk.net", 52180
			);
		statusReporter.addListener(noraUsersStatusReporter);
		
		if(log.isInfoEnabled())
			log.info("Configuration file read and logger configuration completed.");

		if(log.isInfoEnabled()){
			log.info(
				getApplicationName() + "@" + NoraGatewayUtil.getRunningOperatingSystem() +
					" " + getApplicationVersion() +
					" started."
			);
		}

		hostFileLastModified = 0;
		
		reflectorHostFileDownloadService =
			new ReflectorHostFileDownloadService(DStarGatewayImpl.getCreatedGateway());
		reflectorHostFileDownloadService.setProperties(
			gatewayConfiguration.getReflectorHostFileDownloadServiceProperties()
		);

		return ThreadProcessResult.NoErrors;
	}
	
	@Override
	protected void threadFinalize() {
		stopGatewayRepeaters();
		
		if(noraUsersStatusReporter != null && statusReporter != null){
			statusReporter.removeListener(noraUsersStatusReporter);
			noraUsersStatusReporter = null;
		}
		
		if (statusReporter != null && statusReporter.isRunning()) {
			statusReporter.stop();
			statusReporter = null;
		}
		
		if(localSocketIO != null){
			localSocketIO.stop();
			localSocketIO = null;
		}

		if(workerExecutor != null){workerExecutor.shutdown();}
		
		if(log.isInfoEnabled())
			log.info(getApplicationName() + " stopped.");
	}
	
	@Override
	protected ThreadProcessResult process() {
		return processGateway() ? ThreadProcessResult.NoErrors : ThreadProcessResult.FatalError;
	}
	
	private DStarGateway initializeGateway() {
		DStarGateway gateway = null;

		//ゲートウェイが既に作られていたら停止して削除
		gateway = DStarGatewayImpl.getCreatedGateway();
		if(gateway != null){
			if(gateway.isRunning()){gateway.stop();}
			DStarGatewayImpl.removeGateway();
		}

		gateway = DStarGatewayImpl.createGateway(
				exceptionListener,
				gatewayConfiguration.getGatewayProperties().getCallsign(),
				workerExecutor,
				NoraGatewayUtil.getApplicationName(),
				NoraGatewayUtil.getApplicationVersion(),
				NoraGatewayUtil.getRunningOperatingSystem()
		);
		if (gateway == null) {
			return null;
		}

		gateway.setProperties(gatewayConfiguration.getGatewayProperties());

		if(log.isInfoEnabled())
			log.info("    Created gateway..." + gateway.getGatewayCallsign());

		return gateway;
	}

	private boolean initializeRepeaters(DStarGateway gateway) {
		assert gateway != null;

		//レピータが既に作られていたら停止して削除する
		for(DStarRepeater repeater : DStarRepeaterManager.getRepeaters()){
			if(repeater.isRunning()){repeater.stop();}
		}
		DStarRepeaterManager.removeRepeaters(true);

		//設定ファイルからレピータを作成
		for (RepeaterProperties repeaterProperties : gatewayConfiguration.getRepeaterProperties().values()) {

			final DStarRepeater repeater =
				DStarRepeaterManager.createRepeater(
					gateway, repeaterProperties.getType(), repeaterProperties.getCallsign(),
					repeaterProperties, localSocketIO, workerExecutor
				);
			if (repeater == null) { continue; }

//			if (!repeater.setProperties(repeaterProperties)) {
//				return false;
//			}

			if(log.isInfoEnabled())
				log.info("    Created repeater..." + repeater.getRepeaterCallsign());
		}

		if (DStarRepeaterManager.getRepeaters().isEmpty()) {
			if(log.isErrorEnabled())
				log.error("There is no definication repeater and at least one is necessary.");
			
			return false;
		}

		return true;
	}

	private boolean processGateway() {
		if (uncaughtException != null && applicationError == null) {
			if(log.isErrorEnabled())
				log.error("Uncaught exception occurred.", uncaughtException);
			
			return false;
		} else if (applicationError != null) {
			final String message = "Application error occurred.\n" + applicationError;
			if (uncaughtException != null) {
				if(log.isErrorEnabled())
					log.error(message, uncaughtException);
			}
			else {
				if(log.isErrorEnabled())
					log.error(message);
			}
			
			return false;
		}
		else {
			processHostsFile();
			
			reflectorHostFileDownloadService.processService();;

			return true;
		}
	}

	private boolean processHostsFile(){
		if(hostsFileLoadPeriodTimeKeeper.isTimeout()){
			hostsFileLoadPeriodTimeKeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			hostsFileLoadPeriodTimeKeeper.updateTimestamp();

			File dir = context.getExternalFilesDir(null);
			if(dir == null){return false;}
			
			String path = dir.getAbsolutePath() + File.separator + "hosts.txt";
			File hostsFile = new File(path);
				
			if(
				hostsFile.isFile() && hostsFile.canRead() &&
				hostFileLastModified < hostsFile.lastModified()
			){
				if(hostFileLastModified != 0) {
					if(log.isInfoEnabled())
						log.info("Hosts file update detected, loading hosts file...");
				}
				else {
					if(log.isInfoEnabled())
						log.info("Hosts file found, loading hosts file...");
				}
				
				hostFileLastModified = hostsFile.lastModified();

				loadReflectorHosts(hostsFile.getPath());
			}
		}

		return true;
	}

	private boolean startGatewayRepeaters() {
		DStarGateway gateway = null;

		gateway = initializeGateway();
		if(gateway == null){
			gateway = DStarGatewayImpl.getCreatedGateway();
		}
		if(gateway != null && gateway.isRunning()){gateway.stop();}

		if (gateway == null || !gateway.start()) {
			if(log.isErrorEnabled()){
				log.error(
					"Could not start gateway..." +
						(
							gateway != null ?
								gateway.getGatewayCallsign() :
								getGatewayConfiguration().getGatewayProperties().getCallsign()
						)
				);
			}

			return false;
		}

		if (!initializeRepeaters(gateway)) {
			return false;
		}
		for (DStarRepeater repeater : DStarRepeaterManager.getRepeaters()) {
			if (!repeater.start()) {
				if(log.isErrorEnabled())
					log.error("Could not start repeater..." + repeater.getRepeaterCallsign());
				
				return false;
			}
		}

		return true;
	}

	private void stopGatewayRepeaters() {
		DStarGateway gateway = DStarGatewayImpl.getCreatedGateway();
		String gatewayCallsign = gateway.getGatewayCallsign();
		if (gateway != null) {
			gateway.stop();
		}
		DStarGatewayImpl.removeGateway();
		if(log.isInfoEnabled())
			log.info("    Remove Gateway..." + gatewayCallsign);

		List<String> repeaterCallsigns = new ArrayList<>();
		for (DStarRepeater repeater : DStarRepeaterManager.getRepeaters()) {
			if (repeater != null && repeater.isRunning()) {
				repeater.stop();
			}
			repeaterCallsigns.add(repeater.getRepeaterCallsign());
		}
		DStarRepeaterManager.removeRepeaters(false);
		for (String repeaterCallsign : repeaterCallsigns) {
			if(log.isInfoEnabled())
				log.info("    Remove Repeater..." + repeaterCallsign);
		}
	}

}
