package org.jp.illg.util.uart.model;

import com.fazecast.jSerialComm.SerialPort;
import com.ftdi.j2xx.D2xxManager;

public enum UartStopBitModes {
    STOPBITS_ONE(SerialPort.ONE_STOP_BIT, D2xxManager.FT_STOP_BITS_1),
    STOPBITS_ONE_POINT_FIVE(SerialPort.ONE_POINT_FIVE_STOP_BITS, D2xxManager.FT_STOP_BITS_1),
    STOPBITS_TWO(SerialPort.TWO_STOP_BITS, D2xxManager.FT_STOP_BITS_2),
    ;

    private final int serialPortValue;
    private final byte d2xxValue;

    private UartStopBitModes(final int serialPortValue, final byte d2xxValue) {
        this.serialPortValue = serialPortValue;
        this.d2xxValue = d2xxValue;
    }

    public int getSerialPortValue(){return this.serialPortValue;}

    public byte getD2xxValue(){return this.d2xxValue;}
}
