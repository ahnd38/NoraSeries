package org.jp.illg.util.uart;


import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.lang3.SystemUtils;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UartInterfaceFactory {

	public static UartInterface createUartInterface(
		final ThreadUncaughtExceptionListener exceptionListener
	){
		return createUartInterface(exceptionListener, UartInterfaceType.Serial);
	}

	public static UartInterface createUartInterface(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final UartInterfaceType interfaceType
	) {
		UartInterface uartInterface = null;

		if(SystemUtil.IS_Android){
			switch(interfaceType) {
			case Serial:
				try {
					Class<?> d2xxInterfaceClass = Class.forName("org.jp.illg.util.uart.D2xxInterface");
					Class<?> androidHelperClass = Class.forName("org.jp.illg.util.android.AndroidHelper");
					Class<?> contextClass = Class.forName("android.content.Context");

					Method getApplicationContext = androidHelperClass.getMethod("getApplicationContext");

					Constructor<?> constructor =
						d2xxInterfaceClass.getConstructor(ThreadUncaughtExceptionListener.class, contextClass);

					uartInterface =
						(UartInterface)constructor.newInstance(
							exceptionListener,
							getApplicationContext.invoke(null, new Object[]{})
							);
				}catch (
					ClassNotFoundException |
					NoSuchMethodException |
					InvocationTargetException |
					IllegalAccessException |
					InstantiationException ex
				){
					log.warn("Could not load uart interface class for android.", ex);
				}
				break;

			case BluetoothSPP:
				try {
					Class<?> androidBluetoothSPPClass = Class.forName("org.jp.illg.util.uart.AndroidBluetoothSPP");
					Class<?> androidHelperClass = Class.forName("org.jp.illg.util.android.AndroidHelper");
					Class<?> contextClass = Class.forName("android.content.Context");

					Method getApplicationContext = androidHelperClass.getMethod("getApplicationContext");

					Constructor<?> constructor =
							androidBluetoothSPPClass.getConstructor(ThreadUncaughtExceptionListener.class, contextClass);

					uartInterface =
							(UartInterface)constructor.newInstance(
									exceptionListener,
									getApplicationContext.invoke(null, new Object[]{})
							);
				}catch (
						ClassNotFoundException |
						NoSuchMethodException |
						InvocationTargetException |
						IllegalAccessException |
						InstantiationException ex
				){
					log.warn("Could not load uart interface class for android bluetooth spp.", ex);
				}
				break;
			}

		}
		else if(SystemUtils.IS_OS_LINUX || SystemUtils.IS_OS_WINDOWS || SystemUtils.IS_OS_MAC){
			try {
				Class<?> jSerialCommInterfaceClass = Class.forName("org.jp.illg.util.uart.JSerialCommInterface");

				Constructor<?> constructor = jSerialCommInterfaceClass.getConstructor();

				uartInterface = (UartInterface)constructor.newInstance();
			}catch (
				ClassNotFoundException |
				NoSuchMethodException |
				InvocationTargetException |
				IllegalAccessException |
				InstantiationException ex
			){
				if(log.isWarnEnabled())
					log.warn("Could not load uart interface class for linux/windows/mac.", ex);
			}
		}
		else{
			if(log.isErrorEnabled())
				log.error("Could not create uart interface, this operating system not supported.");

			uartInterface = null;
		}

		return uartInterface;
	}

}
