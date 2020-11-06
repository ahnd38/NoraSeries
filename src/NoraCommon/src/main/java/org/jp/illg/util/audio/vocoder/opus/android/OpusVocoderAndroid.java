package org.jp.illg.util.audio.vocoder.opus.android;

import org.jp.illg.util.audio.vocoder.opus.OpusVocoderBase;

import com.sun.jna.Native;

import tomp2p.opuswrapper.Opus;


public class OpusVocoderAndroid extends OpusVocoderBase {

	static {
		System.loadLibrary("opus");
	}

	public OpusVocoderAndroid(final String vocoderType, final boolean useFEC) {
		super(vocoderType, useFEC);
	}

	@Override
	protected Opus createOpusInstance() throws RuntimeException {
		return (Opus) Native.load("opus", Opus.class);
	}
}
