package org.jp.illg.util.audio.vocoder.opus;

import com.sun.jna.ptr.PointerByReference;

import org.jp.illg.util.audio.vocoder.VoiceVocoder;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import tomp2p.opuswrapper.Opus;

@Slf4j
public abstract class OpusVocoderBase implements VoiceVocoder<ShortBuffer> {
	
	private final String logHeader;
	
	private final Opus opus;
	
	private PointerByReference encoder;
	private PointerByReference decoder;
	
	private final IntBuffer errorBuffer;
	
	private final Lock locker;
	
	private boolean decodePacketLossPrev;
	
	@Getter
	@Setter
	private String vocoderType;
	
	@Getter
	@Setter
	private int sampleRate;
	
	@Getter
	@Setter
	private int channel;
	
	@Getter
	@Setter
	private int bitRate;
	
	/**
	 * Frame Durations(micro seconds)
	 */
	private static final int[] frameDurations =
			new int[] {2500,5000,10000,20000,40000,60000,80000,100000,120000};
	
	private final Map<Integer, Integer> frameSamples;
	private final Map<Integer, Integer> bufferSizes;
	
	private final Queue<ByteBuffer> encodedAudio;
	private final Queue<ShortBuffer> decodedAudio;
	
	private ShortBuffer decodeBuffer;
	
	private final boolean useFEC;
	
	private byte[][] decodeSrcAudio;
	private int decodeSrcSel;
	
	@Getter
	private int encodeMaxPacketSize;
	
	@Getter
	private int encodeMinPacketSize;
	
	@Getter
	private int encodeAveragePacketSize;
	
	public OpusVocoderBase() {
		this("", false);
	}
	
	public OpusVocoderBase(final String vocoderType, final boolean useFEC) {
		super();
		
		setVocoderType(vocoderType);
		
		locker = new ReentrantLock();
		
		logHeader = this.getClass().getSimpleName() + " : ";
		
		this.useFEC = useFEC;
		
		opus = createOpusInstance();
		
		errorBuffer = IntBuffer.allocate(1);
		
		frameSamples = new HashMap<>();
		bufferSizes = new HashMap<>();
		
		encodedAudio = new LinkedList<>();
		decodedAudio = new LinkedList<>();
		
		decodePacketLossPrev = false;
		
		decodeSrcAudio = new byte[2][];
		decodeSrcSel = 0;
	}
	
	protected abstract Opus createOpusInstance() throws RuntimeException;
	
	@Override
	public boolean init(final int sampleRate, final int channel, final int bitRate) {
		
		if(
				sampleRate != 8000 &&
				sampleRate != 12000 &&
				sampleRate != 16000 &&
				sampleRate != 24000 &&
				sampleRate != 48000
		) {throw new IllegalArgumentException(logHeader + "Illegal sample rate " + sampleRate + ".");}
		setSampleRate(sampleRate);
		
		if(channel < 1 || channel > 2) {
			throw new IllegalArgumentException(logHeader + "Illegal channel " + channel + ".");
		}
		setChannel(channel);
		
		if(bitRate < 500 || bitRate > 512000) {
			throw new IllegalArgumentException(logHeader + "Illegal bit rate " + bitRate + ".");
		}
		setBitRate(bitRate);
		
		locker.lock();
		try {
			disposeVocoder();
			
			errorBuffer.clear();
			encoder =
					opus.opus_encoder_create(sampleRate, channel, Opus.OPUS_APPLICATION_VOIP, errorBuffer);
			errorBuffer.position(1);
			errorBuffer.flip();
			
			if(encoder == null || errorBuffer.get() != Opus.OPUS_OK) {
				disposeVocoder();
				log.error(logHeader + "Could not create opus encoder.");
				return false;
			}
			
			errorBuffer.clear();
			decoder =
					opus.opus_decoder_create(sampleRate, channel, errorBuffer);
			errorBuffer.position(1);
			errorBuffer.flip();
			
			if(decoder == null || errorBuffer.get() != Opus.OPUS_OK) {
				disposeVocoder();
				log.error(logHeader + "Could not create opus decoder.");
				return false;
			}
			
			opus.opus_encoder_ctl(encoder, Opus.OPUS_RESET_STATE);
			opus.opus_decoder_ctl(decoder, Opus.OPUS_RESET_STATE);

//			opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_VBR_REQUEST, 0);
			opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_BITRATE_REQUEST, bitRate);
//			opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_BANDWIDTH_REQUEST, Opus.OPUS_BANDWIDTH_WIDEBAND);
			opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_MAX_BANDWIDTH_REQUEST, Opus.OPUS_BANDWIDTH_WIDEBAND);
			
