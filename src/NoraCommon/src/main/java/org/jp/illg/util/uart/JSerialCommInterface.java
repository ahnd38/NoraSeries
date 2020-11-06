package org.jp.illg.util.uart;

import java.util.ArrayList;
import java.util.List;

import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.uart.model.events.UartEvent;
import org.jp.illg.util.uart.model.events.UartEventListener;
import org.jp.illg.util.uart.model.events.UartEventType;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class JSerialCommInterface extends UartInterfaceImpl {

	private static final String logTag = JSerialCommInterface.class.getSimpleName() + " : ";

	private static final int errno = -55;

	public static enum JSerialCommInterfaceState {
		Disconnected,
		Connected,
		OpenFailedNoSuchPort,
		OpenFailedCurrentlyInUse,
		OpenFailed
	}

	/**
	 * 接続状態
	 */
	private JSerialCommInterfaceState connectState = JSerialCommInterfaceState.Disconnected;

	/**
	 * シリアルポートインスタンス
	 */
	private SerialPort serialPort = null;

	/**
	 * タイムアウト値(ms)
	 */
	private int timeoutMillis;

	private static final int timeoutMillisDefault = 100;


	private class SerialPortInterfaceReader implements SerialPortDataListener {
		private UartInterface serialPort = null;

		public SerialPortInterfaceReader(UartInterface serialPort) {
			super();
			this.serialPort = serialPort;
		}

		@Override
		public int getListeningEvents() {
			return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
		}

		@Override
		public synchronized void serialEvent(SerialPortEvent event) {
			if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
				return;
			}
			final byte[] receivedData = new byte[event.getSerialPort().bytesAvailable()];
			event.getSerialPort().readBytes(receivedData, receivedData.length);

			for (UartEventListener listener : this.serialPort.getEventListener()) {
				final UartEvent e = new UartEvent(
					this.serialPort,
					receivedData
				);

				if (listener != null && listener instanceof UartEventListener &&
					listener.getLinteningEventType() == UartEventType.DATA_AVAILABLE) {
					listener.uartEvent(e);
				}
			}
		}
	}

	private class SerialPortInterfaceWriter implements SerialPortDataListener {
		private UartInterface serialPort = null;

		public SerialPortInterfaceWriter(UartInterface serialPort) {
			super();

			this.serialPort = serialPort;
		}

		@Override
		public int getListeningEvents() {
			return SerialPort.LISTENING_EVENT_DATA_WRITTEN;
		}

		@Override
		public void serialEvent(SerialPortEvent event) {
			if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_WRITTEN) {
				return;
			}

			for (UartEventListener listener : this.serialPort.getEventListener()) {
				final UartEvent e = new UartEvent(this.serialPort, UartEventType.DATA_WRITTEN);

				if (listener != null && listener instanceof UartEventListener &&
					listener.getLinteningEventType() == UartEventType.DATA_WRITTEN) {
					listener.uartEvent(e);
				}
			}
		}
	}

	public JSerialCommInterface() {
		super();

		timeoutMillis = timeoutMillisDefault;
	}

	@Override
	public List<String> getUartPortList() {
		List<String> ports = new ArrayList<>();

		for (SerialPort port : SerialPort.getCommPorts())
			ports.add(port.getSystemPortName());

		return ports;
	}

	@Override
	public boolean openPort(String uartPortName) {
		try {
			if (this.serialPort != null) {
				this.closePort();
			}

			this.serialPort = SerialPort.getCommPort(uartPortName);

			this.serialPort.setComPortParameters(
				getBaudRate(), getDataBits(),
				getStopBitMode().getSerialPortValue(),
				getParityMode().getSerialPortValue()
			);
			this.serialPort.setFlowControl(getFlowControlMode().getSerialPortValue());

			this.serialPort.setComPortTimeouts(
				SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
				timeoutMillis,
				0
			);

			this.serialPort.addDataListener(new SerialPortInterfaceReader(this));
			this.serialPort.addDataListener(new SerialPortInterfaceWriter(this));

			if (this.serialPort.openPort()) {
				this.connectState = JSerialCommInterfaceState.Connected;
			} else {
				this.connectState = JSerialCommInterfaceState.OpenFailed;
			}
		} catch (Exception ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + "function connect(" + uartPortName + ") failed");

			this.connectState = JSerialCommInterfaceState.OpenFailed;
		}

		return connectState == JSerialCommInterfaceState.Connected;
	}

	@Override
	public void closePort() {
		try {
			if (this.serialPort != null) {
				if (this.serialPort.isOpen()) {
					this.serialPort.closePort();
				}
				this.serialPort.removeDataListener();
			}

			this.serialPort = null;

		} catch (Exception ex) {
			if(log.isWarnEnabled())
				log.warn(logTag + this.getClass().getName() + " function close() error " + ex.getMessage());
		}

		this.connectState = JSerialCommInterfaceState.Disconnected;
	}

	@Override
	public boolean isOpen() {
		return this.serialPort != null && this.serialPort.isOpen();
	}

	@Override
	public int readByteAvailable() {
		if (this.serialPort == null ||
			!this.serialPort.isOpen() ||
			this.connectState != JSerialCommInterfaceState.Connected
		) {
			return errno;
		}

		return this.serialPort.bytesAvailable();
	}

	@Override
	public int readBytes(byte[] buffer, int bytesToRead, long timeoutMillis) {
		if (this.serialPort == null ||
			!this.serialPort.isOpen() ||
			this.connectState != JSerialCommInterfaceState.Connected ||
			buffer == null || buffer.length <= 0
		) {
			return errno;
		}

		if(this.timeoutMillis != timeoutMillis) {
			this.timeoutMillis = (int)timeoutMillis;

			this.serialPort.setComPortTimeouts(
				SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
				this.timeoutMillis,
				0
			);
		}

		final int numRead = this.serialPort.readBytes(buffer, buffer.length);

		return numRead;
	}

	@Override
	public int writeBytes(byte[] buffer, int bytesToWrite, long timeoutMillis) {
		int writeBytes = errno;
		if (this.serialPort != null && buffer != null && bytesToWrite > 0)
			writeBytes = this.serialPort.writeBytes(buffer, bytesToWrite);

		if (log.isTraceEnabled())
			log.trace(logTag + "function writeBytes() called...DATA:" + FormatUtil.bytesToHexDump(buffer, 4));

		return writeBytes;
	}
}
