package org.jp.illg.util;

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PropertyUtils {

	public static boolean getBoolean(Properties properties, String propertyName, boolean defaultValue) {
		if(properties == null || propertyName == null || "".equals(propertyName))
			return defaultValue;

		String propertyValue = properties.getProperty(propertyName);
		if(propertyValue != null) {
			Boolean value = getBoolean(propertyValue);
			if(value != null)
				return value;
			else {
				log.warn("Unspecified property value. [" + propertyName + " : " + propertyValue + "]");
				return defaultValue;
			}
		}
		else {
//			log.warn("Could not found property " + propertyName + ".");
			return defaultValue;
		}
	}

	public static boolean getBoolean(String propertyValue, boolean defaultValue) {
		Boolean result = getBoolean(propertyValue);
		if(result != null)
			return result;
		else
			return defaultValue;
	}

	public static Boolean getBoolean(String propertyValue) {
		if(propertyValue != null) {
			if("true".equals(propertyValue) || "True".equals(propertyValue) || "TRUE".equals(propertyValue))
				return true;
			else if("false".equals(propertyValue) || "False".equals(propertyValue) || "FALSE".equals(propertyValue))
				return false;
			else
				return null;
		}
		else
			return null;
	}

	public static int getInteger(Properties properties, String propertyName, int defaultValue) {
		if(properties == null || propertyName == null || "".equals(propertyName))
			return defaultValue;

		String property = properties.getProperty(propertyName);
		if(property != null) {
			try {
				return Integer.valueOf(property);
			}catch(NumberFormatException ex) {
				log.warn("Could not convert to number. [" + propertyName + " : " + property + "]");
				return defaultValue;
			}
		}
		else
			return defaultValue;
	}

	public static long getLong(Properties properties, String propertyName, long defaultValue) {
		if(properties == null || propertyName == null || "".equals(propertyName))
			return defaultValue;

		String property = properties.getProperty(propertyName);
		if(property != null) {
			try {
				return Long.valueOf(property);
			}catch(NumberFormatException ex) {
				log.warn("Could not convert to number. [" + propertyName + " : " + property + "]");
				return defaultValue;
			}
		}
		else
			return defaultValue;
	}

	public static float getFloat(Properties properties, String propertyName, float defaultValue) {
		if(properties == null || propertyName == null || "".equals(propertyName))
			return defaultValue;

		String property = properties.getProperty(propertyName);
		if(property != null) {
			try {
				return Float.valueOf(property);
			}catch(NumberFormatException ex) {
				log.warn("Could not convert to number. [" + propertyName + " : " + property + "]");
				return defaultValue;
			}
		}
		else
			return defaultValue;
	}

	public static String getString(Properties properties, String propertyName, String defaultValue) {
		if(properties == null || propertyName == null || "".equals(propertyName))
			return defaultValue;

		return properties.getProperty(propertyName, defaultValue);
	}
}
