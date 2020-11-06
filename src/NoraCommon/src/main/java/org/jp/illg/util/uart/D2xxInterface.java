package org.jp.illg.util.uart;

import android.content.Context;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.FT_Device;

import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class D2xxInterface extends UartInterfaceImpl {

    @Getter
    @Setter
    private Context uartContext;

    private D2xxManager d2xxManager;

    @Getter
    @Setter(AccessLevel.PRIVATE)
    private FT_Device d2xxDevice;

    @Getter
    private final Object d2xxDeviceLock;

    @Getter(AccessLevel.PROTECTED)
    @Setter(AccessLevel.PRIVATE)
    private ThreadUncaughtExceptionListener exceptionListener;

    private D2xxInterfaceHelperThread helperThread;

    {
        d2xxDeviceLock = new Object();
    }

    public D2xxInterface(ThreadUncaughtExceptionListener exceptionListener) {
        super();

        setExceptionListener(exceptionListener);
    }

    public D2xxInterface(ThreadUncaughtExceptionListener exceptionListener, Context uartContext) {
        this(exceptionListener);

        setUartContext(uartContext);

        d2xxManager = getD2xxManagerInstance();
    }


    @Override
    public List<String> getUartPortList() {
        List<String> ports = new ArrayList<>();

        if (d2xxManager == null) {d2xxManager = getD2xxManagerInstance();}

        if (d2xxManager != null) {
            int deviceCount = d2xxManager.createDeviceInfoList(getUartContext());

            for(int i = 0; i < deviceCount; i++){
                D2xxManager.FtDeviceInfoListNode node = d2xxManager.getDeviceInfoListDetail(i);
                if(node != null){ports.add(node.serialNumber);}
            }
        }

        return ports;
    }

    @Override
    public boolean openPort(String uartPortName){
        if(uartPortName == null || "".equals(uartPortName)){return false;}

        if (d2xxManager == null) {d2xxManager = getD2xxManagerInstance();}

        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null){
                if (getD2xxDevice().isOpen()){
                    closePort();
                }
            }
        }

        setD2xxDevice(d2xxManager.openBySerialNumber(getUartContext(), uartPortName));
        if(getD2xxDevice() == null){return false;}

        synchronized (getD2xxDeviceLock()) {
            getD2xxDevice().setBitMode((byte)0, D2xxManager.FT_BITMODE_RESET);
            getD2xxDevice().setBaudRate(getBaudRate());
            byte dataBits = (byte)getDataBits();
            byte stopBits = getStopBitMode().getD2xxValue();
            byte parity = getParityMode().getD2xxValue();
            getD2xxDevice().setDataCharacteristics(dataBits, stopBits, parity);
            getD2xxDevice().setFlowControl(getFlowControlMode().getD2xxValue(), (byte)0x0b, (byte)0x0d);

            if (
                !getD2xxDevice().isOpen() ||
                !getD2xxDevice().purge(D2xxManager.FT_PURGE_TX)
            ) {
                return false;
            }

            getD2xxDevice().restartInTask();
        }

        helperThread = new D2xxInterfaceHelperThread(getExceptionListener(),this);
        if(!helperThread.start()){
            closePort();
            return false;
        }

        return true;
    }

    @Override
    public void closePort(){
        if (d2xxManager == null) {d2xxManager = getD2xxManagerInstance();}

        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null){
                if(getD2xxDevice().isOpen()){
                    getD2xxDevice().stopInTask();
                    getD2xxDevice().close();
                }
            }
            setD2xxDevice(null);
        }


        if(helperThread != null && helperThread.isRunning()){helperThread.stop();}
        helperThread = null;
    }

    @Override
    public boolean isOpen() {
        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null)
                return getD2xxDevice().isOpen();
            else
                return false;
        }
    }

    @Override
    public int readByteAvailable(){
        int bytesAvailable = -1;

        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null){
                if(getD2xxDevice().isOpen()){
                    bytesAvailable = getD2xxDevice().getQueueStatus();
                }
            }
        }

        return bytesAvailable;
    }

    @Override
    public int readBytes(byte[] buffer, int bytesToRead, long timeoutMillis){
        if(buffer == null || bytesToRead < 0 || timeoutMillis < 0){return -1;}

        int readBytes = -1;

        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null){
                if(getD2xxDevice().isOpen()){
                    if(timeoutMillis > 0)
                        readBytes = getD2xxDevice().read(buffer, bytesToRead, timeoutMillis);
                    else
                        readBytes = getD2xxDevice().read(buffer, bytesToRead);
                }
            }
        }

        return readBytes;
    }

    @Override
    public int writeBytes(byte[] buffer, int bytesToWrite, long timeoutMillis) {
        if(buffer == null || bytesToWrite < 0 || timeoutMillis < 0){return -1;}

        int writeBytes = -1;

        synchronized (getD2xxDeviceLock()) {
            if(getD2xxDevice() != null){
                if(getD2xxDevice().isOpen()){
                    if(timeoutMillis > 0)
                        writeBytes = getD2xxDevice().write(buffer, bytesToWrite, false);
                    else
                        writeBytes = getD2xxDevice().write(buffer, bytesToWrite, true);
                }
            }
        }

        return writeBytes;
    }

    private D2xxManager getD2xxManagerInstance() {
        try {
            if (getUartContext() != null)
                return D2xxManager.getInstance(getUartContext());
        } catch (D2xxManager.D2xxException ex) {
            log.warn("Could not get d2xx instance.", ex);
        }

        return null;
    }
}
