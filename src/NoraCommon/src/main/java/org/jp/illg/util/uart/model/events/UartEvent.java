package org.jp.illg.util.uart.model.events;

import org.jp.illg.util.uart.UartInterface;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

public class UartEvent {

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private UartEventType eventType;

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private UartInterface uartInterface;

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private byte[] receiveData;


    private UartEvent(){
        super();
    }

    public UartEvent(UartInterface uartInterface, byte[] receiveData){
        this();

        setEventType(UartEventType.DATA_AVAILABLE);
        setUartInterface(uartInterface);
        setReceiveData(receiveData);
    }

    public UartEvent(UartInterface uartInterface, UartEventType uartEventType){
        this();

        setEventType(uartEventType);
        setUartInterface(uartInterface);
    }


}
