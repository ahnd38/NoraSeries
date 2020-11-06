package org.jp.illg.util.uart;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import lombok.extern.slf4j.Slf4j;

@Deprecated
@Slf4j
public class SerialPortInterface {

	/**
	 * ビットレート
	 */
	private int bitRate = 9600;
	/**
	 * データビット数
	 */
	private int dataBits = 8;
	/**
	 * ストップビット数
	 */
	private int stopBits = 1;
	/**
	 * パリティ
	 */
	private int parity = PARITY_NONE;
	/**
	 * フローコントロール
	 */
	private int flowControl = FLOWCONTROL_DISABLE;
	/**
	 * 接続状態
	 */
	private SerialPortInterfaceState connectState = SerialPortInterfaceState.Disconnected;
	/**
	 * 受信イベントリスナ
	 */
	private List<SerialPortInterfaceEventListener> eventListener;

	/**
	 * シリアルポートインスタンス
	 */
	private SerialPort serialPort = null;


	public static final int PARITY_NONE = SerialPort.NO_PARITY;
	public static final int PARITY_ODD = SerialPort.ODD_PARITY;
	public static final int PARITY_EVEN = SerialPort.EVEN_PARITY;
	public static final int PARITY_MARK = SerialPort.MARK_PARITY;
	public static final int PARITY_SPACE = SerialPort.SPACE_PARITY;
	public static final int FLOWCONTROL_DISABLE = SerialPort.FLOW_CONTROL_DISABLED;
	public static final int FLOWCONTROL_CTS = SerialPort.FLOW_CONTROL_CTS_ENABLED;
	public static final int FLOWCONTROL_RTS = SerialPort.FLOW_CONTROL_RTS_ENABLED;
	public static final int FLOWCONTROL_DSR = SerialPort.FLOW_CONTROL_DSR_ENABLED;
	public static final int FLOWCONTROL_DTR = SerialPort.FLOW_CONTROL_DTR_ENABLED;
	public static final int STOPBITS_ONE = SerialPort.ONE_STOP_BIT;
	public static final int STOPBITS_ONE_POINT_FIVE = SerialPort.ONE_POINT_FIVE_STOP_BITS;
	public static final int STOPBITS_TWO = SerialPort.TWO_STOP_BITS;


	public static enum SerialPortInterfaceState {
		Disconnected,
		Connected,
		OpenFailedNoSuchPort,
		OpenFailedCurrentlyInUse,
		OpenFailed
	}

	public enum SerialPortInterfaceEventType
	{
		UNKNOWN,
		DATA_AVAILABLE,
		DATA_WRITTEN
	}

	public class SerialPortInterfaceEvent{
		/**
		 * イベントタイプ
		 */
		private SerialPortInterfaceEventType eventType = SerialPortInterfaceEventType.UNKNOWN;
		/**
		 * シリアルポートインスタンス
		 */
		private SerialPortInterface serialPort;
		/**
		 * 受信データ
		 */
		private byte[] receiveData;

		/**
		 *
		 * @param eventType
		 */
		public SerialPortInterfaceEvent(SerialPortInterfaceEventType eventType,SerialPortInterface serialPort) {
			this(eventType,serialPort,null);
		}

		/**
		 *
		 * @param eventType
		 * @param serialPort
		 * @param receiveData
		 */
		public SerialPortInterfaceEvent(
			SerialPortInterfaceEventType eventType,
			SerialPortInterface serialPort,
			byte[] receiveData
		) {
			super();
			this.eventType = eventType;
			this.serialPort = serialPort;
			this.receiveData = receiveData;
		}

		/**
		 * @return eventType
		 */
		public SerialPortInterfaceEventType getEventType() {
			return eventType;
		}
		/**
		 * @return receiveData
		 */
		public byte[] getReceiveData() {
			return receiveData;
		}
		/**
		 * @return serialPort
		 */
		public SerialPortInterface getSerialPort() {
			return serialPort;
		}
	}

	public interface SerialPortInterfaceEventListener
	{
		public SerialPortInterfaceEventType getEventType();
		public void serialEvent(SerialPortInterfaceEvent event);
	}

