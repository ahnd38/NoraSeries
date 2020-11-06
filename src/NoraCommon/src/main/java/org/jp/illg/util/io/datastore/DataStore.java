package org.jp.illg.util.io.datastore;

import java.lang.reflect.Type;

import com.annimon.stream.Optional;
import com.google.gson.TypeAdapter;

public interface DataStore<T> {

	public Class<?> getDataStoreClass();

	public String getAbsoluteFilePath();

	public T getData();
	public void setData(T data);

	public boolean write();
	public boolean write(T data);

	public Optional<T> read();

	public boolean addTypeAdapter(Type type, TypeAdapter<?> adapter);
	public boolean removeTypeAdapter(Type type);
}
