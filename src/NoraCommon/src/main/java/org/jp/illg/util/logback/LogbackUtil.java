package org.jp.illg.util.logback;

import java.io.InputStream;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogbackUtil {

	private LogbackUtil() {}

	/**
	 * XMLファイルからlogbackを設定する
	 *
	 * @param logbackConfigurationFilePath XML設定ファイルパス
	 * @param resetConfiguration 既存の設定の初期化を行う
	 * @return 設定成功ならtrue
	 */
	public static boolean initializeLogger(
		@NonNull final String logbackConfigurationFilePath,
		final boolean resetConfiguration
	) {
		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

		try {
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			if(resetConfiguration) {context.reset();}
			configurator.doConfigure(logbackConfigurationFilePath);

			return true;
		} catch (JoranException je) {
			if(log.isErrorEnabled())
				log.error("Could not logback initialize configuration " + logbackConfigurationFilePath + ".");
		}

		return false;
	}

	/**
	 * XMLファイルのInputStreamからlogbackの設定を行う
	 *
	 * @param logbackConfigurationFileStream XML設定ファイルのInputStream
	 * @param resetConfiguration 既存の設定の初期化を行う
	 * @return 設定成功ならtrue
	 */
	public static boolean initializeLogger(
		@NonNull final InputStream logbackConfigurationFileStream,
		final boolean resetConfiguration
	) {
		final LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

		try {
			JoranConfigurator configurator = new JoranConfigurator();
			configurator.setContext(context);
			if(resetConfiguration) {context.reset();}
			configurator.doConfigure(logbackConfigurationFileStream);

			return true;
		} catch (JoranException je) {
			if(log.isErrorEnabled())
				log.error("Could not logback initialize configuration " + logbackConfigurationFileStream + ".");
		}

		return false;
	}

	/**
	 * 既存の設定にRollingFileAppenderを追加する
	 *
	 * @param outputDir ログファイル出力先ディレクトリ
	 * @param fileName ファイル名
	 * @param maxHistory 最大履歴数
	 * @return 設定成功ならtrue
	 */
	public static boolean addRollingFileAppender(String outputDir, String fileName, int maxHistory){
		if(outputDir == null || "".equals(outputDir)){return false;}
		if(fileName == null || "".equals(fileName)){fileName = "NoName";}
		if(maxHistory <= 0){maxHistory = 1;}

		final String targetDir = outputDir + "/log";

		final LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();

		final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
		encoder.setContext(lc);
		encoder.setPattern("%d{yyyy/MM/dd HH:mm:ss.SSS}, [%t], %-6p, %c{10}, %m%n");
		encoder.start();

		final RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
		final TimeBasedRollingPolicy<ILoggingEvent> rp = new TimeBasedRollingPolicy<>();
		rp.setContext(lc);
		rp.setParent(fileAppender);
		rp.setFileNamePattern(targetDir + fileName + "_%d{yyyy-MM-dd}.log");
		rp.setMaxHistory(maxHistory);
		rp.start();

		fileAppender.setContext(lc);
		fileAppender.setFile(targetDir + "/" + fileName + ".log");
		fileAppender.setEncoder(encoder);
		fileAppender.setRollingPolicy(rp);
		fileAppender.start();

		final ch.qos.logback.classic.Logger root =
			(ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

		root.addAppender(fileAppender);

		return true;
	}

}
