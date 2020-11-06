package org.jp.illg.dstar.routing.service.ircDDB.db;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.routing.service.ircDDB.db.define.IRCDDBTableID;
import org.jp.illg.dstar.routing.service.ircDDB.db.model.IRCDDBDatabaseModel;
import org.jp.illg.dstar.routing.service.ircDDB.db.model.IRCDDBRecord;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterIPEntry;
import org.jp.illg.dstar.routing.service.ircDDB.model.IRCDDBAppRepeaterUserEntry;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.Timer;
import org.jp.illg.util.gson.GsonTypeAdapters;
import org.jp.illg.util.io.datastore.DataStore;
import org.jp.illg.util.io.datastore.DataStoreManager;
import org.jp.illg.util.thread.RunnableTask;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToIntFunction;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IRCDDBDatabaseController {

	/**
	 * ユーザーレコードの保存期間(日)
	 *
	 * この指定期間を過ぎたレコードは破棄される
	 */
	private static final int userRecordAliveDays = 356;

	/**
	 * レピータレコードの保存期間(日)
	 *
	 * この指定期間を過ぎたレコードは破棄される
	 */
	private static final int repeaterRecordAliveDays = 3560;

	/**
	 * IPレコードの保存期間(日)
	 */
	private static final int ipRecordAliveDays = 356;

	/**
	 * データベースの最短バックアップ間隔(分)
	 */
	private static final int databaseBackupIntervalMinutes = 30;


	private static final String logTag =
		IRCDDBDatabaseController.class.getSimpleName() + " : ";

	private final Lock locker;

	private final ExecutorService workerExecutor;

	private final IRCDDBDatabaseType databaseType;

	private IRCDDBDatabase database;

	private final DataStore<IRCDDBDatabaseModel> backupUtil;

	private boolean databaseUpdated;
	private final Timer databaseBackupIntervalTimer;
	private boolean databaseBackupExecuting;

	private final Timer databaseCleanupIntervalTimer;
	private boolean databaseCleanupExecuting;

	public IRCDDBDatabaseController(
		@NonNull final ExecutorService workerExecutor,
		@NonNull final IRCDDBDatabaseType databaseType,
		@NonNull final String backupDirectoryName,
		@NonNull final String backupFileName
	) {
		super();

		locker = new ReentrantLock();

		this.workerExecutor = workerExecutor;
		this.databaseType = databaseType;

//		this.backupDirectoryName = backupDirectoryName;
//		this.backupFileName = backupFileName;

		databaseBackupIntervalTimer = new Timer();
		databaseBackupIntervalTimer.updateTimestamp();

		databaseCleanupIntervalTimer = new Timer();
		databaseCleanupIntervalTimer.updateTimestamp();

		backupUtil =
			DataStoreManager.createDataStore(
				IRCDDBDatabaseModel.class,
				backupDirectoryName, backupFileName,
				GsonTypeAdapters.getDateAdapter()
			);

		database = null;
		databaseUpdated = false;
		databaseBackupExecuting = false;
	}

	public boolean isRunning() {
		locker.lock();
		try {
			return database != null;
		}finally {
			locker.unlock();
		}
	}

	public boolean start() {
		locker.lock();
		try {
			stop();

			database = createDatabaseInstance(databaseType);

			return database != null;
		}finally {
			locker.unlock();
		}
	}

	public void stop() {
		locker.lock();
		try {
			if(database != null) {database.dispose();}
		}finally {
			locker.unlock();
		}
	}

	public boolean backup() {
		final IRCDDBDatabaseModel backup = new IRCDDBDatabaseModel();

		locker.lock();
		try {
			for(final IRCDDBTableID tableID : IRCDDBTableID.values()) {
				if(tableID == IRCDDBTableID.Unknown) {continue;}

				backup.getDatabase().put(tableID, database.findAll(tableID));
			}
		}finally {
			locker.unlock();
		}

		final boolean result = backupUtil.write(backup);

		if(result) {
			if(log.isInfoEnabled()) {
				log.info(
					logTag +
					"Backup ircddb database to " + backupUtil.getAbsoluteFilePath() + " ... " +
					backup.getDatabase().size() + " tables, " +
					"Total " +
					Stream.of(backup.getDatabase().values())
					.mapToInt(new ToIntFunction<List<IRCDDBRecord>>() {
						@Override
						public int applyAsInt(List<IRCDDBRecord> t) {
							return t.size();
						}
					}).sum() + " records."
				);
			}
		}

		return result;
	}

	public boolean restore() {
		final Optional<IRCDDBDatabaseModel> backup = backupUtil.read();
		if(!backup.isPresent()) {return false;}

		final IRCDDBDatabaseModel data = backup.get();

		locker.lock();
		try {
			for(final Map.Entry<IRCDDBTableID, List<IRCDDBRecord>> e : data.getDatabase().entrySet()) {
				final IRCDDBTableID tableID = e.getKey();
				final List<IRCDDBRecord> records = e.getValue();

				for(final IRCDDBRecord record : records) {
					final Optional<IRCDDBRecord> existingRecord =
						database.findByKey(
							tableID,
							record.getKey()
						);

					if(
						existingRecord.isPresent() &&
						record.getTimestamp().after(existingRecord.get().getTimestamp())
					) {
						database.update(
							tableID,
							record.getTimestamp(),
							record.getKey(),
							record.getValue()
						);
					}
					else {
						database.insert(
							tableID,
							record.getTimestamp(),
							record.getKey(),
							record.getValue()
						);
					}
				}
			}
		}finally {
			locker.unlock();
		}

		cleanupDatabase();

		if(log.isInfoEnabled()) {
			log.info(
				logTag +
				"Restore ircddb database from " + backupUtil.getAbsoluteFilePath() + " ... " +
				data.getDatabase().size() + " tables, " +
				"Total " +
				Stream.of(data.getDatabase().values())
				.mapToInt(new ToIntFunction<List<IRCDDBRecord>>() {
					@Override
					public int applyAsInt(List<IRCDDBRecord> t) {
						return t.size();
					}
				}).sum() + " records."
			);
		}

		return true;
	}

	public void cleanupDatabase() {
		cleanupDatabase(createDefaultFilters());
	}

	public void cleanupDatabase(@NonNull Map<IRCDDBTableID, Predicate<Date>> filters) {
		final StringBuilder sb = new StringBuilder(logTag);
		sb.append("Cleanup database...");
		if(!filters.isEmpty()) {sb.append("\n");}

		locker.lock();
		try{
			for(final Iterator<Map.Entry<IRCDDBTableID, Predicate<Date>>> it = filters.entrySet().iterator(); it.hasNext();) {
				final Map.Entry<IRCDDBTableID, Predicate<Date>> e = it.next();
				final IRCDDBTableID tableID = e.getKey();
				final Predicate<Date> filter = e.getValue();

				final List<IRCDDBRecord> table = database.findAll(tableID);
				final long beforeRecordCount = table.size();
				database.delAll(tableID);

				Stream.of(table)
				.filter(new Predicate<IRCDDBRecord>() {
					@Override
					public boolean test(IRCDDBRecord r) {
						return filter.test(r.getTimestamp());
					}
				})
				.forEach(new Consumer<IRCDDBRecord>() {
					@Override
					public void accept(IRCDDBRecord r) {
						if(!database.insert(tableID, r.getTimestamp(), r.getKey(), r.getValue())) {
							if(log.isDebugEnabled())
								log.debug(logTag + "Record insert failed on restore process.");
						}
					}
				});

				final long afterRecordCount  = database.countRecords(tableID);
				sb.append("    [TableID ");
				sb.append(tableID.getTableID());
				sb.append(" : ");
				sb.append(String.format("%6d", beforeRecordCount));
				sb.append(" -> ");
				sb.append(String.format("%6d", afterRecordCount));
				sb.append(" records]");
				if(it.hasNext()) {sb.append("\n");}
			}
		}finally {
			locker.unlock();
		}

		if(log.isInfoEnabled()) {log.info(sb.toString());}
	}

	public List<IRCDDBAppRepeaterEntry> findGateway(@NonNull final String zoneRepeaterCallsign) {

		List<IRCDDBAppRepeaterEntry> result = Collections.emptyList();

		locker.lock();
		try {
			if(!isRunning()) {return Collections.emptyList();}

			result =
				Stream.of(
					database.findByValue(
						IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable,
						DSTARUtils.formatFullCallsign(
							zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
						).replace(' ', '_')
					)
				)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterEntry>(){
					@Override
					public IRCDDBAppRepeaterEntry apply(IRCDDBRecord record) {
						return convertRepeaterRecord(record);
					}
				})
				.toList();
		}finally {
			locker.unlock();
		}

		return result;
	}

	public Optional<IRCDDBAppRepeaterEntry> findRepeater(@NonNull final String areaRepeaterCallsign) {

		Optional<IRCDDBAppRepeaterEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result = database.findByKey(
					IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable,
					DSTARUtils.formatFullLengthCallsign(
						areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_')
				)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterEntry>(){
					@Override
					public IRCDDBAppRepeaterEntry apply(IRCDDBRecord record) {
						return convertRepeaterRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public Optional<IRCDDBAppRepeaterEntry> findRepeaterLatest() {

		Optional<IRCDDBAppRepeaterEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result =
				database.findLatest(IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterEntry>(){
					@Override
					public IRCDDBAppRepeaterEntry apply(IRCDDBRecord record) {
						return convertRepeaterRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public boolean updateRepeater(
		@NonNull final Date lastUpdateTimeUTC,
		@NonNull final String areaRepeaterCallsign,
		@NonNull final String zoneRepeaterCallsign
	) {
		boolean result = false;

		locker.lock();
		try {
			if(!isRunning()) {return false;}

			databaseUpdated = true;

			final Optional<IRCDDBRecord> record =
				database.findByKey(
					IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable,
					DSTARUtils.formatFullLengthCallsign(
						areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_')
				);

			if(record.isPresent()) {
				result = lastUpdateTimeUTC.after(record.get().getTimestamp()) ?
					database.update(
						IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable,
						lastUpdateTimeUTC,
						DSTARUtils.formatFullLengthCallsign(
							areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
						).replace(' ', '_'),
						DSTARUtils.formatFullCallsign(
							zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
						).replace(' ', '_')
					) : true;
			}
			else {
				result = database.insert(
					IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable,
					lastUpdateTimeUTC,
					DSTARUtils.formatFullLengthCallsign(
						areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_'),
					DSTARUtils.formatFullCallsign(
						zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
					).replace(' ', '_')
				);
			}
		}finally {
			locker.unlock();
		}

		processBackup();
		processCleanup();

		return result;
	}

	public Optional<IRCDDBAppRepeaterUserEntry> findUser(@NonNull final String userCallsign) {

		Optional<IRCDDBAppRepeaterUserEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result =
				database.findByKey(
					IRCDDBTableID.UserVSAreaRepeaterTable,
					DSTARUtils.formatFullLengthCallsign(
						userCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_')
				)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterUserEntry>(){
					@Override
					public IRCDDBAppRepeaterUserEntry apply(IRCDDBRecord record) {
						return convertUserRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public Optional<IRCDDBAppRepeaterUserEntry> findUserLatest() {

		Optional<IRCDDBAppRepeaterUserEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result =
				database.findLatest(IRCDDBTableID.UserVSAreaRepeaterTable)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterUserEntry>(){
					@Override
					public IRCDDBAppRepeaterUserEntry apply(IRCDDBRecord record) {
						return convertUserRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public boolean updateUser(
		@NonNull final Date lastUpdateTimeUTC,
		@NonNull final String userCallsign,
		@NonNull final String areaRepeaterCallsign
	) {
		boolean result = false;

		locker.lock();
		try {
			if(!isRunning()) {return false;}

			databaseUpdated = true;

			final Optional<IRCDDBRecord> record =
				database.findByKey(
					IRCDDBTableID.UserVSAreaRepeaterTable,
					DSTARUtils.formatFullLengthCallsign(
						userCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_')
				);

			if(record.isPresent()) {
				result = lastUpdateTimeUTC.after(record.get().getTimestamp()) ?
					database.update(
						IRCDDBTableID.UserVSAreaRepeaterTable,
						lastUpdateTimeUTC,
						DSTARUtils.formatFullLengthCallsign(
							userCallsign.toUpperCase(Locale.ENGLISH)
						).replace(' ', '_'),
						DSTARUtils.formatFullLengthCallsign(
							areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
						).replace(' ', '_')
					) : true;
			}
			else {
				result = database.insert(
					IRCDDBTableID.UserVSAreaRepeaterTable,
					lastUpdateTimeUTC,
					DSTARUtils.formatFullLengthCallsign(
						userCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_'),
					DSTARUtils.formatFullLengthCallsign(
						areaRepeaterCallsign.toUpperCase(Locale.ENGLISH)
					).replace(' ', '_')
				);
			}
		}finally {
			locker.unlock();
		}

		processBackup();
		processCleanup();

		return result;
	}

	public Optional<IRCDDBAppRepeaterIPEntry> findIP(@NonNull final String zoneRepeaterCallsign) {
		Optional<IRCDDBAppRepeaterIPEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result =
				database.findByKey(
					IRCDDBTableID.ZoneRepeaterVSIPAddressTable,
					DSTARUtils.formatFullCallsign(
						zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
					).replace(' ', '_')
				)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterIPEntry>(){
					@Override
					public IRCDDBAppRepeaterIPEntry apply(IRCDDBRecord record) {
						return convertIPRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public Optional<IRCDDBAppRepeaterIPEntry> findIPLatest() {
		Optional<IRCDDBAppRepeaterIPEntry> result = Optional.empty();

		locker.lock();
		try {
			if(!isRunning()) {return Optional.empty();}

			result =
				database.findLatest(IRCDDBTableID.ZoneRepeaterVSIPAddressTable)
				.map(new Function<IRCDDBRecord, IRCDDBAppRepeaterIPEntry>(){
					@Override
					public IRCDDBAppRepeaterIPEntry apply(IRCDDBRecord record) {
						return convertIPRecord(record);
					}
				});
		}finally {
			locker.unlock();
		}

		return result;
	}

	public boolean updateIP(
		@NonNull final Date lastUpdateTimeUTC,
		@NonNull final String zoneRepeaterCallsign,
		@NonNull final String ipAddress
	) {
		boolean result = false;

		locker.lock();
		try {
			if(!isRunning()) {return false;}

			databaseUpdated = true;

			final Optional<IRCDDBRecord> record =
				database.findByKey(
					IRCDDBTableID.ZoneRepeaterVSIPAddressTable,
					DSTARUtils.formatFullCallsign(
						zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
					).replace(' ', '_')
				);

			if(record.isPresent()) {
				result = lastUpdateTimeUTC.after(record.get().getTimestamp()) ?
					database.update(
						IRCDDBTableID.ZoneRepeaterVSIPAddressTable,
						lastUpdateTimeUTC,
						DSTARUtils.formatFullCallsign(
							zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
						).replace(' ', '_'),
						ipAddress
					) : true;
			}
			else {
				result = database.insert(
					IRCDDBTableID.ZoneRepeaterVSIPAddressTable,
					lastUpdateTimeUTC,
					DSTARUtils.formatFullCallsign(
						zoneRepeaterCallsign.toUpperCase(Locale.ENGLISH), ' '
					).replace(' ', '_'),
					ipAddress
				);
			}
		}finally {
			locker.unlock();
		}

		processBackup();
		processCleanup();

		return result;
	}

	public long countUserRecords() {
		locker.lock();
		try {
			if(!isRunning()) {return -1;}

			return database.countRecords(IRCDDBTableID.UserVSAreaRepeaterTable);
		}finally {
			locker.unlock();
		}
	}

	public long countRepeaterRecords() {
		locker.lock();
		try {
			if(!isRunning()) {return -1;}

			return database.countRecords(IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable);
		}finally {
			locker.unlock();
		}
	}

	private static IRCDDBDatabase createDatabaseInstance(IRCDDBDatabaseType dbType) {
		IRCDDBDatabase result = null;

		final String dbClassName = dbType.getClassName();

		try {
			@SuppressWarnings("unchecked")
			final Class<? extends IRCDDBDatabase> dbClass =
				(Class<? extends IRCDDBDatabase>)Class.forName(dbClassName);

			final Constructor<? extends IRCDDBDatabase> dbClassConstructor =
				dbClass.getConstructor();

			result = dbClassConstructor.newInstance();

		}catch(ReflectiveOperationException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not create database instance = " + dbType + ".");
		}

		return result;
	}

	private void processBackup() {
		locker.lock();
		try {
			if(
				!databaseBackupExecuting &&
				databaseUpdated &&
				databaseBackupIntervalTimer.isTimeout(databaseBackupIntervalMinutes, TimeUnit.MINUTES)
			) {
				databaseUpdated = false;
				databaseBackupIntervalTimer.updateTimestamp();

				databaseBackupExecuting = true;

				try {
					workerExecutor.submit(new RunnableTask() {
						@Override
						public void task() {
							try {
								if(log.isTraceEnabled()) {
									log.trace(logTag + "Running a backup of the IrcDDB database.");
								}
								if(!backup()) {
									if(log.isWarnEnabled())
										log.warn(logTag + "IrcDDB database backup error.");
								}
								else {
									if(log.isDebugEnabled()) {
										log.debug(logTag + "IrcDDB database backup is complete.");
									}
								}
							}finally {
								databaseBackupExecuting = false;
							}
						}
					});
				}catch(RejectedExecutionException ex) {
					if(log.isWarnEnabled()) {
						log.warn(logTag + "Could not execute database backup process", ex);
					}

					databaseBackupExecuting = false;
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private void processCleanup() {
		locker.lock();
		try {
			if(
				!databaseCleanupExecuting &&
				databaseCleanupIntervalTimer.isTimeout(24, TimeUnit.HOURS)
			) {
				databaseCleanupIntervalTimer.updateTimestamp();

				databaseCleanupExecuting = true;
				try {
					workerExecutor.submit(new RunnableTask() {
						@Override
						public void task() {
							try {
								cleanupDatabase();
							}finally {
								databaseCleanupExecuting = false;
							}
						}
					});
				}catch(RejectedExecutionException ex) {
					if(log.isWarnEnabled()) {
						log.warn(logTag + "Could not execute database cleanup process", ex);
					}

					databaseCleanupExecuting = false;
				}
			}
		}finally {
			locker.unlock();
		}
	}

	private static final IRCDDBAppRepeaterEntry convertRepeaterRecord(final IRCDDBRecord record) {
		return new IRCDDBAppRepeaterEntry(
			record.getTimestamp(),
			record.getKey().toUpperCase(Locale.ENGLISH).replace('_', ' '),
			record.getValue().toUpperCase(Locale.ENGLISH).replace('_', ' ')
		);
	}

	private static final IRCDDBAppRepeaterUserEntry convertUserRecord(final IRCDDBRecord record) {
		return new IRCDDBAppRepeaterUserEntry(
			record.getTimestamp(),
			record.getKey().toUpperCase(Locale.ENGLISH).replace('_', ' '),
			record.getValue().toUpperCase(Locale.ENGLISH).replace('_', ' ')
		);
	}

	private static final IRCDDBAppRepeaterIPEntry convertIPRecord(final IRCDDBRecord record) {
		return new IRCDDBAppRepeaterIPEntry(
			record.getTimestamp(),
			record.getKey().toUpperCase(Locale.ENGLISH).replace('_', ' '),
			record.getValue()
		);
	}

	private static final Map<IRCDDBTableID, Predicate<Date>> createDefaultFilters() {
		final Map<IRCDDBTableID, Predicate<Date>> filters = new ConcurrentHashMap<>();

		filters.put(IRCDDBTableID.UserVSAreaRepeaterTable, new Predicate<Date>() {
			@Override
			public boolean test(Date d) {
				return d.after(
					new Date(
						System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(userRecordAliveDays, TimeUnit.DAYS)
					)
				);
			}
		});
		filters.put(IRCDDBTableID.AreaRepeaterVSZoneRepeaterTable, new Predicate<Date>() {
			@Override
			public boolean test(Date d) {
				return d.after(
					new Date(
						System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(repeaterRecordAliveDays, TimeUnit.DAYS)
					)
				);
			}
		});
		filters.put(IRCDDBTableID.ZoneRepeaterVSIPAddressTable, new Predicate<Date>() {
			@Override
			public boolean test(Date d) {
				return d.after(
					new Date(
						System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(ipRecordAliveDays, TimeUnit.DAYS)
					)
				);
			}
		});

		return filters;
	}
}
