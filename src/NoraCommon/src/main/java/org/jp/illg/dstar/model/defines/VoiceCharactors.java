package org.jp.illg.dstar.model.defines;

public enum VoiceCharactors {
	Unknown("", "", ""),
	Silent("Silent", "", ""),
	KizunaAkari(
		"Akari",
		"./assets/Voice/KizunaAkari/",
		"Voice/KizunaAkari/"
	),
	QOW(
		"QOW",
		"./assets/Voice/QOW/",
		"Voice/QOW/"
),
	;

	private final String charactorName;
	private final String voiceDataDirectoryPath;
	private final String voiceDataAndroidAssetPath;


	VoiceCharactors(
		final String charactorName,
		final String voiceDataDirectoryPath,
		final String voiceDataAndroidAssetPath
	) {
		this.charactorName = charactorName;
		this.voiceDataDirectoryPath = voiceDataDirectoryPath;
		this.voiceDataAndroidAssetPath = voiceDataAndroidAssetPath;
	}

	public String getCharactorName() {
		return this.charactorName;
	}

	public String getVoiceDataDirectoryPath() {
		return this.voiceDataDirectoryPath;
	}

	public String getVoiceDataAndroidAssetPath(){ return this.voiceDataAndroidAssetPath;}

	public static VoiceCharactors getTypeByCharactorName(String charactorName) {
		if(charactorName != null) {
			for(VoiceCharactors v : values()) {
				if(v.getCharactorName().equals(charactorName)) {return v;}
			}
		}
		return VoiceCharactors.Unknown;
	}

	public static String getClassNameByVoiceDataDirectoryPath(String voiceDataDirectoryPath) {
		if(voiceDataDirectoryPath != null) {
			for(VoiceCharactors v : values()) {
				if(v.getVoiceDataDirectoryPath().equals(voiceDataDirectoryPath)) {return v.getVoiceDataDirectoryPath();}
			}
		}
		return VoiceCharactors.Unknown.getVoiceDataDirectoryPath();
	}
}
