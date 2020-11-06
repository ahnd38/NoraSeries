package org.jp.illg.util.gson;

import java.lang.reflect.Type;

import com.google.gson.TypeAdapter;

import lombok.Getter;
import lombok.NonNull;

public class GsonTypeAdapter {

	@Getter
	private final Type type;

	@Getter
	private final TypeAdapter<?> adapter;

	public GsonTypeAdapter(
		@NonNull final Type type,
		@NonNull final TypeAdapter<?> adapter
	) {
		super();

		this.type = type;
		this.adapter = adapter;
	}

}
