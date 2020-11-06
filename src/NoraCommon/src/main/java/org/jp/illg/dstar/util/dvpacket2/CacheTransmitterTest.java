package org.jp.illg.dstar.util.dvpacket2;

import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.BackBoneHeader;
import org.jp.illg.dstar.model.BackBoneHeaderFrameType;
import org.jp.illg.dstar.model.BackBoneHeaderType;
import org.jp.illg.dstar.model.DSTARPacket;
import org.jp.illg.dstar.model.DVPacket;
import org.jp.illg.dstar.model.Header;
import org.jp.illg.dstar.model.InternalPacket;
import org.jp.illg.dstar.model.VoiceAMBE;
import org.jp.illg.dstar.model.VoiceData;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.model.defines.PacketType;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.dstar.util.NewDataSegmentEncoder;
import org.jp.illg.util.PerformanceTimer;
import org.jp.illg.util.logback.LogbackUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(JUnit4.class)
public class CacheTransmitterTest {

	private class CacheTransmitterPacket implements TransmitterPacket, Cloneable {

		@Getter
		private DSTARPacket packet;

		@Getter
		private PacketType packetType;

		@Getter
		private FrameSequenceType frameSequenceType;

		private CacheTransmitterPacket(
			@NonNull final PacketType packetType,
			@NonNull final DSTARPacket packet,
			@NonNull final FrameSequenceType frameSequenceType
		) {
			this.packetType = packetType;
			this.packet = packet;
			this.frameSequenceType = frameSequenceType;
		}

		@Override
		public TransmitterPacket clone() {
			CacheTransmitterPacket copy = null;

			try {
				copy = (CacheTransmitterPacket)super.clone();

			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}

			return copy;
		}
	}

	private CacheTransmitter<CacheTransmitterPacket> instance;

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);

		instance = new CacheTransmitter<CacheTransmitterPacket>(50, 10);
	}

	@Test
	public void testCacheTransmitter() {

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final PerformanceTimer timer = new PerformanceTimer();

		final Queue<CacheTransmitterPacket> data = new LinkedList<>();

		data.add(new CacheTransmitterPacket(PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.Start));

		final NewDataSegmentEncoder slowdataEncoder = new NewDataSegmentEncoder();
		int seq = 0;
		for(int c = 0; c < (0x15 * 10); c++) {
			data.add(new CacheTransmitterPacket(
				PacketType.Voice, createVoicePacket(loopblockID, frameID, seq, false, slowdataEncoder), FrameSequenceType.None
			));

			if(seq == 0x14) {
				data.add(new CacheTransmitterPacket(
					PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.None
				));
			}

			seq = (seq + 1) % 0x15;
		}

		data.add(new CacheTransmitterPacket(
			PacketType.Voice, createVoicePacket(loopblockID, frameID, 0x0, true, slowdataEncoder), FrameSequenceType.End
		));

		for(int c = 0; c < 1; c++) {
			timer.start();

			long outputTimeNanos = 0;

			final RateAdjuster<CacheTransmitterPacket> inputAdjuster = new RateAdjuster<>();
			for(final CacheTransmitterPacket packet : data)
				inputAdjuster.writePacket(packet);

			while(
				!timer.isTimeout(10, TimeUnit.SECONDS)
			) {

				inputAdjuster.readDvPacket().ifPresent(new Consumer<CacheTransmitterPacket>() {
					@Override
					public void accept(CacheTransmitterPacket t) {
						instance.inputWrite(t);
					}
				});


				if(instance.hasOutputRead()) {
					final Optional<CacheTransmitterPacket> opPacket = instance.outputRead();
					if(opPacket.isPresent()) {
						final DSTARPacket packet = opPacket.get().getPacket();

						final long diffTime = outputTimeNanos != 0 ? System.nanoTime() - outputTimeNanos:0;

						outputTimeNanos = System.nanoTime();

						log.info(
							"Remain:" + instance.getCachePacketSize() + "/DiffTime:" + TimeUnit.MILLISECONDS.convert(diffTime, TimeUnit.NANOSECONDS) + "ms\n" +
							packet.toString(4)
						);
					}
				}
			}

			log.info("\n----------------------------------------\n");
		}



	}


	private DSTARPacket createHeaderPacket(final UUID loopBlockID, final int frameID) {
		 final DVPacket packet = new DVPacket(
			 new BackBoneHeader(BackBoneHeaderType.DV, BackBoneHeaderFrameType.VoiceDataHeader, frameID),
			 new Header(
				DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.EmptyLongCallsign.toCharArray(),
				DSTARDefines.EmptyLongCallsign.toCharArray(),
				DSTARDefines.EmptyLongCallsign.toCharArray(), DSTARDefines.EmptyShortCallsign.toCharArray()
			)
		);

		return new InternalPacket(loopBlockID, ConnectionDirectionType.Unknown, packet);
	}

	private DSTARPacket createVoicePacket(
		final UUID loopBlockID,
		final int frameID, final int seq, final boolean isLastPacket,
		final NewDataSegmentEncoder slowdataEncoder
	) {
		final VoiceData voiceData = new VoiceAMBE();
		voiceData.setVoiceSegment(DSTARDefines.VoiceSegmentLastBytes);
		slowdataEncoder.encode(voiceData.getDataSegment());

		final BackBoneHeader backbone =
			new BackBoneHeader(
				BackBoneHeaderType.DV,
				isLastPacket ? BackBoneHeaderFrameType.VoiceDataLastFrame : BackBoneHeaderFrameType.VoiceData,
				frameID
			);

		final DVPacket packet = new DVPacket(backbone, voiceData);
		packet.getBackBone().setSequenceNumber((byte)seq);

		return new InternalPacket(loopBlockID, ConnectionDirectionType.Unknown, packet);
	}

}
