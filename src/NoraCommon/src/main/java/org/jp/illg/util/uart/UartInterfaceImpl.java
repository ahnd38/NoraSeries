package org.jp.illg.util.uart;

import java.util.ArrayList;
import java.util.List;

import org.jp.illg.util.uart.model.UartFlowControlModes;
import org.jp.illg.util.uart.model.UartParityModes;
import org.jp.illg.util.uart.model.UartStopBitModes;
import org.jp.illg.util.uart.model.events.UartEventListener;

import lombok.Getter;
import lombok.Setter;

public abstract class UartInterfaceImpl implements UartInterface {

	private static final int errno = -99;

    @Getter
    @Setter
    private int baudRate;

    @Getter
    @Setter
    private int dataBits;

    @Getter
    @Setter
    private UartParityModes parityMode;

    @Getter
    @Setter
    private UartFlowControlModes flowControlMode;

    @Getter
    @Setter
    private UartStopBitModes stopBitMode;

    @Getter
    @Setter
    private String portName;

    private final List<UartEventListener> eventListeners;

    protected UartInterfaceImpl(){
        super();

        eventListeners = new ArrayList<UartEventListener>();

        setBaudRate(9600);
        setDataBits(8);
        setParityMode(UartParityModes.PARITY_NONE);
        setFlowControlMode(UartFlowControlModes.FLOWCONTROL_DISABLE);
        setStopBitMode(UartStopBitModes.STOPBITS_ONE);
        setPortName("");
    }

    @Override
    public UartEventListener[] getEventListener(){
        synchronized (eventListeners){
            return eventListeners.toArray(new UartEventListener[eventListeners.size()]);
        }
    }

    @Override
    public boolean addEventListener(UartEventListener eventListener){
        if(eventListener == null){return false;}

        synchronized (eventListeners){
            return eventListeners.add(eventListener);
        }
    }

    @Override
    public boolean removeEventListener(UartEventListener eventListener){
        if(eventListener == null){return false;}

        synchronized (eventListeners){
            return eventListeners.remove(eventListener);
        }
    }

    @Override
    public void removeEventListenerAll(){
        synchronized (eventListeners){
            eventListeners.clear();
        }
    }



    abstract public List<String> getUartPortList();
    public boolean openPort(){return openPort(getPortName());}
    abstract public boolean openPort(String uartPortName);
    abstract public void closePort();


    @Override
    public int readBytes(byte[] buffer){
        if(buffer == null){return errno;}

        return readBytes(buffer, buffer.length);
    }

    @Override
    public int readBytes(byte[] buffer, int bytesToRead){
        if(buffer == null || bytesToRead < 0){return errno;}

        return readBytes(buffer, bytesToRead, 0);
    }

    @Override
    public int readBytes(byte[] buffer, long timeoutMillis){
        if(buffer == null || timeoutMillis < 0){return errno;}

        return readBytes(buffer, buffer.length, timeoutMillis);
    }

    @Override
    abstract public int readBytes(byte[] buffer, int bytesToRead, long timeoutMillis);

    @Override
    public int writeBytes(byte[] buffer){
        if(buffer == null){return errno;}

        return writeBytes(buffer, buffer.length);
    }

    @Override
    public int writeBytes(byte[] buffer, int bytesToWrite){
        if(buffer == null || bytesToWrite < 0){return errno;}

        return writeBytes(buffer, bytesToWrite, 0);
    }

    @Override
    public int writeBytes(byte[] buffer, long timeoutMillis){
        if(buffer == null || timeoutMillis < 0){return errno;}

        return writeBytes(buffer, buffer.length, 0);
    }

    @Override
    abstract public int writeBytes(byte[] buffer, int bytesToWrite , long timeoutMillis);
}