	/**
	 * @return bitRate
	 */
	public int getBitRate() {
		return bitRate;
	}


	/**
	 * @param bitRate セットする bitRate
	 */
	public void setBitRate(int bitRate) {
		this.bitRate = bitRate;
	}


	/**
	 * @return dataBits
	 */
	public int getDataBits() {
		return dataBits;
	}


	/**
	 * @param dataBits セットする dataBits
	 */
	public void setDataBits(int dataBits) {
		this.dataBits = dataBits;
	}


	/**
	 * @return stopBits
	 */
	public int getStopBits() {
		return stopBits;
	}


	/**
	 * @param stopBits セットする stopBits
	 */
	public void setStopBits(int stopBits) {
		this.stopBits = stopBits;
	}


	/**
	 * @return parity
	 */
	public int getParity() {
		return parity;
	}


	/**
	 * @param parity セットする parity
	 */
	public void setParity(int parity) {
		this.parity = parity;
	}


	/**
	 * @return flowControl
	 */
	public int getFlowControl() {
		return flowControl;
	}


	/**
	 * @param flowControl セットする flowControl
	 */
	public void setFlowControl(int flowControl) {
		this.flowControl = flowControl;
	}


	/**
	 * @return connectState
	 */
	public SerialPortInterfaceState getConnectState() {
		return connectState;
	}



	/**
	 *
	 */
	public SerialPortInterface() {
		super();
		this.eventListener = new LinkedList<SerialPortInterfaceEventListener>();
		this.eventListener.clear();
	}


	/**
	 * シリアルポートに接続する
	 * @param portName シリアルポート名(ex.COM1)
	 * @return
	 * @throws Exception
	 */
	public SerialPortInterfaceState connect(String portName)
	{
		try {
			if(this.serialPort != null) {this.disconnect();}

			this.serialPort = SerialPort.getCommPort(portName);

			this.serialPort.setComPortParameters(this.bitRate, this.dataBits, this.stopBits, this.parity);
			this.serialPort.setFlowControl(this.flowControl);

			this.serialPort.setComPortTimeouts(
					SerialPort.TIMEOUT_NONBLOCKING,
					0,
					0
			);

			this.serialPort.addDataListener(new SerialPortInterfaceReader(this,this.eventListener));
			this.serialPort.addDataListener(new SerialPortInterfaceWriter(this,this.eventListener));

			if(this.serialPort.openPort()) {
				this.connectState = SerialPortInterfaceState.Connected;
			}else {
				this.connectState = SerialPortInterfaceState.OpenFailed;
			}
		}catch(Exception ex) {
			if(log.isTraceEnabled())
				log.trace("function connect(" + portName + ") failed");
			this.connectState = SerialPortInterfaceState.OpenFailed;
		}
		return connectState;
	}

	/**
	 * シリアルポートを切断する
	 */
	public SerialPortInterfaceState disconnect()
	{
		try {
			if(this.serialPort != null){
				if(this.serialPort.isOpen()) {this.serialPort.closePort();}
				this.serialPort.removeDataListener();
			}
		}catch(Exception ex) {
			if(log.isTraceEnabled())
				log.trace(this.getClass().getName() + " function close() error " + ex.getMessage());
		}
		this.connectState = SerialPortInterfaceState.Disconnected;

		return connectState;
	}

	/**
	 * シリアルポートへ書き込む
	 * @param buffer
	 * @param bytesToWrite
	 * @return
	 */
	public boolean writeBytes(byte[] buffer,long bytesToWrite) {
		int writeBytes = -1;
		if(this.serialPort != null && buffer != null && bytesToWrite > 0)
			writeBytes = this.serialPort.writeBytes(buffer, bytesToWrite);
		//if(log.isTraceEnabled()) {log.trace("function writeBytes() called...DATA:" + FormatUtil.bytesToHex(buffer));}
		if(writeBytes != bytesToWrite)
			return false;
		else
			return true;
	}

