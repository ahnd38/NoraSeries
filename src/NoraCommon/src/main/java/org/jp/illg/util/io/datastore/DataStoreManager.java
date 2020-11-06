package org.jp.illg.util.io.datastore;

import java.lang.reflect.Constructor;

import org.apache.commons.lang3.SystemUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.gson.GsonTypeAdapter;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataStoreManager {

	private static final String logTag = DataStoreManager.class + " : ";

	private DataStoreManager() {}

	public static <T> DataStore<T> createDataStore(
		@NonNull final Class<?> dataStoreClass,
		@NonNull final String directoryName,
		@NonNull final String fileName,
		final GsonTypeAdapter... adapters
	) {
		if(fileName == null || "".equals(fileName))
			throw new IllegalArgumentException("Name is null or empty string.");

		if(SystemUtil.IS_Android) {
			return createDataStoreInstance(
				dataStoreClass,
				"org.jp.illg.util.io.datastore.android.DataStoreAndroid",
				directoryName,
				fileName,
				adapters
			);
		}
		else if(SystemUtils.IS_OS_WINDOWS) {
			return createDataStoreInstance(
				dataStoreClass,
				"org.jp.illg.util.io.datastore.win.DataStoreWindows",
				directoryName,
				fileName,
				adapters
			);
		}
		else if(SystemUtils.IS_OS_LINUX) {
			return createDataStoreInstance(
				dataStoreClass,
				"org.jp.illg.util.io.datastore.linux.DataStoreLinux",
				directoryName,
				fileName,
				adapters
			);
		}
		else if(SystemUtils.IS_OS_MAC) {
			return createDataStoreInstance(
				dataStoreClass,
				"org.jp.illg.util.io.datastore.mac.DataStoreMac",
				directoryName,
				fileName,
				adapters
			);
		} else
			throw new RuntimeException("This operating system is not supported.");
	}

	private static <T> DataStore<T> createDataStoreInstance(
		final Class<?> dataStoreClass,
		final String dataStoreImplClassName,
		final String directoryName,
		final String fileName,
		final GsonTypeAdapter... adapters
	) {
		DataStore<T> instance = null;

		try {
			@SuppressWarnings("unchecked")
			final Class<? extends DataStore<T>> dsClass =
				(Class<? extends DataStore<T>>) Class.forName(dataStoreImplClassName);

			final Constructor<? extends DataStore<T>> constructor =
				dsClass.getConstructor(Class.class, String.class, String.class, GsonTypeAdapter[].class);

			instance = constructor.newInstance(dataStoreClass, directoryName, fileName, adapters);

		}catch(ReflectiveOperationException ex) {
			if(log.isErrorEnabled()) {
				log.error(logTag + "Could not create data source instance.", ex);
			}
		}

		return instance;
	}
}
