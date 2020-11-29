package org.jp.illg.nora.gateway;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARSystemManager;
import org.jp.illg.dstar.DSTARSystemManager.DSTARSystem;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.reporter.model.BasicStatusInformation;
import org.jp.illg.dstar.service.icom.IcomRepeaterCommunicationService;
import org.jp.illg.dstar.service.reflectorname.ReflectorNameService;
import org.jp.illg.dstar.service.repeatername.RepeaterNameService;
import org.jp.illg.dstar.service.web.WebRemoteControlService;
import org.jp.illg.dstar.service.web.model.WebRemoteControlServiceEvent;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporter;
import org.jp.illg.nora.gateway.reporter.NoraGatewayStatusReporterWinLinux;
import org.jp.illg.nora.gateway.reporter.model.NoraGatewayStatusReportListener;
import org.jp.illg.nora.gateway.service.norausers.NoraCrashReporter;
import org.jp.illg.nora.gateway.service.norausers.NoraUsersAPIService;
import org.jp.illg.nora.gateway.service.statusfileout.StatusInformationFileOutputService;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.io.fum.FileUpdateMonitoringFunction;
import org.jp.illg.util.io.fum.FileUpdateMonitoringTool;
import org.jp.illg.util.logback.LogbackConfigurator;
import org.jp.illg.util.mon.cpu.CPUUsageMonitorTool;
import org.jp.illg.util.mon.cpu.model.CPUUsageReport;
import org.jp.illg.util.mon.cpu.model.ThreadCPUUsageReport;
import org.jp.illg.util.socketio.SocketIO;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGatewayThread{

	/**
	 * KdkAPIサーバーポート
	 */
	private static final int apiServerPort = 52180;

	/**
	 * KdkAPIサーバーアドレス
	 */
	private static final String apiServerAddress = "kdkapi.k-dk.net";

	/**
	 * logback設定ファイルパス
	 */
	private static final String logbackConfigurationFilePath =
		System.getProperty("user.dir") + File.separator +
		"config" + File.separator +
		"logback.xml";

	/**
	 * ホストファイルパス
	 */
	private static String hostsFilePath =
		System.getProperty("user.dir") + File.separator +
		"config" + File.separator +
		"hosts.txt";

	private static final String logTag =
		NoraGatewayThread.class.getSimpleName() + " : ";

	/**
	 * アプリケーションバージョン情報
	 */
	private final ApplicationInformation<NoraGateway> applicationVersionInfo;

	/**
	 * システムID
	 */
	private final UUID systemID;

	/**
	 * NoraID
	 */
	private UUID noraId;

	/**
	 * 未捕捉例外
	 */
	private Exception uncaughtException = null;

	/**
	 * アプリケーションエラー
	 */
	private String applicationError = null;

	/**
	 * アプリケーション設定ファイルパス
	 */
	private final Path applicationConfigurationFilePath;

	/**
	 * アプリケーション設定ファイルインスタンス
	 */
	private NoraGatewayConfiguration appProperties = new NoraGatewayConfiguration();

	/**
	 * ゲートウェイインスタンス
	 */
	private DSTARGateway gateway;

	/**
	 * CPU使用率ログ出力間隔タイマ
	 */
	private final Timer cpuUsageReportOutputIntervalTimekeeper;

	/**
	 * CPU使用率データ取得間隔タイマ
	 */
	private final Timer cpuUsageReportUpdateIntervalTimekeeper;

	/**
	 * CPU使用率取得ツール
	 */
	private final CPUUsageMonitorTool cpuUsageMonitorTool;

	/**
	 * デバッグフラグ
	 */
	private boolean isDebug = false;

	/**
	 * サービスモード
	 */
	private boolean isServiceMode = false;

	/**
	 * デーモンモード
	 */
	private boolean isDaemonMode = false;

	/**
	 * ワーカースレッド数
	 */
	private int workerThreads;

	/**
	 * アプリケーション内再起動リクエストフラグ
	 */
	private boolean isRequestReboot;

	/**
	 * 返却コード
	 *
	 * 正常であれば0
	 */
	@Getter
	private int resultCode = 0;

	/**
	 * ボススレッド
	 */
	private final ThreadBase bossThread = new ThreadBase(
		this.exceptionListener,
		this.getClass().getSimpleName()
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
			resultCode = entry() ? 0 : -1;

			return ThreadProcessResult.NormalTerminate;
		}
	};

	/**
	 * 例外捕捉リスナ
	 */
	private final ThreadUncaughtExceptionListener exceptionListener =
		new ThreadUncaughtExceptionListener() {
			@Override
			public void threadUncaughtExceptionEvent(
				final Exception ex, final Thread thread
			) {
				uncaughtException = ex;
			}

			@Override
			public void threadFatalApplicationErrorEvent(
				final String message, final Exception ex, final Thread thread
			) {
				if(message != null)
					applicationError = message;
				else
					applicationError = "";

				uncaughtException = ex;
			}
		};

	private final FileUpdateMonitoringFunction hostsFileMonitoringFunction =
		new FileUpdateMonitoringFunction() {
			@Override
			public String getTargetFilePath() {
				return hostsFilePath;
			}

			@Override
			public int getMonitoringIntervalTimeSeconds() {
				return 10;
			}

			@Override
			public boolean initialize(InputStream targetFile) {
				return true;
			}

			@Override
			public boolean fileUpdate(InputStream targetFile) {
				if(log.isInfoEnabled())
					log.info(logTag + "Hosts file update detected, reloading hosts file..." + hostsFilePath);

				if(gateway != null)
					gateway.loadReflectorHosts(hostsFilePath, true);

				return true;
			}

			@Override
			public boolean rollback(InputStream targetFile) {
				return true;
			}
		};

	public NoraGatewayThread(
		@NonNull final UUID systemID,
		@NonNull final ApplicationInformation<NoraGateway> applicationVersionInfo,
		@NonNull final Path applicationConfigurationFilePath,
		final boolean isDebug,
		final boolean isServiceMode,
		final boolean isDaemonMode,
		final int workerThreads
	) {
		super();

		this.systemID = systemID;
		this.applicationVersionInfo = applicationVersionInfo;
		this.applicationConfigurationFilePath = applicationConfigurationFilePath;
		this.isDebug = isDebug;
		this.isServiceMode = isServiceMode;
		this.isDaemonMode = isDaemonMode;
		this.workerThreads = workerThreads;
		if(workerThreads < 1)
			throw new IllegalArgumentException("Worker threads must > 1");

		cpuUsageReportOutputIntervalTimekeeper = new Timer(1, TimeUnit.MINUTES);
		cpuUsageReportUpdateIntervalTimekeeper = new Timer(30, TimeUnit.SECONDS);
		cpuUsageMonitorTool = new CPUUsageMonitorTool();

		gateway = null;

		isRequestReboot = false;
		noraId = null;
	}

	public boolean start() {
		return bossThread.start();
	}

	public void stop() {
		bossThread.stop();
	}

	public boolean awaitThreadTerminate() {
		return bossThread.waitThreadTerminate();
	}

	/**
	 * エントリポイント
	 *
	 * @return
	 */
	private boolean entry() {
		if(log.isInfoEnabled()) {
			log.info(
				logTag +
				"[Environment Information]" + SystemUtil.getLineSeparator() +
				applicationVersionInfo.getApplicationName() +
				(!applicationVersionInfo.isBuildRelease() ? "(DEBUG)" : "") +
				" Version" + applicationVersionInfo.getApplicationVersion() +
				"@" + applicationVersionInfo.getRunningOperatingSystem() + SystemUtil.getLineSeparator() +

				(applicationVersionInfo.isGitDirty() ? "[!]" : "") +
				applicationVersionInfo.getGitCommitID() + "@" + applicationVersionInfo.getGitBranchName() +
				" by " + applicationVersionInfo.getGitCommitterName() +
				"(" + applicationVersionInfo.getGitCommitterEMail() + ")" + SystemUtil.getLineSeparator() +

				"Build by " + applicationVersionInfo.getBuilderName() + "(" + applicationVersionInfo.getBuilderEMail() + ")@" +
				applicationVersionInfo.getBuildTime() + SystemUtil.getLineSeparator() +
				(
					applicationVersionInfo.isGitDirty() ?
						SystemUtil.getLineSeparator() +
						"*** This build contains code that has not been committed to Git ***" +
						SystemUtil.getLineSeparator() :
						""
				) +

				SystemUtil.getJVMInformation(4)
			);
		}

		isRequestReboot = false;
		boolean isSuccess = false;
		do {
			applicationError = null;
			uncaughtException = null;
			isRequestReboot = false;

			isSuccess = entryThread();
		}while(isSuccess && isRequestReboot);

		return isSuccess;
	}

	/**
	 * アプリケーションメインルーチン
	 * @return 正常終了ならtrue/異常終了ならfalse
	 */
	private boolean entryThread() {

		NoraConsoleStatusViewer consoleViewer = null;
		ExecutorService workerExecutor = null;
		FileUpdateMonitoringTool fileUpdateMonitoringTool = null;

		try{
			workerExecutor = Executors.newFixedThreadPool(workerThreads, new ThreadFactory() {
				@Override
				public Thread newThread(Runnable r) {
					final Thread thread = new Thread(r);
					thread.setName("NoraWorker_" + thread.getId());

					return thread;
				}
			});

			fileUpdateMonitoringTool =
				new FileUpdateMonitoringTool(exceptionListener, workerExecutor);
			fileUpdateMonitoringTool.addFunction(
				new LogbackConfigurator(logbackConfigurationFilePath)
					.getFileUpdateMonitoringFunction()
			);

			//logback設定
			if(!fileUpdateMonitoringTool.initialize()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not initialize logger, file = " + logbackConfigurationFilePath);
			}

			//アプリケーション設定読み込み
			if(log.isInfoEnabled())
				log.info(logTag + "Loading application configuration file = " + applicationConfigurationFilePath.toString());

			if(!NoraGatewayConfigurator.readConfiguration(
				applicationVersionInfo,
				appProperties, applicationConfigurationFilePath.toFile()
			)) {
				return false;
			}
			//ホストファイルパスを確定
			hostsFilePath = appProperties.getGatewayProperties().getHostsFile();
			if(log.isInfoEnabled())
				log.info(logTag + "Host file path = " + hostsFilePath);
			//ホストファイル監視設定
			fileUpdateMonitoringTool.addFunction(hostsFileMonitoringFunction);

			if(!isServiceMode && !isDaemonMode) {
				consoleViewer =
					new NoraConsoleStatusViewer(
						systemID,
						isDebug,
						exceptionListener,
						applicationVersionInfo
					);

				if(!consoleViewer.start()) {
					if(log.isErrorEnabled())
						log.error(logTag + "Could not start console viewer.");

					return false;
				}
			}

			return mainProcess(workerExecutor, fileUpdateMonitoringTool);
		}
		finally {
			if(!workerExecutor.isShutdown()) {workerExecutor.shutdown();}
			if(consoleViewer != null && consoleViewer.isRunning()) {consoleViewer.stop();}
		}
	}

	private boolean mainProcess(
		final ExecutorService workerExecutor,
		final FileUpdateMonitoringTool fileUpdateMonitoringTool
	) {
		try(
			final ReflectorNameService reflectorNameService = new ReflectorNameService(
				systemID,
				exceptionListener,
				workerExecutor
			);

			final RepeaterNameService repeaterNameService = new RepeaterNameService(
				systemID,
				exceptionListener,
				workerExecutor
			);

			final NoraGatewayStatusReporter statusReporter = new NoraGatewayStatusReporterWinLinux(
				systemID, exceptionListener, applicationVersionInfo, cpuUsageMonitorTool
			);

			final WebRemoteControlService webRemoteControlService =
				appProperties.getServiceProperties().getWebRemoteControlServiceProperties().isEnable() ?
				new WebRemoteControlService(
					exceptionListener,
					applicationVersionInfo,
					workerExecutor,
					new EventListener<WebRemoteControlServiceEvent>() {
						@Override
						public void event(WebRemoteControlServiceEvent event, Object attachment) {
							if(event == WebRemoteControlServiceEvent.RequestRestart) {
								if(log.isInfoEnabled())
									log.info(logTag + "Restart request from WebRemoteControlService...");

								isRequestReboot = true;
							}
						}
					},
					appProperties.getServiceProperties().getWebRemoteControlServiceProperties().getUserListFile(),
					applicationConfigurationFilePath.normalize().toFile().getAbsolutePath(),
					appProperties.getServiceProperties().getHelperServiceProperties().getPort()
				) : null;

			final StatusInformationFileOutputService statusFileOutputService =
				appProperties.getServiceProperties().getStatusInformationFileOutputServiceProperties().isEnable() ?
				new StatusInformationFileOutputService(
					applicationVersionInfo,
					statusReporter,
					appProperties.getServiceProperties()
						.getStatusInformationFileOutputServiceProperties().getOutputPath()
				) : null;

			final DSTARSystem dstarSystem = DSTARSystemManager.createSystem(
				systemID,
				exceptionListener,
				applicationVersionInfo,
				appProperties.getGatewayProperties(),
				appProperties.getRepeaterProperties(),
				workerExecutor,
				webRemoteControlService,
				reflectorNameService,
				repeaterNameService
			);

			final IcomRepeaterCommunicationService icomRepeaterCommunicationService =
				appProperties.getServiceProperties().getIcomRepeaterCommunicationServiceProperties().isEnable() ?
				IcomRepeaterCommunicationService.createInstance(
					systemID, exceptionListener, workerExecutor, dstarSystem.getSocketIO(), dstarSystem.getGateway()
				) : null;
		) {
			if(dstarSystem == null){
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to create dstar system");

				return false;
			}
			this.gateway = dstarSystem.getGateway();

			final NoraUsersAPIService noraUsersAPIService =
				new NoraUsersAPIService(
					exceptionListener, applicationVersionInfo, dstarSystem.getSocketIO(),
					apiServerAddress, apiServerPort,
					DSTARUtils.formatFullCallsign(appProperties.getGatewayProperties().getCallsign(), ' ')
			);

			if(!checkAllowVersion(noraUsersAPIService)) {
				if(log.isErrorEnabled())
					log.error(logTag + "This software version is out of date, please update !");

				System.err.println("This software version is out of date, please update !");

				return false;
			}
			noraId = noraUsersAPIService.getNoraId();

			statusReporter.addListener(noraUsersAPIService);

			if(!statusReporter.start()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not start status reporter.");

				return false;
			}

			if(webRemoteControlService != null) {
				if(!webRemoteControlService.initialize(
					appProperties.getServiceProperties().getWebRemoteControlServiceProperties().getPort(),
					appProperties.getServiceProperties().getWebRemoteControlServiceProperties().getContext()
				)) {
					if(log.isErrorEnabled())
						log.error(logTag + "Failed to initialize web remote service.");

					return false;
				}
				statusReporter.addListener(new NoraGatewayStatusReportListener() {
					@Override
					public void report(BasicStatusInformation info) {
						webRemoteControlService.setStatusInformation(info);
					}

					@Override
					public void listenerProcess() {
					}
				});
			}

			if(!reflectorNameService.initialize(
				appProperties.getServiceProperties().getReflectorNameServiceProperties())
			) {
				if(log.isErrorEnabled())
					log.error(logTag + "Could not initialize ReflectorNameService.");

				return false;
			}
			if(!repeaterNameService.initialize(
				appProperties.getServiceProperties().getRepeaterNameServiceProperties()
			)) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to initialize RepeaterNameService.");

				return false;
			}
			else if(
				icomRepeaterCommunicationService != null &&
				!icomRepeaterCommunicationService.setProperties(
					appProperties.getServiceProperties().getIcomRepeaterCommunicationServiceProperties()
				)
			) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to initialize icom repeater communication service");

				return false;
			}

			if(!dstarSystem.start()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to start dstar system");

				return false;
			}
			else if(webRemoteControlService != null && !webRemoteControlService.start()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to start web remote service.");

				return false;
			}
			else if(icomRepeaterCommunicationService != null && !icomRepeaterCommunicationService.start()) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to start icom repeater communication service");

				return false;
			}

			if(log.isInfoEnabled()) {
				log.info(logTag + "Configuration file read and logger configuration completed.");

				log.info(logTag + applicationVersionInfo.getApplicationName() + " started.");
			}

			return mainLoop(
				reflectorNameService,
				repeaterNameService,
				webRemoteControlService,
				noraUsersAPIService,
				statusReporter,
				dstarSystem.getSocketIO(),
				workerExecutor,
				fileUpdateMonitoringTool,
				dstarSystem
			);
		}
	}

	private boolean mainLoop(
		final ReflectorNameService reflectorNameService,
		final RepeaterNameService repeaterNameService,
		final WebRemoteControlService webRemoteControlService,
		final NoraUsersAPIService apiService,
		final NoraGatewayStatusReporter reporter,
		final SocketIO localSocketIO,
		final ExecutorService workerExecutor,
		final FileUpdateMonitoringTool fileUpdateMonitoringTool,
		final DSTARSystem dstarSystem
	) {
		if(log.isInfoEnabled()) {
			log.info(
				logTag +
				applicationVersionInfo.getApplicationName() +
				(!applicationVersionInfo.isBuildRelease() ? "(DEBUG)" : "") +
				" Version" + applicationVersionInfo.getApplicationVersion() +
				"@" + applicationVersionInfo.getRunningOperatingSystem() + "\n" +

				(applicationVersionInfo.isGitDirty() ? "[!]" : "") +
				applicationVersionInfo.getGitCommitID() + "@" + applicationVersionInfo.getGitBranchName() +
				" by " + applicationVersionInfo.getGitCommitterName() +
				"(" + applicationVersionInfo.getGitCommitterEMail() + ")" + "\n" +

				"Build by " + applicationVersionInfo.getBuilderName() + "(" + applicationVersionInfo.getBuilderEMail() + ")@" +
				applicationVersionInfo.getBuildTime() +

				(applicationVersionInfo.isGitDirty() ? "\n\n*** This build contains code that has not been committed to Git ***" : "")
			);
		}

		try {
			final BufferedReader stdinReader = new BufferedReader(new InputStreamReader(System.in));

			isRequestReboot = false;
			while(
				applicationError == null && uncaughtException == null &&
				!isRequestReboot
			) {
				reflectorNameService.processService();
				repeaterNameService.processService();

				apiService.processService();

				processCPUUsageReport();

				if(webRemoteControlService != null)
					webRemoteControlService.processService();

				fileUpdateMonitoringTool.process();

				if(stdinReader.ready()) {break;}
				else if(apiService.isVersionDeny()) {
					if(log.isErrorEnabled())
						log.error(logTag + "This version is out of date, please update !");

					System.err.println("This version is out of date, please update !");

					break;
				}

				dstarSystem.processService();

				Thread.sleep(TimeUnit.MILLISECONDS.toMillis(1000));
			}

			if(isRequestReboot) {
				if(log.isInfoEnabled()) {log.info(logTag + "Rebooting...");}
			}

			if(applicationError != null) {
				if(log.isErrorEnabled()) {
					log.error(
						logTag +
							"Application fatal error occurred.\n" + applicationError,
						uncaughtException
					);
				}
			}
			else if(uncaughtException != null) {
				throw uncaughtException;
			}

			return true;
		}catch(Exception ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Application uncaught error occurred.", ex);

			if(
				noraId != null &&
				appProperties.getServiceProperties().getCrashReportServiceProperties().isEnable()
			) {
				try {
					NoraCrashReporter.crashReport(
						apiServerAddress, apiServerPort,
						noraId,
						applicationVersionInfo,
						DSTARUtils.formatFullCallsign(
							appProperties.getGatewayProperties().getCallsign()
						),
						ex
					);
				}catch(Exception ex2) {
					if(log.isErrorEnabled())
						log.error(logTag + "Could not send crash report", ex2);
				}
			}
		}

		return false;
	}

	private boolean processCPUUsageReport() {

		if(cpuUsageReportUpdateIntervalTimekeeper.isTimeout()) {
			cpuUsageReportUpdateIntervalTimekeeper.setTimeoutTime(10, TimeUnit.SECONDS);
			cpuUsageReportUpdateIntervalTimekeeper.updateTimestamp();

			cpuUsageMonitorTool.measure();
		}
		else if(
			isDebug &&
			cpuUsageReportOutputIntervalTimekeeper.isTimeout() &&
			log.isInfoEnabled()
		) {
			cpuUsageReportOutputIntervalTimekeeper.setTimeoutTime(5, TimeUnit.MINUTES);
			cpuUsageReportOutputIntervalTimekeeper.updateTimestamp();

			final CPUUsageReport report = cpuUsageMonitorTool.getCPUUsageReport();

			double cpuUsageTotal = 0;

			final StringBuilder sb = new StringBuilder();
			sb.append("[Thread CPU Usage Report]\n");
			for(
				final Iterator<ThreadCPUUsageReport> it =
					report.getThreadUsageReport().values().iterator();
				it.hasNext();
			) {
				final ThreadCPUUsageReport threadReport = it.next();

				sb.append("    ");

				sb.append("ID:");
				sb.append(String.format("0x%08X", threadReport.getThreadId()));

				sb.append('/');

				sb.append("CPUUsage:");
				sb.append(String.format("%06.3f%%", threadReport.getCpuUsageCurrent() != 0.0d ? (threadReport.getCpuUsageCurrent() * 100) : 0.0d));
				sb.append("(Ave:");
				sb.append(String.format("%06.3f%%", threadReport.getCpuUsageAverage() != 0.0d ? (threadReport.getCpuUsageAverage() * 100) : 0.0d));
				sb.append('/');
				sb.append("Max:");
				sb.append(String.format("%06.3f%%", threadReport.getCpuUsageMax() != 0.0d ? (threadReport.getCpuUsageMax() * 100) : 0.0d));
				sb.append(')');

				sb.append('/');

				sb.append("Name:");
				sb.append(threadReport.getThreadName());

				sb.append("\n");

				cpuUsageTotal += threadReport.getCpuUsageCurrent();
			}

			sb.append("  ------------------------------------------------------------\n");
			sb.append("    TotalCpuUsage : ");
			sb.append(String.format("%07.3f%%", cpuUsageTotal * 100.0d));

			log.info(logTag + sb.toString());
		}


		return true;
	}

	private boolean checkAllowVersion(
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
