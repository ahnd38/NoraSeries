package org.jp.illg.dstar.service.reflectorname.importers.url;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;
import org.jp.illg.dstar.service.reflectorname.importers.ReflectorHostsImporterBase;
import org.jp.illg.util.PropertyUtils;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class URLImporter extends ReflectorHostsImporterBase{

	private static final String logTag = URLImporter.class.getSimpleName() + " : ";

	private String url;
	private static final String urlPropertyName = "URL";
	private static final String urlDefault = null;


	public URLImporter(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor
	) {
		super(systemID, exceptionListener, workerExecutor);

		url = urlDefault;
	}

	@Override
	public boolean setPropertiesInternal(Properties properties) {
		url = PropertyUtils.getString(properties, urlPropertyName, urlDefault);

		return true;
	}

	@Override
	public ThreadProcessResult processImporter() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	public ReflectorHostsImporterType getImporterType() {
		return ReflectorHostsImporterType.URL;
	}

	@Override
	public boolean hasUpdateReflectorHosts() {
		return true;
	}

	@Override
	public String getTargetName() {
		return url;
	}

	@Override
	public InputStream getReflectorHosts() {
		if(url == null || "".equals(url)) {
			if(log.isErrorEnabled())
				log.error(logTag + "Illegal target URL = " + url);

			return null;
		}

		try {
			final URL targetURL = new URL(url);

			return new BufferedInputStream(targetURL.openStream());
		}catch(final IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Illegal target URL = " + url, ex);
		}

		return null;
	}

}
