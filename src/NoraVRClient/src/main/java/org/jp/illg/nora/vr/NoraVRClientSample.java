package org.jp.illg.nora.vr;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.Mixer;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.nora.vr.NoraVRClient.NoraVRDownlinkAudioPacket;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.nora.vr.protocol.model.NoraVRConfiguration;
import org.jp.illg.util.Timer;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture;
import org.jp.illg.util.audio.util.winlinux.AudioPlaybackCapture.AudioPlaybackCaptureEventLintener;
import org.jp.illg.util.audio.util.winlinux.AudioTool;
import org.jp.illg.util.logback.LogbackUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NoraVRClientSample {
	/**
	 * 接続先NoraGateway(NoraVR)アドレス&ポート
	 */
	private final String serverAddress = "192.168.200.210";
	private final int serverPort = 52162;

	/**
	 * ログインコールサイン
	 */
	private final String loginCallsign = "JI1ROJ  ";

	/**
	 * ログインパスワード
	 */
	private final String loginPassword = "5Lcw9t";

	/**
	 * 送信時自局MYコールサイン
	 */
	private final String myCallsign = "JI1ROJ  ";
	private final String myCallsignShort = "DEBG";

	/**
	 * 送信時ショートメッセージ
	 */
	private final String shortMessage = "NoraVR Debugging....";

	/**
	 * アプリケーション名称
	 */
	private final String applicationName = "";

	/**
	 * アプリケーションバージョン
	 */
	private final String applicationVersion = "";

	/**
	 * 使用コーデック
	 */
	private final NoraVRCodecType codecType = NoraVRCodecType.Opus64k;


	private final AudioPlaybackCapture	audio;
	private boolean transmitting;

	private boolean transmitStart;
	private boolean transmitEnd;

	private NoraVRConfiguration config;

	private boolean destinationGateway;
	private String yourCallsign;

	private String captureMixerName;
	private String playbackMixerName;

	private final AudioPlaybackCaptureEventLintener micInputEventListener =
		new AudioPlaybackCaptureEventLintener() {

			@Override
			public void audioCaptureEvent(byte[] audioData) {
				if(audioData == null || audioData.length != 320) {return;}

				final ShortBuffer pcm = ShortBuffer.allocate(audioData.length >> 1);
				for(int i = 0; i < audioData.length && pcm.hasRemaining(); i += 2) {
					final short sample =
						(short)(((audioData[i] << 8) & 0xFF00) | (audioData[i + 1] & 0x00FF));

					pcm.put(sample);
				}
				pcm.flip();

				if(transmitStart) {
					if(!noravrClient.writeVoice(
						destinationGateway, myCallsign, myCallsignShort, yourCallsign,
						pcm, false, shortMessage
					)) {
						audio.closeCapture();

						transmitting = false;
					}

					transmitStart = false;
				}
				else if(transmitEnd) {
					noravrClient.writeVoice(
						destinationGateway, myCallsign, myCallsignShort, yourCallsign,
						pcm, true, shortMessage
					);

					audio.closeCapture();

					transmitStart = false;
					transmitEnd = false;
					transmitting = false;

				}
				else if(transmitting) {
					noravrClient.writeVoice(
						destinationGateway, myCallsign, myCallsignShort, yourCallsign,
						pcm,  false, shortMessage
					);
				}
			}
		};

	private final Runnable speakerOutputThread = new Runnable() {

		@Override
		public void run() {
			try {
				audio.openPlayback(playbackMixerName);

				final Timer timer = new Timer(1000);

				while(!Thread.interrupted()) {
					NoraVRDownlinkAudioPacket<?> voicePacket = null;
					while((voicePacket = noravrClient.readVoice()) != null) {
						if(voicePacket.codec == NoraVRCodecType.AMBE) {continue;}

						final ShortBuffer voice = (ShortBuffer)voicePacket.audio;

						final byte[] audio = new byte[voice.remaining() << 1];
						for(int i = 0; i < audio.length && voice.hasRemaining(); i += 2) {
							final short sample = voice.get();
							audio[i] = (byte)((sample >> 8) & 0xFF);
							audio[i + 1] = (byte)(sample & 0xFF);
						}

						NoraVRClientSample.this.audio.startPlayback();
						NoraVRClientSample.this.audio.writePlayback(audio, 0);

						timer.updateTimestamp();

//						log.trace("Write to speaker...\n" + FormatUtil.bytesToHexDump(audio, 4));
					}

					if(timer.isTimeout()) {NoraVRClientSample.this.audio.stopPlayback();}

					try {
						Thread.sleep(10);
					} catch (InterruptedException ex) {
						break;
					}
				}
			}finally {
				audio.closePlayback();
			}
		}
	};

	private final NoraVREventListener noravrEventListener =
		new NoraVREventListener() {
			@Override
			public void receiveVoice() {

			}

			@Override
			public void loginSuccess(int protocolVersion) {
				if(log.isInfoEnabled())
					log.info("[Event] Login success !, Protocol version = " + protocolVersion);

			}

			@Override
			public boolean loginFailed(String reason) {
				if(log.isWarnEnabled())
					log.warn("[Event] Login failed !, Reason = " + reason);

				return true;
			}

			@Override
			public boolean connectionFailed(String reason) {
				log.warn("[Event] Connection failed !, Reason = " + reason);

				return true;
			}

			@Override
			public void configurationSet(NoraVRConfiguration configuration) {
				config = configuration;

				if(log.isInfoEnabled()) {
					log.info(
						"[Event] Configuration set complete !, " +
						String.format("Value=0x%04X", configuration.getValue())
					);
				}
			}

			@Override
			public void reflectorLink(String linkedReflectorCallsign) {
				if(log.isInfoEnabled())
					log.info("[Event] Reflector linked !, Callsign = " + linkedReflectorCallsign);
			}

			@Override
			public void transmitTimeout(final int frameID) {
				if(log.isWarnEnabled()) {
					log.warn("[Event] Voice transmit timeout !");
				}
			}

			@Override
			public void repeaterInformation(
				String callsign, String name, String location,
				double frequencyMHz, double frequencyOffsetMHz,
				double serviceRangeKm, double agl, String url,
				String description1, String description2
			) {
				if(log.isInfoEnabled()) {
					log.info(
						"[Event] Repeater information received.\n    " +
						"Callsign:" + callsign + "/Name:" + name + "/Location:" + location +
						"/Freq:" + String.format("%.3fMHz(Offset:%+.3fMHz)", frequencyMHz, frequencyOffsetMHz) +
						"/ServiceRange:" + serviceRangeKm + "km" +
						"/Agl:" + agl + "m" +
						"/Url:" + url +
						"/Description:" + description1 + "*" + description2
					);
				}
			}

			@Override
			public void userList(List<NoraVRUser> users) {
				final StringBuilder sb = new StringBuilder();
				for(final Iterator<NoraVRUser> it = users.iterator(); it.hasNext();) {
					final NoraVRUser user = it.next();
					sb.append("    ");
					if(user.isRemoteUser()) {sb.append("[*R]");}
					sb.append(user.getCallsignLong());
					sb.append('_');
					sb.append(user.getCallsignShort());

					if(it.hasNext()) {sb.append('/');}
				}

				if(log.isInfoEnabled()) {
					log.info("[Event] User list received.\n" + sb.toString());
				}
			}

			@Override
			public void accessLog(List<NoraVRAccessLog> logs) {
				final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
				final StringBuilder sb = new StringBuilder();
				for(final Iterator<NoraVRAccessLog> it = logs.iterator(); it.hasNext();) {
					final NoraVRAccessLog log = it.next();

					sb.append("    Time:");
					sb.append(dateFormat.format(new Date(log.getAccessTime())));

					sb.append('/');

					sb.append("Route:");
					sb.append(log.getRoute());

					sb.append('/');

					sb.append("YourCallsign:");
					sb.append(log.getYourCallsign());

					sb.append('/');

					sb.append("MyCallsign:");
					sb.append(log.getMyCallsignLong());
					sb.append('_');
					sb.append(log.getMyCallsignShort());

					if(it.hasNext()) {sb.append('\n');}
				}

				if(log.isInfoEnabled()) {
					log.info("[Event] Access log received.\n" + sb.toString());
				}
			}

			@Override
			public void routingService(String routingServiceName) {
				if(log.isInfoEnabled()) {
					log.info("[Event] Routing service changed -> " + routingServiceName);
				}
			}
		};

	private final NoraVRClient noravrClient;

	public NoraVRClientSample() {
		super();

		final AudioFormat format =
			new AudioFormat(8000.0F, 16, 1, true, true);

		final List<Mixer.Info> captureMixers = AudioTool.getCaptureMixers(format);
		if(!captureMixers.isEmpty()) {captureMixerName = captureMixers.get(0).getName();}

		final List<Mixer.Info> playbackMixers = AudioTool.getPlaybackMixers(format);
		if(!playbackMixers.isEmpty()) {playbackMixerName = playbackMixers.get(0).getName();}

		audio =
			new AudioPlaybackCapture(
				format,
				320,
				micInputEventListener
			);

		noravrClient = new NoraVRClient(
			noravrEventListener, false,
			(applicationName != null && !"".equals(applicationName)) ? applicationName : NoraVRClientSample.class.getSimpleName(),
			(applicationVersion != null && !"".equals(applicationVersion)) ? applicationVersion : "1,0"
		);

		transmitting = false;
		transmitStart = false;
		transmitEnd = false;

		destinationGateway = false;
		yourCallsign = DSTARDefines.CQCQCQ;
	}

	public int process() {

		final Thread speaker = new Thread(speakerOutputThread);

		try {

			if(!noravrClient.connect(loginCallsign, loginPassword, serverAddress, serverPort, codecType)) {
				if(log.isErrorEnabled())
					log.error("Failed NoraVR connect()");

				return -1;
			}
			speaker.start();

			int inputChar;
			do {
				try {
					inputChar = System.in.read();
				}catch(IOException ex) {
					break;
				}

				if(inputChar == ' ') {
					if(!transmitting) {
						if(noravrClient.isConnected())
							transmit(true);
					}
					else {
						transmit(false);
					}
				}
				else if(inputChar == 'l') {
					destinationGateway = false;
					yourCallsign = DSTARDefines.CQCQCQ;

					log.info("Set to local CQ = " + yourCallsign);
				}
				else if(inputChar == 'i') {
					destinationGateway = true;
					yourCallsign = "_______I";

					log.info("UR set to connect information = " + yourCallsign);
				}
				else if(inputChar == 'u') {
					destinationGateway = true;
					yourCallsign = "_______U";

					log.info("UR set to reflector unlink = " + yourCallsign);
				}
				else if(inputChar == '1') {
					destinationGateway = true;
					yourCallsign = "XLX380ZL";

					log.info("Set to reflector link " + yourCallsign);
				}
				else if(inputChar == '2') {
					destinationGateway = true;
					yourCallsign = "XLX380BL";

					log.info("Set to reflector link " + yourCallsign);
				}
				else if(inputChar == '3') {
					destinationGateway = true;
					yourCallsign = "XLX380DL";

					log.info("Set to reflector link " + yourCallsign);
				}
				else if(inputChar == 'r') {
					destinationGateway = true;
					yourCallsign = DSTARDefines.CQCQCQ;

					log.info("UR set to use reflector = " + yourCallsign + "(USE GATEWAY:ON)");
				}
				else if(inputChar == 'e' && config != null) {
					log.info("Change request echoback to " + !config.isEchoback());

					noravrClient.changeEcho(!config.isEchoback());
				}
				else if(inputChar == 'q') {
					transmit(false);

					break;
				}
			}while(inputChar >= 0);
		}finally {
			noravrClient.disconnect();

			speaker.interrupt();
			try {
				speaker.join();
			} catch (InterruptedException ex) {}

			audio.close();
		}

		return 0;
	}

	private void transmit(final boolean start) {
		if(!transmitting && start) {
			transmitStart = true;
			transmitEnd = false;
			transmitting = true;

			audio.openCapture(captureMixerName);
			audio.startCapture();
		}
		else if(transmitting && !start) {
			transmitStart = false;
			transmitEnd = true;
			transmitting = true;
		}
		else {
			transmitStart = false;
			transmitEnd = false;
			transmitting = false;

			audio.stopCapture();
			audio.closeCapture();
		}
	}

	public static void main(String[] args) {

		InputStream logConfig = null;
		try {
			logConfig = NoraVRClientSample.class.getClassLoader().getResourceAsStream("logback_stdconsole.xml");

			if (!LogbackUtil.initializeLogger(logConfig, true))
				log.warn("Could not debug log configuration !");
		} finally {
			try {
				logConfig.close();
			} catch (IOException ex) {
			}
		}

		int result = 0;
		try {
			final NoraVRClientSample app = new NoraVRClientSample();

			result = app.process();
		}catch(Exception ex) {
			if(log.isErrorEnabled())
				log.error("Uncaught application error !", ex);

			result = -1;
		}

		System.exit(result);
	}
}
