package org.jp.illg.dstar.util.icom.rptlst;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.service.repeatername.importers.icom.model.IcomRepeater;

public class IcomRepeaterList {

	private static final Lock locker;
	private final static Map<String, IcomRepeater> repeaterList;
	private static boolean isReaded;

	static {
		locker = new ReentrantLock();
		repeaterList = new ConcurrentHashMap<>();
		isReaded = false;
	}

	private IcomRepeaterList() {}

	public static IcomRepeater findRepeater(String repeaterCallsign) {
		if(repeaterCallsign == null) {return null;}

		locker.lock();
		try {
			if(!isReaded) {isReaded = readIcomRepeaterList();}
		}finally {
			locker.unlock();
		}

		final IcomRepeater repeater = repeaterList.get(repeaterCallsign);

		return repeater;

	}

	public static boolean readIcomRepeaterList() {
		final Map<String, IcomRepeater> list =
			IcomRepeaterListReader.readIcomRepeaterListCSV();

		if(list == null) {return false;}

		locker.lock();
		try {
			if(list.size() >= 1) {repeaterList.putAll(list);}
		}finally {
			locker.unlock();
		}

		return true;
	}
}
