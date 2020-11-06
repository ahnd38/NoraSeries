package org.jp.illg.nora.android.config;

import android.content.Context;

import com.annimon.stream.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.jp.illg.nora.NoraGatewayForAndroid;
import org.jp.illg.nora.android.view.model.ApplicationConfig;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ApplicationConfigReaderWriter {

	private static final String applicationConfigFileName =
			NoraGatewayForAndroid.getApplicationName() + ".json";

	public static boolean saveConfig(Context context, ApplicationConfig applicationConfig){
		if(context == null || applicationConfig == null){return false;}

		boolean success = true;

		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		String json = gson.toJson(applicationConfig);

		log.trace("Saving application config...\n" + json);

		File applicationConfigFile = new File(context.getFilesDir(), applicationConfigFileName);
		if(applicationConfigFile.exists()){applicationConfigFile.delete();}

		FileWriter writer = null;
		try{
			writer = new FileWriter(applicationConfigFile);
			writer.write(json);
			writer.flush();
		}catch (IOException ex){
			log.warn("Could not save application configuration file.", ex);
			success = false;
		}finally {
			try{
				if(writer != null){writer.close();}
			}catch (IOException ex){log.debug("Error occurred at writer close().", ex);}
		}

		return success;
	}

	public static Optional<ApplicationConfig> loadConfig(Context context){
		if(context == null){return Optional.empty();}

		Gson gson = new Gson();

		File applicationConfigFile = new File(context.getFilesDir(), applicationConfigFileName);
		if(!applicationConfigFile.exists()){return Optional.of(new ApplicationConfig());}

		StringBuilder sb = new StringBuilder();
		FileReader reader = null;
		try{
			reader = new FileReader(applicationConfigFile);
			int c;
			while((c = reader.read()) != -1){
				sb.append((char)c);
			}
		}catch(IOException ex){
			log.warn("Could not load application configuration file.", ex);
			return Optional.of(new ApplicationConfig());
		}finally {
			try{
				if(reader != null){reader.close();}
			}catch (IOException ex){log.debug("Error occurred at reader close().", ex);}
		}

		log.trace("Loading application config...\n" + sb.toString());

		ApplicationConfig applicationConfig =null;
		try {
			applicationConfig = gson.fromJson(sb.toString(), ApplicationConfig.class);
		}catch (JsonSyntaxException ex){
			log.warn("Could not load application configuration json file.", ex);
		}

		return Optional.ofNullable(applicationConfig);
	}

}
