package org.jp.illg.dstar.service.web.func;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.BasicConfigurationBuilder;
import org.apache.commons.configuration2.builder.fluent.Parameters;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.configuration2.io.FileHandler;
import org.jp.illg.dstar.service.web.WebRemoteClientManager;
import org.jp.illg.dstar.service.web.model.WebRemoteControlErrorCode;
import org.jp.illg.dstar.service.web.model.WebRemoteControlServiceEvent;
import org.jp.illg.dstar.service.web.model.WebRemoteUserGroup;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder;
import org.jp.illg.dstar.service.web.util.DashboardEventListenerBuilder.DashboardEventListener;
import org.jp.illg.util.event.EventListener;
import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ConfigFunctions {

	private static final String logTag =
		ConfigFunctions.class.getSimpleName() + " : ";

	public static boolean setup(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final EventListener<WebRemoteControlServiceEvent> eventListener,
		@NonNull final String configurationFilePath,
		@NonNull final String authUserListFilePath,
		final int helperPort
	) {
		return
			setupRequestReadAuthUserListFile(
				exceptionListener, server, workerExecutor, clientManager, authUserListFilePath, helperPort
			) &&
			setupRequestSaveAuthUserListFile(
				exceptionListener, server, workerExecutor, clientManager, authUserListFilePath, helperPort
			) &&
			setupRequestReadConfigurationFile(
				exceptionListener, server, workerExecutor, clientManager, configurationFilePath, helperPort
			) &&
			setupRequestSaveConfigurationFile(
				exceptionListener, server, workerExecutor, clientManager, configurationFilePath, helperPort
			) &&
			setupRequestApplicationReboot(
				exceptionListener, server, workerExecutor, clientManager, eventListener
			) &&
			setupRequestSystemShutdown(
				exceptionListener, server, workerExecutor, clientManager, helperPort
			) &&
			setupRequestSystemReboot(
				exceptionListener, server, workerExecutor, clientManager, helperPort
			) &&
			setupRequestApplicationUpdate(
				exceptionListener, server, workerExecutor, clientManager, helperPort
			);
	}

	private static final boolean setupRequestApplicationReboot(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final EventListener<WebRemoteControlServiceEvent> eventListener
	) {
		final String requestEventName = "request_application_reboot";
		final String responseEventName = "response_application_reboot";

		server.addEventListener(
			requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(
				ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestApplicationRebootEvent(
							client, data, ackSender,
							requestEventName, responseEventName,
							workerExecutor, eventListener
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestSystemShutdown(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		final int helperPort
	) {
		final String requestEventName = "request_system_shutdown";
		final String responseEventName = "response_system_shutdown";

		server.addEventListener(
			requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestSystemShutdownEvent(
							client, data, ackSender,
							requestEventName, responseEventName, workerExecutor, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestSystemReboot(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		final int helperPort
	) {
		final String requestEventName = "request_system_reboot";
		final String responseEventName = "response_system_reboot";

		server.addEventListener(
			requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(
				ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestSystemRebootEvent(
							client, data, ackSender,
							requestEventName, responseEventName, workerExecutor, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestApplicationUpdate(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		final int helperPort
	) {
		final String requestEventName = "request_application_update";
		final String responseEventName = "response_application_update";

		server.addEventListener(
			requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(
				ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestApplicationUpdateEvent(
							client, data, ackSender,
							requestEventName, responseEventName, workerExecutor, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestReadAuthUserListFile(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final String authUserListFilePath,
		final int helperPort
	) {
		final String requestEventName = "request_read_auth_user_list_file";
		final String responseEventName = "response_read_auth_user_list_file";

		server.addEventListener(
			requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestReadAuthUserListEvent(
							client, data, ackSender,
							requestEventName, responseEventName, authUserListFilePath, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestSaveAuthUserListFile(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final String authUserListFilePath,
		final int helperPort
	) {
		final String requestEventName = "request_save_auth_user_list_file";
		final String responseEventName = "response_save_auth_user_list_file";

		server.addEventListener(requestEventName, Properties.class,
			new DashboardEventListenerBuilder<>(ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Properties>() {
					@Override
					public void onEvent(SocketIOClient client, Properties data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestSaveAuthUserListEvent(
							client, data, ackSender,
							requestEventName, responseEventName, authUserListFilePath, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestReadConfigurationFile(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final String configurationFilePath,
		final int helperPort
	) {
		final String requestEventName = "request_read_configuration_file";
		final String responseEventName = "response_read_configuration_file";

		server.addEventListener(requestEventName, Object.class,
			new DashboardEventListenerBuilder<>(ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Object>() {
					@Override
					public void onEvent(SocketIOClient client, Object data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestReadConfigurationEvent(
							client, data, ackSender,
							requestEventName, responseEventName, configurationFilePath, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static final boolean setupRequestSaveConfigurationFile(
		final ThreadUncaughtExceptionListener exceptionListener,
		@NonNull final SocketIOServer server,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final WebRemoteClientManager clientManager,
		@NonNull final String configurationFilePath,
		final int helperPort
	) {
		final String requestEventName = "request_save_configuration_file";
		final String responseEventName = "response_save_configuration_file";

		server.addEventListener(requestEventName, Properties.class,
			new DashboardEventListenerBuilder<>(ConfigFunctions.class, requestEventName,
				new DashboardEventListener<Properties>() {
					@Override
					public void onEvent(SocketIOClient client, Properties data, AckRequest ackSender) {
						if(!clientManager.hasUserAuthority(client, WebRemoteUserGroup.Administrators)) {
							sendResponse(
								client, responseEventName, new Properties(),
								WebRemoteControlErrorCode.AuthorizedPermission, null
							);
							return;
						}

						onRequestSaveConfigurationEvent(
							client, data, ackSender,
							requestEventName, responseEventName, configurationFilePath, helperPort
						);
					}
				}
			)
			.setExceptionListener(exceptionListener)
			.createDataListener()
		);

		return true;
	}

	private static void onRequestReadConfigurationEvent(
		SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final String configurationFilePath,
		final int helperPort
	) {
		String configFile = null;

		final File configurationFile = new File(configurationFilePath);
		if(configurationFile.exists()) {
			if(configurationFile.canRead())
				configFile = readConfigFile(configurationFile);
			else {
				final Properties requestData = new Properties();
				requestData.setProperty("config_file_path", configurationFilePath);
				final Properties helperData = sendRequestToNoraHelper(
					"RequestConfigurationFileRead", helperPort, 2000, requestData
				);
				configFile = helperData.getProperty("config_file");
			}
		}

		final WebRemoteControlErrorCode errorCode =
			configFile != null ?
				WebRemoteControlErrorCode.NoError : WebRemoteControlErrorCode.SystemError;

		final Properties prop = new Properties();
		prop.setProperty("config_file", configFile);
		prop.setProperty("config_file_path", configurationFilePath);

		sendResponse(client, responseEventName, prop, errorCode, null);
	}

	private static void onRequestSaveAuthUserListEvent(
		SocketIOClient client, Properties data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final String configurationFilePath,
		final int helperPort
	) {
		final String configFileContents = data.getProperty("config_file");
		if(configFileContents == null) {
			sendResponse(
				client, responseEventName, new Properties(),
				WebRemoteControlErrorCode.SystemError, "Config file data is not found"
			);
			return;
		}
		try {
			if(!checkSyntaxConfig(configFileContents)) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Configuration XML file syntax error"
				);
				return;
			}
		}catch(IOException | ConfigurationException ex) {
			sendResponse(
				client, responseEventName, new Properties(),
				WebRemoteControlErrorCode.SystemError, "Configuration XML file syntax error" + System.lineSeparator() + ex
			);
			return;
		}

		final File configFile = new File(configurationFilePath);
		if(configFile.canWrite()) {
			if(!saveConfigFile(configFile, configFileContents)) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Could not write to configuration file"
				);
				return;
			}
		}
		else {
			final Properties sendData = new Properties();
			sendData.setProperty("config_file", configFileContents);
			sendData.setProperty("config_file_path", configFile.getAbsolutePath());
			final Properties helperData = sendRequestToNoraHelper("RequestConfigurationFileUpdate", helperPort, 2000);
			final boolean helperResult =
				helperData != null ? Boolean.valueOf(helperData.getProperty("result", String.valueOf(false))) : false;
			if(!helperResult) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Could not write to configuration file"
				);
				return;
			}
		}

		sendResponse(
			client, responseEventName, new Properties(),
			WebRemoteControlErrorCode.NoError, null
		);
	}

	private static void onRequestReadAuthUserListEvent(
		SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final String configurationFilePath,
		final int helperPort
	) {
		String configFile = null;

		final File configurationFile = new File(configurationFilePath);
		if(configurationFile.exists()) {
			if(configurationFile.canRead())
				configFile = readConfigFile(configurationFile);
			else {
				final Properties sendData = new Properties();
				sendData.setProperty("config_file_path", configurationFilePath);
				final Properties helperData = sendRequestToNoraHelper(
					"RequestConfigurationFileRead", helperPort, 2000, sendData
				);
				configFile = helperData.getProperty("config_file");
			}
		}

		final WebRemoteControlErrorCode errorCode =
			configFile != null ?
				WebRemoteControlErrorCode.NoError : WebRemoteControlErrorCode.SystemError;

		final Properties prop = new Properties();
		prop.setProperty("config_file", configFile);
		prop.setProperty("config_file_path", configurationFilePath);

		sendResponse(client, responseEventName, prop, errorCode, null);
	}

	private static void onRequestSaveConfigurationEvent(
		SocketIOClient client, Properties data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final String configurationFilePath,
		final int helperPort
	) {
		final String configFileContents = data.getProperty("config_file");
		if(configFileContents == null) {
			sendResponse(
				client, responseEventName, new Properties(),
				WebRemoteControlErrorCode.SystemError, "Config file data is not found"
			);
			return;
		}
		try {
			if(!checkSyntaxConfig(configFileContents)) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Configuration XML file syntax error"
				);
				return;
			}
		}catch(IOException | ConfigurationException ex) {
			sendResponse(
				client, responseEventName, new Properties(),
				WebRemoteControlErrorCode.SystemError, "Configuration XML file syntax error" + System.lineSeparator() + ex
			);
			return;
		}

		final File configFile = new File(configurationFilePath);
		if(configFile.canWrite()) {
			if(!saveConfigFile(configFile, configFileContents)) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Could not write to configuration file"
				);
				return;
			}
		}
		else {
			final Properties sendData = new Properties();
			sendData.setProperty("config_file", configFileContents);
			sendData.setProperty("config_file_path", configFile.getAbsolutePath());
			final Properties helperData =
				sendRequestToNoraHelper("RequestConfigurationFileUpdate", helperPort, 2000, sendData);
			final boolean helperResult =
				helperData != null ? Boolean.valueOf(helperData.getProperty("result", String.valueOf(false))) : false;
			if(!helperResult) {
				sendResponse(
					client, responseEventName, new Properties(),
					WebRemoteControlErrorCode.SystemError, "Could not write to configuration file"
				);
				return;
			}
		}

		sendResponse(
			client, responseEventName, new Properties(),
			WebRemoteControlErrorCode.NoError, null
		);
	}

	private static void onRequestApplicationRebootEvent(
		final SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final ExecutorService workerExecutor,
		final EventListener<WebRemoteControlServiceEvent> eventListener
	) {
		sendResponse(
			client, responseEventName, new Properties(),
			WebRemoteControlErrorCode.NoError, null
		);

		workerExecutor.submit(new RunnableTask() {
			@Override
			public void task() {
				eventListener.event(WebRemoteControlServiceEvent.RequestRestart, null);
			}
		});
	}

	private static void onRequestSystemShutdownEvent(
		final SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final ExecutorService workerExecutor,
		final int helperPort
	) {
		workerExecutor.submit(new RunnableTask() {
			@Override
			public void task() {
				processRequestToNoraHelper(
					client, data, ackSender, requestEventName, responseEventName,
					"RequestSystemShutdown", helperPort, 2000
				);
			}
		});
	}

	private static void onRequestSystemRebootEvent(
		final SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final ExecutorService workerExecutor,
		final int helperPort
	) {
		workerExecutor.submit(new RunnableTask() {
			@Override
			public void task() {
				processRequestToNoraHelper(
					client, data, ackSender, requestEventName, responseEventName,
					"RequestSystemReboot", helperPort, 2000
				);
			}
		});
	}

	private static void onRequestApplicationUpdateEvent(
		final SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final ExecutorService workerExecutor,
		final int helperPort
	) {
		workerExecutor.submit(new RunnableTask() {
			@Override
			public void task() {
				processRequestToNoraHelper(
					client, data, ackSender, requestEventName, responseEventName,
					"RequestApplicationUpdate", helperPort, 300000
				);
			}
		});
	}

	private static final Properties sendRequestToNoraHelper(
		final String requestTypeNoraHelper,
		final int helperPort,
		final int timeoutMillis
	) {
		return sendRequestToNoraHelper(requestTypeNoraHelper, helperPort, timeoutMillis, null);
	}

	private static final Properties sendRequestToNoraHelper(
		final String requestTypeNoraHelper,
		final int helperPort,
		final int timeoutMillis,
		Properties sendData
	) {
		if(sendData == null) {sendData = new Properties();}
		sendData.setProperty("requestType", requestTypeNoraHelper);

		final byte[] sendBuffer =
			new Gson().toJson(sendData, Properties.class).getBytes(StandardCharsets.UTF_8);
		final DatagramPacket sendPacket = new DatagramPacket(
			sendBuffer, sendBuffer.length, new InetSocketAddress(InetAddress.getLoopbackAddress(), helperPort)
		);

		try(final DatagramSocket socket = new DatagramSocket()) {
			socket.setSoTimeout(timeoutMillis);

			socket.send(sendPacket);

			final byte[] receiveBuffer = new byte[1024 * 1024];
			final DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
			try {
				socket.receive(receivePacket);
			}catch(SocketTimeoutException ignore) {
				if(log.isErrorEnabled())
					log.error(logTag + "No response from nora helper.");

				return null;
			}

			try(
				final BufferedReader reader =
					new BufferedReader(new InputStreamReader(
						new ByteArrayInputStream(
							receivePacket.getData(), receivePacket.getOffset(), receivePacket.getLength()
						), StandardCharsets.UTF_8)
					)
			){
				final Properties prop = new Gson().fromJson(reader, Properties.class);

				return prop;
			}catch(JsonIOException | JsonSyntaxException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "Illegal data received from nora helper.");

				return null;
			}
		}catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Socker error", ex);

			return null;
		}
	}

	private static final void processRequestToNoraHelper(
		final SocketIOClient client, Object data, AckRequest ackSender,
		final String requestEventName,
		final String responseEventName,
		final String requestTypeNoraHelper,
		final int helperPort,
		final int timeoutMillis
	) {

		final Properties noraHelperResultData = sendRequestToNoraHelper(
			requestTypeNoraHelper, helperPort, timeoutMillis
		);
		final boolean noraHelperResult =
			noraHelperResultData != null ?
				Boolean.valueOf(noraHelperResultData.getProperty("result", String.valueOf(false))) : false;

		sendResponse(
			client, responseEventName, noraHelperResultData != null ? noraHelperResultData : new Properties(),
			noraHelperResult ? WebRemoteControlErrorCode.NoError : WebRemoteControlErrorCode.SystemError, null
		);
	}

	private static final String readConfigFile(final File configFile) {
		if(!configFile.canRead()) {return null;}

		final StringBuilder sb = new StringBuilder("");
		try(final BufferedReader reader =
				new BufferedReader(new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))
		){
			String line = null;
			while((line = reader.readLine()) != null) {
				sb.append(line);
				sb.append(System.lineSeparator());
			}
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not read configuration file = " + configFile.getAbsolutePath());

			return null;
		}

		return sb.toString();
	}

	private static final boolean checkSyntaxConfig(final String configFile) throws IOException, ConfigurationException{
		try (final BufferedReader reader = new BufferedReader(new StringReader(configFile))){
			final XMLConfiguration config =
					new BasicConfigurationBuilder<>(XMLConfiguration.class)
							.configure(new Parameters().xml()).getConfiguration();

			final FileHandler fh = new FileHandler(config);
			fh.load(reader);

			return true;
		}
	}

	private static final boolean saveConfigFile(final File configFile, final String configFileContents) {
		if(!configFile.canWrite()) {return false;}

		try (final BufferedWriter writer =
				new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile), StandardCharsets.UTF_8))
		){
			writer.append(configFileContents);
			writer.flush();

			return true;
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not write to configuration file = " + configFile.getAbsolutePath(), ex);

			return false;
		}
	}

	private static void sendResponse(
		final SocketIOClient client,
		final String eventName,
		final Properties prop,
		final WebRemoteControlErrorCode errorCode,
		final String message
	) {
		prop.setProperty(
			"error_code",
			String.valueOf(errorCode.getErrorCode())
		);
		prop.setProperty(
			"error_message",
			message != null ? message : errorCode.getMessage()
		);

		client.sendEvent(eventName, prop);
	}
}
