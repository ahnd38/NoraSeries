/**
 *
 */
package org.jp.illg.dstar.dvdongle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.TooManyListenersException;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jp.illg.dstar.dvdongle.commands.CompressedAudioDataFromDongle;
import org.jp.illg.dstar.dvdongle.commands.DvDongleCommand;
import org.jp.illg.dstar.dvdongle.commands.RunStateRequest;
import org.jp.illg.dstar.dvdongle.commands.RunStateResponse;
import org.jp.illg.dstar.dvdongle.commands.UncompressedAudioDataFromDongle;
import org.jp.illg.dstar.dvdongle.commands.UnknownMessage;
import org.jp.illg.dstar.dvdongle.model.DvDongleRunStates;
import org.jp.illg.util.BufferState;
import org.jp.illg.util.FormatUtil;

import jd2xx.JD2XX;
import jd2xx.JD2XXEvent;
import jd2xx.JD2XXEventListener;
import lombok.extern.slf4j.Slf4j;

/**
 * @author AHND
 *
 */
@Slf4j
public class DvDongleInterface
	implements Runnable,JD2XXEventListener{


	public interface DvDongleInterfaceReceiveEventListener{
		public void handleDvDongleInterfaceReceiveEvent(List<DvDongleCommand> commands);
	}

	private DvDongleInterfaceReceiveEventListener receiveEventListener;

	/**
	 * @return receiveEventListener
	 */
	public DvDongleInterfaceReceiveEventListener getReceiveEventListener() {
		return receiveEventListener;
	}

	/**
	 * @param receiveEventListener セットする receiveEventListener
	 */
	public void setReceiveEventListener(DvDongleInterfaceReceiveEventListener receiveEventListener) {
		this.receiveEventListener = receiveEventListener;
	}

	/**
	 * DVドングルが接続されているポート名
	 */
	private String donglePortName;


	/**
	 * @return donglePortName
	 */
	public String getDonglePortName() {
		return donglePortName;
	}

	/**
	 * @param donglePortName セットする donglePortName
	 */
	public void setDonglePortName(String donglePortName) {
		this.donglePortName = donglePortName;
	}

	/**
	 * ドングルポートIF
	 */
//	private SerialPortInterface donglePortInterface;
	private JD2XX donglePortInterface;

	/**
	 * 通信スレッド
	 */
	private Thread workerThread;

	boolean workerThreadAvailable;

	/**
	 * 通信状態列挙体
	 * @author AHND
	 *
	 */
	private static enum CommunicationState{
		INITIALIZE,
		PORT_OPEN,
		SEND_RUN_CMD,
		WAIT_RUN_CMD,
		WAIT_MAIN,
	}
	private CommunicationState communicationState;

	private long communicationStateTimestamp;

	/**
	 *
	 */
	private ByteBuffer recvBuffer;
	private BufferState recvBufferState;
	private boolean recvBufferUpdate;
	private long recvTimestamp;

	private List<DvDongleCommand> sendCommands;
	private Queue<DvDongleCommand> sendRequestQueue;
	private List<DvDongleCommand> recvCommands;


	public DvDongleInterface() {
		super();

		this.workerThread = new Thread(this);
		this.communicationState = CommunicationState.INITIALIZE;
		this.clearCommunicationTimestamp();

		this.donglePortInterface = new JD2XX();
		try {
			this.donglePortInterface.addEventListener(this);
		}catch(TooManyListenersException ex) {}

		this.recvCommands = new LinkedList<>();
		this.recvCommands.clear();
		this.sendRequestQueue = new ConcurrentLinkedQueue<DvDongleCommand>();
		this.sendRequestQueue.clear();
		this.sendCommands = new LinkedList<>();
		this.sendCommands.clear();

		this.recvBuffer = ByteBuffer.allocate(102400);
		this.recvBuffer.clear();
		this.recvBufferState = BufferState.INITIALIZE;

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
					this.workerThread.wait(10);
				}
				this.process();
			}while(this.workerThreadAvailable && !this.workerThread.isInterrupted());
		}catch(InterruptedException ex) {

		}catch(Exception ex) {
			log.error("function run() error occurred.",ex);
		}finally {
			try {
				if(this.donglePortInterface != null) {this.donglePortInterface.close();}
			}catch(Exception ex){
				log.warn("",ex);
			}
		}
	}


	private void process() {

		//受信データがあれば解析する
		this.analyzeReceiveBuffer();

		synchronized(this.sendRequestQueue) {
			for(Iterator<DvDongleCommand> it = this.sendRequestQueue.iterator();it.hasNext();) {
				this.sendCommands.add(it.next());
				it.remove();
			}
		}

		switch(this.communicationState) {
		case INITIALIZE:

/*
			this.donglePortInterface.setDataBits(8);
			this.donglePortInterface.setStopBits(SerialPortInterface.STOPBITS_ONE);
			this.donglePortInterface.setParity(SerialPortInterface.PARITY_NONE);
			this.donglePortInterface.setFlowControl(SerialPortInterface.FLOWCONTROL_DISABLE);
*/
			this.communicationState = CommunicationState.PORT_OPEN;
			break;

		case PORT_OPEN:
			synchronized(this.recvBuffer) {
				this.recvTimestamp = System.currentTimeMillis();
			}

			boolean openSuccess = false;
			try {
				this.donglePortInterface.openBySerialNumber(this.donglePortName);

				this.donglePortInterface.setBaudRate(230400);
				this.donglePortInterface.setDataCharacteristics(
						  8, JD2XX.STOP_BITS_1, JD2XX.PARITY_NONE
				);
				this.donglePortInterface.setFlowControl(
						  JD2XX.FLOW_NONE, 0, 0
				);
				this.donglePortInterface.notifyOnRxchar(true);

				openSuccess = true;
			} catch (IOException ex) {
				openSuccess = false;
			}

			if(!openSuccess) {
				log.error("function run() donglePortInterface Open failed,communication thread exit.");
				this.workerThreadAvailable = false;
			}else {
				this.communicationState = CommunicationState.SEND_RUN_CMD;
			}
			break;

		case SEND_RUN_CMD:
			this.sendCommand(new RunStateRequest(DvDongleRunStates.Run));
			this.updateCommunicationTimestamp();
			this.communicationState = CommunicationState.WAIT_RUN_CMD;
			break;

		case WAIT_RUN_CMD:
			if(this.isCommunicationTimeout(5000)) {
				if(log.isWarnEnabled())
					log.warn("function process() communication timeout occured...'WAIT_RUN_CMD'.");

				this.communicationState = CommunicationState.SEND_RUN_CMD;
			}else {
				for(Iterator<DvDongleCommand> it = this.recvCommands.iterator();it.hasNext();) {
					DvDongleCommand command = it.next();
					it.remove();

					if(command == null || !(command instanceof RunStateResponse)) {continue;}

					this.updateCommunicationTimestamp();
					this.communicationState = CommunicationState.WAIT_MAIN;
				}
			}
			break;

		case WAIT_MAIN:
			//送信するコマンドがあるか？
			if(this.sendCommands.size() > 0) {
				for(Iterator<DvDongleCommand> it = this.sendCommands.iterator();it.hasNext();) {
					this.sendCommand(it.next());
					it.remove();

					if(this.workerThread.isInterrupted()) {return;}
				}
			//受信したコマンドがあるか？
			}else if(this.recvCommands.size() > 0) {
				List<DvDongleCommand> notifyCommands = new LinkedList<>(this.recvCommands);
				this.notifyReceiveEvent(notifyCommands);

				this.recvCommands.clear();

				if(this.workerThread.isInterrupted()) {return;}
			}
			break;


		default:
			break;
		}

	}

	private void clearCommunicationTimestamp() {
		this.communicationStateTimestamp = 0;
	}

	private void updateCommunicationTimestamp() {
		this.communicationStateTimestamp = System.currentTimeMillis();
	}

	private boolean isCommunicationTimeout(long timeoutMillis) {
		return (this.communicationStateTimestamp + timeoutMillis) < System.currentTimeMillis() ? true:false;
	}


	private void analyzeReceiveBuffer() {
		DvDongleCommand command = null;
		boolean match = false;

		synchronized(this.recvBuffer) {

			if(!this.recvBufferUpdate) {return;}

			do {
				if(
						//Run State Response?
						(command = new RunStateResponse().analyzeCommandData(this.recvBuffer)) != null ||
						//Uncompressed Data?
						(command = new UncompressedAudioDataFromDongle().analyzeCommandData(this.recvBuffer)) != null ||
						//Compressed Data?
						(command = new CompressedAudioDataFromDongle().analyzeCommandData(this.recvBuffer)) != null ||
						//Unknown message?
						(command = new UnknownMessage().analyzeCommandData(this.recvBuffer)) != null
				) {
					//受信コマンドキューへ追加
					this.recvCommands.add(command);

					if(command instanceof UnknownMessage && log.isDebugEnabled()) {
						log.debug(
								"function analyzeReceiveBuffer() received unknown message from dongle..." +
								FormatUtil.bytesToHex(
										((UnknownMessage)command).getUnknownMessage()
								)
						);
					}

					this.recvTimestamp = System.currentTimeMillis();

					match = true;
				}else {
					match = false;
				}
			}while(match && this.recvBuffer.limit() > 0);

			this.recvBufferUpdate = false;
		}

		return;
	}

