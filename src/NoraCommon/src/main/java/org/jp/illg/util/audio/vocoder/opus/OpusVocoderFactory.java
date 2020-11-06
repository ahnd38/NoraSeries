package org.jp.illg.util.audio.vocoder.opus;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.ShortBuffer;

import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.audio.vocoder.VoiceVocoder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpusVocoderFactory {

	private OpusVocoderFactory() {
		super();
	}


	@SuppressWarnings("unchecked")
	public static VoiceVocoder<ShortBuffer> createOpusVocoder(final String vocoderType, final boolean useFEC) {

		VoiceVocoder<ShortBuffer> vocoder = null;

		if(SystemUtil.IS_Android) {
			try {
				Class<?> vocoderClass =
						Class.forName("org.jp.illg.util.audio.vocoder.opus.android.OpusVocoderAndroid");
				
				Constructor<?> constructor = vocoderClass.getConstructor(String.class, Boolean.TYPE);
				
				vocoder = (VoiceVocoder<ShortBuffer>)constructor.newInstance(vocoderType, useFEC);
			}catch (
				ClassNotFoundException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException |
				InstantiationException ex
			){
				log.warn("Could not load voice vocoder class for android.", ex);
			}
		}
		else {
			try {
				Class<?> vocoderClass =
					Class.forName("org.jp.illg.util.audio.vocoder.opus.winlinux.OpusVocoderWinLinux");

				Constructor<?> constructor = vocoderClass.getConstructor(String.class, Boolean.TYPE);

				vocoder = (VoiceVocoder<ShortBuffer>)constructor.newInstance(vocoderType, useFEC);
			}catch (
				ClassNotFoundException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException |
				InstantiationException ex
			){
				log.warn("Could not load voice vocoder class for linux/windows.", ex);
			}
		}

		return vocoder;
	}
}