			if(useFEC) {
				opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_PACKET_LOSS_PERC_REQUEST, 20);
				opus.opus_encoder_ctl(encoder, Opus.OPUS_SET_INBAND_FEC_REQUEST, 1);
			}
			
			frameSamples.clear();
			bufferSizes.clear();
			for(final int duration : frameDurations) {
				frameSamples.put(duration, getSampleRate() / (1000000 / duration));
				
				final int bitsPerFrame = getBitRate() / (1000000 / duration);
				bufferSizes.put(
						duration,
						(int)Math.ceil(bitsPerFrame / 8.0F)
				);
			}
			
			decodeBuffer = ShortBuffer.allocate((int)Math.ceil(getSampleRate() / (1F / 0.12F)));
			
			encodedAudio.clear();
			decodedAudio.clear();
			
		}finally {locker.unlock();}
		
		return true;
	}
	
	@Override
	public void dispose() {
		disposeVocoder();
	}
	
	@Override
	public boolean encodeInput(ShortBuffer pcm) {
		if(encoder == null || pcm == null || !isValidFrameSampleLength(pcm.remaining()))
			return false;
		
		final int sample = pcm.remaining() / getChannel();
		
		locker.lock();
		try {
			final int outputBufferSize = getBitRate() / (getSampleRate() / sample) / 8;
			final ByteBuffer output = ByteBuffer.allocate(outputBufferSize);
			
			int result =
					opus.opus_encode(encoder, pcm, sample, output, outputBufferSize);
			
			if(result > 0) {
				output.position(result);
				output.flip();
				
				while(encodedAudio.size() > 100) {
					encodedAudio.poll();
				}
				
				if(
						(encodeMinPacketSize == 0 && result > 0) ||
								result < encodeMinPacketSize
				) {
					encodeMinPacketSize = result;
				}
				if(result > encodeMaxPacketSize) {encodeMaxPacketSize = result;}
				encodeAveragePacketSize = (encodeAveragePacketSize + result) >> 1;
				
				return encodedAudio.add(output);
			}
			else {
				log.warn(logHeader + "Opus encode failed, error code = " + result + ".");
				
				return false;
			}
		}finally {locker.unlock();}
	}
	
	@Override
	public byte[] encodeOutput() {
		locker.lock();
		try {
			if(encodedAudio.isEmpty()) {return null;}
			
			final ByteBuffer audio = encodedAudio.poll();
			
			byte[] result = new byte[audio.remaining()];
			for(int i = 0; i <  result.length && audio.hasRemaining(); i++) {
				result[i] = audio.get();
			}
			
			return result;
		}finally {locker.unlock();}
	}
	
	@Override
	public boolean decodeInput(final byte[] audio, final boolean packetLoss) {
		if(
				decoder == null ||
				(!packetLoss && audio == null)
		) {return false;}
		
		decodeSrcAudio[decodeSrcSel] = audio;
		
		locker.lock();
		try {
			int outputSamples = 0;
			if(packetLoss)
				outputSamples = opus.opus_decoder_ctl(decoder, Opus.OPUS_GET_LAST_PACKET_DURATION_REQUEST);
			else {
				outputSamples = opus.opus_decoder_get_nb_samples(decoder, audio, audio.length);
				
				int calcOutputSamples = decodeBuffer.capacity() / getChannel();
				if(outputSamples > calcOutputSamples) {
					outputSamples = calcOutputSamples;
				}
			}
			
			decodeBuffer.clear();
			
			int decodeResult = 0;
			
			if(useFEC) {
				if(decodePacketLossPrev) {
					if(log.isDebugEnabled()){
						log.debug(logHeader + "Try FEC decode ( Samples = " + outputSamples + ")");
					}
					
					final byte[] srcAudio = decodeSrcAudio[decodeSrcSel];
					decodeResult =
							opus.opus_decode(
									decoder,
									packetLoss ? null : srcAudio,
									packetLoss ? 0 : srcAudio.length,
									decodeBuffer,
									outputSamples,
									1
							);
				}
				else {
					final byte[] srcAudio = decodeSrcAudio[1 - decodeSrcSel];
					decodeResult =
							opus.opus_decode(
									decoder,
									srcAudio,
									srcAudio != null ? srcAudio.length : 0,
									decodeBuffer,
									outputSamples,
									0
							);
				}
			}
			else {
				final byte[] srcAudio = decodeSrcAudio[decodeSrcSel];
				decodeResult =
						opus.opus_decode(
								decoder,
								packetLoss ? null : srcAudio,
								packetLoss ? 0 : srcAudio.length,
								decodeBuffer,
								outputSamples,
								0
						);
			}
			
			boolean success = false;
			
			if(decodeResult > 0) {
				decodeBuffer.position(decodeResult);
				decodeBuffer.flip();
				
				final ShortBuffer copy = ShortBuffer.allocate(decodeBuffer.remaining());
				copy.put(decodeBuffer);
				copy.flip();
				
				
				while(decodedAudio.size() > 100) {
					decodedAudio.poll();
				}
				
				success = decodedAudio.add(copy);
			}
			else {
				if(log.isDebugEnabled()){
					log.debug(
							logHeader + "Opus decode failed, error code = " + decodeResult + "."
					);
				}
				
				success = false;
			}
			
			decodePacketLossPrev = packetLoss;
			
			decodeSrcSel = 1 - decodeSrcSel;
			
			return success;
			
		}finally {locker.unlock();}
	}
	
	@Override
	public ShortBuffer decodeOutput() {
		locker.lock();
		try {
			if(decodedAudio.isEmpty()) {return null;}
			
			return decodedAudio.poll();
		}finally {locker.unlock();}
	}
	
	private boolean isValidFrameSampleLength(final int frameSampleLength) {
		locker.lock();
		try {
			final int sample = frameSampleLength / getChannel();
			
			for(final int length : frameSamples.values()) {
				if(length == sample) {return true;}
			}
			return false;
		}finally {locker.unlock();}
	}
	/*
		private boolean isValidBufferLength(final int bufferLength) {
			locker.lock();
			try {
				for(final int length : bufferSizes.values()) {
					if(length == bufferLength) {return true;}
				}
				return false;
			}finally {locker.unlock();}
		}
	*/
	private void disposeVocoder() {
		locker.lock();
		try {
			if(encoder != null) {
				opus.opus_encoder_destroy(encoder);
				encoder = null;
			}
			if(decoder != null) {
				opus.opus_decoder_destroy(decoder);
				decoder = null;
			}
		}finally {locker.unlock();}
	}
}
