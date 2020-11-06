package org.jp.illg.util.uart;

import java.util.List;

import org.jp.illg.util.uart.model.UartFlowControlModes;
import org.jp.illg.util.uart.model.UartParityModes;
import org.jp.illg.util.uart.model.UartStopBitModes;
import org.jp.illg.util.uart.model.events.UartEventListener;

public interface UartInterface {

    public void setBaudRate(int baudRate);
    public int getBaudRate();

    public void setDataBits(int dataBits);
    public int getDataBits();

    public void setParityMode(UartParityModes parityMode);
    public UartParityModes getParityMode();

    public void setFlowControlMode(UartFlowControlModes flowControlMode);
    public UartFlowControlModes getFlowControlMode();

    public void setStopBitMode(UartStopBitModes stopBitMode);
    public UartStopBitModes getStopBitMode();

    public void setPortName(String portName);
    public String getPortName();


    public UartEventListener[] getEventListener();
    public boolean addEventListener(UartEventListener eventListener);
    public boolean removeEventListener(UartEventListener eventListener);
    public void removeEventListenerAll();


    public List<String> getUartPortList();
    public boolean openPort();
    public boolean openPort(String uartPortName);
    public void closePort();

    public boolean isOpen();


    public int readByteAvailable();
    public int readBytes(byte[] buffer);
    public int readBytes(byte[] buffer, int bytesToRead);
    public int readBytes(byte[] buffer, long timeoutMillis);
    public int readBytes(byte[] buffer, int bytesToRead, long timeoutMillis);

    public int writeBytes(byte[] buffer);
    public int writeBytes(byte[] buffer, int bytesToWrite);
    public int writeBytes(byte[] buffer, long timeoutMillis);
    public int writeBytes(byte[] buffer, int bytesToWrite, long timeoutMillis);

}
