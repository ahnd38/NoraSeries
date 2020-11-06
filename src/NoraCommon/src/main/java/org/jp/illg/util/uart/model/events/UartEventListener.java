package org.jp.illg.util.uart.model.events;

public interface UartEventListener {
    public UartEventType getLinteningEventType();
    public void uartEvent(UartEvent uartEvent);
}
