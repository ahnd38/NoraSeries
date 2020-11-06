package org.jp.illg.util.uart.model;

import com.fazecast.jSerialComm.SerialPort;
import com.ftdi.j2xx.D2xxManager;

public enum UartFlowControlModes {
    FLOWCONTROL_DISABLE(SerialPort.FLOW_CONTROL_DISABLED, D2xxManager.FT_FLOW_NONE),
    FLOWCONTROL_CTS(SerialPort.FLOW_CONTROL_CTS_ENABLED, D2xxManager.FT_FLOW_RTS_CTS),
    FLOWCONTROL_RTS(SerialPort.FLOW_CONTROL_RTS_ENABLED, D2xxManager.FT_FLOW_RTS_CTS),
    FLOWCONTROL_DSR(SerialPort.FLOW_CONTROL_DSR_ENABLED, D2xxManager.FT_FLOW_DTR_DSR),
    FLOWCONTROL_DTR(SerialPort.FLOW_CONTROL_DTR_ENABLED, D2xxManager.FT_FLOW_DTR_DSR),
    ;

    private final int serialPortValue;
    private final short d2xxValue;

    private UartFlowControlModes(final int serialPortValue, final short d2xxValue) {
        this.serialPortValue = serialPortValue;
        this.d2xxValue = d2xxValue;
    }

    public int getSerialPortValue(){return this.serialPortValue;}

    public short getD2xxValue(){return this.d2xxValue;}
}
