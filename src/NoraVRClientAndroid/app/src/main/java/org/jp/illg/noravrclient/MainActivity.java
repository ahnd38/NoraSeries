package org.jp.illg.noravrclient;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.noravrclient.model.NoraVRClientConfig;
import org.jp.illg.noravrclient.util.AlertDialogFragment;
import org.jp.illg.noravrclient.util.DialogFragmentBase;
import org.jp.illg.noravrclient.util.RegexInputFilter;
import org.jp.illg.util.android.pttutil.PTTDetectService;
import org.jp.illg.util.android.pttutil.PTTDetector;
import org.jp.illg.util.android.pttutil.PTTState;
import org.jp.illg.util.android.pttutil.PTTType;
import org.parceler.Parcels;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Bundler;
import icepick.Icepick;
import icepick.State;
import in.shadowfax.proswipebutton.ProSwipeButton;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.primitives.Shorts.max;
import static org.jp.illg.noravrclient.NoraVRClientDefine.*;
import static org.jp.illg.noravrclient.NoraVRClientUtil.*;

@Slf4j
public class MainActivity extends AppCompatActivity implements DialogFragmentBase.Callback {
	
	private static final int LastHeardListLimitPortrait = 10;
	private static final int LastHeardListLimitLandscape = 5;
	
	private static final int PermissionRequestCode = 0x5790;
	
	private final int IDDIAG_CONFIG = 0x342108;
	private final int IDDIAG_TRANSMITHISTORY = 0xdfa4326;
	private final int IDDIAG_GPSLOCATION = 0x6a5436d;
	private final int IDDIAG_ALART = 0x342109;
	
	private static final int TRANSMIT_TIMELIMIT_SECONDS = 180;
	
	private enum ViewState {
		Initialize,
		Connecting,
		Connected,
		Disconnected,
		ConnectionFailed,
	}
	
	private final String configFileName = "NoraVRClientConfig.json";
	
	@BindView(R.id.buttonMainConnect)
	Button buttonConnect;
	
	@BindView(R.id.buttonConfig)
	Button buttonConfig;
	
	@BindView(R.id.textViewMainStatus)
	TextView textViewMainStatus;
	
	@BindView(R.id.buttonMainRXCS)
	Button buttonMainRXCS;
	
	@BindView(R.id.editTextMainYourCallsign)
	EditText editTextYourCallsign;
	
	@BindView(R.id.switchMainYourCallsignCQ)
	Switch switchMainYourCallsignCQ;
	
	@BindView(R.id.buttonMainHistory)
	Button buttonMainHistory;
	
	@BindView(R.id.switchMainUseGateway)
	Switch switchMainUseGateway;
	
	@BindView(R.id.imageviewMain)
	ImageView imageviewMain;
	
	@BindView(R.id.buttonMainLastHeard)
	Button buttonMainLastHeard;
	
	@BindView(R.id.textViewMainReceiveStatus)
	TextView textViewMainReceiveStatus;
	
	@BindView(R.id.switchMainEchoback)
	Switch switchMainEchoback;
	
	@BindView(R.id.textViewMainLinkedReflector)
	TextView textViewMainLinkedReflector;
	
	@BindView(R.id.seekBarMainMicGain)
	SeekBar seekBarMainMicGain;
	
	@BindView(R.id.textViewMainMicGainCurrent)
	TextView textViewMainMicGainCurrent;
	
	@BindView(R.id.switchMainEnableMicAGC)
	Switch switchMainEnableMicAGC;
	
	@BindView(R.id.progressBarMicLevel)
	ProgressBar progressBarMicLevel;
	
	@BindView(R.id.progressBarSpeakerLevel)
	ProgressBar progressBarSpeakerLevel;
	
	@BindView(R.id.swipeButtonPTT)
	ProSwipeButton swipeButtonPTT;
	
	@BindView(R.id.textViewMainApplicationVersion)
	TextView textViewMainApplicationVersion;
	
	@BindView(R.id.textViewMainRemainingTime)
	TextView textViewMainRemainingTime;
	
	private NoraVRClientConfig config = new NoraVRClientConfig();
	
	@State
	boolean enableEchoback;
	
	@State
	long lastReceiveActivityTime;
	
	@State
	long lastMicActivityTime;
	
	@State
	String linkedReflectorCallsign;
	
	@State
	int remainingSeconds;
	
	@State
	String captureCallsign;
	
	@State(TransmitCallsignHistoryBundler.class)
	Deque<String> transmitCallsignHistory;
	
	@State(LastHeardListBundler.class)
	Deque<HeardEntry> lastHeardList;
	
	@State
	int lastHeardListLimit;
	
	@State
	boolean disableAudioRecord;
	
	@State
	boolean disableAccessFineLocation;
	
	@State
	int gpsLocationShowingFrameID;
	
	private Timer remainingTimer;
	private final Handler handler;
	
	@State
	int currentSpeakerLevel;
	
	@State
	int currentMicLevel;
	
	@State
	boolean transmitting;
	
	boolean paused;
	boolean configDialogShowing;
	boolean connected;
	
	private PTTDetectService pttDetectService;
	private boolean pttDetectServiceBound;
	
	private final BroadcastReceiver pttBroadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final PTTType pttType =
					PTTType.getTypeByName(
						intent.getStringExtra(PTTDetector.EXTRA_PTT_TYPE)
					);
			final PTTState pttState =
					PTTState.getTypeByName(
							intent.getStringExtra(PTTDetector.EXTRA_PTT_STATE)
					);
			
