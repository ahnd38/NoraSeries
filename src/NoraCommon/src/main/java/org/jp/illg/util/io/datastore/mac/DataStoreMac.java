package org.jp.illg.util.io.datastore.mac;

import org.jp.illg.util.gson.GsonTypeAdapter;
import org.jp.illg.util.io.datastore.DataStoreBase;

import lombok.NonNull;

public class DataStoreMac<T> extends DataStoreBase<T> {

	public DataStoreMac(
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
