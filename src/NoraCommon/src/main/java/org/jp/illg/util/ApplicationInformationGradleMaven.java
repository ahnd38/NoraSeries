package org.jp.illg.util;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApplicationInformationGradleMaven<T> implements ApplicationInformation<T> {

	private static final String logTag = ApplicationInformationGradleMaven.class.getSimpleName() + " : ";

	private static final long startTime;

	private Class<T> applicationClass;

	static {
		startTime = System.nanoTime();
	}

	@SuppressWarnings("unchecked")
	public ApplicationInformationGradleMaven(T... nop){
		applicationClass = (Class<T>)nop.getClass().getComponentType();
	}

	public String getApplicationName() {
		return applicationClass.getSimpleName();
	}

	public String getApplicationVersion() {

		String version = null;

		version = getApplicationVersionFromGradle();

		if(version != null && !"".equals(version)) {return version;}

		version = getVersionPropertyFromMaven("version");

		if(version != null && !"".equals(version)) {return version;}

		return "Unknown";
	}

	@Override
	public String getBuildTime() {
		String value = getGitPropertyFromMaven("git.build.time");

		return value != null ? value : "";
	}

	@Override
	public String getBuilderName() {
		String value = getGitPropertyFromMaven("git.build.user.name");

		return value != null ? value : "";
	}

	@Override
	public String getBuilderEMail() {
		String value = getGitPropertyFromMaven("git.build.user.email");

		return value != null ? value : "";
	}

	@Override
	public boolean isBuildRelease() {
		return Boolean.getBoolean(getVersionPropertyFromMaven("releasebuild"));
	}

	@Override
	public String getGitBranchName() {
		String value = getGitPropertyFromMaven("git.branch");

		return value != null ? value : "";
	}

	@Override
	public String getGitCommitID() {
		String value = getGitPropertyFromMaven("git.commit.id.abbrev");

		return value != null ? value : "";
	}

	@Override
	public String getGitCommitTime() {
		String value = getGitPropertyFromMaven("git.commit.time");

		return value != null ? value : "";
	}

	@Override
	public String getGitCommitterName() {
		String value = getGitPropertyFromMaven("git.commit.user.name");

		return value != null ? value : "";
	}

	@Override
	public String getGitCommitterEMail() {
		String value = getGitPropertyFromMaven("git.commit.user.email");

		return value != null ? value : "";
	}

	@Override
	public boolean isGitDirty() {
		return isDirtyFromMaven();
	}

	public String getRunningOperatingSystem() {
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

	public long getUptime(@NonNull TimeUnit timeunit) {
		return timeunit.convert(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
	}

	public long getUptimeSeconds() {
		return getUptime(TimeUnit.SECONDS);
	}

	private String getApplicationVersionFromGradle() {

		Class<?> buildConfigClass = null;

		try {
			buildConfigClass = Class.forName(applicationClass.getPackage().getName() + "." + "BuildConfig");

			Field versionNameField = buildConfigClass.getDeclaredField("VERSION_NAME");

			String version = versionNameField.get(null).toString();

			return version;
		} catch (Exception ex) {
			if(log.isTraceEnabled())
				log.trace("Error occurred get application version from gradle.", ex);
		}

		return null;
	}

	private boolean isDirtyFromMaven() {
		return Boolean.valueOf(getGitPropertyFromMaven("git.dirty"));
	}

	private String getVersionPropertyFromMaven(final String propertyName) {
		final Properties props = loadVersionPropertiesFromMaven(applicationClass);
		if(props == null) {return null;}

		return props.getProperty(propertyName);
	}

	private static Properties loadVersionPropertiesFromMaven(final Class<?> applicationClass) {
		try(final InputStream stream = applicationClass.getResourceAsStream("/version.prop")){
			if(stream == null) {return null;}

			final Properties props = new Properties();
			props.load(stream);

			return props;
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "version.prop load error", ex);
		}

		return null;
	}

	private String getGitPropertyFromMaven(final String propertyName) {
		final Properties props = loadGitPropertiesFromMaven(applicationClass);
		if(props == null) {return null;}

		return props.getProperty(propertyName);
	}

	private static Properties loadGitPropertiesFromMaven(final Class<?> applicationClass) {
		try(final InputStream stream = applicationClass.getResourceAsStream("/git.properties")){
			if(stream == null) {return null;}

			final Properties props = new Properties();
			props.load(stream);

			return props;
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "git.properties load error", ex);
		}

		return null;
	}
}
