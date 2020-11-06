package org.jp.illg.util.audio.vocoder;

public interface VoiceVocoder<T> {

	public boolean init(final int sampleRate, final int channel, final int bitRate);
	public void dispose();

	public String getVocoderType();
	public void setVocoderType(final String vocoderType);

	public boolean encodeInput(T pcm);
	public byte[] encodeOutput();

	public boolean decodeInput(byte[] audio, boolean packetLoss);
	public T decodeOutput();


	public int getEncodeMaxPacketSize();

	public int getEncodeMinPacketSize();

	public int getEncodeAveragePacketSize();
}
