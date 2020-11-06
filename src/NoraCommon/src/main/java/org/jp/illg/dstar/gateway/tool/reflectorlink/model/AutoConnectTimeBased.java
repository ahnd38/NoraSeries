package org.jp.illg.dstar.gateway.tool.reflectorlink.model;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.gateway.tool.reflectorlink.ReflectorLinkManagerImpl;
import org.jp.illg.dstar.util.CallSignValidator;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoConnectTimeBased extends AutoConnectEntryBase implements AutoConnectEntry {



	private final List<TimeBasedEntry> entries;
	private final Lock entriesLock;

	private static final Pattern timePattern = Pattern.compile("^[0-9]{1,2}[:][0-9]{1,2}[:][0-9]{1,2}$");

	public AutoConnectTimeBased(
		final ReflectorLinkManagerImpl manager,
		final String repeaterCallsign
	) {
		super(manager, repeaterCallsign, true);

		entries = new LinkedList<>();
		entriesLock = new ReentrantLock();
	}

	public boolean addTimeBasedEntry(
		final String dayOfWeek,
		final String startTime,
		final String endTime,
		final String linkReflectorCallsign
	) {
		if(dayOfWeek == null) {return false;}
		int dayOfWeekConverted = 0;
		if("mon".equalsIgnoreCase(dayOfWeek) || "monday".equalsIgnoreCase(dayOfWeek)) {
			dayOfWeekConverted = Calendar.MONDAY;
		}
		else if(
			"tue".equalsIgnoreCase(dayOfWeek) || "thes".equalsIgnoreCase(dayOfWeek) ||
			"tuesday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.TUESDAY;
		}
		else if(
			"wed".equalsIgnoreCase(dayOfWeek) || "wednesday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.WEDNESDAY;
		}
		else if(
			"thu".equalsIgnoreCase(dayOfWeek) || "thurs".equalsIgnoreCase(dayOfWeek) ||
			"thur".equalsIgnoreCase(dayOfWeek) || "thursday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.THURSDAY;
		}
		else if(
			"fri".equalsIgnoreCase(dayOfWeek) || "friday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.FRIDAY;
		}
		else if(
			"sat".equalsIgnoreCase(dayOfWeek) || "saturday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.SATURDAY;
		}
		else if(
			"sun".equalsIgnoreCase(dayOfWeek) || "sunday".equalsIgnoreCase(dayOfWeek)
		) {
			dayOfWeekConverted = Calendar.SUNDAY;
		}
		else {
			log.error("Could not converted dayOfWeek = " + dayOfWeek + ".");
			return false;
		}

		Optional<Calendar> start = parseTime(startTime);
		if(!start.isPresent()) {return false;}
		Calendar startTimeConverted = start.get();

		Optional<Calendar> end = parseTime(endTime);
		if(!end.isPresent()) {return false;}
		Calendar endTimeConverted = end.get();

		if(startTimeConverted.after(endTimeConverted)) {
			log.warn(
				"Illegal relationship starttime and endtime,Swaped starttime and endtime.\n" +
				"[StartTime]" + DateFormatUtils.format(startTimeConverted, "HH:mm:ss") +
				"/[EndTime]" + DateFormatUtils.format(startTimeConverted, "HH:mm:ss")
			);
			Calendar tmp = startTimeConverted;
			startTimeConverted = endTimeConverted;
			endTimeConverted = tmp;
		}

		if(!CallSignValidator.isValidReflectorCallsign(linkReflectorCallsign)) {
			log.error("Illegal reflector callsign = " + linkReflectorCallsign);
			return false;
		}

		TimeBasedEntry entry =
				new TimeBasedEntry(dayOfWeekConverted, startTimeConverted, endTimeConverted, linkReflectorCallsign);

		entriesLock.lock();
		try {
			entries.add(entry);
		}finally {
			entriesLock.unlock();
		}

		return true;
	}

	@Override
	public AutoConnectMode getMode() {
		return AutoConnectMode.TimeBased;
	}

	@Override
	public Optional<AutoConnectRequestData> getLinkDataIfAvailableInt() {
		Optional<AutoConnectRequestData> result = Optional.empty();

		Calendar now = Calendar.getInstance();

		final int todayDayOfWeek = now.get(Calendar.DAY_OF_WEEK);

		entriesLock.lock();
		try {
			List<TimeBasedEntry> withinPeriodEntries =
				Stream.of(entries)
				.filter(new Predicate<TimeBasedEntry>() {
					@Override	//同じ曜日でフィルタ
					public boolean test(TimeBasedEntry entry) {
						return todayDayOfWeek == entry.getDayOfWeek();
					}
				})
				.filter(new Predicate<TimeBasedEntry>() {
					@Override	//期間内でフィルタ
					public boolean test(TimeBasedEntry entry) {
						return isWithinPeriod(entry.getStartTime(), entry.getEndTime());
					}
				})
				.sorted(ComparatorCompat.comparingLong(new ToLongFunction<TimeBasedEntry>() {
					@Override	//開始時刻でソート
					public long applyAsLong(TimeBasedEntry t) {
						return t.getStartTime().getTimeInMillis();
					}
				}))
				.toList();

			if(!withinPeriodEntries.isEmpty()) {
				TimeBasedEntry entry = withinPeriodEntries.get(0);

				AutoConnectRequestData data =
						new AutoConnectRequestData(getTargetRepeater(), entry.getLinkReflectorCallsign());

				result = Optional.of(data);
			}

		}finally {
			entriesLock.unlock();
		}

		return result;
	}

	private static boolean isWithinPeriod(Calendar startTime, Calendar endTime) {
		assert startTime != null && endTime != null;

		String datePtn = "yyyy/MM/dd HH:mm:ss.SSS";

		Calendar now = Calendar.getInstance();

		int year = now.get(Calendar.YEAR);
		int month = now.get(Calendar.MONTH);
		int day = now.get(Calendar.DAY_OF_MONTH);

		startTime.set(Calendar.YEAR, year);
		startTime.set(Calendar.MONTH, month);
		startTime.set(Calendar.DAY_OF_MONTH, day);

		endTime.set(Calendar.YEAR, year);
		endTime.set(Calendar.MONTH, month);
		endTime.set(Calendar.DAY_OF_MONTH, day);

		boolean within = startTime.before(now) && endTime.after(now);

		log.trace(
				(within ? "* " : "") +
				"startTime=" + DateFormatUtils.format(startTime, datePtn) + "/" +
				"now=" + DateFormatUtils.format(now, datePtn) + "/" +
				"endTime=" + DateFormatUtils.format(endTime, datePtn)
		);

		return within;
	}

	private static Optional<Calendar> parseTime(String time) {
		if(time == null) {return Optional.empty();}

		if(!timePattern.matcher(time).matches()){
			log.error("Illegal time format = " + time + ".");
			return Optional.empty();
		}

		String[] t = time.split(":");
		if(t.length < 3) {return Optional.empty();}

		int[] v = new int[t.length];
		for(int i = 0; i < t.length; i++) {
			String s = t[i];

			int a = 0;
			try {
				a = Integer.valueOf(s);
				if(i == 0) {
					if(a > 24) {
						a = 24;
						log.warn("Illegal time hour format = " + time + ",Replace to " + a + ".");
					}
				}
				else if(i == 1 || i == 2) {
					if(a > 59) {
						a = 59;
						log.warn("Illegal time minute/seconds format = " + time + ",Replace to " + a + ".");
					}
				}
			}catch(NumberFormatException ex) {
				log.error("Illegal time format = " + time + ".", ex);
				return Optional.empty();
			}

			v[i] = a;
		}

		int hours = v[0];
		int minutes = v[1];
		int seconds = v[2];

		Calendar result = Calendar.getInstance();

		result.set(Calendar.HOUR_OF_DAY, hours);
		result.set(Calendar.MINUTE, minutes);
		result.set(Calendar.SECOND, seconds);

		return Optional.of(result);
	}

}
