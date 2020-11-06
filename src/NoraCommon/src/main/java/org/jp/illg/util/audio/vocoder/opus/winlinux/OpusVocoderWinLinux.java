package org.jp.illg.util.audio.vocoder.opus.winlinux;

import java.io.IOException;

import org.jp.illg.util.audio.vocoder.opus.OpusVocoderBase;

import club.minnced.opus.util.OpusLibrary;
import tomp2p.opuswrapper.Opus;


public class OpusVocoderWinLinux extends OpusVocoderBase {

	public OpusVocoderWinLinux(final String vocoderType, final boolean useFEC) {
		super(vocoderType, useFEC);
	}

	@Override
	protected Opus createOpusInstance() throws RuntimeException {
		if(!OpusLibrary.isSupportedPlatform())
			throw new RuntimeException("Opus not supported this platform.");

		try {
			if(!OpusLibrary.isInitialized()) {OpusLibrary.loadFromJar();}
		}catch(IOException ex) {
			throw new RuntimeException("Could not load opus lib.", ex);
		}

		return Opus.INSTANCE;
	}
}
