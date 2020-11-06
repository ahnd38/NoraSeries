package org.jp.illg.dstar.util.icom.rptlst;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.jp.illg.dstar.service.repeatername.importers.icom.model.IcomRepeater;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.util.FileUtil;
import org.jp.illg.util.SystemUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IcomRepeaterListReader {
	
	private static final String logTag =
		IcomRepeaterListReader.class.getSimpleName() + " : ";
	
	public IcomRepeaterListReader() {}
	
	public static Map<String, IcomRepeater> readIcomRepeaterListCSV(){
		if(SystemUtil.IS_Android)
			return readIcomRepeaterListCSVFromAndroidAsset("RepeaterList/RepeaterList.csv");
		else
			return readIcomRepeaterListCSV("./assets/RepeaterList/RepeaterList.csv");
	}
	
	public static Map<String, IcomRepeater> readIcomRepeaterListCSVFromAndroidAsset(
		String filePath
	){
		if (filePath == null || "".equals(filePath)) {
			log.warn("Could not read host file. file path is empty.");
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
				final BufferedReader dis = new BufferedReader(
					new InputStreamReader(hostsFileStream, StandardCharsets.UTF_8)
				);
				
				return readIcomRepeaterListCSV(dis);
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
	
	public static Map<String, IcomRepeater> readIcomRepeaterListCSV(
		String filePath
	){
		if (filePath == null || "".equals(filePath)) {
			if(log.isWarnEnabled())
				log.warn(logTag + "Could not icom repeater list file, filepath is empty.");
			
			return null;
		}

		final File listFile = new File(filePath);
		boolean externalMode = listFile.exists() && listFile.canRead();

		if(externalMode) {
			if (!listFile.exists()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not found specified file " + filePath + ".");
				
				return null;
			} else if (!listFile.canRead()) {
				if(log.isWarnEnabled())
					log.warn(logTag + "Could not load the specified file, Is correct the file permission?");
				
				return null;
			}
		}

		BufferedReader dis = null;
		try{
			if(externalMode) {
				dis = new BufferedReader(
					new InputStreamReader(new FileInputStream(listFile), StandardCharsets.UTF_8)
				);

				return readIcomRepeaterListCSV(dis);
			}
			else {
				filePath = filePath.replaceFirst("^[.]*", "");
				byte[] ambeFileBin = FileUtil.getReadAllBytes(filePath);
				if(ambeFileBin == null) {
					if(log.isWarnEnabled())
						log.warn("Could not read " + filePath + ".");
					
					return null;
				}
				dis = new BufferedReader(
					new InputStreamReader(new ByteArrayInputStream(ambeFileBin), StandardCharsets.UTF_8)
				);

				return readIcomRepeaterListCSV(dis);
			}
		}catch(IOException ex) {
			if(log.isWarnEnabled())
				log.warn("Could not load specified file " + filePath + ".", ex);
		}finally {
			try{
				if(dis != null){dis.close();}
			}catch (IOException ioex){}
		}

		return null;
	}
	
	public static Map<String, IcomRepeater> readIcomRepeaterListCSV(
		final BufferedReader in
	) {
		final Map<String, IcomRepeater> result = new HashMap<>();
		
		String line = null;
		int lineNumber = 0;
		try {
			while((line = in.readLine()) != null) {
				lineNumber++;
				if(lineNumber <= 1) {continue;}
				
				final String[] items = line.split(",");
				
				if(items.length < 6) {continue;}
				
				int groupNo = 0;
				try {
					groupNo = Integer.valueOf(items[0]);
				}catch(NumberFormatException ex) {
					if(log.isDebugEnabled())
						log.debug(logTag + "Invalid group no format, lineNumber = " + lineNumber + ".");
					
					continue;
				}
				
				String groupName = items[1].trim();
				String name = items[2].trim();
				String subName = items[3].trim();
				String repeaterCallsign = DSTARUtils.formatFullLengthCallsign(items[4].trim());
				String gatewayCallsign = DSTARUtils.formatFullCallsign(repeaterCallsign, 'G');
				
				double frequency = 0.0d;
				if(items.length >= 7) {
					final String item = items[6];
					try {
						frequency = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid frequency format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				String duplexMode = "";
				if(items.length >= 8) {duplexMode = items[7].trim();}
				
				double frequencyOffsetMHz = 0.0d;
				if(items.length >= 9) {
					final String item = items[8].trim();
					try {
						frequencyOffsetMHz = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid frequency offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				String mode = "";
				if(items.length >= 10) {mode = items[9].trim();}
				
				String tone = "";
				if(items.length >= 11) {tone = items[10].trim();}
				
				String repeaterTone = "";
				if(items.length >= 12) {repeaterTone = items[11].trim();}
				
				boolean isRepeater = true;
				if(items.length >= 13) {
					final String item = items[12].trim();
					
					if("YES".equalsIgnoreCase(item))
						isRepeater = true;
					else if("NO".equalsIgnoreCase(item))
						isRepeater = false;
					else {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid repeater format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				boolean isRepeater1Use = false;
				if(items.length >= 14) {
					final String item = items[13].trim();
					
					if("YES".equalsIgnoreCase(item))
						isRepeater1Use = true;
					else if("NO".equalsIgnoreCase(item))
						isRepeater1Use = false;
					else {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid repeater 1 use format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				String position = "";
				if(items.length >= 15) {position = items[14].trim();}
				
				double latitude = 0.0d;
				if(items.length >= 16) {
					final String item = items[15].trim();
					try {
						latitude = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid latitude offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				double longitude = 0.0d;
				if(items.length >= 17) {
					final String item = items[16].trim();
					try {
						latitude = Double.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid longitude offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				int utcOffset = 0;
				if(items.length >= 18) {
					final String item = items[17].trim();
					try {
						utcOffset = Integer.valueOf(item);
					}catch(NumberFormatException ex) {
						if(log.isDebugEnabled())
							log.debug(logTag + "Invalid utc offset offset format = " + item + ", lineNumber = " + lineNumber + ".");
					}
				}
				
				final IcomRepeater repeater =
					new IcomRepeater(
						groupNo, groupName, name, subName,
						repeaterCallsign, gatewayCallsign,
						frequency, duplexMode, frequencyOffsetMHz,
						mode, tone, repeaterTone,
						isRepeater, isRepeater1Use,
						position, latitude, longitude, utcOffset
					);
				
				result.put(repeaterCallsign, repeater);
			}
		}catch(IOException ex) {
			if(log.isWarnEnabled()) {
				log.warn(logTag + "Could not read icom repeater list.", ex);
			}
		}
		
		return result;
	}
}
