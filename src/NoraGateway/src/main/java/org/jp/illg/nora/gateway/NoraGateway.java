package org.jp.illg.nora.gateway;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.jp.illg.util.ApplicationInformation;
import org.jp.illg.util.ApplicationInformationGradleMaven;
import org.jp.illg.util.LockFileUtil;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.logback.LogbackUtil;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraGateway {

	/**
	 * アプリケーション設定ファイルパスデフォルト
	 */
	private static final String applicationConfigurationFilePathDefault =
		System.getProperty("user.dir") + File.separator +
		"config" + File.separator +
		"NoraGateway.xml";

	/**
	 * アプリケーションバージョン情報
	 */
	private static final ApplicationInformation<NoraGateway> applicationVersionInfo;

	/**
	 * アプリケーションインスタンス
	 */
	private static NoraGateway instance;

	/**
	 * ログタグ
	 */
	private static final String logTag;

	/**
	 * システムID
	 */
	private final UUID systemID;

	/**
	 * メインスレッド
	 */
	@SuppressWarnings("unused")
	private Thread mainThread;

	/**
	 * アプリケーション設定ファイルパス
	 */
	private Path applicationConfigurationFilePath;

	/**
	 * デバッグフラグ
	 */
	private boolean isDebug = false;

	/**
	 * 二重起動防止チェック無効フラグ
	 */
	private boolean disableDuplicateProcessCheck = false;

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
	private int workerThreads = workerThreadsDefault;
	private static final int workerThreadsDefault;

	/**
	 * スタティックイニシャライザ
	 */
	static {
		logTag = NoraGateway.class.getSimpleName() + " : ";

		applicationVersionInfo = new ApplicationInformationGradleMaven<>();

		final int currentCPUCount = SystemUtil.getAvailableProcessors();
		if(currentCPUCount > 8)
			workerThreadsDefault = 8;
		else if(currentCPUCount < 2)
			workerThreadsDefault = 2;
		else
			workerThreadsDefault = currentCPUCount;
	}

	/**
	 * デフォルトコンストラクタ
	 */
	public NoraGateway(@NonNull final UUID systemID) {
		super();

		this.systemID = systemID;
		this.workerThreads = workerThreadsDefault;

		try {
			applicationConfigurationFilePath =
				Paths.get(applicationConfigurationFilePathDefault).normalize();
		}catch(InvalidPathException ex) {
			throw new RuntimeException(ex);
		}
	}

	/**
	 * エントリポイント
	 * @param args コマンドラインスイッチ
	 */
	public static void main(final String[] args) {

		try {
			instance = new NoraGateway(UUID.randomUUID());

			System.exit(instance.mainBootstrap(args));
		}catch(Throwable ex) {
			System.err.println("Application error\n" + ex);
		}

		System.exit(-1);
	}

	/**
	 * アプリケーション名を取得する
	 * @return アプリケーション名
	 */
	public static final String getApplicationName() {
		return applicationVersionInfo.getApplicationName();
	}

	/**
	 * アプリケーションバージョンを取得する
	 * @return アプリケーションバージョン
	 */
	public static final String getApplicationVersion() {
		return applicationVersionInfo.getApplicationVersion();
	}

	/**
	 * OS名を取得する
	 * @return OS名
	 */
	public static final String getRunningOperatingSystem() {
		return applicationVersionInfo.getRunningOperatingSystem();
	}

	/**
	 * アプリケーションの稼働時間を取得する
	 * @return アプリケーション起動からの稼働時間
	 */
	public static long getApplicationUptimeSeconds() {
		return applicationVersionInfo.getUptimeSeconds();
	}

	private int mainBootstrap(final String[] args) {

		mainThread = Thread.currentThread();

		//コマンドラインスイッチを解析
		final int commandLineSwitchParseResult = processCommandLineSwitch(args);
		if(
			commandLineSwitchParseResult <= -1 ||
			commandLineSwitchParseResult >= 1
		) {
			System.exit(commandLineSwitchParseResult);
		}

		if(isDebug) {
			//デバッグ時は、デバッグ用のlogback設定ファイルを読み込む
			try(final InputStream debugLogConfig =
				NoraGateway.class.getClassLoader().getResourceAsStream("logback_debug.xml")
			){
				if(!LogbackUtil.initializeLogger(debugLogConfig, true)) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Could not load debug log configuration !");
				}
			} catch (IOException ex) {
				System.err.println("Loggging initialize error");
				ex.printStackTrace(System.err);
			}
		}

		int result = 0;

		//二重起動チェック
		boolean isDuplicateProcess = false;
		if(!disableDuplicateProcessCheck) {
			try(final LockFileUtil lock = new LockFileUtil("./" + getApplicationName() + ".lock")){
				isDuplicateProcess = !lock.getLock();
			}
		}

		if(isDuplicateProcess) {
			System.err.println("Could not get file lock, other " + getApplicationName() + " is running?");

			result = -1;
		}
		else
			result = mainProcess();

		return result;
	}

	private int mainProcess() {
		final NoraGatewayThread thread = new NoraGatewayThread(
			systemID,
			applicationVersionInfo,
			applicationConfigurationFilePath,
			isDebug,
			isServiceMode,
			isDaemonMode,
			workerThreads
		);
		try {
			if(!thread.start()) {
				System.err.println("Could not start nora gateway thread.");

				return -1;
			}

			if(!isDaemonMode) {
				thread.awaitThreadTerminate();

				return thread.getResultCode();
			}
			else
				return 0;
		}finally {
			if(!isDaemonMode) {thread.stop();}
		}
	}

	private int processCommandLineSwitch(String[] args) {
		final Options options = new Options();
		options.addOption("c", "config", true, "Config file path");
		options.addOption("d", "daemon", false, "Daemon mode");
		options.addOption("ddpc", "disableDuplicateProcessCheck", false, "Disable duplicate process check");
		options.addOption("s", "service", false, "Enable service mode");
		options.addOption("d", "debug", false, "Enable application debug mode");
		options.addOption("h", "help", false, "Print this message");
		options.addOption("v", "version",false,"Print application version");
		options.addOption("w", "worker", true, "Worker threads");


		final CommandLineParser parser = new DefaultParser();
		CommandLine cmd = null;
		try {
			cmd = parser.parse(options, args);

			if(cmd.hasOption("c")) {
				final String configFilePathString = cmd.getOptionValue("c");
				if(configFilePathString == null || "".equals(configFilePathString)) {
					System.err.println("Configuration file path param 'c' must have file path");
					return -1;
				}

				try {
					applicationConfigurationFilePath =
						Paths.get(configFilePathString).normalize();
				}catch(InvalidPathException ex) {
					System.err.println("Illegal configuration file path = " + configFilePathString + "\n" + ex);
					return -1;
				}
			}

			if(cmd.hasOption("disableDuplicateProcessCheck")) {
				disableDuplicateProcessCheck = true;
			}

			if(cmd.hasOption("help")) {
				final HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp( "[options]", options );
				return 1;
			}

			if(cmd.hasOption("debug")) {
				isDebug = true;
			}

			if(cmd.hasOption("service")) {
				isServiceMode = true;
			}

			if(cmd.hasOption("daemon")) {
				isDaemonMode = true;
			}

			if(cmd.hasOption("version")) {
				System.out.println(
					applicationVersionInfo.getApplicationName() +
					" Version" + applicationVersionInfo.getApplicationVersion() +
					"@" + applicationVersionInfo.getRunningOperatingSystem() + " " +
					(applicationVersionInfo.isGitDirty() ? "[!]" : "") +
					applicationVersionInfo.getGitCommitID() + "@" + applicationVersionInfo.getGitBranchName() +
					" by " + applicationVersionInfo.getGitCommitterName() +
					"(" + applicationVersionInfo.getGitCommitterEMail() + ")" + " " +
					"Build by " + applicationVersionInfo.getBuilderName() + "(" + applicationVersionInfo.getBuilderEMail() + ")@" +
					applicationVersionInfo.getBuildTime() +
					(applicationVersionInfo.isGitDirty() ?
						"\n\n*** This build contains code that has not been committed to Git ***" : ""
					)
				);
				return 1;
			}

			if(cmd.hasOption("worker")) {
				final String workersStr = cmd.getOptionValue("worker");
				int workers = workerThreadsDefault;
				try {
					workers = Integer.valueOf(workersStr);
				}catch(NumberFormatException ex) {
					System.err.println("Illegal worker count value = " + workersStr);

					return -1;
				}

				this.workerThreads = workers;
			}

			return 0;
		}
		catch(ParseException ex) {
			System.err.println("Command line parse error\n" + ex);

			return -1;
		}
	}
}
