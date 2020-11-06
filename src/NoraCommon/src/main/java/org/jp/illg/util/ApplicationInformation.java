package org.jp.illg.util;

import java.util.concurrent.TimeUnit;

import lombok.NonNull;

public interface ApplicationInformation<T> {

	public String getApplicationName();

	public String getApplicationVersion();

	public String getRunningOperatingSystem();

	public String getBuildTime();
	public String getBuilderName();
	public String getBuilderEMail();

	public String getGitBranchName();
	public String getGitCommitID();
	public String getGitCommitTime();
	public String getGitCommitterName();
	public String getGitCommitterEMail();
	public boolean isGitDirty();

	public long getUptime(@NonNull TimeUnit timeunit);

	public long getUptimeSeconds();
}
