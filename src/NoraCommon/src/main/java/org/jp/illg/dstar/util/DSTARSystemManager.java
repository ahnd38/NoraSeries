package org.jp.illg.dstar.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.DSTARGatewayManager;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.repeater.DSTARRepeaterManager;
import org.jp.illg.util.SystemUtil;

import lombok.NonNull;

public class DSTARSystemManager {

	private static class SystemEntry{
		private final UUID systemID;

		private long lastTranferringTime = 0;

		private boolean isTransferring;

		private SystemEntry(final UUID systemID) {
			this.systemID = systemID;

			lastTranferringTime = SystemUtil.getNanoTimeCounterValue();

			isTransferring = false;
		}
	}

	private static final Lock locker;
	private static final Map<UUID, SystemEntry> systemEntries;

	static {
		locker = new ReentrantLock();
		systemEntries = new ConcurrentHashMap<>();
	}

	private DSTARSystemManager() {}

	public static void process() {
		List<SystemEntry> entries = null;
		locker.lock();
		try {
			entries = new ArrayList<>(systemEntries.values());
		}finally {
			locker.unlock();
		}

		for(final SystemEntry entry : entries) {
			entry.isTransferring = isDataTransferringInternal(entry.systemID);
			if(entry.isTransferring)
				entry.lastTranferringTime = SystemUtil.getNanoTimeCounterValue();
		}
	}

	/**
	 * 指定時間以上のアイドル期間の存在を確認する
	 * @param systemID システムID
	 * @param minimumIdleTime 最小アイドル時間
	 * @param minimumIdleTimeUnit 最小アイドル時間単位
	 * @return 指定時間以上のアイドル期間があればtrue
	 */
	public static boolean isIdleSystem(
		@NonNull final UUID systemID,
		final long minimumIdleTime,
		@NonNull final TimeUnit minimumIdleTimeUnit
	) {
		SystemEntry entry = null;
		locker.lock();
		try {
			entry = systemEntries.get(systemID);
			if(entry == null) {
				entry = new SystemEntry(systemID);
				systemEntries.put(systemID, entry);
			}
		}finally {
			locker.unlock();
		}

		entry.isTransferring = isDataTransferringInternal(entry.systemID);
		if(entry.isTransferring)
			entry.lastTranferringTime = SystemUtil.getNanoTimeCounterValue();

		return !entry.isTransferring && (
			entry.lastTranferringTime +
			TimeUnit.NANOSECONDS.convert(minimumIdleTime, minimumIdleTimeUnit)
		) < SystemUtil.getNanoTimeCounterValue();
	}

	private static boolean isDataTransferringInternal(final UUID systemID) {
		final DSTARGateway gateway = DSTARGatewayManager.getGateway(systemID);
		if(gateway != null && gateway.isDataTransferring()) {return true;}

		final List<DSTARRepeater> repeaters = DSTARRepeaterManager.getRepeaters(systemID);
		if(repeaters != null) {
			for(final DSTARRepeater repeater : repeaters) {
				if(repeater.isDataTransferring()) {return true;}
			}
		}

		return false;
	}
}
