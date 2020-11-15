package org.jp.illg.nora;

import android.content.Context;

import com.ftdi.j2xx.D2xxManager;

import org.jp.illg.dstar.DSTARSystemManager;
import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.service.hfdownloader.ReflectorHostFileDownloadService;
import org.jp.illg.dstar.service.reflectorhosts.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.model.WebRemoteControlServiceEvent;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.gateway.NoraGatewayConfiguration;
import org.jp.illg.nora.gateway.NoraGatewayConfigurator;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporter;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporterAndroid;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.nora.gateway.service.norausers.NoraCrashReporter;
import org.jp.illg.nora.gateway.service.norausers.NoraUsersAPIService;
import org.jp.illg.noragateway.NoraGateway;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
public class NoraGatewayForAndroid {

	private static final String apiServerAddress = "kdkapi.k-dk.net";
	private static final int apiServerPort = 52180;

	private static final String logTag = NoraGatewayForAndroid.class.getSimpleName() + " : ";
	private static final ApplicationInformation<?> applicationVersionInfo = NoraGateway.getApplicationInformation();

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
	 * システムID
	 */
	private final UUID systemID;

	/**
	 * 親の例外補足リスナ
	 */
	private final ThreadUncaughtExceptionListener parentExceptionListener;

	/**
	 * 親のステータスリスナ
	 */
	private final NoraGatewayStatusReportListener parentStatusReportListener;

	/**
	 * アプリケーション設定ファイルインスタンス
	 */
	@Getter
	@Setter
	private NoraGatewayConfiguration gatewayConfiguration = new NoraGatewayConfiguration();

	/**
	 * 例外捕捉リスナ
	 */
	private static ThreadUncaughtExceptionListener exceptionListener =
			new ThreadUncaughtExceptionListener() {
				@Override
				public void threadUncaughtExceptionEvent(Exception ex, Thread thread){
					uncaughtException = ex;
				}

				@Override
				public void threadFatalApplicationErrorEvent(String message, Exception ex, Thread thread) {
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

	private ThreadBase thread;

	private DSTARSystemManager.DSTARSystem dstarSystem;

	private Context context;
	



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
		super();

		if (context == null) {
			throw new IllegalArgumentException("Context must not null.");
		}

		this.parentExceptionListener = exceptionListener;
		this.parentStatusReportListener = statusReportListener;
		systemID = UUID.randomUUID();

		this.context = context;
		mainThread = Thread.currentThread();

		hostsFileLoadPeriodTimeKeeper = new Timer();
		hostFileLastModified = 0;
	}

	public static String getApplicationName() {
		return applicationVersionInfo.getApplicationName();
	}

	public static String getApplicationVersion() {
		return applicationVersionInfo.getApplicationVersion();
	}

	public boolean readGatewayConfiguration(int configResource) {
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
			} catch (IOException ex) { }
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

		final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
		if(gateway == null || !gateway.isRunning()){return false;}

		return gateway.loadReflectorHosts(filePath, true);
	}

	public boolean start(NoraGatewayConfiguration configuration) {
		if (configuration == null) {
			return false;
		}

		if(isRunning()) {stop();}

		gatewayConfiguration = configuration;

		thread = createThread();

		if(!thread.start()){
			stop();

			return false;
		}

		return true;
	}

	public void stop() {
		if(isRunning()) {thread.stop();}

		thread = null;
	}

	public boolean isRunning() {
		return thread != null && thread.isRunning();
	}