/*
	@Override
	public SerialPortInterfaceEventType getEventType() {
		return SerialPortInterfaceEventType.DATA_AVAILABLE;
	}
*/

	@Override
	public void jd2xxEvent(JD2XXEvent event) {

		byte[] receiveData = null;
		int readBytes = 0;

		try {
			synchronized(this.donglePortInterface) {
				int queuebytes = this.donglePortInterface.getQueueStatus();
				if(queuebytes <= 0) {return;}

				receiveData = new byte[queuebytes];

				readBytes =
						this.donglePortInterface.read(receiveData);
			}
		}catch(IOException ex) {
			return;
		}

		synchronized(this.recvBuffer) {
			//受信バッファが古ければ捨てる
			if(this.recvTimestamp + 5000 < System.currentTimeMillis()) {
				if(this.recvBufferState == BufferState.WRITE) {
					this.recvBuffer.flip();
					this.recvBufferState = BufferState.READ;
				}
				if(log.isWarnEnabled()) {
					this.recvBuffer.rewind();
					log.warn(
							"function serialEvent() purged receive cache data..." +
							"[" + this.recvBuffer.limit() + "bytes]" +
							FormatUtil.byteBufferToHex(this.recvBuffer, 10) + "..."
					);
				}
				this.recvBuffer.clear();
				this.recvBufferState = BufferState.INITIALIZE;
				this.recvTimestamp = System.currentTimeMillis();
			}

			if(this.recvBufferState == BufferState.READ) {
				this.recvBuffer.compact();
				this.recvBufferState = BufferState.WRITE;
			}

//			if(event.getReceiveData() != null && event.getReceiveData().length > 0) {
//				this.recvBuffer.put(event.getReceiveData());	//受信バッファにコピー
			if(receiveData != null && receiveData.length >= 1) {
				this.recvBuffer.put(receiveData,0,readBytes);

				this.recvBufferState = BufferState.WRITE;

				if(log.isTraceEnabled()) {
	//				log.trace("function serialEvent() received data..." + FormatUtil.bytesToHex(event.getReceiveData()));
					this.recvBuffer.flip();
					this.recvBufferState = BufferState.READ;
					log.trace("buffer data updated..." + FormatUtil.byteBufferToHex(recvBuffer));
				}
			}

			if(this.recvBufferState == BufferState.WRITE) {
				this.recvBuffer.flip();
				this.recvBufferState = BufferState.READ;
			}

			this.recvBufferUpdate = true;
		}
	}

	/**
	 * 送信コマンドをキューに追加する
	 * @param command 送信コマンド
	 * @return キューへ正常に追加出来ればtrue
	 */
	public boolean addSendCommand(DvDongleCommand command) {
		if(command == null || !(command instanceof DvDongleCommand))
			throw new IllegalArgumentException();

		if(!this.workerThreadAvailable) {return false;}

		synchronized(this.sendRequestQueue) {
			this.sendRequestQueue.add(command);
		}

		synchronized(this.workerThread) {
			this.workerThread.notifyAll();
		}

		return true;
	}

	/**
	 * 受信イベントを通知する
	 * @param commands
	 */
	private void notifyReceiveEvent(List<DvDongleCommand> commands) {

		//受信イベントハンドラが登録されていれば呼ぶ
		if(
				this.getReceiveEventListener() != null &&
				this.getReceiveEventListener() instanceof DvDongleInterfaceReceiveEventListener
		) {
			this.getReceiveEventListener().handleDvDongleInterfaceReceiveEvent(commands);
		}
	}

	/**
	 * DVドングルへ送信する
	 * @param command
	 * @return
	 */
	private boolean sendCommand(DvDongleCommand command) {
		byte[] sendBytes = command.assembleCommandData();

//		boolean result = this.donglePortInterface.writeBytes(sendBytes, sendBytes.length);
//		boolean result = this.donglePortInterface.writeBytesWithStream(sendBytes);

		boolean result = false;
		try {
			result =
					this.donglePortInterface.write(sendBytes) == JD2XX.OK ? true:false;
		}catch(IOException ex) {}

		if(this.bytesToWriteDvDonglePC + 30000 < System.currentTimeMillis()) {
			long bytessec = this.bytesToWriteDvDongle / 30;

			if(log.isDebugEnabled())
				log.debug("function sendCommand() " + (bytessec * 8 / 1000) + "kbits/sec...");

			this.bytesToWriteDvDongle = 0;
			this.bytesToWriteDvDonglePC = System.currentTimeMillis();
		}else {
			this.bytesToWriteDvDongle += sendBytes.length;
		}

		return result;
	}

	private long bytesToWriteDvDongle = 0;
	private long bytesToWriteDvDonglePC = System.currentTimeMillis();

}
