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

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepairCacheTransmitterTest {


	private RepairCacheTransporter<TransmitterPacketImpl> instance;


	@Before
	public void setup() {
		LogbackUtil.initializeLogger(getClass().getClassLoader().getResourceAsStream("logback_stdconsole.xml"), true);

		instance = new RepairCacheTransporter<>(25, 10);
	}


	@Test
	public void testRepairCacheTransmitter() {
		final int frameID = DSTARUtils.generateFrameID();
		final UUID loopblockID = DSTARUtils.generateLoopBlockID();

		final PerformanceTimer timer = new PerformanceTimer();

		final Queue<TransmitterPacketImpl> data = new LinkedList<>();

		data.add(new TransmitterPacketImpl(PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.Start));

		final NewDataSegmentEncoder slowdataEncoder = new NewDataSegmentEncoder();
		int seq = 0;
		for(int c = 0; c < (0x15 * 10); c++) {
			if(seq != 0x7)
				data.add(new TransmitterPacketImpl(PacketType.Voice, createVoicePacket(loopblockID, frameID, seq, false, slowdataEncoder), FrameSequenceType.None));

			if(seq == 0x14)
				data.add(new TransmitterPacketImpl(PacketType.Header, createHeaderPacket(loopblockID, frameID), FrameSequenceType.None));

			seq = (seq + 1) % 0x15;
		}

//		data.add(new PacketObject(createVoicePacket(0x01, slowdataEncoder), FrameSequenceType.None));

		data.add(new TransmitterPacketImpl(
			PacketType.Voice,
			createVoicePacket(loopblockID, frameID, seq, true, slowdataEncoder), FrameSequenceType.End
		));

		for(int c = 0; c < 1; c++) {
			timer.start();

			long outputTimeNanos = 0;

			final RateAdjuster<TransmitterPacketImpl> inputAdjuster = new RateAdjuster<>();
			for(final TransmitterPacketImpl packet : data)
				inputAdjuster.writePacket(packet);

			while(
				!timer.isTimeout(10, TimeUnit.SECONDS)
			) {

				inputAdjuster.readDvPacket().ifPresent(new Consumer<TransmitterPacketImpl>() {
					@Override
					public void accept(TransmitterPacketImpl t) {
						instance.writePacket(t);
					}
				});


				if(instance.hasReadablePacket()) {
					final Optional<TransmitterPacketImpl> opPacket = instance.readPacket();
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
