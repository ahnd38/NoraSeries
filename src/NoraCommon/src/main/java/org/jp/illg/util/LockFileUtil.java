package org.jp.illg.util;

import java.io.File;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.StandardOpenOption;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LockFileUtil implements AutoCloseable{

	private static final String logHeader;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private String lockFilePath;

	FileChannel fileChannel;
	FileLock fileLock;

	static {
		logHeader = LockFileUtil.class.getSimpleName() + " : ";
	}

	private LockFileUtil() {
		super();


	}

	public LockFileUtil(String lockFilePath) {
		this();

		if(lockFilePath == null || "".equals(lockFilePath))
			throw new IllegalArgumentException("LockFilePath is must not null");

		setLockFilePath(lockFilePath);

		fileChannel = null;
		fileLock = null;
	}

	public synchronized boolean isLock() {
		return fileChannel != null && fileChannel.isOpen() && fileLock != null && fileLock.isValid();
	}

	public synchronized boolean getLock() {

		if(isLock()) {return true;}


		releaseLock();

		File lockFile = new File(getLockFilePath());

		try {
			FileChannel fc =
				FileChannel.open(lockFile.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);

			FileLock lock = fc.tryLock();

			if(lock != null) {
				return true;
			}
			else {releaseLock();}
		}catch(IOException ex) {
			log.error(logHeader + "Could not get lock.", ex);
		}

		return false;
	}

	public synchronized void releaseLock() {
		if(fileLock != null) {
			try {
				fileLock.release();
			}catch(IOException ex) {
				if(log.isDebugEnabled()) {log.debug(logHeader + "Filelock release error.", ex);}
			}
			fileLock = null;
		}
		if(fileChannel != null && fileChannel.isOpen()) {
			try {
				fileChannel.close();
			}catch(IOException ex) {
				if(log.isDebugEnabled()) {log.debug(logHeader + "Filechannel close error.", ex);}
			}
			fileChannel = null;
		}
	}

	@Override
	public void close() {
		releaseLock();
	}
}
