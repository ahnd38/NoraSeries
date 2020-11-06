package org.jp.illg.util.ambe;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.util.FileUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AMBEBinaryFileReader {

	private static final String ambeFileHeader = "AMBE";

	private static final int ambeFileOffset = DSTARDefines.VoiceSegmentLength * 15; //15

	private static final int ambeFileSizeLimit = 1024 * 128;

	private AMBEBinaryFileReader() {}


	public static byte[] readAMBEBinaryFileFromAndroidAsset(String filePath){

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
				final DataInputStream dis = new DataInputStream(new BufferedInputStream(hostsFileStream));

				return readAMBEBinaryFile(dis, ambeFileSizeLimit);
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

	public static byte[] readAMBEBinaryFile(String filePath) {

		if (filePath == null && "".equals(filePath)) {
			if(log.isWarnEnabled())
				log.warn("Could not read host file. hostsfile configuration is empty.");

			return null;
		}

		File ambeFile = new File(filePath);
		boolean externalMode = ambeFile.exists() && ambeFile.canRead();

		long fileLength = 0;

		if(externalMode) {
			fileLength = ambeFile.length();

			if (!ambeFile.exists()) {
				if(log.isWarnEnabled())
					log.warn("Could not found specified file " + filePath + ".");

				return null;
			} else if (fileLength > ambeFileSizeLimit) {
				if(log.isWarnEnabled())
					log.warn("Specified ambe binary file exseeds limit(" + ambeFileSizeLimit + "), ignore exceeded data.");

				fileLength = ambeFileSizeLimit;
			} else if (fileLength < (ambeFileOffset * 2)) {
				if(log.isWarnEnabled())
					log.warn("Specified ambe binary file under limit" + (ambeFileOffset * 2) + " bytes.");

				return null;
			}
			else if (fileLength <= ambeFileHeader.length()) {
				if(log.isWarnEnabled())
					log.warn("Specified file is bellow the minimum capacity, Could not read.");

				return null;
			} else if (!ambeFile.canRead()) {
				if(log.isWarnEnabled())
					log.warn("Could not load the specified file, Is correct the file permission?");

				return null;
			}
		}

		DataInputStream dis = null;
		try{
			if(externalMode) {
				dis = new DataInputStream(new FileInputStream(ambeFile));

				return readAMBEBinaryFile(dis, fileLength);
			}
			else {
				filePath = filePath.replaceFirst("^[.]*", "");
				byte[] ambeFileBin = FileUtil.getReadAllBytes(filePath);
				if(ambeFileBin == null) {
					if(log.isWarnEnabled())
						log.warn("Could not read " + filePath + ".");

					return null;
				}
				dis = new DataInputStream(new ByteArrayInputStream(ambeFileBin));

				return readAMBEBinaryFile(dis, ambeFileSizeLimit);
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

	public static byte[] readAMBEBinaryFile(DataInputStream ambeFileStream, long sizeLimit) {

		long sizeCount = 0;

		byte[] headerBuffer = new byte[ambeFileHeader.length()];
		try{
			if(	// check header
				ambeFileStream == null ||
				ambeFileStream.read(headerBuffer) != ambeFileHeader.length() ||
				!ambeFileHeader.equals(new String(headerBuffer))
			) {
				if(log.isWarnEnabled())
					log.warn("Specified file configuration is invalid.");

				return null;
			}
			sizeCount += ambeFileHeader.length();

			boolean overflow = false;

			ByteBuffer data = ByteBuffer.allocate(ambeFileSizeLimit);

			//(int)fileLength - ambeFileHeader.length() - (ambeFileOffset * 2)

			// 先頭オフセット分読み捨て
			for(int count = 0; count < ambeFileOffset; count++) {
				ambeFileStream.read();
				sizeCount++;
			}

			byte[] buffer = new byte[128];
			int readBytes = 0;
			while((readBytes = ambeFileStream.read(buffer))!= -1) {
				if(data.remaining() < readBytes) {
					readBytes = data.remaining();
					overflow = false;
				}

				long limitRemain = sizeLimit - (sizeCount + readBytes);
				if(limitRemain < readBytes)
					readBytes = limitRemain < 0 ? 0 : (int)limitRemain;

				sizeCount += readBytes;

				if(readBytes > 0){data.put(buffer, 0, readBytes);}
			}

			if(overflow) {
				if(log.isWarnEnabled())
					log.warn("Data buffer overflow. please add data buffer size.");
			}

			data.flip();

			int resultLength =
					((int)data.remaining() / (int)DSTARDefines.VoiceSegmentLength) * DSTARDefines.VoiceSegmentLength;
			byte[] result = new byte[resultLength];
			for(int index = 0; index < result.length && data.hasRemaining(); index++)
				result[index] = data.get();

			return result;
		} catch (IOException ex) {
			if(log.isWarnEnabled())
				log.warn("Could not read ambe binary file.", ex);

			return null;
		}
	}

}
