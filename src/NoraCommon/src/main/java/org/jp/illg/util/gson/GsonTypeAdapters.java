package org.jp.illg.util.gson;

import java.io.IOException;
import java.util.Date;

import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import lombok.Getter;

public class GsonTypeAdapters {

	private GsonTypeAdapters() {}

	@Getter
	private static final GsonTypeAdapter dateAdapter =
		new GsonTypeAdapter(
			Date.class,
			new TypeAdapter<Date>() {

				@Override
				public void write(JsonWriter out, Date value) throws IOException {
					if(value == null) {
						out.nullValue();
						return;
					}

					out.value(value.getTime());

					return;
				}

				@Override
				public Date read(JsonReader in) throws IOException {
					if(in.peek() == JsonToken.NULL) {
						in.nextNull();

						return null;
					}

					final String timeString = in.nextString();
					long time = 0;
					try {
						time = Long.valueOf(timeString);
					}catch(NumberFormatException ex) {
						throw new JsonSyntaxException("Time value = " + timeString + " is not valid format.", ex);
					}

					return new Date(time);
				}
			}
		);
}
