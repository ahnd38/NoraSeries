package org.jp.illg.util.uart;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.uart.model.events.UartEvent;
import org.jp.illg.util.uart.model.events.UartEventListener;
import org.jp.illg.util.uart.model.events.UartEventType;

import java.util.Arrays;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class D2xxInterfaceHelperThread extends ThreadBase {


    @Getter(AccessLevel.PRIVATE)
    private final D2xxInterface d2xxInterface;

    private byte[] receiveBuffer;

    private long eventMask;


    public D2xxInterfaceHelperThread(ThreadUncaughtExceptionListener exceptionListener, D2xxInterface d2xxInterface){
        super(exceptionListener, D2xxInterfaceHelperThread.class.getSimpleName());

        setProcessLoopPeriodMillis(1);

        receiveBuffer = new byte[1024];

        this.d2xxInterface = d2xxInterface;

		eventMask = 0x0;
    }

	private BroadcastReceiver rxcharBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			D2xxInterfaceHelperThread.super.wakeupProcessThread();
		}
	};

    @Override
    public boolean start(){
        if(getD2xxInterface() == null) {
            log.error("Could not start " + getWorkerThreadName() + ", because d2xxInterface is not set.");
            return false;
        }else if(getD2xxInterface().getD2xxDevice() == null){
            log.error("Could not start " + getWorkerThreadName() + ", because d2xxInterface.d2xxDevice is not set.");
            return false;
        }



        return super.start();
    }


    @Override
    protected ThreadProcessResult threadInitialize(){
    	eventMask = eventMask | D2xxManager.FT_EVENT_RXCHAR;

    	if((eventMask & D2xxManager.FT_EVENT_RXCHAR) != 0) {
		    LocalBroadcastManager.getInstance(getD2xxInterface().getUartContext())
				    .registerReceiver(rxcharBroadcastReceiver, new IntentFilter("FT_EVENT_RXCHAR"));
	    }

	    synchronized (getD2xxInterface().getD2xxDeviceLock()){
    		getD2xxInterface().getD2xxDevice().setEventNotification(eventMask);
	    }

    	return ThreadProcessResult.NoErrors;
    }

    @Override
    protected ThreadProcessResult process(){

        FT_Device ftDev = getD2xxInterface().getD2xxDevice();
        if(ftDev == null){return ThreadProcessResult.FatalError;}

        byte[] receiveData = null;

        synchronized (getD2xxInterface().getD2xxDeviceLock()){
            int availableBytes = ftDev.getQueueStatus();
            if(availableBytes > receiveBuffer.length){availableBytes = receiveBuffer.length;}

            int readBytes = ftDev.read(receiveBuffer, availableBytes);

            if(readBytes > 0){
                receiveData = Arrays.copyOf(receiveBuffer, readBytes);
                for(UartEventListener listener : getD2xxInterface().getEventListener()){
                    if(listener.getLinteningEventType() == UartEventType.DATA_AVAILABLE)
                        listener.uartEvent(new UartEvent(getD2xxInterface(), receiveData));
                }
            }
        }

        return ThreadProcessResult.NoErrors;
    }

    @Override
    protected void threadFinalize(){
	    if((eventMask & D2xxManager.FT_EVENT_RXCHAR) != 0) {
		    LocalBroadcastManager.getInstance(getD2xxInterface().getUartContext())
				    .unregisterReceiver(rxcharBroadcastReceiver);
	    }
    }
}
