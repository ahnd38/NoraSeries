package org.jp.illg.util.uart;

import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.BufferState;
import org.jp.illg.util.BufferUtil;
import org.jp.illg.util.BufferUtilObject;
import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.android.bluetooth.BluetoothSerial;
import org.jp.illg.util.android.bluetooth.BluetoothSerialRawListener;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;
import org.jp.illg.util.uart.model.events.UartEvent;
import org.jp.illg.util.uart.model.events.UartEventListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AndroidBluetoothSPP extends UartInterfaceImpl
		implements UartInterface, BluetoothSerialRawListener {
	
	private final String logHeader;
	
	private final Context context;
	private final ThreadUncaughtExceptionListener threadUncaughtExceptionListener;
	
	private final Lock locker;
	private final Condition connecting;
	
	private boolean connectResult;
	
	private BluetoothSerial bluetoothService;
	
	private final Lock readBufferLocker;
	private final ByteBuffer readBuffer;
	private BufferState readBufferState;
	private final Timer readBufferTimekeeper;
	
	public AndroidBluetoothSPP(
			final ThreadUncaughtExceptionListener exceptionListener,
			@NonNull final Context context
	) {
		super();
		
		logHeader = this.getClass().getSimpleName() + " : ";
		
		this.context = context;
		this.threadUncaughtExceptionListener = exceptionListener;
		
		bluetoothService = new BluetoothSerial(context, this);
		
		locker = new ReentrantLock();
		connecting = locker.newCondition();
		
		connectResult = false;
		
		readBufferLocker = new ReentrantLock();
		readBuffer = ByteBuffer.allocateDirect(8096);
		readBufferState = BufferState.INITIALIZE;
		readBufferTimekeeper = new Timer(5, TimeUnit.SECONDS);
	}
	
	@Override
	public List<String> getUartPortList(){
		String[] btDeviceNames = bluetoothService.getPairedDevicesName();
		
		if(btDeviceNames != null)
			return Arrays.asList(btDeviceNames);
		else
			return new ArrayList<>();
	}

	@Override
	public boolean openPort(String uartPortName){
		if(uartPortName == null || "".equals(uartPortName))
			return false;
		
		if(bluetoothService.isConnected()) {bluetoothService.stop();}
		
		bluetoothService.setup();
		
		if(!bluetoothService.checkBluetooth() || !bluetoothService.isBluetoothEnabled())
		{
			log.warn(logHeader + "Bluetooth is disabled, please enable bluetooth service for the your phone.");
			return false;
		}
		
		BluetoothDevice targetDevice = null;
		Set<BluetoothDevice> devices = bluetoothService.getPairedDevices();
		for(BluetoothDevice device : devices){
			if(device.getName().equals(uartPortName)){
				targetDevice = device;
				break;
			}
		}
		
		if(targetDevice == null){
			log.warn(logHeader + "Not found bluetooth device " + uartPortName + ".");
			return false;
		}
		
		bluetoothService.start();
		bluetoothService.connect(targetDevice);
		
		locker.lock();
		try {
			if(!connecting.await(20, TimeUnit.SECONDS)){
				log.warn(logHeader + "Failed bluetooth connect, timeout to " + uartPortName + ".");
				return false;
			}
		}catch(InterruptedException ex){
			return false;
		}finally{locker.unlock();}
		
		return connectResult;
	}
	
	@Override
	public void closePort(){
		bluetoothService.stop();
	}
	
	@Override
	public boolean isOpen() {
		return bluetoothService.isConnected();
	}
	
	@Override
	public int readByteAvailable(){
		readBufferLocker.lock();
		try{
			readBufferState = BufferState.toREAD(readBuffer, readBufferState);
			
			return readBuffer.remaining();
		}finally{readBufferLocker.unlock();}
	}
	
	@Override
	public int readBytes(byte[] buffer, int bytesToRead, long timeoutMillis){
		if(buffer == null || buffer.length <= 0 || bytesToRead <= 0 || timeoutMillis < 0)
			return 0;
		
		readBufferLocker.lock();
		try{
			readBufferState = BufferState.toREAD(readBuffer, readBufferState);
			
			int bytesCount = 0;
			for(
					bytesCount = 0;
					bytesCount  < bytesToRead && bytesCount < buffer.length && readBuffer.hasRemaining();
					bytesCount++
			){
				buffer[bytesCount] = readBuffer.get();
			}
			
			return bytesCount;
		}finally{readBufferLocker.unlock();}
	}
	
	@Override
	public int writeBytes(byte[] buffer, int bytesToWrite , long timeoutMillis){
		if(buffer == null || bytesToWrite <= 0 || timeoutMillis < 0 || !bluetoothService.isConnected())
			return 0;
		
		if(buffer.length > bytesToWrite)
			buffer = Arrays.copyOf(buffer, bytesToWrite);
		
		bluetoothService.write(buffer);
		
		return buffer.length;
	}
	
	/**
	 * Bluetooth adapter is not present on this device.
	 */
	@Override
	public void onBluetoothNotSupported(){
		if(log.isTraceEnabled())
			log.trace(logHeader + "onBluetoothNotSupported()");
	}
	
	/**
	 * This device's Bluetooth adapter is turned off.
	 */
	@Override
	public void onBluetoothDisabled(){
		if(log.isTraceEnabled())
			log.trace(logHeader + "onBluetoothDisabled()");
	}
	
	/**
	 * Disconnected from a remote Bluetooth device.
	 */
	@Override
	public void onBluetoothDeviceDisconnected(){
		if(log.isTraceEnabled())
			log.trace(logHeader + "onBluetoothDeviceDisconnected()");
	}
	
	/**
	 * Connecting to a remote Bluetooth device.
	 */
	@Override
	public void onConnectingBluetoothDevice(){
		if(log.isTraceEnabled())
			log.trace(logHeader + "onConnectingBluetoothDevice()");
	}
	
	/**
	 * Connected to a remote Bluetooth device.
	 *
	 * @param name The name of the remote device.
	 * @param address The MAC address of the remote device.
	 */
	@Override
	public void onBluetoothDeviceConnected(String name, String address){
		connectResult = true;
		
		locker.lock();
		try {
			connecting.signalAll();
		}finally{locker.unlock();}
		
		log.info(logHeader + "Connected to " + name + "/" + address + ".");
	}
	
	/**
	 * Specified message is read from the serial port.
	 *
	 * @param message The message read.
	 */
	@Override
	public void onBluetoothSerialRead(String message){
	
	}
	
	/**
	 * Specified message is written to the serial port.
	 *
	 * @param message The message written.
	 */
	@Override
	public void onBluetoothSerialWrite(String message){
	
	}
	
	/**
	 * Specified message is read from the serial port.
	 *
	 * @param bytes The byte array read.
	 */
	@Override
	public void onBluetoothSerialReadRaw(byte[] bytes){
		if(bytes == null){return;}
		
		if(log.isTraceEnabled()){
			log.trace(
					logHeader +
							"Read from Bluetooth device " +
							bluetoothService.getConnectedDeviceName() + "/" +
							bluetoothService.getConnectedDeviceAddress() + ".\n" +
							FormatUtil.bytesToHexDump(bytes, 4)
			);
		}
		
		readBufferLocker.lock();
		try {
			BufferUtilObject result =
					BufferUtil.putBuffer(logHeader, readBuffer, readBufferState, readBufferTimekeeper, bytes, true);
			readBufferState = result.getBufferState();
		}finally {
			readBufferLocker.unlock();
		}
		
		for(final UartEventListener listener : getEventListener()){
			final UartEvent event =
					new UartEvent(this, bytes);
			
			if(listener != null){listener.uartEvent(event);}
		}
	}
	
	/**
	 * Specified message is written to the serial port.
	 *
	 * @param bytes The byte array written.
	 */
	@Override
	public void onBluetoothSerialWriteRaw(byte[] bytes){
		if(bytes == null){return;}
		
		if(log.isTraceEnabled()){
			log.trace(
					logHeader +
					"Write to Bluetooth device " +
					bluetoothService.getConnectedDeviceName() + "/" +
					bluetoothService.getConnectedDeviceAddress() + ".\n" +
					FormatUtil.bytesToHexDump(bytes, 4)
			);
		}
	}
}
