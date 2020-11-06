package org.jp.illg.util.audio.util.winlinux;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

import lombok.NonNull;

public class AudioTool {

	private AudioTool() {
		super();
	}

	public static List<Mixer.Info> getCaptureMixers(@NonNull final AudioFormat format){
		return getMixerInfos(true, format);
	}

	public static List<Mixer.Info> getPlaybackMixers(@NonNull final AudioFormat format){
		return getMixerInfos(false, format);
	}

	public static List<Mixer.Info> getMixerInfos(
		final boolean isCapture, @NonNull final AudioFormat format
	){
		DataLine.Info lineInfo =
			new DataLine.Info(isCapture ? TargetDataLine.class : SourceDataLine.class, format);
		List<Mixer.Info> filteredMixers = filterDevices(lineInfo);

		return filteredMixers;
	}

	public static Mixer.Info getMixerInfo(
		final boolean isCapture,
		final String captureMixerName, final AudioFormat captureAudioFormat
	) {

		final DataLine.Info targetInfo =
			new DataLine.Info(isCapture ? TargetDataLine.class:SourceDataLine.class, captureAudioFormat);
		final List<Mixer.Info> mixers = filterDevices(targetInfo);
		if(mixers == null || mixers.size() < 1){
			return null;
		}
		Mixer.Info mixerInfo = null;
		if(captureMixerName != null) {
			for(final Mixer.Info mixer : mixers) {
				if(captureMixerName.equals(mixer.getName())) {
					mixerInfo = mixer;
					break;
				}
			}
			if(mixerInfo == null) {
				return null;
			}
		}
		else {
			mixerInfo = mixers.get(0);
		}

		return mixerInfo;
	}

	private static List<Mixer.Info> filterDevices(final Line.Info supportedLine) {
		List<Mixer.Info> result = new ArrayList<Mixer.Info>();

		Mixer.Info[] infos = AudioSystem.getMixerInfo();
		for (Mixer.Info info : infos) {
			Mixer mixer = AudioSystem.getMixer(info);
			if (mixer.isLineSupported(supportedLine)) {
				result.add(info);
			}
		}
		return result;
	}

}
