package org.jp.illg.dstar.service.reflectorname;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.DSTARSystemManager;
import org.jp.illg.dstar.model.config.ReflectorHostsImporterProperties;
import org.jp.illg.dstar.model.config.ReflectorNameServiceProperties;
import org.jp.illg.dstar.model.defines.DSTARProtocol;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfoKey;
import org.jp.illg.dstar.service.Service;
import org.jp.illg.dstar.service.reflectorname.define.ReflectorHostsImporterType;
import org.jp.illg.dstar.service.reflectorname.importers.ReflectorHostsImporter;
import org.jp.illg.dstar.service.reflectorname.model.ImporterEntry;
import org.jp.illg.dstar.service.reflectorname.util.ReflectorHostsFileReaderWriter;
import org.jp.illg.dstar.service.repeatername.model.RepeaterData;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.Callback;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToIntFunction;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorNameService implements Service {

	private static final String logTag;

	private final Lock locker;

	private final UUID systemID;
	private final ThreadUncaughtExceptionListener exceptionListener;
	private final ExecutorService workerExecutor;

	private final Map<ReflectorHostInfoKey, ReflectorHostInfo> hosts;


	@Getter
	@Setter
	private String outputFilePath;
	private static final String outputFilePathDefault = "./hosts.output.txt";

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private long lastUpdateTime;

	@Getter
	@Setter
	private Callback<List<ReflectorHostInfo>> onReflectorHostChangeEventListener;

	@Getter
	@Setter
	private Function<ReflectorHostInfo, RepeaterData> funcFindRepeaterInfo;

	private boolean isRunning;

	private static final List<DSTARProtocol> defaultBaseReflectorPreferredProtocol;
	private final List<DSTARProtocol> baseReflectorPreferredProtocols;

	private final Map<String, DSTARProtocol> reflectorPreferredProtocols;

	private final Timer saveIntervalTimekeeper;

	private final Timer processIntervalTimerKeeper;

	private final List<ImporterEntry> importers;

	static {
		logTag = ReflectorNameService.class.getSimpleName() + " : ";

		defaultBaseReflectorPreferredProtocol = new LinkedList<>();
		defaultBaseReflectorPreferredProtocol.add(DSTARProtocol.DCS);
		defaultBaseReflectorPreferredProtocol.add(DSTARProtocol.DPlus);
		defaultBaseReflectorPreferredProtocol.add(DSTARProtocol.DExtra);
		defaultBaseReflectorPreferredProtocol.add(DSTARProtocol.JARLLink);
	}

	public ReflectorNameService(
		@NonNull final UUID systemID,
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final ExecutorService workerExecutor
	) {
		super();

		this.systemID = systemID;
		this.exceptionListener = exceptionListener;
		this.workerExecutor = workerExecutor;

		hosts = new HashMap<>(2000);
		locker = new ReentrantLock();

		importers = new ArrayList<>(16);

		setLastUpdateTime(System.currentTimeMillis());

		saveIntervalTimekeeper = new Timer();
		saveIntervalTimekeeper.updateTimestamp();
		processIntervalTimerKeeper = new Timer();
		saveIntervalTimekeeper.updateTimestamp();

		baseReflectorPreferredProtocols = new LinkedList<>();

		reflectorPreferredProtocols = new ConcurrentHashMap<>();

		isRunning = false;

		setOutputFilePath(outputFilePathDefault);

		initializePreferredProtocols();
	}

	public boolean initialize(
		@NonNull final ReflectorNameServiceProperties properties
	) {
		locker.lock();
		try {
			importers.clear();

			for(final ReflectorHostsImporterProperties importerProperties : properties.getImporters()) {
				if(!importerProperties.isEnable()) {continue;}

				final ReflectorHostsImporter importer =
					createImporter(systemID, importerProperties, exceptionListener, workerExecutor);

				if(importer == null)
					continue;
				else if(!importer.setProperties(importerProperties.getConfigurationProperties())) {
					if(log.isWarnEnabled())
						log.warn(logTag + "Failed to initialize importer = " + importer.getImporterType());

					continue;
				}

				importers.add(new ImporterEntry(importer, true, importerProperties.getIntervalMinutes()));
			}
		}finally {
			locker.unlock();
		}

		return true;
	}

	public void setDefaultReflectorPreferredProtocol(List<DSTARProtocol> protocols) {
		if(protocols == null || protocols.size() <= 0) {return;}

		locker.lock();
		try {
			baseReflectorPreferredProtocols.clear();

			baseReflectorPreferredProtocols.addAll(protocols);
		}finally {
			locker.unlock();
		}
	}

	public void setReflectorPreferredProtocol(Map<String, DSTARProtocol> entries) {
		if(entries == null || entries.size() <= 0) {return;}

		locker.lock();
		try {
			reflectorPreferredProtocols.clear();

			reflectorPreferredProtocols.putAll(entries);
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean start() {
		isRunning = true;

		return true;
	}

	@Override
	public void stop() {
		isRunning = false;
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public ThreadProcessResult processService() {
		if(processIntervalTimerKeeper.isTimeout(5, TimeUnit.SECONDS)) {
			processIntervalTimerKeeper.updateTimestamp();

			if(
				saveIntervalTimekeeper.isTimeout(30, TimeUnit.SECONDS) &&
				getLastUpdateTime() > saveIntervalTimekeeper.getTimestampMilis()
			) {
				saveIntervalTimekeeper.updateTimestamp();

				saveHosts(getOutputFilePath());
			}
		}

		processImporters();

		return ThreadProcessResult.NoErrors;
	}

	public boolean loadHostsFromAndroidAssets(
		String filePath, boolean rewriteDataSource, boolean logSuppress
	) {
		final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
			ReflectorHostsFileReaderWriter.readHostFileFromAndroidAssets(filePath, rewriteDataSource);

		return updateHosts(false, readHosts, filePath, true);
	}

	public boolean loadHosts(boolean logSuppress) {
		final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
			ReflectorHostsFileReaderWriter.readHostFile(getOutputFilePath(), false);

		return readHosts != null && updateHosts(logSuppress, readHosts, getOutputFilePath(), true);
	}

	public boolean loadHosts(String filePath, boolean rewriteDataSource, boolean logSuppress) {
		final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
			ReflectorHostsFileReaderWriter.readHostFile(filePath, rewriteDataSource);

		return readHosts != null && updateHosts(logSuppress, readHosts, filePath, true);
	}

	public boolean loadHosts(URL url, boolean rewriteDataSource, boolean logSuppress) {
		if(url == null) {return false;}

		final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
			ReflectorHostsFileReaderWriter.readHostFile(url, rewriteDataSource);

		return readHosts != null && updateHosts(logSuppress, readHosts, url.toExternalForm(), true);
	}

	public boolean loadHosts(
		Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts,
		final String dataSource,
		final boolean deleteSameDataSource,
		final boolean logSuppress
	) {
		if(readHosts == null) {return false;}

		return updateHosts(logSuppress, readHosts, dataSource, deleteSameDataSource);
	}

	public boolean saveHosts(String filePath) {

		if(log.isInfoEnabled())
			log.info(logTag + "Saving hosts file to " + filePath);

		final File file = new File(filePath);
		locker.lock();
		try {
			return ReflectorHostsFileReaderWriter.writeHostFile(hosts, file);
		}finally {
			locker.unlock();
		}
	}

	public void clear() {
		locker.lock();
		try {
			hosts.clear();
		}finally {
			locker.unlock();
		}
	}

	public Optional<ReflectorHostInfo> findHostByReflectorCallsign(final String reflectorCallsign) {
		if(reflectorCallsign == null) {return Optional.empty();}

		locker.lock();
		try {
			//識別符号込みで完全一致するものを検索
			final Optional<ReflectorHostInfo> result =
				findHostByReflectorCallsignInt(reflectorCallsign);
			if(result.isPresent()) {return result;}

			//完全一致しなければ、識別符号を除外して検索
			final String callsignExcludeSuffix = DSTARUtils.formatFullCallsign(reflectorCallsign, ' ');
			return findHostByReflectorCallsignInt(callsignExcludeSuffix);
		}finally {
			locker.unlock();
		}
	}

	public List<ReflectorHostInfo> findHostByFullText(final String queryText, final int limit) {
		if(queryText == null) {return Collections.emptyList();}

		locker.lock();
		try {
			return Stream.of(hosts.values())
			.filter(new Predicate<ReflectorHostInfo>() {
				@Override
				public boolean test(ReflectorHostInfo host) {
					return host.getReflectorCallsign().contains(queryText) || host.getName().contains(queryText);
				}
			})
			.limit(limit)
			.toList();
		}finally {
			locker.unlock();
		}
	}

	public int getHostsRecordSize() {
		locker.lock();
		try {
			return hosts.size();
		}finally {
			locker.unlock();
		}
	}

	public List<ReflectorHostInfo> getHosts() {
		locker.lock();
		try {
			return Stream.of(hosts.values())
			.map(new Function<ReflectorHostInfo, ReflectorHostInfo>(){
				@Override
				public ReflectorHostInfo apply(ReflectorHostInfo h) {
					return h.clone();
				}
			}).toList();
		}finally {
			locker.unlock();
		}
	}

	private void processImporters() {
		locker.lock();
		try {
			for(final ImporterEntry entry : importers)
				processImporter(entry);
		}finally {
			locker.unlock();
		}
	}

	private void processImporter(final ImporterEntry entry) {
		entry.getImporter().processImporter();

		if(
			entry.isEnable() &&
			entry.getIntervalTimekeeper().isTimeout(entry.getIntervalMinutes(), TimeUnit.MINUTES) &&
			entry.getImporter().hasUpdateReflectorHosts() &&
			DSTARSystemManager.isIdleSystem(systemID, 3, TimeUnit.MINUTES)
		) {
			try(final InputStream src = entry.getImporter().getReflectorHosts()){
				if(src != null) {
					final Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts =
						ReflectorHostsFileReaderWriter.readHostFile(
							src,
							entry.getImporter().getTargetName(),
							true
						);

					loadHosts(readHosts, entry.getImporter().getTargetName(), true, false);
				}
			} catch (IOException ex) {

			}finally {
				entry.getIntervalTimekeeper().updateTimestamp();
			}
		}
	}

	private boolean updateHosts(
		final boolean logSuppress,
		Map<ReflectorHostInfoKey, ReflectorHostInfo> readHosts,
		final String dataSource,
		final boolean deleteSameDataSource
	) {
		assert readHosts != null;

		final Function<ReflectorHostInfo, RepeaterData> funcFindRepeaterInfo = getFuncFindRepeaterInfo();

		final List<ReflectorHostInfo> updateHosts = new LinkedList<>();

		locker.lock();
		try {
			for(ReflectorHostInfo hostInfo : readHosts.values()) {
				if(!CallSignValidator.isValidReflectorCallsign(hostInfo.getReflectorCallsign())) {
					continue;
				}

				final ReflectorHostInfoKey key =
					new ReflectorHostInfoKey(hostInfo.getReflectorCallsign(), hostInfo.getDataSource());

				final ReflectorHostInfo oldHostInfo = hosts.get(key);

				final boolean isUpdateHostInfo = oldHostInfo != null &&
					(
						!hostInfo.equals(oldHostInfo) &&
						(
							hostInfo.getUpdateTime() >= oldHostInfo.getUpdateTime() ||
							hostInfo.getPriority() <= oldHostInfo.getPriority()
						)
					);

				final boolean isTimestampUpdateOnly = oldHostInfo != null &&
					(
						hostInfo.equalsIgnoreUpdateTimestamp(oldHostInfo) &&
						hostInfo.getUpdateTime() >= oldHostInfo.getUpdateTime()
					);

				if(oldHostInfo == null || isUpdateHostInfo || isTimestampUpdateOnly){
					if(oldHostInfo != null) {hosts.remove(key);}

					if(funcFindRepeaterInfo != null) {
						final RepeaterData repeaterData = funcFindRepeaterInfo.apply(hostInfo);
						final String repeaterName = repeaterData != null ? repeaterData.getName() : "";

						hostInfo.setName(repeaterName);
					}

					hosts.put(key, hostInfo);

					updateHosts.add(hostInfo.clone());

					setLastUpdateTime(System.currentTimeMillis());

					if(!logSuppress) {
						if(oldHostInfo == null) {
							if(log.isInfoEnabled()) {
								log.info(
									logTag +
									"Create reflector host " +
									hostInfo.getReflectorCallsign() +
									"/" +
									"Name:" + hostInfo.getName() +
									"/" +
									"Addr:" + hostInfo.getReflectorAddress() + ":" + hostInfo.getReflectorPort() +
									"(" + hostInfo.getReflectorProtocol() + ") " + "/" +
									"Priority:" + hostInfo.getPriority() + "/" +
									"Src:" + hostInfo.getDataSource()

								);
							}
						}
						else if(isUpdateHostInfo && !isTimestampUpdateOnly) {
							if(log.isInfoEnabled()) {
								log.info(
									logTag +
									"Update reflector host " +
									hostInfo.getReflectorCallsign() +
									"/" +
									"Name:" + oldHostInfo.getName() + "->" + hostInfo.getName() +
									"/" +
									"Addr:" + oldHostInfo.getReflectorAddress() + ":" + oldHostInfo.getReflectorPort() +
									"(" + oldHostInfo.getReflectorProtocol() + ")" +
									"->" +
									hostInfo.getReflectorAddress() + ":" + hostInfo.getReflectorPort() +
									"(" + hostInfo.getReflectorProtocol() + ")" +
									"/" +
									"Priority:" + oldHostInfo.getPriority() + "->" + hostInfo.getPriority() +
									"/" +
									"Src:" + hostInfo.getDataSource()
								);
							}
						}
					}
				}
			}

			if(deleteSameDataSource) {
				for(final Iterator<ReflectorHostInfo> it = hosts.values().iterator(); it.hasNext();) {
					final ReflectorHostInfo info = it.next();

					if(
						info.getDataSource().equals(dataSource) &&
						!readHosts.containsKey(new ReflectorHostInfoKey(info.getReflectorCallsign(), dataSource))
					) {
						it.remove();

						setLastUpdateTime(System.currentTimeMillis());

						if(log.isInfoEnabled()) {
							log.info(
								logTag +
								"Delete unavailable reflector host entry = " +
								info.getReflectorCallsign() +
								"/" +
								"Name:" + info.getName() +
								"/" +
								"Addr:" + info.getReflectorAddress() + ":" + info.getReflectorPort() +
								"(" + info.getReflectorProtocol() + ") " + "/" +
								"Priority:" + info.getPriority() + "/" +
								"Src:" + info.getDataSource()
							);
						}
					}
				}
			}
		}finally {
			locker.unlock();
		}

		final Callback<List<ReflectorHostInfo>> reflectorHostChangedListener = getOnReflectorHostChangeEventListener();
		if(reflectorHostChangedListener != null)
			reflectorHostChangedListener.call(updateHosts);

		return true;
	}

	private Optional<ReflectorHostInfo> findHostByReflectorCallsignInt(final String reflectorCallsign) {

		final String reflectorCallsignFormated =
			DSTARUtils.formatFullLengthCallsign(reflectorCallsign);

		final List<ReflectorHostInfo> reflectors =
			Stream.of(hosts)
			.filter(new Predicate<Entry<ReflectorHostInfoKey, ReflectorHostInfo>>() {
				@Override
				public boolean test(Entry<ReflectorHostInfoKey, ReflectorHostInfo> t) {
					return t.getKey().getReflectorCallsign().equals(reflectorCallsignFormated);
				}
			})
			.map(new Function<Entry<ReflectorHostInfoKey, ReflectorHostInfo>, ReflectorHostInfo>(){
				@Override
				public ReflectorHostInfo apply(Entry<ReflectorHostInfoKey, ReflectorHostInfo> t) {
					return t.getValue().clone();
				}
			}).toList();

		//リフレクターに対して希望するプロトコルがあれば
		//対象のプロトコルで接続可能なリフレクターを検索
		DSTARProtocol preferredProtocol =
			reflectorPreferredProtocols.get(reflectorCallsignFormated);
		//識別符号ありで検索してなければ、識別符号なしで再検索
		if(
			preferredProtocol == null &&
			reflectorCallsignFormated.charAt(DSTARDefines.CallsignFullLength - 1) != ' '
		) {
			preferredProtocol =
				reflectorPreferredProtocols.get(DSTARUtils.formatFullCallsign(reflectorCallsignFormated, ' '));
		}
		if(preferredProtocol != null) {
			final Optional<ReflectorHostInfo> preferredProtocolHostInfo =
				findHostByReflectorCallsignInt(reflectors, preferredProtocol);

			if(preferredProtocolHostInfo.isPresent()) {return preferredProtocolHostInfo;}
		}

		for(final DSTARProtocol protocol : baseReflectorPreferredProtocols) {
			final Optional<ReflectorHostInfo> result =
				findHostByReflectorCallsignInt(reflectors, protocol);

			if(result.isPresent()) {return result;}
		}

		return Optional.empty();
	}

	private Optional<ReflectorHostInfo> findHostByReflectorCallsignInt(
		final List<ReflectorHostInfo> reflectors, final DSTARProtocol protocol
	) {
		final Optional<Entry<Integer, List<ReflectorHostInfo>>> f =
			Stream.of(reflectors)
			.filter(new Predicate<ReflectorHostInfo>() {
				@Override
				public boolean test(ReflectorHostInfo info) {
					return info.getReflectorProtocol() == protocol;
				}
			})
			.groupBy(new Function<ReflectorHostInfo, Integer>(){
				@Override
				public Integer apply(ReflectorHostInfo t) {
					return t.getPriority();
				}
			})
			.sorted(ComparatorCompat.comparingInt(new ToIntFunction<Entry<Integer, List<ReflectorHostInfo>>>() {
				@Override
				public int applyAsInt(Entry<Integer, List<ReflectorHostInfo>> t) {
					return t.getKey();
				}
			}))
			.findFirst();

		if(!f.isPresent()) {return Optional.empty();}


		return Stream.of(f.get().getValue())
			.sorted(ComparatorCompat.comparingLong(new ToLongFunction<ReflectorHostInfo>() {
				@Override
				public long applyAsLong(ReflectorHostInfo info) {
					return info.getUpdateTime();
				}
			}).reversed())
			.findFirst();
	}

	private static ReflectorHostsImporter createImporter(
		final UUID systemID,
		final ReflectorHostsImporterProperties properties,
		final ThreadUncaughtExceptionListener exceptionListener,
		final ExecutorService workerExecutor
	) {
		final ReflectorHostsImporterType importerType = properties.getType();

		try {
			@SuppressWarnings("unchecked")
			final Class<? extends ReflectorHostsImporter> importerClass =
				(Class<? extends ReflectorHostsImporter>)Class.forName(importerType.getImplementationClassName());

			final Constructor<? extends ReflectorHostsImporter> constructor =
				importerClass.getConstructor(
					UUID.class,
					ThreadUncaughtExceptionListener.class,
					ExecutorService.class
				);

			return constructor.newInstance(systemID, exceptionListener, workerExecutor);
		}catch(final ReflectiveOperationException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not create importer instance type = " + importerType, ex);
		}

		return null;
	}

	private void initializePreferredProtocols() {
		locker.lock();
		try {
			baseReflectorPreferredProtocols.clear();

			baseReflectorPreferredProtocols.addAll(defaultBaseReflectorPreferredProtocol);
		}finally {
			locker.unlock();
		}
	}
}