	private ThreadBase createThread() {
		return new ThreadBase(
			exceptionListener,
			NoraGatewayForAndroid.class.getSimpleName()
		) {
			@Override
			protected ThreadProcessResult threadInitialize() {
				return ThreadProcessResult.NoErrors;
			}

			@Override
			protected void threadFinalize() {
			}

			@Override
			protected ThreadProcessResult process() {
				try {
					final boolean isNormalTerminate = entryThread();

					if(!isNormalTerminate && parentExceptionListener != null)
						parentExceptionListener.threadFatalApplicationErrorEvent("", null, Thread.currentThread());

					return isNormalTerminate ? ThreadProcessResult.NormalTerminate : ThreadProcessResult.FatalError;
				}catch(final Exception ex){
					if(log.isErrorEnabled())
						log.error(logTag + "Application uncaught error", ex);

					if(parentExceptionListener != null)
						parentExceptionListener.threadUncaughtExceptionEvent(ex, Thread.currentThread());
				}

				return ThreadProcessResult.FatalError;
			}
		};
	}

	private boolean entryThread() throws Exception{
		if(log.isInfoEnabled())
			log.info(getApplicationName() + " Version" + getApplicationVersion());

		final ExecutorService workerExecutor =
			Executors.newFixedThreadPool(4, new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					final Thread t = new Thread(r);
					t.setName("NoraWorker_" + t.getId());

					return t;
				}
			});

		try(
			final ReflectorNameService reflectorNameService = new ReflectorNameService();
			final RepeaterNameService repeaterNameService = new RepeaterNameService(
				systemID,
				exceptionListener,
				workerExecutor
			);
			final WebRemoteControlService webRemoteControlService = new WebRemoteControlService(
				exceptionListener,
				applicationVersionInfo,
				workerExecutor,
				new EventListener<WebRemoteControlServiceEvent>() {
					@Override
					public void event(WebRemoteControlServiceEvent event, Object attachment) {

					}
				},
				"",
				"",
				0
			);
			final ReflectorHostFileDownloadService reflectorHostFileDownloadService =
				new ReflectorHostFileDownloadService(
					systemID,
					exceptionListener,
					workerExecutor,
					new EventListener<ReflectorHostFileDownloadService.DownloadHostsData>() {
						@Override
						public void event(ReflectorHostFileDownloadService.DownloadHostsData event, Object attachment) {
							final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
							if(gateway == null || !gateway.isRunning())
								return;

							gateway.loadReflectorHosts(event.getHosts(), event.getUrl().toExternalForm(), true);
						}
					}
				);
			final NoraGatewayStatusReporter statusReporter = new NoraGatewayStatusReporterAndroid(
				systemID,
				exceptionListener,
				applicationVersionInfo,
				TimeUnit.MILLISECONDS.toMillis(500)
			);
			final DSTARSystemManager.DSTARSystem dstarSystem = DSTARSystemManager.createSystem(
				systemID,
				exceptionListener,
				NoraGateway.getApplicationInformation(),
				gatewayConfiguration.getGatewayProperties(),
				gatewayConfiguration.getRepeaterProperties(),
				workerExecutor,
				webRemoteControlService,
				reflectorNameService,
				repeaterNameService
			);
		) {
			if(dstarSystem == null){
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to create dstar system");

				return false;
			}

			try(
				final NoraUsersAPIService noraUsersStatusReporter = new NoraUsersAPIService(
					exceptionListener,
					applicationVersionInfo,
					dstarSystem.getSocketIO(),
					apiServerAddress, apiServerPort,
					DSTARUtils.formatFullCallsign(
						dstarSystem.getGateway().getGatewayCallsign()
					)
				);
			){
				if(!isAllowVersion(noraUsersStatusReporter)){
					if(log.isErrorEnabled())
						log.error(logTag + "This version is too old, please update !");

					return false;
				}

				if(!webRemoteControlService.initialize(3000, "/nora")){
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to initialize web remote control service");

					return false;
				}

				statusReporter.addListener(noraUsersStatusReporter);
				statusReporter.addListener(parentStatusReportListener);

				if(!statusReporter.start()){
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to start status reporter");

					return false;
				}
				else if(!reflectorHostFileDownloadService.setProperties(
					gatewayConfiguration.getServiceProperties().getReflectorHostFileDownloadServiceProperties()
				)){
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to set properties to reflector host file download service");

					return false;
				}

				if(log.isInfoEnabled())
					log.info(logTag + "Starting system...");

				if(!dstarSystem.start()){
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to start system");

					return false;
				}

				if(log.isInfoEnabled()){
					log.info(
						getApplicationName() + "@" + applicationVersionInfo.getRunningOperatingSystem() +
						" " + getApplicationVersion() +
						" started."
					);
				}

				hostFileLastModified = 0;

				return mainLoop(
					reflectorNameService,
					repeaterNameService,
					webRemoteControlService,
					noraUsersStatusReporter,
					reflectorHostFileDownloadService,
					dstarSystem,
					statusReporter
				);
			}
		}finally{
			DSTARSystemManager.finalizeSystem(systemID);

			workerExecutor.shutdown();

			if(log.isInfoEnabled())
				log.info(getApplicationName() + " stopped.");
		}
	}

	private boolean mainLoop(
		final ReflectorNameService reflectorNameService,
		final RepeaterNameService repeaterNameService,
		final WebRemoteControlService webRemoteControlService,
		final NoraUsersAPIService apiService,
		final ReflectorHostFileDownloadService reflectorHostFileDownloadService,
		final DSTARSystemManager.DSTARSystem dstarSystem,
		final NoraGatewayStatusReporter statusReporter
	) throws Exception{
		uncaughtException = null;
		applicationError = null;

		try {
			while (true) {
				if (uncaughtException != null && applicationError == null) {
					throw uncaughtException;
				} else if (applicationError != null) {
					final String message = "Application error occurred.\n" + applicationError;

					if (uncaughtException != null) {
						if (log.isErrorEnabled())
							log.error(message, uncaughtException);
					} else {
						if (log.isErrorEnabled())
							log.error(logTag + message);
					}

					return false;
				}
				else if(apiService.isVersionDeny()) {
					if(log.isErrorEnabled())
						log.error(logTag + "This version is out of date, please update !");

					return false;
				}

				processHostsFile();

				reflectorNameService.processService();
				repeaterNameService.processService();
				if (webRemoteControlService != null) {
					webRemoteControlService.processService();
				}
				apiService.processService();
				reflectorHostFileDownloadService.processService();

				dstarSystem.processService();

				try {
					Thread.sleep(1000L);
				} catch (final InterruptedException ex) {
					return true;
				}
			}
		}catch(Exception ex) {
			if (log.isErrorEnabled())
				log.error("Uncaught exception occurred.", uncaughtException);

			if(apiService.getNoraId() != null){
				NoraCrashReporter.crashReport(
					apiServerAddress, apiServerPort,
					apiService.getNoraId(),
					applicationVersionInfo,
					DSTARUtils.formatFullCallsign(
						gatewayConfiguration.getGatewayProperties().getCallsign()
					),
					uncaughtException
				);
			}

			throw ex;
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

	private boolean isAllowVersion(
		final NoraUsersAPIService service
	) {
		final Timer checkVersionTimekeeper = new Timer();
		checkVersionTimekeeper.updateTimestamp();

		if(log.isInfoEnabled())
			log.info(logTag + "Checking version to kdk api server...");

		boolean isAllowVersion = false;

		while(!checkVersionTimekeeper.isTimeout(10, TimeUnit.MINUTES)) {
			service.processService();

			if(service.isVersionAllow() || service.isVersionDeny()) {
				isAllowVersion = service.isVersionAllow() && !service.isVersionDeny();
				break;
			}

			try {
				Thread.sleep(1000L);
			}catch(InterruptedException ex) {
				return false;
			}
		}

		if(isAllowVersion) {
			if(log.isInfoEnabled())
				log.info(logTag + "Version check result OK !(" + applicationVersionInfo.getApplicationVersion() + ")");
		}
		else {
			if(log.isErrorEnabled())
				log.error(logTag + "Version check result NG !(" + applicationVersionInfo.getApplicationVersion() + ")");
		}

		return isAllowVersion;
	}
}
