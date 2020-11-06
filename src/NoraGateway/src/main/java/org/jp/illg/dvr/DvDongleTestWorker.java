/**
 *
 */
package org.jp.illg.dvr;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jp.illg.dstar.dvdongle.DvDongleInterface;
import org.jp.illg.dstar.dvdongle.DvDongleInterface.DvDongleInterfaceReceiveEventListener;
import org.jp.illg.dstar.dvdongle.commands.CompressedAudioDataFromDongle;
import org.jp.illg.dstar.dvdongle.commands.DvDongleCommand;
import org.jp.illg.dstar.dvdongle.commands.UnCompressedAudioDataToDongle;
import org.jp.illg.dstar.g2route.command.VoiceDataToInet;
import org.jp.illg.dstar.g2route.command.VoiceHeaderToInet;
import org.jp.illg.dstar.jptrust.DstarNetworkInterface;
import org.jp.illg.dstar.jptrust.DstarNetworkInterfaceProcess.DstarNetworkInterfaceReceiveEventHandler;
import org.jp.illg.dstar.jptrust.command.DstarNetworkCommand;
import org.jp.illg.dstar.util.DstarUtil;
import org.jp.illg.util.ArrayUtil;
import org.jp.illg.util.AudioInputWorker;
import org.jp.illg.util.AudioInputWorker.AudioInputEventLintener;

/**
 * @author AHND
 *
 */