	/**
	 * シリアルポートから読み込む(セミブロッキングモード)
	 * @param buffer
	 * @return
	 */
	public int readBytesBySemiBlockingMode(byte[] buffer)
	{
		if(
				this.serialPort == null ||
				!this.serialPort.isOpen()  ||
				this.connectState != SerialPortInterfaceState.Connected ||
				buffer == null || buffer.length <= 0
		) {return -1;}

		this.serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 100, 0);

		int numRead = this.serialPort.readBytes(buffer, buffer.length);

		return numRead;
	}

	/**
	 * イベントリスナを登録する
	 * @param eventListener
	 * @return
	 */
	public boolean addEventListener(SerialPortInterfaceEventListener eventListener)
	{
		if(this.eventListener != null && eventListener != null && eventListener instanceof SerialPortInterfaceEventListener)
			this.eventListener.add(eventListener);

		return true;
	}

	/**
	 * 指定されたイベントリスナを削除する
	 * @param eventListener
	 * @return
	 */
	public boolean removeEventListener(SerialPortInterfaceEventListener eventListener)
	{
		if(this.eventListener != null && eventListener != null && eventListener instanceof SerialPortInterfaceEventListener)
			return this.eventListener.remove(eventListener);
		else
			return false;
	}

	/**
	 * イベントリスナを全て削除する
	 */
	public void removeEventListenerAll() {
		if(this.eventListener != null) {this.eventListener.clear();}
	}

	/**
	 *
	 * @author AHND
	 *
	 */
	private class SerialPortInterfaceReader implements SerialPortDataListener
	{
		private SerialPortInterface serialPort = null;
		private List<SerialPortInterfaceEventListener> listenerList;

		public SerialPortInterfaceReader(SerialPortInterface serialPort,List<SerialPortInterfaceEventListener> listenerList) {
			super();
			this.serialPort = serialPort;
			this.listenerList = listenerList;
		}

		@Override
		public int getListeningEvents() {
			return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
		}

		@Override
		public synchronized void serialEvent(SerialPortEvent event) {
			if(event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {return;}
			byte[] receivedData = new byte[event.getSerialPort().bytesAvailable()];
			event.getSerialPort().readBytes(receivedData, receivedData.length);

			synchronized (listenerList) {
				Iterator<SerialPortInterfaceEventListener> i = listenerList.iterator();
				while(i.hasNext()) {
					SerialPortInterfaceEventListener listener = i.next();
					SerialPortInterfaceEvent e =
						new SerialPortInterfaceEvent(
								SerialPortInterfaceEventType.DATA_AVAILABLE,
								this.serialPort,
								receivedData
						);
					if(
						listener != null && listener instanceof SerialPortInterfaceEventListener &&
						listener.getEventType() == SerialPortInterfaceEventType.DATA_AVAILABLE
					) {listener.serialEvent(e);}
				}
			}
		}
	}

	/**
	 *
	 * @author AHND
	 *
	 */
	private class SerialPortInterfaceWriter implements SerialPortDataListener
	{
		private SerialPortInterface serialPort = null;
		private List<SerialPortInterfaceEventListener> listenerList;

		public SerialPortInterfaceWriter(SerialPortInterface serialPort,List<SerialPortInterfaceEventListener> listenerList) {
			super();
			this.serialPort = serialPort;
			this.listenerList = listenerList;
		}

		@Override
		public int getListeningEvents() {
			return SerialPort.LISTENING_EVENT_DATA_WRITTEN;
		}

		@Override
		public void serialEvent(SerialPortEvent event) {
			if(event.getEventType() != SerialPort.LISTENING_EVENT_DATA_WRITTEN) {return;}

			synchronized (listenerList) {
				Iterator<SerialPortInterfaceEventListener> i = listenerList.iterator();
				while(i.hasNext()) {
					SerialPortInterfaceEventListener listener = i.next();
					SerialPortInterfaceEvent e =
						new SerialPortInterfaceEvent(SerialPortInterfaceEventType.DATA_WRITTEN,this.serialPort);
					if(listener != null && listener instanceof SerialPortInterfaceEventListener &&
							listener.getEventType() == SerialPortInterfaceEventType.DATA_WRITTEN
					) {listener.serialEvent(e);}
				}
			}
		}

	}

}
