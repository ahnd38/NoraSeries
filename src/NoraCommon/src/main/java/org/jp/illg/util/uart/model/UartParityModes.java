package org.jp.illg.util.uart.model;

import com.fazecast.jSerialComm.SerialPort;
import com.ftdi.j2xx.D2xxManager;

public enum UartParityModes {
    PARITY_NONE(SerialPort.NO_PARITY, D2xxManager.FT_PARITY_NONE),
    ODD_PARITY(SerialPort.ODD_PARITY, D2xxManager.FT_PARITY_ODD),
    PARITY_EVEN(SerialPort.EVEN_PARITY, D2xxManager.FT_PARITY_EVEN),
    PARITY_MARK(SerialPort.MARK_PARITY, D2xxManager.FT_PARITY_MARK),
    PARITY_SPACE(SerialPort.SPACE_PARITY, D2xxManager.FT_PARITY_SPACE),
    ;

    private final int serialPortValue;
    private final byte d2xxValue;

    private UartParityModes(final int serialPortValue, final byte d2xxValue) {
        this.serialPortValue = serialPortValue;
        this.d2xxValue = d2xxValue;
    }

    public int getSerialPortValue(){return this.serialPortValue;}

    public byte getD2xxValue(){return this.d2xxValue;}
}