			onPTTDetected(pttType, pttState);
		}
	};
	
	public static class TransmitCallsignHistoryBundler implements Bundler<Deque<String>> {
		public void put(String key, Deque<String> value, Bundle bundle) {
            bundle.putStringArray(key, value.toArray(new String[0]));
		}
		
		public Deque<String> get(String key, Bundle bundle) {
			final Deque<String> result = new LinkedList<>();
			
			final String[] history = bundle.getStringArray(key);
			if(history != null) {result.addAll(Arrays.asList(history));}
			
			return result;
		}
	}
	
	public static class LastHeardListBundler implements Bundler<Deque<HeardEntry>> {
		public void put(String key, Deque<HeardEntry> value, Bundle bundle) {
			final Parcelable[] entries = new Parcelable[value.size()];
			int i = 0;
			for(final HeardEntry entry : value)
				entries[i++] = Parcels.wrap(entry);
			
			bundle.putParcelableArray(key, entries);
		}
		
		public Deque<HeardEntry> get(String key, Bundle bundle) {
			final Deque<HeardEntry> result = new LinkedList<>();
			
			final Parcelable[] parcelableEntries = bundle.getParcelableArray(key);
			if(parcelableEntries != null && parcelableEntries.length >= 1){
				final HeardEntry[] entries = new HeardEntry[parcelableEntries.length];
				int i = 0;
				for(final Parcelable entry : parcelableEntries)
					entries[i++] = Parcels.unwrap(entry);
				
				result.addAll(Arrays.asList(entries));
			}
			
			return result;
		}
	}
	
	private final ServiceConnection pttDetectorServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
			PTTDetectService.LocalBinder binder =
					(PTTDetectService.LocalBinder)iBinder;
			pttDetectService = binder.getService();
			pttDetectServiceBound = true;
			NoraVRClientApplication.setPttDetectService(pttDetectService);
			
			final PTTType pttType = PTTType.getTypeByName(config.getExternalPTTType());
			if(pttDetectService != null) {
				pttDetectService.setPTTToggleMode(config.isPttToggleMode());
				pttDetectService.setPTTType(pttType, config.getExternalPTTKeycode());
			}
		}
		
		@Override
		public void onServiceDisconnected(ComponentName componentName) {
			pttDetectServiceBound = false;
			NoraVRClientApplication.setPttDetectService(null);
		}
	};
	
	public MainActivity(){
		super();
		
		transmitCallsignHistory = new LinkedList<>();
		
		intentFilter = new IntentFilter();
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_CONNECT));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_DISCONNECT));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_CONNECTIONSTATE_GET));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_MICVOICE));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_CONNECTIONSTATE_CHANGE));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_RECEIVEVOICE));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_TRANSMITVOICE_START));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_TRANSMITVOICE_END));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_TRANSMITVOICE_TIMEOUT));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_CHANGEECHOBACK));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_LINKEDREFLECTOR_CHANGE));
		intentFilter.addAction(String.valueOf(MSG_RESPONSE_LASTHEARDLIST));
		intentFilter.addAction(String.valueOf(MSG_NOTIFY_LASTHEARDLIST_CHANGE));
		
		handler = new Handler();
		
		lastReceiveActivityTime = 0;
		enableEchoback = false;
		paused = false;
		configDialogShowing = false;
		connected = false;
		linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		remainingSeconds = TRANSMIT_TIMELIMIT_SECONDS;
		
		captureCallsign = "";
		
		lastHeardList = new LinkedList<>();
		
		disableAccessFineLocation = false;
		disableAudioRecord = false;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onCreate()");
		
		ButterKnife.bind(this);
		
		if(isLandscape())
			lastHeardListLimit = LastHeardListLimitLandscape;
		else
			lastHeardListLimit = LastHeardListLimitPortrait;
		
		textViewMainApplicationVersion.setText(String.format("Ver.%S", BuildConfig.VERSION_NAME));
		
		buttonConfig.setOnClickListener(buttonConfigOnClickListener);
		buttonConnect.setOnClickListener(buttonConnectOnClickListener);
		buttonMainRXCS.setOnClickListener(buttonMainRXCSOnClickListener);
		editTextYourCallsign.addTextChangedListener(editTextYourCallsignTextWatcher);
		editTextYourCallsign.setFilters(
				new InputFilter[]{
						new InputFilter.AllCaps(),
						new InputFilter.LengthFilter(8),
						new RegexInputFilter("^[A-Z0-9_/ ]+$")
				});
		switchMainYourCallsignCQ.setOnCheckedChangeListener(switchMainYourCallsignCQOnCheckedChangeListener);
		buttonMainHistory.setOnClickListener(buttonMainHistoryOnClickListener);
		switchMainUseGateway.setOnCheckedChangeListener(switchMainUseGatewayOnCheckedChangeListener);
		switchMainEchoback.setOnCheckedChangeListener(switchMainEchobackOnCheckedChangeListener);
		swipeButtonPTT.setOnSwipeListener(swipeButtonPTTOnSwipeListener);
		swipeButtonPTT.setOnClickListener(swipeButtonPTTOnClickListener);
		seekBarMainMicGain.setOnSeekBarChangeListener(seekBarMainMicGainOnSeekBarChangeListener);
		switchMainEnableMicAGC.setOnCheckedChangeListener(switchMainEnableMicAGCOnCheckedChangeListener);

		progressBarMicLevel.setMax(0x7FFF);
		progressBarSpeakerLevel.setMax(0x7FFF);
		seekBarMainMicGain.setMax(600);
		
		getLBM(getApplicationContext()).registerReceiver(broadcastReceiver, intentFilter);
		
		final List<String> permissionRequests = new ArrayList<>();
		
		if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO) !=
				PackageManager.PERMISSION_GRANTED
		){permissionRequests.add(Manifest.permission.RECORD_AUDIO);}
		if(ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.ACCESS_FINE_LOCATION) !=
				PackageManager.PERMISSION_GRANTED
		){permissionRequests.add(Manifest.permission.ACCESS_FINE_LOCATION);}
		
		if(!permissionRequests.isEmpty()){
			ActivityCompat.requestPermissions(
					this,
					permissionRequests.toArray(new String[0]),
					PermissionRequestCode
			);
		}
		
		setViews(ViewState.Initialize);
		
		final Handler handler = new Handler();
		final Runnable speakerTask = new Runnable() {
			@Override
			public void run() {
				if(
						(lastReceiveActivityTime + TimeUnit.MILLISECONDS.toNanos(500))
						<= System.nanoTime()
				) {
					lastReceiveActivityTime = System.nanoTime();
					
					textViewMainReceiveStatus.setText("");
					
					currentSpeakerLevel = 0;
				}
				
				handler.postDelayed(this, 100);
			}
		};
		handler.post(speakerTask);
		
		final Runnable micTask = new Runnable() {
			@Override
			public void run() {
				if(
						(lastMicActivityTime + TimeUnit.MILLISECONDS.toNanos(200))
								<= System.nanoTime()
				) {
					lastMicActivityTime = System.nanoTime();
					
					currentMicLevel = 0;
				}
				
				
				handler.postDelayed(this, 100);
			}
		};
		handler.post(micTask);
		
		final Runnable micSpeakerProgressTask =
				new Runnable() {
					@Override
					public void run() {
						processAudioProgress(progressBarMicLevel, currentMicLevel);
						
						processAudioProgress(progressBarSpeakerLevel, currentSpeakerLevel);
						
						handler.postDelayed(this, 10);
					}
				};
		handler.post(micSpeakerProgressTask);
		
