package org.jp.illg.util.io.datastore.win;

import org.jp.illg.util.gson.GsonTypeAdapter;
import org.jp.illg.util.io.datastore.DataStoreBase;

import lombok.NonNull;

public class DataStoreWindows<T> extends DataStoreBase<T> {

//	private static final String logTag = DataStoreWindows.class.getSimpleName() + " : ";

	public DataStoreWindows(
		@NonNull Class<?> dataStore,
		@NonNull final String directoryName,
		@NonNull final String fileName,
		final GsonTypeAdapter... adapters
	) {
		super(dataStore, directoryName, fileName, adapters);
	}

	@Override
	protected String getTargetDirectoryPath() {
		return System.getProperty("user.home");
	}
}
