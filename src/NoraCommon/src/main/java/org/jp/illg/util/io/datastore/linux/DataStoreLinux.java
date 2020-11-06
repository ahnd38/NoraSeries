package org.jp.illg.util.io.datastore.linux;

import java.io.File;

import org.jp.illg.util.gson.GsonTypeAdapter;
import org.jp.illg.util.io.datastore.DataStoreBase;

import lombok.NonNull;

public class DataStoreLinux<T> extends DataStoreBase<T> {

	//	private static final String logTag = DataStoreLinux.class.getSimpleName() + " : ";

	public DataStoreLinux(
		@NonNull Class<?> dataStore,
		@NonNull final String directoryName,
		@NonNull final String fileName,
		final GsonTypeAdapter... adapters
	) {
		super(dataStore, directoryName, fileName, adapters);
	}

	@Override
	protected String getTargetDirectoryPath() {
		final File homeDir = new File(System.getProperty("user.home"));
		final File userDir = new File(System.getProperty("user.dir"));

		if(homeDir.isDirectory() && homeDir.canRead() && homeDir.canWrite()) {
			return homeDir.getAbsolutePath();
		}
		else if(userDir.isDirectory() && userDir.canRead() && userDir.canWrite()) {
			return userDir.getAbsolutePath();
		}
		else {
			return null;
		}
	}
}
