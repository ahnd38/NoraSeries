package org.jp.illg.dstar.service.repeatername.importers.icom;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.model.defines.RepeaterListImporterType;
import org.jp.illg.dstar.service.repeatername.RepeaterListImporter;
import org.jp.illg.dstar.service.repeatername.importers.icom.model.IcomRepeater;
import org.jp.illg.dstar.service.repeatername.model.RepeaterData;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.FileUtil;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IcomRepeaterListImporter implements RepeaterListImporter{

	private static final String logTag = IcomRepeaterListImporter.class.getSimpleName() + " : ";

	private final Lock locker;

	private Properties properties;

	private String targetFilePath;
	private static final String targetFilePathDefault =
		"." + File.separator + "config" + File.separator + "RepeaterList.csv";

	private List<RepeaterData> cache;

	private long targetFileLastModified;


	public IcomRepeaterListImporter(
		final ThreadUncaughtExceptionListener exceptionListener,
		final ExecutorService workerExecutor
	) {
		super();

		locker = new ReentrantLock();

		targetFilePath = targetFilePathDefault;
		targetFileLastModified = 0;

		cache = null;
	}

	@Override
	public boolean startImporter() {
		return true;
	}

	@Override
	public void stopImporter() {

	}

	public boolean setProperties(@NonNull Properties properties) {
		locker.lock();
		try {
			this.properties = properties;

			final String targetFilePath = properties.getProperty("TargetFilePath");
			if(targetFilePath == null || "".equals(targetFilePath)) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not read targetFilePath, replace to default value = " + targetFilePathDefault);
			}
			this.targetFilePath = targetFilePath;

		}finally {
			locker.unlock();
		}

		return true;
	}

	public Properties getProperties() {
		return properties;
	}

	@Override
	public ThreadProcessResult processImporter() {
		return ThreadProcessResult.NoErrors;
	}

	@Override
	public RepeaterListImporterType getImporterType() {
		return RepeaterListImporterType.ICOM;
	}

	@Override
	public boolean hasUpdateRepeaterList() {
		return targetFileLastModified < getFileLastModifiedTime(targetFilePath);
	}

	@Override
	public List<RepeaterData> getRepeaterList() {
		locker.lock();
		try {
			targetFileLastModified = getFileLastModifiedTime(targetFilePath);

			if(log.isInfoEnabled())
				log.info(logTag + "Reading repeater list from " + targetFilePath);

			final Map<String, IcomRepeater> rptMap = readIcomRepeaterListCSV(targetFilePath);
			if(rptMap != null)
				cache = new ArrayList<>(rptMap.values());
		}finally {
			locker.unlock();
		}

		return cache;
	}

	private static Map<String, IcomRepeater> readIcomRepeaterListCSV(
		String filePath
	){
		if (filePath == null || "".equals(filePath)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not icom repeater list file, filepath is empty.");

			return null;
		}

		final File listFile = new File(filePath);
		final boolean externalMode = listFile.exists() && listFile.canRead();

		if(externalMode) {
			if (!listFile.exists()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not found specified file " + filePath + ".");

				return null;
			} else if (!listFile.canRead()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not load the specified file, Is correct the file permission?");

				return null;
			}
		}

		BufferedReader dis = null;
		try{
			if(externalMode) {
				dis = new BufferedReader(
					new InputStreamReader(new FileInputStream(listFile), StandardCharsets.UTF_8)
				);

				return readIcomRepeaterListCSV(dis);
			}
			else {
				filePath = filePath.replaceFirst("^[.]*", "");
				final byte[] fileBin = FileUtil.getReadAllBytes(filePath);
				if(fileBin == null) {
					if(log.isWarnEnabled())
						log.warn("Could not read " + filePath + ".");

					return null;
				}
				dis = new BufferedReader(
					new InputStreamReader(new ByteArrayInputStream(fileBin), StandardCharsets.UTF_8)
				);

				return readIcomRepeaterListCSV(dis);
			}
		}catch(IOException ex) {
			if(log.isWarnEnabled())
				log.warn("Could not load specified file " + filePath + ".", ex);
		}finally {
			try{
				if(dis != null){dis.close();}
			}catch (IOException ioex){}
		}

		return null;
	}

	private static Map<String, IcomRepeater> readIcomRepeaterListCSV(
		final BufferedReader in
	) {
		final Map<String, IcomRepeater> result = new HashMap<>();

		String line = null;
		int lineNumber = 0;
		try {
			while((line = in.readLine()) != null) {
				lineNumber++;
				if(lineNumber <= 1) {continue;}

				final String[] items = line.split(",");

				if(items.length < 6) {continue;}

				int groupNo = 0;
				try {
					groupNo = Integer.valueOf(items[0]);
				}catch(NumberFormatException ex) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Invalid group no format, lineNumber = " + lineNumber + ".");

					continue;
				}

				String groupName = items[1].trim();
				String name = items[2].trim();
				String subName = items[3].trim();
				String repeaterCallsign = DSTARUtils.formatFullLengthCallsign(items[4].trim());
				String gatewayCallsign = DSTARUtils.formatFullCallsign(repeaterCallsign, 'G');

				double frequency = 0.0d;
				if(items.length >= 7) {
					final String item = items[6];
					try {
						frequency = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid frequency format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				String duplexMode = "";
				if(items.length >= 8) {duplexMode = items[7].trim();}

				double frequencyOffsetMHz = 0.0d;
				if(items.length >= 9) {
					final String item = items[8].trim();
					try {
						frequencyOffsetMHz = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid frequency offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				String mode = "";
				if(items.length >= 10) {mode = items[9].trim();}

				String tone = "";
				if(items.length >= 11) {tone = items[10].trim();}

				String repeaterTone = "";
				if(items.length >= 12) {repeaterTone = items[11].trim();}

				boolean isRepeater = true;
				if(items.length >= 13) {
					final String item = items[12].trim();

					if("YES".equalsIgnoreCase(item))
						isRepeater = true;
					else if("NO".equalsIgnoreCase(item))
						isRepeater = false;
					else {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid repeater format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				boolean isRepeater1Use = false;
				if(items.length >= 14) {
					final String item = items[13].trim();

					if("YES".equalsIgnoreCase(item))
						isRepeater1Use = true;
					else if("NO".equalsIgnoreCase(item))
						isRepeater1Use = false;
					else {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid repeater 1 use format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				String position = "";
				if(items.length >= 15) {position = items[14].trim();}

				double latitude = 0.0d;
				if(items.length >= 16) {
					final String item = items[15].trim();
					try {
						latitude = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid latitude offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				double longitude = 0.0d;
				if(items.length >= 17) {
					final String item = items[16].trim();
					try {
						latitude = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid longitude offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				int utcOffset = 0;
				if(items.length >= 18) {
					final String item = items[17].trim();
					try {
						utcOffset = Integer.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid utc offset offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}

				final IcomRepeater repeater =
					new IcomRepeater(
						groupNo, groupName, name, subName,
						repeaterCallsign, gatewayCallsign,
						frequency, duplexMode, frequencyOffsetMHz,
						mode, tone, repeaterTone,
						isRepeater, isRepeater1Use,
						position, latitude, longitude, utcOffset
					);

				result.put(repeaterCallsign, repeater);
			}
		}catch(IOException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logTag + "Could not read icom repeater list.", ex);
			}
		}

		return result;
	}

	private static long getFileLastModifiedTime(
		final String targetFilePath
	) {
		final File targetFile = new File(targetFilePath);
		if(!targetFile.exists() || !targetFile.canRead()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Target file " + targetFilePath + " is not exists or can not read.");

			return -1;
		}

		return targetFile.lastModified();
	}
}
