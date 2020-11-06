package org.jp.illg.util.io.datastore;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.gson.GsonTypeAdapter;

import com.annimon.stream.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class DataStoreBase<T> implements DataStore<T> {

	private static final String logTag = DataStoreBase.class.getSimpleName() + " : ";

	private final Lock locker = new ReentrantLock();

	@Getter
	private final Class<?> dataStoreClass;

	@Getter
	private final String fileName;

	@Getter
	private final String directoryName;

	private T data;

	private final Map<Type, GsonTypeAdapter> adapters;

	public DataStoreBase(
		@NonNull Class<?> dataStoreClass,
		@NonNull final String directoryName,
		@NonNull final String fileName,
		final GsonTypeAdapter... adapters
	) {
		super();

		this.dataStoreClass = dataStoreClass;

		if("".equals(fileName))
			throw new IllegalArgumentException("name must not empty.");

		this.fileName = fileName;

		this.directoryName = directoryName;

		this.adapters = new HashMap<>();

		if(adapters != null) {
			for(final GsonTypeAdapter adapter : adapters)
				this.adapters.put(adapter.getType(), adapter);
		}
	}

	@Override
	public T getData() {
		return this.data;
	}

	@Override
	public void setData(T data) {
		locker.lock();
		try {
			this.data = data;
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean write() {
		locker.lock();
		try{
			final T data = getData();
			if(data == null) {return false;}

			return write(data);
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean write(@NonNull T data) {

		locker.lock();
		try{
			final boolean success =  writeInternal(data, adapters);

			if (success) { setData(data); }

			return success;
		}finally{
			locker.unlock();
		}
	}

	@Override
	public Optional<T> read() {

		locker.lock();
		try{
			final Optional<T> data = readInternal();

			if(data.isPresent()) { setData(data.get()); }

			return data;
		}finally{
			locker.unlock();
		}
	}

	@Override
	public boolean addTypeAdapter(
		@NonNull final Type type, @NonNull final TypeAdapter<?> adapter
	) {
		locker.lock();
		try {
			return adapters.put(type, new GsonTypeAdapter(type, adapter)) == null;
		}finally {
			locker.unlock();
		}
	}

	@Override
	public boolean removeTypeAdapter(@NonNull final Type type) {
		locker.lock();
		try {
			return adapters.remove(type) != null;
		}finally {
			locker.unlock();
		}
	}

	@Override
	public final String getAbsoluteFilePath() {
		final String targetDirectoryPath  = getTargetDirectoryPath();
		if(targetDirectoryPath == null){return null;}

		final File targetDirectory = new File(targetDirectoryPath);

		return
			targetDirectory.getAbsolutePath() + File.separator +
			(!"".equals(getDirectoryName()) ? (getDirectoryName() + File.separator) : "") +
			getFileName() + ".json";
	}

	/**
	 * データを保存するディレクトリを取得する
	 * @return データを保存するディレクトリ(取得不能の場合にはnull)
	 */
	protected abstract String getTargetDirectoryPath();

	/**
	 * データ読み込みソースを取得する
	 * @return データ読み込みソース(取得不能の場合にはnull)
	 * @throws IOException IOエラー例外
	 */
	private InputStream getReadSource() throws IOException{
		final File targetFile = getTargetFile();
		if(targetFile == null) {return null;}

		return new FileInputStream(targetFile);
	}

	/**
	 * データ書き込みターゲットを取得する
	 * @return データ書き込みターゲット(取得不能の場合にはnull)
	 * @throws IOException IOエラー例外
	 */
	private OutputStream getWriteTarget() throws IOException {
		final File targetFile = getTargetFile();
		if(targetFile == null) {return null;}

		return new FileOutputStream(targetFile);
	}

	private Optional<T> readInternal() {

		locker.lock();
		try(final InputStream is = getReadSource()) {
			return
				is != null ? readInternal(getDataStoreClass(), is, adapters) : Optional.empty();
		}catch(FileNotFoundException ex){
			if(log.isDebugEnabled())
				log.debug(logTag + "File is not found = " + getAbsoluteFilePath());
		}catch(IOException ex) {
			if (log.isErrorEnabled())
				log.error(logTag + "Read error.", ex);
		}finally{
			locker.unlock();
		}

		return Optional.empty();
	}

	private boolean writeInternal(final T data, final Map<Type, GsonTypeAdapter> adapters) {

		locker.lock();
		try(final OutputStream os = getWriteTarget()){
			return os != null && writeInternal(getDataStoreClass(), data, os, adapters);
		}catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Write error.", ex);
		}finally{
			locker.unlock();
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private static <T> Optional<T> readInternal(
		final Class<?> dataSourceClass, final InputStream is,
		final Map<Type, GsonTypeAdapter> adapters
	) throws IOException {

		T data;
		final GsonBuilder gsonBuilder = new GsonBuilder();
		for(final GsonTypeAdapter adapter : adapters.values())
			gsonBuilder.registerTypeAdapter(adapter.getType(), adapter.getAdapter());

		final Gson gson = gsonBuilder.create();
		try(
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
		) {
			data = (T) gson.fromJson(reader, dataSourceClass);
		}

		return Optional.ofNullable(data);
	}

	private static <T> boolean writeInternal(
		final Class<?> dataSourceClass,
		final T data, final OutputStream os,
		final Map<Type, GsonTypeAdapter> adapters
	) throws IOException {

		final GsonBuilder gsonBuilder = new GsonBuilder();
		for(final GsonTypeAdapter adapter : adapters.values())
			gsonBuilder.registerTypeAdapter(adapter.getType(), adapter.getAdapter());

		if(log.isDebugEnabled() || log.isTraceEnabled())
			gsonBuilder.setPrettyPrinting();

		final Gson gson = gsonBuilder.create();

		final String json = gson.toJson(data, dataSourceClass);

		try(
			final BufferedWriter writer =
				new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))
		) {
			writer.append(json);
			writer.flush();
		}

		return true;
	}

	private File getTargetFile() throws IOException {
		String targetDirectoryPath = getTargetDirectoryPath();
		if(targetDirectoryPath == null) {return null;}

		if(!"".equals(getDirectoryName()))
			targetDirectoryPath = targetDirectoryPath + File.separator + getDirectoryName();

		final File targetDirectory = new File(targetDirectoryPath);

		if(!targetDirectory.exists() && !targetDirectory.mkdirs()) {
			if(log.isErrorEnabled()){
				log.error(
					logTag +
						"Could not create file, please check directory permission = " + targetDirectory.getAbsolutePath()
				);
			}
		}

		final String targetFilePath = getAbsoluteFilePath();
		if(targetFilePath == null){return null;}

		final File targetFile = new File(targetFilePath);
		if(!targetFile.exists() && !targetFile.createNewFile()){
			if(log.isErrorEnabled()){
				log.error(
					logTag +
						"Could not create file, please check file permission = " + targetFile.getAbsolutePath()
				);
			}
		}

		return targetFile;
	}
}