//		startService(new Intent(this, PTTDetectService.class));
		bindService(
				new Intent(this, PTTDetectService.class),
				pttDetectorServiceConnection, Context.BIND_AUTO_CREATE
		);
	}
	
	@Override
	protected void onStart(){
		super.onStart();
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onStart()");
		
		readConfig();
		
		final PTTType pttType = PTTType.getTypeByName(config.getExternalPTTType());
		if(pttDetectService != null) {
			pttDetectService.setPTTType(pttType, config.getExternalPTTKeycode());
		}
		swipeButtonPTT.setDisableSwipe(config.isTouchPTTTransmit());
	}
	
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState){
		Icepick.restoreInstanceState(this, savedInstanceState);
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onRestoreInstanceState()");
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		
		paused = false;
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onResume()");
		
		if(config.isYourCallsignCQ())
			editTextYourCallsign.setText("CQCQCQ");
		else
			editTextYourCallsign.setText(config.getYourCallsign());
		
		switchMainUseGateway.setChecked(config.isUseGateway());
		switchMainEchoback.setChecked(enableEchoback);
		switchMainYourCallsignCQ.setChecked(config.isYourCallsignCQ());
		
		final Intent msg = new Intent(String.valueOf(MSG_REQUEST_CONNECTIONSTATE_GET));
		sendMessageToHost(getApplicationContext(), msg);
		
//		progressBarMicLevel.setSecondaryProgress(0x3FFF);
		
		seekBarMainMicGain.setProgress(
				300 + (config.getMicGain() != 0.0D ? (int)(config.getMicGain() * 10.0D) : 0)
		);
		textViewMainMicGainCurrent.setText(
				String.format(Locale.getDefault(),"%+.1fdB", config.getMicGain())
		);
		switchMainEnableMicAGC.setChecked(config.isEnableMicAGC());
		
		disableDisplaySleep(config.isDisableDisplaySleep());
		
		transmitCallsignHistory.clear();
		if(config.getTransmitCallsignHistory() != null)
			transmitCallsignHistory.addAll(Arrays.asList(config.getTransmitCallsignHistory()));
		
		final Intent requestLastHeard = new Intent(String.valueOf(MSG_REQUEST_LASTHEARDLIST));
		sendMessageToHost(getApplicationContext(), requestLastHeard);
		
		final IntentFilter pttIntentFilter =
				new IntentFilter(PTTDetector.PTT_DETECTED);
		getLBM(getApplicationContext()).registerReceiver(pttBroadcastReceiver, pttIntentFilter);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onSaveInstanceState()");
		
		Icepick.saveInstanceState(this, outState);
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onPause()");
		
		paused = true;
		
		getLBM(getApplicationContext()).unregisterReceiver(pttBroadcastReceiver);
		
		if(remainingTimer != null)
			remainingTimer.cancel();
		
		final Intent msg =
		new Intent(String.valueOf(MSG_REQUEST_TRANSMITVOICE_END));
		sendMessageToHost(getApplicationContext(), msg);
		
		disableDisplaySleep(false);
	}
	
	@Override
	protected void onStop() {
		super.onStop();
		
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onStop()");
		
		saveConfig();
	}
	
	@Override
	protected void onDestroy(){
		if(log.isTraceEnabled())
			log.trace(MainActivity.class.getSimpleName() + "." + "onDestroy()");
		
		unbindService(pttDetectorServiceConnection);
		
		getLBM(getApplicationContext()).unregisterReceiver(broadcastReceiver);
		
		super.onDestroy();
	}
	
	@Override
	public void onRequestPermissionsResult(
			int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults
	) {
		switch(requestCode){
			case PermissionRequestCode:
				for(int i = 0; i < permissions.length && i < grantResults.length; i++){
					final boolean granted =
							grantResults[i] == PackageManager.PERMISSION_GRANTED;
					final String permission = permissions[i];
					
					switch(permission){
						case Manifest.permission.RECORD_AUDIO:
							if(!granted) {
								disableAudioRecord = true;
							}
							break;
							
						case Manifest.permission.ACCESS_FINE_LOCATION:
							if(!granted) {
								disableAccessFineLocation = true;
							}
							break;
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	@Override
	public void onDialogResult(int requestCode, int resultCode, @Nullable Intent data) {
		configDialogShowing = false;
		
		switch (requestCode){
			case IDDIAG_CONFIG:
					if(data != null){
						config.setServerAddress(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_SERVERADDRESS)
						);
						config.setServerPort(
								data.getIntExtra(NoraVRClientConfigDialog.IDEXT_SERVERPORT, -1)
						);
						config.setLoginCallsign(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_LOGINCALLSIGN)
						);
						config.setLoginPassword(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_LOGINPASSWORD)
						);
						config.setCodecType(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_CODECTYPE)
						);
						config.setMyCallsign(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_MYCALLSIGNLONG)
						);
						config.setMyCallsignShort(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_MYCALLSIGNSHORT)
						);
						config.setEnableTransmitShortMessage(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_ENABLESHORTMESSAGE, false)
						);
						config.setShortMessage(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_SHORTMESSAGE)
						);
						config.setEnableTransmitGPS(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_ENABLETRANSMITGPS, false)
						);
						config.setEnablePlayBeepReceiveStart(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_ENABLEBEEPONRECEIVESTART, false)
						);
						config.setEnablePlayBeepReceiveEnd(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_ENABLEBEEPONRECEIVEEND, false)
						);
						config.setEnableGPSLocationPopup(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_ENABLEGPSLOCATIONPOPUP, true)
						);
						config.setDisableDisplaySleep(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_DISABLEDISPLAYSLEEP, false)
						);
						config.setExternalPTTType(
								data.getStringExtra(NoraVRClientConfigDialog.IDEXT_EXTERNALPTTTYPE)
						);
						config.setExternalPTTKeycode(
								data.getIntExtra(NoraVRClientConfigDialog.IDEXT_EXTERNALPTTKEYCODE, 0)
						);
						config.setTouchPTTTransmit(
								data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_TOUCHPTTTRANSMIT, false)
						);
						config.setPttToggleMode(
							data.getBooleanExtra(NoraVRClientConfigDialog.IDEXT_PTTTOGGLEMODE, false)
						);
						
						saveConfig();
						
						disableDisplaySleep(config.isDisableDisplaySleep());

						final PTTType pttType =
								PTTType.getTypeByName(config.getExternalPTTType());
						if(pttDetectService != null) {
							pttDetectService.setPTTType(pttType, config.getExternalPTTKeycode());
							pttDetectService.setPTTToggleMode(config.isPttToggleMode());
						}
						
						swipeButtonPTT.setDisableSwipe(config.isTouchPTTTransmit());
						
						sendConfigToService();
					}
				break;
				
			case IDDIAG_TRANSMITHISTORY:
				if(data != null){
					final String yourCallsign =
							data.getStringExtra(NoraVRYourCallsignSelectorDialog.IDEXT_SELECTEDCALLSIGN);
					if(yourCallsign != null) {
						MainActivity.this.switchMainYourCallsignCQ.setChecked(false);
						
						MainActivity.this.editTextYourCallsign.setText(yourCallsign);
						config.setYourCallsign(yourCallsign);
					}
				}
				break;
				
			case IDDIAG_GPSLOCATION:
				break;
				
			default:
				break;
		}
	}
	
	@Override
	public void onDialogCancelled(int requestCode) {
		configDialogShowing = false;
	}
	
	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		if(log.isTraceEnabled())
			log.trace("dispatchKeyEvent() event = " + event);
		
		if(event != null && pttDetectService != null)
			pttDetectService.receiveKeyEvent(event);
		
		if(event == null || event.getKeyCode() != config.getExternalPTTKeycode())
			return super.dispatchKeyEvent(event);
		else
			return true;
	}

	private final Button.OnClickListener buttonConfigOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			configDialogShowing = true;
			
			new NoraVRClientConfigDialog.Builder()
				.setServerAddress(config.getServerAddress())
				.setServerPort(config.getServerPort())
				.setLoginCallsign(config.getLoginCallsign())
				.setLoginPassword(config.getLoginPassword())
				.setCodecType(NoraVRCodecType.getTypeByTypeName(config.getCodecType()))
				.setMyCallsignLong(config.getMyCallsign())
				.setMyCallsignShort(config.getMyCallsignShort())
				.setEnableTransmitShortMessage(config.isEnableTransmitShortMessage())
				.setShortMessage(config.getShortMessage())
				.setEnableTransmitGPS(config.isEnableTransmitGPS())
				.setEnableBeepOnReceiveStart(config.isEnablePlayBeepReceiveStart())
				.setEnableBeepOnReceiveEnd(config.isEnablePlayBeepReceiveEnd())
				.setEnableGPSLocationPopup(config.isEnableGPSLocationPopup())
				.setDisableDisplaySleep(config.isDisableDisplaySleep())
				.setExternalPTTType(config.getExternalPTTType())
				.setExternalPTTKeyCode(config.getExternalPTTKeycode())
				.setTouchPTTTransmit(config.isTouchPTTTransmit())
				.setPTTToggleMode(config.isPttToggleMode())
				.build(IDDIAG_CONFIG)
				.showOn(MainActivity.this, MainActivity.class.getSimpleName());
		}
	};
	
	private final Button.OnClickListener buttonConnectOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			if(!NoraVRClientService.isServiceStarted()) {
				final Intent intent =
						new Intent(getApplicationContext(), NoraVRClientService.class);
				intent.putExtra(ID_SERVERADDRESS, config.getServerAddress());
				intent.putExtra(ID_SERVERPORT, config.getServerPort());
				intent.putExtra(ID_LOGINUSER, config.getLoginCallsign());
				intent.putExtra(ID_LOGINPASSWORD, config.getLoginPassword());
				intent.putExtra(ID_CODECTYPE, config.getCodecType());
				putConfig(intent);
				
				if(Build.VERSION.SDK_INT  >= Build.VERSION_CODES.O)
					startForegroundService(intent);
				else
					startService(intent);
				
			}
			else{
				Intent msg =
						new Intent(String.valueOf(MSG_REQUEST_TRANSMITVOICE_END));
				sendMessageToHost(getApplicationContext(), msg);
				
				msg =
						new Intent(String.valueOf(MSG_REQUEST_DISCONNECT));
				
				sendMessageToHost(getApplicationContext(), msg);
			}
			
			view.setEnabled(false);
		}
	};
	
	private final Button.OnClickListener buttonMainHistoryOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					new NoraVRYourCallsignSelectorDialog.Builder()
							.setHistoryCallsigns(transmitCallsignHistory.toArray(new String[0]))
							.build(IDDIAG_TRANSMITHISTORY)
							.showOn(MainActivity.this, MainActivity.class.getSimpleName());
				}
			};
	
	private final TextWatcher editTextYourCallsignTextWatcher = new TextWatcher() {
		@Override
		public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

		}
		
		@Override
		public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
		
		}
		
		@Override
		public void afterTextChanged(Editable editable) {
			config.setYourCallsign(editable.toString().trim());
		}
	};
	
	private final Button.OnClickListener buttonMainRXCSOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					if(
							CallSignValidator.isValidUserCallsign(
								DSTARUtils.formatFullLengthCallsign(captureCallsign)
							)
					){
						switchMainYourCallsignCQ.setChecked(false);
						
						editTextYourCallsign.setText(captureCallsign.trim());
						config.setYourCallsign(captureCallsign.trim());
					}
				}
			};
	
	private final Switch.OnCheckedChangeListener switchMainYourCallsignCQOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
					if(b){
						editTextYourCallsign.removeTextChangedListener(editTextYourCallsignTextWatcher);
						editTextYourCallsign.setText(NoraVRClientDefine.CQCQCQ);
					}
					else{
						editTextYourCallsign.setText(config.getYourCallsign());
						editTextYourCallsign.addTextChangedListener(editTextYourCallsignTextWatcher);
					}
					
					editTextYourCallsign.setEnabled(!b);
					config.setYourCallsignCQ(b);
				}
			};
	
	private final Switch.OnCheckedChangeListener switchMainUseGatewayOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
					config.setUseGateway(b);
				}
			};
	
	private final Switch.OnCheckedChangeListener switchMainEchobackOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
					enableEchoback = b;
					
					final Intent msg = new Intent(String.valueOf(MSG_REQUEST_CHANGEECHOBACK));
					msg.putExtra(ID_ECHOBACK, enableEchoback);
					sendMessageToHost(getApplicationContext(), msg);
				}
			};
	
	private final View.OnClickListener swipeButtonPTTOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					if(transmitting){
						transmitEnd();
						pttDetectService.resetState();
					}
					else if(config.isTouchPTTTransmit()){
						transmitStart();
					}
				}
			};
	
	private final ProSwipeButton.OnSwipeListener swipeButtonPTTOnSwipeListener =
			new ProSwipeButton.OnSwipeListener() {
				@Override
				public void onSwipeConfirm() {
					transmitStart();
				}
			};
	
	private final SeekBar.OnSeekBarChangeListener seekBarMainMicGainOnSeekBarChangeListener =
			new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if(fromUser){
						final double value =
								progress != 300 ? (double)(progress - 300) / 10.0D : 0.0D;
						config.setMicGain(value);
						
						textViewMainMicGainCurrent.setText(
								String.format(Locale.getDefault(),"%+.1fdB", value)
						);
						
						sendConfigToService();
					}

				}
				
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
				
				}
				
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
				
				}
			};
	
	private final Switch.OnCheckedChangeListener switchMainEnableMicAGCOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
					config.setEnableMicAGC(b);
					
					sendConfigToService();
				}
			};
	
	private final BroadcastReceiver broadcastReceiver =
			new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					switch (Integer.valueOf(intent.getAction())){
						case MSG_RESPONSE_CONNECT:
							onReceiveResponseConnect(intent);
							break;
							
						case MSG_RESPONSE_DISCONNECT:
							onReceiveResponseDisconnect(intent);
							break;
							
						case MSG_RESPONSE_TRANSMITVOICE_START:
							onReceiveResponseTransmitStart(intent);
							break;
							
						case MSG_RESPONSE_TRANSMITVOICE_END:
							onReceiveResponseTransmitEnd(intent);
							break;
							
						case MSG_NOTIFY_MICVOICE:
							onReceiveNotifyMicVoice(intent);
							break;
							
						case MSG_NOTIFY_RECEIVEVOICE:
							onReceiveNotifyReceiveVoice(intent);
							break;
							
						case MSG_RESPONSE_CONNECTIONSTATE_GET:
						case MSG_NOTIFY_CONNECTIONSTATE_CHANGE:
							onReceiveResponseConnectionStateGet(intent);
							break;
							
						case MSG_NOTIFY_LINKEDREFLECTOR_CHANGE:
							onReceiveNotifyLinkedReflectorChange(intent);
							break;
							
						case MSG_NOTIFY_TRANSMITVOICE_TIMEOUT:
							onReceiveNotifyTransmitTimeout(intent);
							break;
						
						case MSG_NOTIFY_LASTHEARDLIST_CHANGE:
						case MSG_RESPONSE_LASTHEARDLIST:
							onReceiveResponseLastHeardList(intent);
							break;
							
						default:
							break;
					}
				}
			};
	private final IntentFilter intentFilter;
	
	private void onReceiveResponseConnect(final Intent intent){
		connected = true;
		
		setViews(ViewState.Connecting);
		
		buttonConnect.setEnabled(true);
	}
	
	private void onReceiveResponseDisconnect(final Intent intent){
		connected = false;
		
		setViews(ViewState.Disconnected);
		
		buttonConnect.setEnabled(true);
	}
	
	private void onReceiveResponseTransmitStart(final Intent intent){
		if(remainingTimer != null)
			remainingTimer.cancel();
		
		remainingTimer = new Timer();
		
		remainingSeconds = TRANSMIT_TIMELIMIT_SECONDS;
		
		remainingTimer.schedule(
				new TimerTask() {
					@Override
					public void run() {
						handler.post(new Runnable() {
							@Override
							public void run() {
								textViewMainRemainingTime.setText(
										String.valueOf(remainingSeconds)
								);
								
								if(remainingSeconds <= 0)
									remainingTimer.cancel();
								else
									remainingSeconds--;
							}
						});
					}
				},
				0, 1000
		);
		
		switchMainEnableMicAGC.setEnabled(false);
		
		imageviewMain.setImageResource(R.drawable.img_transmitting);
		
		lockScreenOrientation();
	}
	
	private void onReceiveResponseTransmitEnd(final Intent intent){
		swipeButtonPTT.showResultIcon(false);
		
		if(remainingTimer != null)
			remainingTimer.cancel();
		
		textViewMainRemainingTime.setText("");
		
		switchMainEnableMicAGC.setEnabled(true);
		
		imageviewMain.setImageResource(R.drawable.img_standby);
		
		unlockScreenOrientation();
	}
	
	private void onReceiveNotifyMicVoice(final Intent intent){
		final short[] voice =
				intent.getShortArrayExtra(ID_MICVOICE);
		for(int i = 0; i < voice.length; i++) {
			if(voice[i] < 0){voice[i] = (short)-voice[i];}
		}
		final short max = max(voice);
		
		currentMicLevel = max;
		lastMicActivityTime = System.nanoTime();
	}
	
	private void onReceiveNotifyReceiveVoice(final Intent intent){
		final short[] voice =
				intent.getShortArrayExtra(ID_RECEIVEVOICE);
		for(int i = 0; i < voice.length; i++) {
			if(voice[i] < 0){voice[i] = (short)-voice[i];}
		}
		final short max = max(voice);
		
		final int frameID = intent.getIntExtra(ID_FRAMEID, 0x0);
		final String myCallsignLong =
				intent.getStringExtra(ID_MYCALLSIGN_LONG);
		final String myCallsignShort =
				intent.getStringExtra(ID_MYCALLSIGN_SHORT);
		final String yourCallsign =
				intent.getStringExtra(ID_YOURCALLSIGN);
		final String repeater1Callsign =
				intent.getStringExtra(ID_RPT1CALLSIGN);
		final String repeater2Callsign =
				intent.getStringExtra(ID_RPT2CALLSIGN);
		final byte[] flags =
				intent.getByteArrayExtra(ID_DV_FLAGS);
		final boolean frameStart =
				intent.getBooleanExtra(ID_FRAMESTART, false);
		final boolean frameEnd =
				intent.getBooleanExtra(ID_FRAMEEND, false);
		final String shortMessage =
				intent.getStringExtra(ID_SHORTMESSAGE);
		final double latitude =
				intent.getDoubleExtra(ID_LATITUDE, 0.0D);
		final double longitude =
				intent.getDoubleExtra(ID_LONGITUDE, 0.0D);
		final boolean gpsLocationReceived =
				intent.getBooleanExtra(ID_GPSLOCATION_RECEIVED, false);
		
		if(frameStart) {
			boolean isNULL = false;
			boolean isUR = false;
			boolean isRPT = false;
			if (
				yourCallsign != null && config.getMyCallsign() != null &&
					flags != null && flags.length == 3
			) {
				isNULL = (flags[0] & 0x7) == 0x0;
				
				final String yourCallFormated = String.format("%-8S", yourCallsign);
				final String myCallFormated = String.format("%-8S", config.getMyCallsign());
				
				if (yourCallFormated.equals(myCallFormated)) {
					isUR = (flags[0] & 0x7) == 0x2;
					isRPT = (flags[0] & 0x7) == 0x1;
				}
			}
			
			if (isUR)
				textViewMainRemainingTime.setText("UR?");
			else if (isRPT)
				textViewMainRemainingTime.setText("RPT?");
			else
				textViewMainRemainingTime.setText("");
			
			
			if (isNULL)
				captureCallsign = myCallsignLong.trim();
		}
		
		if(frameEnd)
			imageviewMain.setImageResource(R.drawable.img_standby);
		else
			imageviewMain.setImageResource(R.drawable.img_receiving);
		
		if(gpsLocationShowingFrameID != frameID && gpsLocationReceived){
			gpsLocationShowingFrameID = frameID;
			
			if(!paused && config.isEnableGPSLocationPopup()) {
				new NoraVRReceiveGPSLocationDialog.Builder()
						.setCallsign(myCallsignLong + " " + myCallsignShort)
						.setMessage(shortMessage)
						.setLatitude(String.valueOf(latitude))
						.setLongitude(String.valueOf(longitude))
						.build(IDDIAG_GPSLOCATION)
						.showOn(this, MainActivity.class.getSimpleName());
			}
		}
		
		final String receiveMessage =
				"UR:" + yourCallsign + "/" + "MY:" + myCallsignLong + "_" + myCallsignShort +
				(shortMessage != null && !"".equals(shortMessage) ? "\n" + shortMessage : "");
		textViewMainReceiveStatus.setText(receiveMessage);
		
		currentSpeakerLevel = max;
		lastReceiveActivityTime = System.nanoTime();
	}
	
	private void onReceiveResponseConnectionStateGet(final Intent intent){
		final int connectionState =
				intent.getIntExtra(ID_CONNECTION_STATE, CONNECTIONSTATE_UNKNOWN);
		final String connectionReason =
				intent.getStringExtra(ID_CONNECTION_REASON);
		final String linkedReflectorCallsign =
				intent.getStringExtra(ID_REFLECTORCALLSIGN);
		
		switch(connectionState){
			case CONNECTIONSTATE_UNKNOWN:
				setViews(ViewState.ConnectionFailed, "???");
				break;
				
			case CONNECTIONSTATE_SUCCESS:
				connected = true;
				
				setViews(ViewState.Connected);
				break;
				
			case CONNECTIONSTATE_LOGINFAILED:
			case CONNECTIONSTATE_CONNECTIONFAILED:
				connected = false;
				
				final String messageTitle =
						connectionState == CONNECTIONSTATE_LOGINFAILED ?
								"LOGIN FAILED" : "CONNECTION FAILED";
				
				setViews(ViewState.ConnectionFailed, messageTitle);
				
				if(!paused) {
					new AlertDialogFragment.Builder()
							.setTitle(messageTitle)
							.setMessage(connectionReason)
							.setPositiveButton("OK")
							.setCancelable(false)
							.build(IDDIAG_ALART)
							.showOn(this, MainActivity.class.getSimpleName());
				}

				break;
		}
		
		this.linkedReflectorCallsign = linkedReflectorCallsign;
		setViewLinkedReflector(linkedReflectorCallsign);
	}
	
	private void onReceiveNotifyLinkedReflectorChange(final Intent intent){
		final String linkedReflector =
				intent.getStringExtra(ID_REFLECTORCALLSIGN);
		
		if(linkedReflector != null && !"        ".equals(linkedReflector))
			linkedReflectorCallsign = linkedReflector;
		else
			linkedReflectorCallsign = DSTARDefines.EmptyLongCallsign;
		
		setViewLinkedReflector(linkedReflectorCallsign);
	}
	
	private void onReceiveNotifyTransmitTimeout(final Intent intent){
		final Intent msg =
				new Intent(String.valueOf(MSG_REQUEST_TRANSMITVOICE_END));
		sendMessageToHost(getApplicationContext(), msg);
		
		disableDisplaySleep(config.isDisableDisplaySleep());
		
		if(!paused) {
			new AlertDialogFragment.Builder()
					.setTitle("PTT TIMEOUT")
					.setMessage("Transmit time exceed.")
					.setPositiveButton("OK")
					.setCancelable(false)
					.build(IDDIAG_ALART)
					.showOn(this, MainActivity.class.getSimpleName());
		}
	}
	
	private void onReceiveResponseLastHeardList(final Intent intent){
		final HeardEntry[] entries =
				(HeardEntry[])intent.getSerializableExtra(ID_HEARDENTRYLIST);
		if(entries == null){return;}
		
		lastHeardList.clear();
		lastHeardList.addAll(Arrays.asList(entries));
		
		updateViewLastHeardList();
	}
	
	private void onPTTDetected(final PTTType pttType, final PTTState pttState) {
		if(log.isTraceEnabled())
			log.trace("onPTTDetected() pttType = " + pttType + "/pttState = " + pttState);
		
		if(!paused && !configDialogShowing && connected) {
			if(pttState == PTTState.UP) {
				transmitEnd();
			}
			else if(pttState == PTTState.DOWN){
				swipeButtonPTT.performOnSwipe();
			}
		}
	}
	
	private void setViews(final ViewState state){
		setViews(state, null);
	}
	
	private void setViews(final ViewState state, final String message) {
		
		switch(state){
			case Initialize:
				if(message != null)
					textViewMainStatus.setText(message);
				else
					textViewMainStatus.setText("INITIALIZED");
				
				buttonConnect.setText("CONNECT");
				
				linkedReflectorCallsign = null;
				
				switchMainEchoback.setEnabled(false);
				switchMainEchoback.setChecked(false);
				
				swipeButtonPTT.setEnabled(false);
				
				imageviewMain.setImageResource(R.drawable.img_sleep);
				break;
				
			case Connecting:
				if(message != null)
					textViewMainStatus.setText(message);
				else
					textViewMainStatus.setText("CONNECTING...");
				
				buttonConnect.setText("DISCONNECT");
				
				linkedReflectorCallsign = null;
				
				switchMainEchoback.setEnabled(false);
				switchMainEchoback.setChecked(false);
				
				swipeButtonPTT.setEnabled(false);
				
				imageviewMain.setImageResource(R.drawable.img_connecting);
				break;
				
			case Connected:
				if(message != null)
					textViewMainStatus.setText(message);
				else
					textViewMainStatus.setText("CONNECTED");
				
				buttonConnect.setText("DISCONNECT");
				
				switchMainEchoback.setEnabled(true);
				switchMainEchoback.setChecked(false);
				
				swipeButtonPTT.setEnabled(!disableAudioRecord);
				
				imageviewMain.setImageResource(R.drawable.img_standby);
				break;
				
			case Disconnected:
				if(message != null)
					textViewMainStatus.setText(message);
				else
					textViewMainStatus.setText("DISCONNECTED");
				
				buttonConnect.setText("CONNECT");
				
				linkedReflectorCallsign = null;
				
				switchMainEchoback.setEnabled(false);
				switchMainEchoback.setChecked(false);
				
				swipeButtonPTT.setEnabled(false);
				
				imageviewMain.setImageResource(R.drawable.img_sleep);
				break;
				
			case ConnectionFailed:
				if(message != null)
					textViewMainStatus.setText(message);
				else
					textViewMainStatus.setText("CONNECTION FAILED");
				
				buttonConnect.setText("DISCONNECT");
				
				linkedReflectorCallsign = null;
				
				switchMainEchoback.setEnabled(false);
				switchMainEchoback.setChecked(false);
				
				swipeButtonPTT.setEnabled(false);
				
				imageviewMain.setImageResource(R.drawable.img_error);
				break;
		}
		
		setViewLinkedReflector(linkedReflectorCallsign);
		
		textViewMainRemainingTime.setText("");
		
		updateViewLastHeardList();
	}
	
	private void updateViewLastHeardList(){
		StringBuffer sb = new StringBuffer();
		
		final DateFormat df = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
		
		int count = 0;
		for(final HeardEntry entry : lastHeardList) {
			if(count < lastHeardListLimit){
				sb.append(df.format(new Date(entry.heardTime)));
				
				final char repeater1Suffix =
						entry.repeater1Callsign.charAt(entry.repeater1Callsign.length() - 1);
				final char repeater2Suffix =
						entry.repeater2Callsign.charAt(entry.repeater2Callsign.length() - 1);
				
				if(repeater1Suffix == 'G' && repeater2Suffix != 'G'){
					if(
							entry.yourCallsign.equals(
									NoraVRClientUtil.formatCallsignFullLength(config.getMyCallsign())
							)
					)
						sb.append(" [ CALL ]");
					else
						sb.append(" [  GW  ]");
				}
				else if(repeater1Suffix != 'G')
					sb.append(" [LOCAL ]");
				else
					sb.append(" [ ???? ]");
				
				sb.append(" ");
				sb.append(entry.myCallsign);
				sb.append(" ");
				sb.append(entry.myCallsignShort);
				
				/*
				if(isLandscape()){
					sb.append("/");
					sb.append(entry.repeater1Callsign);
					sb.append("/");
					sb.append(entry.repeater2Callsign);
				}
				*/
				
				sb.append("\n");
				
				count++;
			} else {break;}
		}

		//TODO
//		textViewMainLastHeardList.setText(sb.toString());
	}
	
	private void setViewLinkedReflector(final String linkedReflectorCallsign) {
		if(linkedReflectorCallsign != null && !DSTARDefines.EmptyLongCallsign.equals(linkedReflectorCallsign))
			textViewMainLinkedReflector.setText(String.format("%s LINKED", linkedReflectorCallsign));
		else
			textViewMainLinkedReflector.setText("UNLINKED");
	}
	
	private void transmitStart() {
		addTransmitHistory(config.getYourCallsign());
		
		final Intent msg =
				new Intent(String.valueOf(MSG_REQUEST_TRANSMITVOICE_START));
		
		msg.putExtra(
				ID_MYCALLSIGN_LONG,
				NoraVRClientUtil.formatCallsignFullLength(config.getMyCallsign())
		);
		msg.putExtra(
				ID_MYCALLSIGN_SHORT,
				NoraVRClientUtil.formatCallsignShortLength(config.getMyCallsignShort())
		);
		
		if(switchMainYourCallsignCQ.isChecked()) {
			msg.putExtra(
					ID_YOURCALLSIGN,
					NoraVRClientUtil.formatCallsignFullLength(NoraVRClientDefine.CQCQCQ)
			);
		}
		else {
			msg.putExtra(
					ID_YOURCALLSIGN,
					NoraVRClientUtil.formatCallsignFullLength(config.getYourCallsign())
			);
		}
		
		msg.putExtra(ID_USE_GATEWAY, config.isUseGateway());
		
		sendMessageToHost(getApplicationContext(), msg);
		
		disableDisplaySleep(true);
		
		transmitting = true;
	}
	
	private void transmitEnd(){
		final Intent msg =
				new Intent(String.valueOf(MSG_REQUEST_TRANSMITVOICE_END));
		sendMessageToHost(getApplicationContext(), msg);
		
		disableDisplaySleep(config.isDisableDisplaySleep());
		
		transmitting = false;
	}
	
	private boolean saveConfig(){
		final Gson gson = new Gson();
		
		final String json = gson.toJson(config);
		
		boolean success = false;
		
		File applicationConfigFile =
				new File(getApplicationContext().getFilesDir(), configFileName);
		if(applicationConfigFile.exists()){applicationConfigFile.delete();}
		
		FileWriter writer = null;
		try{
			writer = new FileWriter(applicationConfigFile);
			writer.write(json);
			writer.flush();
		}catch (IOException ex){
			log.warn("Could not save application configuration file.", ex);
			success = false;
		}finally {
			try{
				if(writer != null){writer.close();}
			}catch (IOException ex){log.debug("Error occurred at writer close().", ex);}
		}
		
		return success;
	}
	
	private boolean readConfig(){
		Gson gson = new Gson();
		
		File applicationConfigFile =
				new File(getApplicationContext().getFilesDir(), configFileName);
		if(!applicationConfigFile.exists()){return false;}
		
		final StringBuilder sb = new StringBuilder();
		FileReader reader = null;
		try{
			reader = new FileReader(applicationConfigFile);
			int c;
			while((c = reader.read()) != -1){
				sb.append((char)c);
			}
		}catch(IOException ex){
			log.warn("Could not load application configuration file.", ex);
			return false;
		}finally {
			try{
				if(reader != null){reader.close();}
			}catch (IOException ex){log.debug("Error occurred at reader close().", ex);}
		}
		
		log.trace("Loading application config...\n" + sb.toString());
		
		NoraVRClientConfig applicationConfig =null;
		try {
			applicationConfig = gson.fromJson(sb.toString(), NoraVRClientConfig.class);
		}catch (JsonSyntaxException ex){
			log.warn("Could not load application configuration json file.", ex);
		}
		
		config = applicationConfig;
		
		return true;
	}
	
	private void addTransmitHistory(final String callsign) {
		//履歴に追加
		while(transmitCallsignHistory.size() >= 20) {
			transmitCallsignHistory.pollLast();
		}
		
		for(Iterator<String> it = transmitCallsignHistory.iterator(); it.hasNext();){
			final String historyCallsign = it.next();
			
			if(historyCallsign.equals(callsign)) {it.remove();}
		}
		
		transmitCallsignHistory.addFirst(callsign);
		
		config.setTransmitCallsignHistory(
				transmitCallsignHistory.toArray(new String[0])
		);
	}
	
	private boolean isLandscape(){
		return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
	}
	
	private void disableDisplaySleep(final boolean enable) {
		if(enable)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
	}
	
	private void processAudioProgress(
			final ProgressBar progressBar, final int currentLevel
	) {
		
		final int progressCurrent = progressBar.getSecondaryProgress();
		final int levelDiff = progressCurrent - currentLevel;
		
		if(progressCurrent <= currentLevel)
			progressBar.setSecondaryProgress(currentLevel);
		else if(levelDiff >= 100) {
			progressBar.setSecondaryProgress(progressCurrent - 100);
		}
		else
			progressBar.setSecondaryProgress(currentLevel);
		
		progressBar.setProgress(currentLevel);
	}
	
	private void lockScreenOrientation() {
		if(isLandscape())
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		else
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
	}
	
	private void unlockScreenOrientation() {
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
	}
	
	private void sendConfigToService(){
		final Intent intent = new Intent(String.valueOf(MSG_REQUEST_CONFIG_SET));
		putConfig(intent);
		
		sendMessageToHost(getApplicationContext(), intent);
	}
	
	private void putConfig(@NonNull final Intent intent) {
		intent.putExtra(ID_ENABLE_MICAGC, config.isEnableMicAGC());
		intent.putExtra(ID_MICGAIN, config.getMicGain());
		intent.putExtra(ID_ENABLETRANSMITSHORTMESSAGE, config.isEnableTransmitShortMessage());
		intent.putExtra(ID_SHORTMESSAGE, config.getShortMessage());
		intent.putExtra(
				ID_ENABLETRANSMITGPS,
				config.isEnableTransmitGPS() && !disableAccessFineLocation
		);
		intent.putExtra(ID_ENABLEBEEPONRECEIVESTART, config.isEnablePlayBeepReceiveStart());
		intent.putExtra(ID_ENABLEBEEPONRECEIVEEND, config.isEnablePlayBeepReceiveEnd());
		intent.putExtra(ID_DISABLE_AUDIORECORD, disableAudioRecord);
	}
}
