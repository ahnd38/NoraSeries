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

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RunWith(JUnit4.class)
public class RateAdjusterTest {

	private class RateAdjusterData implements TransmitterPacket, Cloneable {

		@Getter
		private DSTARPacket packet;

		@Getter
		private PacketType packetType;

		@Getter
		private FrameSequenceType frameSequenceType;

		private RateAdjusterData(
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
			RateAdjusterData copy = null;

			try {
				copy = (RateAdjusterData)super.clone();

			}catch(CloneNotSupportedException ex) {
				throw new RuntimeException(ex);
			}

			return copy;
		}
	}

	private RateAdjuster<RateAdjusterData> instance;

	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);

		instance = new RateAdjuster<RateAdjusterTest.RateAdjusterData>();
	}

	@Test
	public void testRateAdjuster() {

		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final PerformanceTimer timer = new PerformanceTimer();

		final Queue<RateAdjusterData> data = new LinkedList<>();

		data.add(new RateAdjusterData(PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.Start));

		final NewDataSegmentEncoder slowdataEncoder = new NewDataSegmentEncoder();
		int seq = 0;
		for(int c = 0; c < (0x15 * 10); c++) {
			data.add(new RateAdjusterData(
				PacketType.Voice, createVoicePacket(loopblockID, frameID, seq, false, slowdataEncoder), FrameSequenceType.None
			));

			if(seq == 0x14) {
				data.add(new RateAdjusterData(
					PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.None
				));
			}

			seq = (seq + 1) % 0x15;
		}

		data.add(new RateAdjusterData(
			PacketType.Voice, createVoicePacket(loopblockID, frameID, 0x0, true, slowdataEncoder), FrameSequenceType.End
		));

		for(int c = 0; c < 5; c++) {
			instance.writePacket(data);

			timer.start();

			long outputTimeNanos = 0;


			boolean started = false;
			while(
				!timer.isTimeout(10, TimeUnit.SECONDS) &&
				instance.getCachePacketSize() >= 1
			) {
				if(!started && timer.isTimeout(4, TimeUnit.SECONDS)) {
					started = true;
					instance.start();

					if(log.isInfoEnabled())
						log.info("START");
				}

				if(instance.hasReadableDvPacket()) {
					final Optional<RateAdjusterData> opPacket = instance.readDvPacket();
					if(opPacket.isPresent()) {
						final DSTARPacket packet = opPacket.get().getPacket();

						final long diffTime = outputTimeNanos != 0 ? System.nanoTime() - outputTimeNanos:0;

						outputTimeNanos = System.nanoTime();

						log.info(
							"DiffTime:" + TimeUnit.MILLISECONDS.convert(diffTime, TimeUnit.NANOSECONDS) + "ms\n" +
							packet.toString(4)
						);
					}
				}
			}

			if(log.isInfoEnabled())
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
