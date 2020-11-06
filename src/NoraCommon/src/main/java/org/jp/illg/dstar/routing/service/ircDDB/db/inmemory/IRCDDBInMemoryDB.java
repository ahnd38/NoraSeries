package org.jp.illg.dstar.routing.service.ircDDB.db.inmemory;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.routing.service.ircDDB.db.IRCDDBDatabase;
import org.jp.illg.dstar.routing.service.ircDDB.db.define.IRCDDBTableID;
import org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model.CacheRecord;
import org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model.Record;
import org.jp.illg.dstar.routing.service.ircDDB.db.inmemory.model.Table;
import org.jp.illg.dstar.routing.service.ircDDB.db.model.IRCDDBRecord;
import org.jp.illg.util.Timer;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.ToLongFunction;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IRCDDBInMemoryDB implements IRCDDBDatabase{

	/**
	 * キャッシュレコード制限数
	 */
	private static final int cacheRecordLimit = 50;

	/**
	 * キャッシュレコード生存時間(分)
	 */
	private static final int cacheRecordAliveTimeMinutes = 60;

	/**
	 * テーブル初期容量
	 */
	private static final int initialTableCapacity = 5000;


	private static final String logTag =
		IRCDDBInMemoryDB.class.getSimpleName() + " : ";

	private final Lock locker;

	private final Map<IRCDDBTableID, Table<Record>> tables;

	private final Map<IRCDDBTableID, Table<CacheRecord>> cacheTables;
	private final Timer cacheTablesCleanupIntervalLimitter;


	public IRCDDBInMemoryDB() {
		super();

		locker = new ReentrantLock();
		tables = new HashMap<>();
		cacheTables = new HashMap<>();
		cacheTablesCleanupIntervalLimitter = new Timer();
		cacheTablesCleanupIntervalLimitter.updateTimestamp();
	}

	@Override
	public boolean insert(
		@NonNull final IRCDDBTableID tableID,
		@NonNull final Date timestamp,
		@NonNull final String key, @NonNull final String value
	) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);

		table.getLocker().lock();
		try {
			table.getTable().put(key, new Record(timestamp, key, value));
		}finally {
			table.getLocker().unlock();
		}


		final Table<CacheRecord> cacheTable = getCacheRecords(tableID);

		addCacheTableRecord(cacheTable, timestamp, key, value);

		return true;
	}

	@Override
	public boolean update(
		@NonNull IRCDDBTableID tableID,
		@NonNull final Date timestamp,
		@NonNull final String key, @NonNull final String value
	) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);

		table.getLocker().lock();
		try {
			final Record record = table.getTable().get(key);
			if(record == null) {return false;}

			record.getLocker().lock();
			try {
				record.setTimestamp(timestamp);
				record.setValue(value);
			}finally {
				record.getLocker().unlock();
			}
		}finally {
			table.getLocker().unlock();
		}


		final Table<CacheRecord> cacheTable = getCacheRecords(tableID);

		cacheTable.getLocker().lock();
		try {
			cacheTable.getTable().put(key, new CacheRecord(timestamp, key, value));
		}finally {
			cacheTable.getLocker().unlock();
		}

		return true;
	}

	@Override
	public Optional<IRCDDBRecord> findByKey(
		@NonNull IRCDDBTableID tableID, @NonNull String key
	) {
		cleanupCacheTables();

		//キャッシュから検索
		final Table<CacheRecord> cacheTable = getCacheRecords(tableID);

		cacheTable.getLocker().lock();
		try {
			final CacheRecord cacheRecord = cacheTable.getTable().get(key);

			if(cacheRecord != null) {
				cacheRecord.getLocker().lock();
				try {
					if(
						!cacheRecord.getInactivityTimer().isTimeout(
							cacheRecordAliveTimeMinutes, TimeUnit.MINUTES
						)
					) {
						cacheRecord.getInactivityTimer().updateTimestamp();

						if(log.isTraceEnabled())
							log.trace(logTag + "Cache hit record from " + tableID + " = " + cacheRecord);

						return Optional.of(convert(cacheRecord));
					}
				}finally {
					cacheRecord.getLocker().unlock();
				}
			}
		}finally {
			cacheTable.getLocker().unlock();
		}


		//キャッシュからヒットしなかった場合は全検索
		final Table<Record> table = getTable(tableID);

		IRCDDBRecord result;

		table.getLocker().lock();
		try {
			final Record record = table.getTable().get(key);
			if(record == null) {return Optional.empty();}

			result = convert(record);
		}finally {
			table.getLocker().unlock();
		}

		return Optional.of(result);
	}

	@Override
	public List<IRCDDBRecord> findByValue(
		@NonNull IRCDDBTableID tableID,
		@NonNull final String value
	) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);

		final List<IRCDDBRecord> result = new ArrayList<>();

		table.getLocker().lock();
		try {
			for(final Record record : table.getTable().values()) {
				record.getLocker().lock();
				try {
					if(!record.getValue().equals(value)) {continue;}

					result.add(
						new IRCDDBRecord(record.getTimestamp(), record.getKey(), record.getValue())
					);
				}finally {
					record.getLocker().unlock();
				}
			}
		}finally {
			table.getLocker().unlock();
		}

		return result;
	}

	@Override
	public Optional<IRCDDBRecord> findLatest(@NonNull IRCDDBTableID tableID) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);

		IRCDDBRecord result;

		table.getLocker().lock();
		try {
			final Optional<Record> latestRecord =
				Stream.of(table.getTable().values())
				.max(ComparatorCompat.comparingLong(new ToLongFunction<Record>() {
					@Override
					public long applyAsLong(Record r) {
						return r.getTimestamp().getTime();
					}
				}));
			if(!latestRecord.isPresent()) {return Optional.empty();}

			latestRecord.get().getLocker().lock();
			try {
				result =
					new IRCDDBRecord(
						latestRecord.get().getTimestamp(),
						latestRecord.get().getKey(), latestRecord.get().getValue()
					);
			}finally {
				latestRecord.get().getLocker().unlock();
			}
		}finally {
			table.getLocker().unlock();
		}

		return Optional.of(result);
	}

	@Override
	public List<IRCDDBRecord> findAll(
		@NonNull IRCDDBTableID tableID
	) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);

		table.getLocker().lock();
		try {
			final List<IRCDDBRecord> result = new ArrayList<>(table.getTable().size());

			for(final Record record : table.getTable().values()) {
				record.getLocker().lock();
				try {
					result.add(
						new IRCDDBRecord(record.getTimestamp(), record.getKey(), record.getValue())
					);
				}finally {
					record.getLocker().unlock();
				}
			}

			return result;

		}finally {
			table.getLocker().unlock();
		}
	}

	@Override
	public void delAll(@NonNull IRCDDBTableID tableID) {
		cleanupCacheTables();

		final Table<Record> table = getTable(tableID);
		table.getLocker().lock();
		try {
			table.getTable().clear();
		}finally {
			table.getLocker().unlock();
		}
	}

	@Override
	public void delAll() {
		locker.lock();
		try {
			for(IRCDDBTableID tableID : IRCDDBTableID.values())
				delAll(tableID);

		}finally {
			locker.unlock();
		}
	}

	@Override
	public long countRecords(@NonNull IRCDDBTableID tableID) {
		final Table<Record> table = getTable(tableID);

		table.getLocker().lock();
		try {
			return table.getTable().size();
		}finally {
			table.getLocker().unlock();
		}
	}

	@Override
	public void dispose() {

	}

	private final Table<Record> getTable(final IRCDDBTableID tableID) {

		Table<Record> table;

		locker.lock();
		try {
			table = tables.get(tableID);
			if(table == null) {
				table = new Table<Record>(initialTableCapacity);

				tables.put(tableID, table);

				if(log.isTraceEnabled())
					log.trace(logTag + "Create table id = " + tableID + ".");
			}
		}finally {
			locker.unlock();
		}

		return table;
	}

	private final Table<CacheRecord> getCacheRecords(final IRCDDBTableID tableID) {

		Table<CacheRecord> cacheTable;

		locker.lock();
		try {
			cacheTable = this.cacheTables.get(tableID);

			if(cacheTable == null) {
				cacheTable = new Table<>(cacheRecordLimit);

				this.cacheTables.put(tableID, cacheTable);

				if(log.isTraceEnabled())
					log.trace(logTag + "Create cache table id = " + tableID + ".");
			}
		}finally {
			locker.unlock();
		}

		return cacheTable;
	}

	private static final IRCDDBRecord convert(final Record record) {
		IRCDDBRecord result;

		record.getLocker().lock();
		try {
			result =
				new IRCDDBRecord(
					record.getTimestamp(),
					record.getKey(), record.getValue()
				);
		}finally {
			record.getLocker().unlock();
		}

		return result;
	}

	private boolean addCacheTableRecord(
		final Table<CacheRecord> targetTable,
		final Date timestamp,
		final String key, final String value
	) {
		targetTable.getLocker().lock();
		try {
			while(targetTable.getTable().size() >= cacheRecordLimit) {
				Stream.of(targetTable.getTable().values())
				.min(ComparatorCompat.comparingLong(new ToLongFunction<CacheRecord>() {
					@Override
					public long applyAsLong(CacheRecord t) {
						return t.getInactivityTimer().getTimestampMilis();
					}
				}))
				.ifPresent(new Consumer<CacheRecord>() {
					@Override
					public void accept(CacheRecord t) {
						targetTable.getTable().remove(t.getKey());

						if(log.isTraceEnabled())
							log.trace(logTag + "Delete records that exceed the limit = " + t);
					}
				});
			}

			targetTable.getTable().put(key, new CacheRecord(timestamp, key, value));
		}finally {
			targetTable.getLocker().unlock();
		}

		return true;
	}

	private final void cleanupCacheTables() {
		locker.lock();
		try {
			if(cacheTablesCleanupIntervalLimitter.isTimeout(15, TimeUnit.MINUTES)) {
				cacheTablesCleanupIntervalLimitter.updateTimestamp();

				for(final IRCDDBTableID tableID : IRCDDBTableID.values()) {
					if(tableID == IRCDDBTableID.Unknown) {continue;}

					final Table<CacheRecord> cacheTable = getCacheRecords(tableID);

					cacheTable.getLocker().lock();
					try {
						for(
							final Iterator<CacheRecord> it =
								cacheTable.getTable().values().iterator(); it.hasNext();
						) {
							final CacheRecord cacheRecord = it.next();

							cacheRecord.getLocker().lock();
							try {
								if(
									cacheRecord.getInactivityTimer().isTimeout(
										cacheRecordAliveTimeMinutes, TimeUnit.MINUTES
									)
								) {
									it.remove();

									if(log.isTraceEnabled())
										log.trace(logTag + "Remove inactivity cache record = " + cacheRecord);
								}
							}finally {
								cacheRecord.getLocker().unlock();
							}
						}
					}finally {
						cacheTable.getLocker().unlock();
					}
				}
			}
		}finally {
			locker.unlock();
		}
	}
}