public class DvDongleTestWorker
	implements
		Runnable,
		DvDongleInterfaceReceiveEventListener,
		DstarNetworkInterfaceReceiveEventHandler,
		AudioInputEventLintener
{

	private static final String destinationAddress = "localhost";
	private static final int destinationPort = 20010;


	private static final Log log = LogFactory.getLog(DvDongleTestWorker.class);


	private DstarNetworkInterface dni;
	private DvDongleInterface ddi;
	private AudioInputWorker aiw;


	private Queue<byte[]> aiwReceiveQueue;
	private List<byte[]> aiwReceiveBuffers;

	private Queue<DvDongleCommand> ddiReceiveQueue;
	private List<DvDongleCommand> ddiReceiveCommands;


	private boolean headerSendFlag;

	private VoiceDataToInet voicePacket;


	/**
	 * スレッド
	 */
	private Thread workerThread;

	boolean workerThreadAvailable;



	private DvDongleTestWorker() {
		super();
	}

	public DvDongleTestWorker(
			DstarNetworkInterface dni,
			DvDongleInterface ddi,
			AudioInputWorker aiw
	) {
		this();

		this.dni = dni;
		this.ddi = ddi;
		this.aiw = aiw;

		this.aiwReceiveQueue = new ConcurrentLinkedQueue<byte[]>();
		this.aiwReceiveQueue.clear();
		this.aiwReceiveBuffers = new LinkedList<byte[]>();
		this.aiwReceiveBuffers.clear();

		this.ddiReceiveQueue = new ConcurrentLinkedQueue<DvDongleCommand>();
		this.ddiReceiveQueue.clear();
		this.ddiReceiveCommands = new LinkedList<DvDongleCommand>();
		this.ddiReceiveCommands.clear();

		this.headerSendFlag = false;
	}

	/**
	 *
	 * @return
	 */
	public boolean start() {
		if(
			this.workerThreadAvailable &&
			this.workerThread != null && this.workerThread.isAlive()
		) {this.stop();}

		this.workerThread = new Thread(this);
		this.workerThread.setName(this.getClass().getSimpleName() + "Worker");
		this.workerThreadAvailable = true;
		this.workerThread.start();

		this.aiwReceiveQueue.clear();
		this.aiwReceiveBuffers.clear();
		this.ddiReceiveQueue.clear();
		this.ddiReceiveCommands.clear();

		this.headerSendFlag = false;

		this.aiw.setInputEventListener(this);
		this.dni.setControlChannelReceiveEventHandler(this);
		this.dni.setDataRxChannelReceiveEventHandler(this);
		this.dni.setDataTxChannelReceiveEventHandler(this);
		this.ddi.setReceiveEventListener(this);

		this.dni.start();
		this.ddi.start();
		this.aiw.start();

		return true;
	}

	/**
	 *
	 */
	public void stop() {
		this.workerThreadAvailable = false;
		if(this.workerThread != null && this.workerThread.isAlive()) {
			this.workerThread.interrupt();
			try {
				this.workerThread.join();
			} catch (InterruptedException ex) {
				if(log.isDebugEnabled()) {log.debug("function stop() received interrupt.",ex);}
			}
		}
	}

	@Override
	public void run() {
		try {
			do {
				synchronized(this.workerThread) {
					this.workerThread.wait();
				}

				this.process();

			}while(this.workerThreadAvailable);
		}catch(InterruptedException ex){

		}catch(Exception ex) {
			if(log.isFatalEnabled())
				log.fatal("function run() error occurred.",ex);
		}finally {

		}
	}


	private void process() {

		//
		synchronized(this.aiwReceiveQueue) {
			for(Iterator<byte[]> it = this.aiwReceiveQueue.iterator();it.hasNext();) {
				this.aiwReceiveBuffers.add(it.next());
				it.remove();
			}
		}

		//
		synchronized(this.ddiReceiveQueue) {
			for(Iterator<DvDongleCommand> it = this.ddiReceiveQueue.iterator();it.hasNext();) {
				this.ddiReceiveCommands.add(it.next());
				it.remove();
			}
		}



		//オーディオ入力があればドングルへ送信
		for(Iterator<byte[]> it = this.aiwReceiveBuffers.iterator();it.hasNext();) {
			byte[] audioInput = it.next();
			it.remove();

			UnCompressedAudioDataToDongle uad = new UnCompressedAudioDataToDongle();

			short[] uncompAudio16 = uad.getAudioData();
			for(int index = 0,bi = 0;index < uncompAudio16.length && bi < audioInput.length;index++) {
				byte msb = audioInput[bi++];
				byte lsb = audioInput[bi++];

				uncompAudio16[index] =
						(short)((((msb << 8 & 0xFF00)) | (lsb & 0x00FF)) & 0xFFFF);
			}
			// DV Dongleへ送信
			this.ddi.addSendCommand(uad);
		}


		//ドングルから圧縮済データがあればネットワークへ送信
		for(Iterator<DvDongleCommand> it = this.ddiReceiveCommands.iterator();it.hasNext();) {
			DvDongleCommand ddc = it.next();
			it.remove();

			if(ddc == null || !(ddc instanceof CompressedAudioDataFromDongle)){continue;}
			CompressedAudioDataFromDongle cad = (CompressedAudioDataFromDongle)ddc;

			//ヘッダが送信されていなければ送信
			if(!this.headerSendFlag) {
				VoiceHeaderToInet header = new VoiceHeaderToInet();
				try {
					header.setRemoteAddress(InetAddress.getByName(destinationAddress));
				}catch(UnknownHostException ex) {
					continue;
				}
				header.setRemotePort(destinationPort);
				ArrayUtil.copyOf(header.getRepeater2Callsign(), "JJ0TPX A".toCharArray());
				ArrayUtil.copyOf(header.getRepeater1Callsign(), "JJ0TPX B".toCharArray());
				ArrayUtil.copyOf(header.getYourCallsign(), "CQCQCQ  ".toCharArray());
				ArrayUtil.copyOf(header.getMyCallsign(), "JI1ROJ A".toCharArray());

				for(int index = 0;index < 5;index++)
					this.dni.addDataTxChannelSendCommand(header);

				this.headerSendFlag = true;
			}

			if(this.voicePacket == null) {this.voicePacket = new VoiceDataToInet();}

			for(
					int index = 0,bi = 0;
					index < cad.getChannelData().length;
					index++
			) {
				byte lsb = (byte)(cad.getChannelData()[index] & 0xFF);
				byte msb = (byte)((cad.getChannelData()[index] >>> 8) & 0xFF);

				if(this.voicePacket.getVoiceData().getVoiceSegment().length > bi)
					this.voicePacket.getVoiceData().getVoiceSegment()[bi++] = lsb;
				else
					break;

				if(this.voicePacket.getVoiceData().getVoiceSegment().length > bi)
					this.voicePacket.getVoiceData().getVoiceSegment()[bi++] = msb;
				else
					break;
			}

			if(this.backboneSequence == 0) {
				this.voicePacket.getVoiceData().getDataSegment()[0] = 0x55;
				this.voicePacket.getVoiceData().getDataSegment()[1] = 0x2d;
				this.voicePacket.getVoiceData().getDataSegment()[2] = 0x16;
			}else {
				this.voicePacket.getVoiceData().getDataSegment()[0] = 0x66;
				this.voicePacket.getVoiceData().getDataSegment()[1] = 0x66;
				this.voicePacket.getVoiceData().getDataSegment()[2] = 0x66;
			}

			this.voicePacket.getBackBone().setSequence((byte)this.backboneSequence);

			if(this.backboneSequence >= 0x14)
				this.backboneSequence = 0x0;
			else
				this.backboneSequence++;

			try {
				this.voicePacket.setRemoteAddress(InetAddress.getByName(destinationAddress));
			}catch(UnknownHostException ex) {
				continue;
			}
			this.voicePacket.setRemotePort(destinationPort);

			//終了の際にはラストパケットを送る
			if(this.workerThread.isInterrupted() || !this.workerThreadAvailable) {
				this.voicePacket.getDvPacket().getVoiceData().setVoiceSegment(
						DstarUtil.getBusyVoice()[DstarUtil.getBusyVoice().length - 1]
				);
				this.voicePacket.getBackBone().setSequence(
						(byte)(this.voicePacket.getBackBone().getSequence() | 0x40)
				);
			}

			//ボイスデータをネットワークに送信
			this.dni.addDataTxChannelSendCommand(this.voicePacket.clone());


			if(this.commandSendTimestamp + 10000 < System.currentTimeMillis()) {
				int commandPerSec = this.commandSendCount / 10;

				if(log.isDebugEnabled())
					log.debug("to Inet Command send rate..." + commandPerSec + "packets/sec");

				this.commandSendCount = 0;
				this.commandSendTimestamp = System.currentTimeMillis();
			}else {
				this.commandSendCount++;
			}
		}
	}

	private int backboneSequence = 0;
	private int commandSendCount = 0;
	private long commandSendTimestamp = System.currentTimeMillis();

	@Override
	public void handleDvDongleInterfaceReceiveEvent(List<DvDongleCommand> commands) {

		String commandsStr = "";

		synchronized(this.ddiReceiveQueue) {
			for(Iterator<DvDongleCommand> it = commands.iterator();it.hasNext();) {
				DvDongleCommand command = it.next();
				this.ddiReceiveQueue.add(command);
				it.remove();

				commandsStr += command.getClass().getSimpleName() + "/";
			}
		}

		if(log.isTraceEnabled())
			log.trace("function handleDvDongleInterfaceReceiveEvent() " + commandsStr);

		synchronized(this.workerThread) {
			this.workerThread.notifyAll();
		}
	}

	@Override
	public void handleDstarNetworkInterfaceReceiveEvent(List<DstarNetworkCommand> receiveCommands) {
		for(Iterator<DstarNetworkCommand> it = receiveCommands.iterator();it.hasNext();) {
			it.next();
			it.remove();	//読み捨て
		}

		synchronized(this.workerThread) {
			this.workerThread.notifyAll();
		}
	}

	@Override
	public void handleAudioInputEvent(byte[] audioData) {

		synchronized(this.aiwReceiveQueue) {
//			if(this.aiwReceiveQueue.size() >= 100)
//				this.aiwReceiveQueue.poll();

			this.aiwReceiveQueue.add(audioData);
		}

		synchronized(this.workerThread) {
			this.workerThread.notifyAll();
		}
	}


}
