package org.jp.illg.dstar.service.reflectorhosts;


import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.SystemUtil;

import com.annimon.stream.Stream;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorHostsFileReaderWriter {

	private static final String hostFormat =
		"[HostFormat]" +
		" ReflectorCallsign [tab] ReflectorAddress(:PortNumber) [tab] Protocol [tab] Priority " +
		"[tab] Name [tab] DataSource [tab] UpdateUnixTimestamp";

	private static final String logTag;

	private static final Pattern xrfPattern;
	private static final Pattern dcsPattern;
	private static final Pattern refPattern;
	private static final Pattern xlxPattern;
	private static final Pattern repeaterLinkPattern;

	static {
		logTag = ReflectorHostsFileReaderWriter.class.getSimpleName() + " : ";

		xrfPattern = Pattern.compile("^[X][R][F][0-9]{3}[ ]{2}$");
		dcsPattern = Pattern.compile("^[D][C][S][0-9]{3}[ ]{2}$");
		refPattern = Pattern.compile("^[R][E][F][0-9]{3}[ ]{2}$");
		xlxPattern = Pattern.compile("^[X][L][X][0-9]{3}[ ]{2}$");
		repeaterLinkPattern = Pattern.compile("^(([1-9][A-Z])|([A-Z][0-9])|([A-Z][A-Z][0-9]))[0-9A-Z]*[ ]{0,2}[ A-FH-Z]$");
	}

	public static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFileFromAndroidAssets(
		final String filePath,
		final boolean rewriteDataSource
	) {
		final Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts = new HashMap<>();

		if (filePath == null || "".equals(filePath)) {
			if(log.isErrorEnabled())
				log.error("Could not read host file. file path is empty.");

			return null;
		}

		InputStream hostsFileStream = null;
		try {
			try {
				Class<?> androidHelperClass = Class.forName("org.jp.illg.util.android.AndroidHelper");
				Method getApplicationContext = androidHelperClass.getMethod("getApplicationContext");

				Object context = getApplicationContext.invoke(null, new Object[]{});
				Method getAssets = context.getClass().getMethod("getAssets");
				Object assetManager = getAssets.invoke(context, new Object[]{});

				Method open = assetManager.getClass().getMethod("open", new Class[]{String.class});
				hostsFileStream = (InputStream) open.invoke(assetManager, filePath);
			} catch (
				ClassNotFoundException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException ex
			) {
				if(log.isWarnEnabled())
					log.warn("Could not load asset " + filePath + ".");
			}

			if (hostsFileStream != null){
				return readHostFile(
					hosts, new BufferedReader(new InputStreamReader(hostsFileStream)),
					rewriteDataSource ? filePath : null
				);
			}
			else
				return null;
		}finally {
			try {
				if (hostsFileStream != null) {
					hostsFileStream.close();
				}
			}catch (IOException ex){}
		}
	}

	public static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		String filePath,
		final boolean rewriteDataSource
	) {
		if (filePath == null || "".equals(filePath)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read host file. file path is empty.");

			return null;
		}

		final File hostsFile = new File(filePath);

		if (!hostsFile.exists()) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not found hosts file " + filePath + ".");

			return null;
		}

		try (
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(hostsFile), StandardCharsets.UTF_8))
		) {
			return readHostFile(new HashMap<>(), reader, rewriteDataSource ? filePath : null);
		}catch (FileNotFoundException ex){
			if(log.isErrorEnabled())
				log.error(logTag + "Not found host file " + filePath + ".");
		} catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read host file =" + filePath + ".", ex);
		}

		return null;
	}

	public static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		@NonNull File hostsFile,
		final boolean rewriteDataSource
	) {
		try (
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(hostsFile), StandardCharsets.UTF_8))
		){
			return readHostFile(new HashMap<>(), reader, rewriteDataSource ? hostsFile.getPath() : "");
		}catch(IOException ex){
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read host file from file = " + hostsFile, ex);
		}

		return null;
	}

	public static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		@NonNull InputStream hostsFileStream,
		final String dataSource,
		final boolean rewriteDataSource
	) {
		try(
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(hostsFileStream, StandardCharsets.UTF_8))
		) {
			return readHostFile(new HashMap<>(), reader, rewriteDataSource ? dataSource : null);
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read host file from input stream.", ex);
		}

		return null;
	}

	public static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		@NonNull URL url,
		final boolean rewriteDataSource
	) {
		try (final BufferedInputStream in = new BufferedInputStream(url.openStream())) {
			return readHostFile(new HashMap<>(), in, url.toExternalForm(), rewriteDataSource);
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read host file from url = " + url, ex);
		}

		return null;
	}

	private static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts, InputStream hostsFileStream,
		final String dataSource,
		final boolean rewriteDataSource
	) throws IOException {
		try(
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(hostsFileStream, StandardCharsets.UTF_8))
		) {
			return readHostFile(hosts, reader, rewriteDataSource ? dataSource : null);
		}
	}

	public static boolean writeHostFile(
		@NonNull Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts, @NonNull File hostsFile
	){
		try (
			final BufferedWriter br =
				new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(hostsFile), StandardCharsets.UTF_8)
				)
		){
			return writeHostFile(hosts, br);
		}catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not write reflector host file.", ex);
		}

		return false;
	}

	public static boolean writeHostFile(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts, BufferedWriter hostsFileStream
	) {
		if(hosts == null || hostsFileStream == null) {return false;}

		final List<ReflectorHostInfo> sortedHosts =
			Stream.of(hosts.values())
			.sorted(new Comparator<ReflectorHostInfo>() {
				@Override
				public int compare(ReflectorHostInfo o1, ReflectorHostInfo o2) {
					return o1.getReflectorCallsign().compareTo(o2.getReflectorCallsign());
				}
			})
			.toList();

		try {
			final String cr = System.lineSeparator();

			hostsFileStream.write("# ");
			hostsFileStream.write(
				DateFormatUtils.format(System.currentTimeMillis(), "yyyy.MM.dd G 'at' HH:mm:ss z")
			);
			hostsFileStream.write(cr);

			hostsFileStream.write("# ");
			hostsFileStream.write(cr);

			hostsFileStream.write("# ");
			hostsFileStream.write(hostFormat);
			hostsFileStream.write(cr);

			hostsFileStream.write(cr);

			for(ReflectorHostInfo hostInfo : sortedHosts) {
				final StringBuffer sb = new StringBuffer();

				sb.append(
					DSTARUtils.formatFullLengthCallsign(hostInfo.getReflectorCallsign())
				);
				sb.append("\t");

				final String addressPort =
					String.format(
						"%-40s",
						hostInfo.getReflectorAddress() + ":" + hostInfo.getReflectorPort()
					);
				sb.append(addressPort);
				sb.append("\t");

				sb.append(String.format("%-10s", hostInfo.getReflectorProtocol()));
				sb.append("\t");

				sb.append(String.format("%-4s", hostInfo.getPriority()));

				sb.append("\t");

				sb.append(String.format("%-40s", "\"" + hostInfo.getName() + "\""));

				sb.append("\t");

				sb.append(String.format("%-60s", "\"" + hostInfo.getDataSource() + "\""));

				sb.append("\t");

				sb.append(hostInfo.getUpdateTime());

				sb.append(cr);

				hostsFileStream.write(sb.toString());
			}

			hostsFileStream.flush();

			return true;
		}catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not write reflector host file.", ex);
		}

		return false;
	}

	private static Map<ReflectorHostInfoKey, ReflectorHostInfo> readHostFile(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts,
		final BufferedReader hostsFile,
		final String rewriteDataSource
	) {
		assert hostsFile != null;

		final long currentUnixTime = System.currentTimeMillis() / 1000;

		if(hosts == null) {hosts = new HashMap<>();}

		BufferedReader br = hostsFile;
		try{
			String readLine;
//			Pattern replacePattern = Pattern.compile("([\\t]|[ ]){2,}");
			final Pattern replacePattern = Pattern.compile("([\\t]|[ ])+");
			final Pattern moduleCheckPattern = Pattern.compile("^[A-Z0-9 ]{7}[A-Z].*$");

			while((readLine = br.readLine()) != null) {
				//リフレクターコールサインにモジュールが含まれれば
				//コールサインに含まれる空白をアンダーバーに変換
				if(moduleCheckPattern.matcher(readLine).matches()) {
					final String repeaterCallsign = readLine.substring(0, 8).replace(' ', '_');
					final String info = readLine.substring(8, readLine.length());
					readLine = repeaterCallsign + info;
				}

				readLine = replacePattern.matcher(readLine).replaceAll("\t");
				final String[] entries = readLine.split("\t");

				if(
					entries == null ||
					entries.length < 2 ||
					(entries[0].length() > 0 && (entries[0].charAt(0) == '#' || entries[0].charAt(0) == ';'))
				) {continue;}

				String reflectorCallsign = entries[0];
				String reflectorAddress = entries[1];
				DSTARProtocol reflectorProtocol = DSTARProtocol.Unknown;
				if(entries.length >= 3)
					reflectorProtocol = DSTARProtocol.getProtocolByName(entries[2]);

				int priority = ReflectorHostInfo.priorityDefault;
				if(entries.length >= 4) {
					try {
						priority = Integer.valueOf(entries[3]);

						if(ReflectorHostInfo.priorityMax > priority) {priority = ReflectorHostInfo.priorityMax;}
						if(ReflectorHostInfo.priorityMin < priority) {priority = ReflectorHostInfo.priorityMin;}

					}catch(NumberFormatException ex) {
						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Illegal priority number format, replace to default value " +
								ReflectorHostInfo.priorityDefault + ".\n    " + readLine
							);
						}
					}
				}

				String name = entries.length >= 5 ? entries[4] : "";
				if(name.length() >= 2 && name.startsWith("\"") && name.endsWith("\"")) {
					name = name.substring(1, name.length() - 1);
				}
				else {
					name = "";
				}

				String dataSource = entries.length >= 6 ? entries[5] : "";
				if(dataSource.length() >= 2 && dataSource.startsWith("\"") && dataSource.endsWith("\"")) {
					dataSource = dataSource.substring(1, dataSource.length() - 1);
				}
				else {
					dataSource = "";
				}

				long updateTime = currentUnixTime;
				String updateTimeStr = entries.length >= 7 ? entries[6] : "";
				if(updateTimeStr.length() >= 1) {
					try {
						updateTime = Long.valueOf(updateTimeStr);
					}catch(NumberFormatException ex) {
						if(log.isWarnEnabled()) {
							log.warn(logTag + "Illegal UpdateTime format, Callsign = " + reflectorCallsign + ".");
						}
						continue;
					}

					if(updateTime < 0) {
						if(log.isWarnEnabled()) {
							log.warn(logTag + "Out of range UpdateTime, Callsign = " + reflectorCallsign + ".");
						}

						continue;
					}
				}

				if(
					reflectorCallsign == null || "".equals(reflectorCallsign) ||
					reflectorAddress == null || "".equals(reflectorAddress)
				) {
					if(log.isWarnEnabled()) {
						log.warn(
							logTag +
							"Ignore reflector address registration " +
							((reflectorCallsign != null && !"".equals(reflectorCallsign)) ? reflectorCallsign:"NOCALL") + "," +
							(reflectorAddress != null ? reflectorAddress:"") + "."
						);
					}
					continue;
				}

				//両端空白削除
				reflectorCallsign =
					DSTARUtils.formatFullLengthCallsign(
						reflectorCallsign.replace('_', ' ').trim()
					);
				reflectorAddress = reflectorAddress.trim();

				final boolean reflectorAddressContainsPort = reflectorAddress.contains(":");
				final String[] reflectorAddressParsed = reflectorAddress.split(":");

				if(	//アドレスの記述は、IPv4もしくは、ドメイン記述に該当するか？
					!SystemUtil.getDomainRegExpPattern().matcher(reflectorAddress).matches() &&
					!SystemUtil.getIpv4RegExpPattern().matcher(reflectorAddress).matches() &&
					!(reflectorAddressContainsPort && reflectorAddressParsed.length >= 2)
				) {
					if(log.isWarnEnabled()) {
						log.warn(
							"Ignore reflector address registration, " +
							reflectorCallsign + "," + reflectorAddress + " is illegal address format.\n" +
							hostFormat
						);
					}
					continue;
				}

				int reflectorPort = -1;
				if(reflectorAddressContainsPort) {
					reflectorAddress = reflectorAddressParsed[0];

					try {
						reflectorPort = Integer.valueOf(reflectorAddressParsed[1]);
					}catch(NumberFormatException ex) {
						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Ignore reflector address registration, " +
								reflectorCallsign + "," + reflectorAddress +
								":" + reflectorAddressParsed[1] +
								" is illegal port format.\n" +
								hostFormat
							);
						}
						continue;
					}
				}

				final List<ReflectorHostInfo> hostInfo = new ArrayList<>(2);

				if(xlxPattern.matcher(reflectorCallsign).matches()) {
					// XLX reflector
					hostInfo.add(
						new ReflectorHostInfo(
							DSTARProtocol.DCS,
							reflectorPort > 0 ? reflectorPort : DSTARProtocol.DCS.getPortNumber(),
							reflectorCallsign,
							reflectorAddress,
							priority,
							updateTime,
							rewriteDataSource != null ? rewriteDataSource : dataSource,
							name
						)
					);
				}
				else if(xrfPattern.matcher(reflectorCallsign).matches()) {
					// DExtra reflector
					hostInfo.add(
						new ReflectorHostInfo(
							DSTARProtocol.DExtra,
							reflectorPort > 0 ? reflectorPort : DSTARProtocol.DExtra.getPortNumber(),
							reflectorCallsign,
							reflectorAddress,
							priority,
							updateTime,
							rewriteDataSource != null ? rewriteDataSource : dataSource,
							name
						)
					);
/*
					final String xlxCallsign = reflectorCallsign.replace("XRF", "XLX");
					hostInfo.add(
						new ReflectorHostInfo(
							DStarProtocol.DCS,
							DStarProtocol.DCS.getPortNumber(),
							xlxCallsign,
							reflectorAddress,
							priority,
							updateTime,
							rewriteDataSource != null ? rewriteDataSource : dataSource,
							name
						)
					);
*/
				}
				else if(dcsPattern.matcher(reflectorCallsign).matches()) {
					// DCS reflector
					hostInfo.add(
						new ReflectorHostInfo(
							DSTARProtocol.DCS,
							reflectorPort > 0 ? reflectorPort : DSTARProtocol.DCS.getPortNumber(),
							reflectorCallsign,
							reflectorAddress,
							priority,
							updateTime,
							rewriteDataSource != null ? rewriteDataSource : dataSource,
							name
						)
					);
				}
				else if(refPattern.matcher(reflectorCallsign).matches()) {
					// REF reflector
					hostInfo.add(
						new ReflectorHostInfo(
							DSTARProtocol.DPlus,
							reflectorPort > 0 ? reflectorPort : DSTARProtocol.DPlus.getPortNumber(),
							reflectorCallsign,
							reflectorAddress,
							priority,
							updateTime,
							rewriteDataSource != null ? rewriteDataSource : dataSource,
							name
						)
					);
				}
				else if(repeaterLinkPattern.matcher(reflectorCallsign).matches()) {
					// その他のリンク

					if(reflectorProtocol == DSTARProtocol.Unknown) {
						if(log.isWarnEnabled()) {
							log.warn(
								logTag +
								"Ignore reflector address registration, " +
								reflectorCallsign + "," + reflectorAddress +
								" must specify protocol.\n" +
								hostFormat
							);
						}
						continue;
					}

					ReflectorHostInfo host = null;
					if(reflectorAddressContainsPort && reflectorPort > 0) {
						host =
							new ReflectorHostInfo(
								reflectorProtocol,
								reflectorPort,
								reflectorCallsign,
								reflectorAddress,
								priority,
								updateTime,
								rewriteDataSource != null ? rewriteDataSource : dataSource,
								name
							);
					}
					else {
						host =
							new ReflectorHostInfo(
								reflectorProtocol,
								reflectorProtocol.getPortNumber(),
								reflectorCallsign,
								reflectorAddress,
								priority,
								updateTime,
								rewriteDataSource != null ? rewriteDataSource : dataSource,
								name
							);
					}

					hostInfo.add(host);
				}

				for(ReflectorHostInfo host : hostInfo) {
					final ReflectorHostInfoKey key =
						new ReflectorHostInfoKey(host.getReflectorCallsign(), host.getDataSource());

					final ReflectorHostInfo oldHost = hosts.get(key);
					if(oldHost != null) {
						final boolean replace =
							host.getPriority() <= oldHost.getPriority();
						if(replace) {
							if(log.isWarnEnabled()) {
								log.warn(
									logTag +
									"Duplicate reflector address " + host.getReflectorCallsign() +
									", remove old registerd address.\n" +
									"    REMOVED   : " + oldHost.toString() + "\n" +
									"    REPLACE TO: " + host.toString()
								);
							}

							hosts.remove(key);

							hosts.put(key, host);
						}
						else {
							if(log.isWarnEnabled())
								log.warn(logTag + "Duplicate reflector address " + host.getReflectorCallsign() + ".");
						}
					}
					else {
						hosts.put(key, host);
					}
				}
			}
		}catch(IOException ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not read hosts file " + hostsFile.toString() + ".", ex);
		}

		if(hosts != null)
			if(log.isDebugEnabled())
				log.debug(logTag + "Read hosts file " + hosts.size() + "entries.");

		return hosts;
	}
}
