package org.jp.illg.nora.gateway.service.norausers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.jp.illg.nora.gateway.service.norausers.model.Request;
import org.jp.illg.nora.gateway.service.norausers.model.RequestType;
import org.jp.illg.nora.gateway.service.norausers.model.Result;
import org.jp.illg.nora.gateway.service.norausers.model.ResultType;
import org.jp.illg.util.ApplicationInformation;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraCrashReporter {

	private static final String logTag = NoraCrashReporter.class.getSimpleName() + " : ";

	private NoraCrashReporter() {}

	public static boolean crashReport(
		@NonNull final String apiServerAddress,
		final int apiServerPort,
		@NonNull final UUID noraId,
		@NonNull final ApplicationInformation<?> applicationInformation,
		@NonNull final String callsign,
		@NonNull final Exception uncaughtException
	) {
		boolean result = false;

		final InetSocketAddress serverAddr =
			new InetSocketAddress(apiServerAddress, apiServerPort);
		if(serverAddr.isUnresolved()) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not resolve server address = " + apiServerAddress);

			return false;
		}

		final Request request = new Request(callsign, noraId.toString());
		request.setRequestType(RequestType.CrashReport.toString());
		request.setApplicationName(applicationInformation.getApplicationName());
		request.setApplicationVersion(applicationInformation.getApplicationVersion());
		request.setRunningOsName(applicationInformation.getRunningOperatingSystem());
		try(
			final StringWriter sw = new StringWriter();
			final PrintWriter pw = new PrintWriter(sw);
		) {
			uncaughtException.printStackTrace(pw);
			pw.flush();

			request.setCrashReport(sw.toString());
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Crash report convert error", ex);

			return false;
		}

		final Gson gson = new GsonBuilder().create();
		final String sendData = gson.toJson(request) + "\r\n\r\n";

		try(
			final Socket socket = new Socket(serverAddr.getAddress(), serverAddr.getPort());
			final BufferedWriter writer =
				new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
			final BufferedReader reader =
				new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
		){
			socket.setSoTimeout(5000);

			writer.append(sendData);
			writer.flush();

			final StringBuilder recvBuffer = new StringBuilder();
			String readLine = null;
			while((readLine = reader.readLine()) != null) {
				if(log.isTraceEnabled())
					log.trace(logTag + "Receive data\n    " + readLine);

				recvBuffer.append(readLine);
				recvBuffer.append("\r\n");

				if(recvBuffer.indexOf("\r\n\r\n") >= 0) {
					final Result resultData = gson.fromJson(recvBuffer.toString(), Result.class);
					final ResultType resultType = ResultType.getTypeByValueIgnoreCase(resultData.getResultType());

					result = resultType == ResultType.ACK;
					if(!result) {
						if(log.isWarnEnabled())
							log.warn(logTag + "Server returned NAK after send crash report...\n" + recvBuffer.toString());
					}

					break;
				}
			}
		} catch (IOException | JsonSyntaxException ex) {
			if(log.isErrorEnabled())
				log.error(logTag + "Could not send crash report", ex);

			return false;
		}

		return result;
	}
}
