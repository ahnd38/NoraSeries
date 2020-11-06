package org.jp.illg.util.gson;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import lombok.NonNull;

public final class GsonTool {
	
	private GsonTool() {}
	
	public static boolean getBoolean(
		@NonNull final  JsonObject jsonObj, @NonNull final String memberName, final boolean defaultValue
	) {
		JsonElement element = null;
		if(jsonObj.has(memberName) && (element = jsonObj.get(memberName)) != null) {
			try {
				return element.getAsBoolean();
			}catch(IllegalStateException | ClassCastException ex) {
				return defaultValue;
			}
		}
		else {
			return defaultValue;
		}
	}
	
	public static long getLong(
		@NonNull final  JsonObject jsonObj, @NonNull final String memberName, final long defaultValue
	) {
		JsonElement element = null;
		if(jsonObj.has(memberName) && (element = jsonObj.get(memberName)) != null) {
			try {
				return element.getAsLong();
			}catch(IllegalStateException | ClassCastException ex) {
				return defaultValue;
			}
		}
		else {
			return defaultValue;
		}
	}
	
	public static double getDouble(
		@NonNull final  JsonObject jsonObj, @NonNull final String memberName, final double defaultValue
	) {
		JsonElement element = null;
		if(jsonObj.has(memberName) && (element = jsonObj.get(memberName)) != null) {
			try {
				return element.getAsDouble();
			}catch(IllegalStateException | ClassCastException ex) {
				return defaultValue;
			}
		}
		else {
			return defaultValue;
		}
	}
	
	public static int getInt(
		@NonNull final  JsonObject jsonObj, @NonNull final String memberName, final int defaultValue
	) {
		JsonElement element = null;
		if(jsonObj.has(memberName) && (element = jsonObj.get(memberName)) != null) {
			try {
				return element.getAsInt();
			}catch(IllegalStateException | ClassCastException ex) {
				return defaultValue;
			}
		}
		else {
			return defaultValue;
		}
	}
	
	public static String getString(
		@NonNull final  JsonObject jsonObj, @NonNull final String memberName, @NonNull final String defaultValue
	) {
		JsonElement element = null;
		if(jsonObj.has(memberName) && (element = jsonObj.get(memberName)) != null) {
			try {
				return element.getAsString();
			}catch(IllegalStateException | ClassCastException ex) {
				return defaultValue;
			}
		}
		else {
			return defaultValue;
		}
	}

}
