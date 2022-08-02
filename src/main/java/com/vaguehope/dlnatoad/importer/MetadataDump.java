package com.vaguehope.dlnatoad.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

class MetadataDump {

	public static final String NEW_FILE_EXTENSION = ".json";
	public static final String PROCESSED_FILE_EXTENSION = ".imported";
	public static final String FAILED_FILE_EXTENSION = ".failed";

	public static MetadataDump readFile(final File file) throws IOException {
		try (final InputStream s = new FileInputStream(file)) {
			return readInputStream(s);
		}
	}

	public static MetadataDump readInputStream(final InputStream is) throws IOException {
		return readReader(new BufferedReader(new InputStreamReader(is)));
	}

	public static MetadataDump readReader(final Reader reader) throws IOException {
		final Gson gson = new GsonBuilder()
				.registerTypeAdapter(BigInteger.class, new BigIntegerDeserializer())
				.create();

		final Type collectionType = new TypeToken<Collection<HashAndTags>>() {}.getType();
		final List<HashAndTags> arr = new ArrayList<>(gson.fromJson(reader, collectionType));
		return new MetadataDump(arr);
	}

	private static class BigIntegerDeserializer implements JsonDeserializer<BigInteger> {
		@Override
		public BigInteger deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			return new BigInteger(json.getAsString(), 16);
		}
	}

	private final Collection<HashAndTags> hashAndTags;

	public MetadataDump(final Collection<HashAndTags> hashAndTags) {
		this.hashAndTags = hashAndTags;
	}

	public Collection<HashAndTags> getHashAndTags() {
		return this.hashAndTags;
	}

}
