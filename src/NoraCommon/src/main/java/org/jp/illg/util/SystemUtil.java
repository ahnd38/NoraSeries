package org.jp.illg.util;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import lombok.Getter;

public class SystemUtil {

	public final static boolean IS_Android;

	@Getter
	private final static Pattern domainRegExpPattern;

	@Getter
	private final static Pattern ipv4RegExpPattern;

	@Getter
	private final static String lineSeparator;

	private static Method elapsedRealtimeNanos = null;


	static{
		String vendorName = System.getProperty("java.vm.vendor");
		if(vendorName != null){vendorName = vendorName.toLowerCase();}

		IS_Android = vendorName != null && vendorName.contains("android");

		domainRegExpPattern =
			Pattern.compile("\\b((?=[a-z0-9-]{1,63}\\.)(xn--)?[a-z0-9]+(-[a-z0-9]+)*\\.)+[a-z]{2,63}\\b(|[:][0-9]{1,5})$");

		ipv4RegExpPattern =
			Pattern.compile("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)(|[:][0-9]{1,5})$");

		lineSeparator = System.getProperty("line.separator");

		if(IS_Android) {
			try {
				Class<?> systemClockClass = Class.forName("android.os.SystemClock");
				elapsedRealtimeNanos = systemClockClass.getMethod("elapsedRealtimeNanos");
			}catch(ClassNotFoundException|NoSuchMethodException ex){
				throw new RuntimeException();
			}
		}
	}

	public static int getAvailableProcessors() {
		return Runtime.getRuntime().availableProcessors();
	}

	public static double getJavaVersion() {
		final String v = System.getProperty("java.vm.specification.version");
		if(v == null || "".equals(v)) {return -1.0d;}

		double version = 0.0d;
		try {
			version = Double.valueOf(v);
		}catch(NumberFormatException ex) {
			version = -1.0d;
		}

		return version;
	}

	public static String getJVMInformation(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();

		for(int i = 0; i < indentLevel; i++) {sb.append(' ');}
		sb.append("[");
		sb.append("Java");
		sb.append("]");

		sb.append(getLineSeparator());

		for(int i = 0; i < (indentLevel + 4); i++) {sb.append(' ');}
		sb.append(System.getProperty("java.version"));
		sb.append('(');
		sb.append(System.getProperty("java.vm.specification.version"));
		sb.append(')');


		sb.append(getLineSeparator());


		for(int i = 0; i < indentLevel; i++) {sb.append(' ');}
		sb.append("[");
		sb.append("JVM");
		sb.append("]");

		sb.append(getLineSeparator());

		for(int i = 0; i < (indentLevel + 4); i++) {sb.append(' ');}
		sb.append(System.getProperty("java.vm.name"));
		sb.append(" ");
		sb.append(System.getProperty("java.vm.version"));

		sb.append(getLineSeparator());

		for(int i = 0; i < (indentLevel + 4); i++) {sb.append(' ');}
		sb.append(System.getProperty("java.vm.vendor"));


		sb.append(getLineSeparator());


		for(int i = 0; i < indentLevel; i++) {sb.append(' ');}
		sb.append("[");
		sb.append("OS");
		sb.append("]");

		sb.append(getLineSeparator());

		for(int i = 0; i < (indentLevel + 4); i++) {sb.append(' ');}
		sb.append(System.getProperty("os.name"));
		sb.append(" ");
		sb.append(System.getProperty("os.arch"));

		return sb.toString();
	}

	public static long getCurrentTimeMillis() {
		return System.currentTimeMillis();
	}

	public static long getNanoTimeCounterValue(){
		long nanoTime = -1;

		try {
			if(elapsedRealtimeNanos != null)
				nanoTime = (Long)elapsedRealtimeNanos.invoke(null);
			else
				nanoTime = System.nanoTime();
		}catch(ReflectiveOperationException ex) {
			throw new RuntimeException();
		}

		if(nanoTime < 0)
			throw new RuntimeException("Could not get counter value.");
		else
			return nanoTime;
	}

	public static long getAbsoluteTimeMillisFromNanoTimeCounterValue(
		final long nanoTimeCounterValue
	) {
		if(nanoTimeCounterValue < 0) {return 0;}

		return getCurrentTimeMillis() -
			(TimeUnit.MILLISECONDS.convert(getNanoTimeCounterValue() - nanoTimeCounterValue, TimeUnit.NANOSECONDS));
	}
}
