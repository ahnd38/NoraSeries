package org.jp.illg.util.io.datastore.android;

import android.content.Context;

import com.annimon.stream.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import org.jp.illg.util.android.AndroidHelper;
import org.jp.illg.util.gson.GsonTypeAdapter;
import org.jp.illg.util.io.datastore.DataStoreBase;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DataStoreAndroid<T> extends DataStoreBase<T> {

//	private static final String logTag = DataStoreAndroid.class.getSimpleName() + " : ";

	public DataStoreAndroid(
		@NonNull Class<?> dataStore,
		@NonNull final String directoryName,
		@NonNull final String fileName,
		final GsonTypeAdapter... adapters
	) {
		super(dataStore, directoryName, fileName, adapters);
	}

	/**
	 * データを保存するディレクトリを取得する
	 * @return データを保存するディレクトリ(取得不能の場合にはnull)
	 */
	protected String getTargetDirectoryPath() {
		final Context context = AndroidHelper.getApplicationContext();
		if(context == null){return null;}

		final File dir = context.getExternalFilesDir(null);
		if(dir == null){return null;}

		return dir.getAbsolutePath();
	}
}


