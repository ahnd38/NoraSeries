package org.jp.illg.nora.gateway;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.jp.illg.util.SystemUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Deprecated
public class NoraGatewayUtil {

	private static final long applicationStartTimestampNanos;

	static {
		applicationStartTimestampNanos = System.nanoTime();
	}

	private NoraGatewayUtil() {
		super();
	}

	public static String getApplicationName() {
		StringBuilder sb = new StringBuilder("NoraGateway");

		return sb.toString();
	}

	public static String getApplicationVersion() {

		String version = null;

		version = getApplicationVersionFromGradle();

		if(version != null && !"".equals(version)) {return version;}

		version = getApplicationVersionFromMaven();

		if(version != null && !"".equals(version)) {return version;}

		return "Unknown";
	}

	public static String getRunningOperatingSystem() {
		if(SystemUtil.IS_Android) {
			return "android";
		}else if(SystemUtils.IS_OS_LINUX) {
			return "linux";
		}else if(SystemUtils.IS_OS_WINDOWS) {
			return "windows";
		}else if(SystemUtils.IS_OS_MAC) {
			return "mac";
		}else {
			return "unknown";
		}
	}

	/**
	 * アプリケーションの稼働時間を取得する
	 * @return アプリケーション起動からの稼働時間
	 */
	public static long getApplicationUptimeSeconds() {
		return TimeUnit.SECONDS.convert(System.nanoTime() - applicationStartTimestampNanos, TimeUnit.NANOSECONDS);
	}

	private static String getApplicationVersionFromGradle() {

		Class<?> buildConfigClass = null;

		try {
			buildConfigClass = Class.forName("org.jp.illg.noragateway.BuildConfig");

			Field versionNameField = buildConfigClass.getDeclaredField("VERSION_NAME");

			String version = versionNameField.get(null).toString();

			return version;
		} catch (Exception ex) {
			log.trace("Error occurred get application version from gradle.", ex);
		}

		return null;
	}

	private static String getApplicationVersionFromMaven() {

		String path = "/version.prop";
		InputStream stream = NoraGatewayUtil.class.getResourceAsStream(path);
		if (stream == null) {return null;}

		Properties props = new Properties();
		try {
			props.load(stream);

			return (String) props.get("version");
		} catch (IOException ex) {
		}finally {
			try {
				if(stream != null) {stream.close();}
			}catch(IOException exio) {}
		}

		return null;
	}
}
