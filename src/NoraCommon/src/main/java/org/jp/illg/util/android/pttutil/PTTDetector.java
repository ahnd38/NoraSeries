package org.jp.illg.util.android.pttutil;

import android.bluetooth.BluetoothAssignedNumbers;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.view.KeyEvent;

import com.itsmartreach.libzm.ZmCmdLink;

import static org.jp.illg.util.android.pttutil.BroadcastUtil.getLBM;

import java.util.Arrays;

import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PTTDetector {
	
	private static final String logTag;
	
	public static final String PTT_DETECTED =
			PTTDetector.class.getName() + "." + "PTT_DETECTED";
	
	public static final String EXTRA_PTT_TYPE =
			PTTDetector.class.getName() + "." + "EXTRA_PTT_TYPE";
	
	public static final String EXTRA_PTT_STATE =
			PTTDetector.class.getName() + "." + "EXTRA_PTT_STATE";
	
	public static final String KEY_DETECTED =
			PTTDetector.class.getName() + "." + "KEY_DETECTED";
	
	public static final String EXTRA_KEY_STATE =
			PTTDetector.class.getName() + "." + "EXTRA_KEY_STATE";
	
	public static final String EXTRA_KEY_TYPE =
			PTTDetector.class.getName() + "." + "EXTRA_KEY_TYPE";
	
	public static final String EXTRA_KEY_CODE =
			PTTDetector.class.getName() + "." + "EXTRA_KEY_CODE";
	
	@Getter
	@Setter
	private int pttKeyCode;
	
	@Getter
	@Setter
	private PTTType pttType;
	
	@Getter
	@Setter
	private boolean toggleMode;
	
	private ZmCmdLink mZmCmdLink;
	
	private boolean toggle;
	
	private final Context context;
	
	private final LocalBroadcastManager broadcastManager;

	private final BroadcastReceiver vendorSpecificHeadsetEventReceiver =
			new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					if(log.isTraceEnabled())
						log.trace("vendorSpecificHeadsetEventReceiver.onReceive() = " + intent);
					
					if(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT.equals(intent.getAction())) {
						final String cmd =
								intent.getStringExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD);
						final int cmdType =
								intent.getIntExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, -1);
						final Object[] cmdArgs =
								(Object[]) intent.getSerializableExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS);
						
						if (log.isTraceEnabled()) {
							log.trace(
								"Event invoked(cmd=" + cmd + "/cmd type=" + cmdType + "/cmdArgs=" + Arrays.toString(cmdArgs) + ")"
							);
						}
						
						final PTTResult pttResult = isDetectPTT(cmdArgs);
						if(pttResult != null && pttResult.isDetected()) {
							final Intent keyIntent = new Intent(KEY_DETECTED);
							keyIntent.putExtra(EXTRA_KEY_TYPE, PTTType.HandMicTypeA.getTypeName());
							keyIntent.putExtra(EXTRA_KEY_CODE, 0);
							keyIntent.putExtra(EXTRA_KEY_STATE, pttResult.getState().getTypeName());
							
							broadcastManager.sendBroadcast(keyIntent);
							
							if(getPttType() == pttResult.getType()) {
								if(isToggleMode()) {
									if(pttResult.getState() == PTTState.DOWN)
										toggle = !toggle;
								}
								else
									toggle = false;
								
								final PTTState pttState =
									isToggleMode() ?
										(toggle ? PTTState.DOWN : PTTState.UP) : pttResult.getState();
								
								dispatchPTTDetected(pttResult.getType(), pttState);
							}
						}
					}
				}
			};
	
	private final ZmCmdLink.ZmEventListener zmEventListener = new ZmCmdLink.ZmEventListener() {
		@Override
		public void onScoStateChanged(boolean isConnected) {
			if(log.isTraceEnabled())
				log.trace(logTag + "ScoStateChanged = connected:" + isConnected);
		}
		
		@Override
		public void onSppStateChanged(boolean isConnected) {
			if(log.isTraceEnabled())
				log.trace(logTag + "SppStateChanged = connected:" + isConnected);
		}
		
		@Override
		public void onUserEvent(ZmCmdLink.ZmUserEvent event) {
			if(event != null){
				final PTTResult pttResult = isDetectPTT(event);
				if(pttResult != null && pttResult.isDetected()){
					final Intent keyIntent = new Intent(KEY_DETECTED);
					keyIntent.putExtra(EXTRA_KEY_TYPE, PTTType.HandMicTypeB.getTypeName());
					keyIntent.putExtra(EXTRA_KEY_CODE, 0);
					keyIntent.putExtra(EXTRA_KEY_STATE, pttResult.getState().getTypeName());
					
					broadcastManager.sendBroadcast(keyIntent);
					
					if(getPttType() == pttResult.getType()) {
						if(isToggleMode()) {
							if(pttResult.getState() == PTTState.DOWN)
								toggle = !toggle;
						}
						else
							toggle = false;
						
						final PTTState pttState =
							isToggleMode() ?
								(toggle ? PTTState.DOWN : PTTState.UP) : pttResult.getState();
						
						dispatchPTTDetected(pttResult.getType(), pttState);
					}
				}
			}
		}
		
		@Override
		public void onBatteryLevelChanged(int level) {
		}
		
		@Override
		public void onVolumeChanged(boolean isSco) {
		}
	};
	
	static {
		logTag = PTTDetector.class.getSimpleName() + " : ";
	}
	
	public PTTDetector(@NonNull final Context context) {
		super();
		
		this.context = context;
		
		broadcastManager = getLBM(context);
		
		pttKeyCode = 0x0;
		pttType = null;
		toggleMode = false;
		
		toggle = false;
	}
	
	public PTTDetector(
			@NonNull final Context context,
			final PTTType pttType
	) {
		this(context);
		
		this.pttType = pttType;
	}
	
	public PTTDetector(
			@NonNull final Context context,
			final PTTType pttType,
			final int pttKeyCode
	) {
		this(context, pttType);
		
		this.pttKeyCode = pttKeyCode;
	}
	
	public void start() {
		final IntentFilter vendorSpecificHeadsetEventIntentFilter =
				new IntentFilter(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT);
		vendorSpecificHeadsetEventIntentFilter.addCategory(
				BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." +
						BluetoothAssignedNumbers.PLANTRONICS
		);
		context.registerReceiver(
				vendorSpecificHeadsetEventReceiver, vendorSpecificHeadsetEventIntentFilter
		);
		
		mZmCmdLink = new ZmCmdLink(context, zmEventListener, true);
	}
	
	public void stop(){
		context.unregisterReceiver(vendorSpecificHeadsetEventReceiver);
		mZmCmdLink.destroy();
	}
	
	public void resetState() {
		toggle = false;
	}
	
	public void receiveKeyEvent(KeyEvent keyEvent) {
		if(log.isTraceEnabled()){
			log.trace("receiveKeyEvent() keyEvent = " + keyEvent);
		}
		
		if(keyEvent == null)
			return;
		
		if(
			(keyEvent.getAction() == KeyEvent.ACTION_DOWN && keyEvent.getRepeatCount() == 0) ||
			keyEvent.getAction() == KeyEvent.ACTION_UP
		){
			final Intent keyIntent = new Intent(KEY_DETECTED);
			keyIntent.putExtra(EXTRA_KEY_TYPE, PTTType.KeyBoard.getTypeName());
			keyIntent.putExtra(EXTRA_KEY_CODE, keyEvent.getKeyCode());
			
			switch(keyEvent.getAction()) {
				case KeyEvent.ACTION_DOWN:
					keyIntent.putExtra(EXTRA_KEY_STATE, PTTState.DOWN.getTypeName());
					break;
				
				case KeyEvent.ACTION_UP:
					keyIntent.putExtra(EXTRA_KEY_STATE, PTTState.UP.getTypeName());
					break;
				
				default:
					return;
			}
			
			broadcastManager.sendBroadcast(keyIntent);
		}
		
		if(getPttType() == PTTType.KeyBoard){
			final PTTResult pttResult = isDetectPTT(keyEvent, getPttKeyCode());
			if(pttResult != null && pttResult.isDetected()) {
				if(isToggleMode()) {
					if(pttResult.getState() == PTTState.DOWN)
						toggle = !toggle;
				}
				else
					toggle = false;
				
				final PTTState pttState =
						isToggleMode() ?
								(toggle ? PTTState.DOWN : PTTState.UP) : pttResult.getState();
				
				dispatchPTTDetected(pttResult.getType(), pttState);
			}
		}
		
	}
	
	private void dispatchPTTDetected(final PTTType pttType, final PTTState pttState) {
		final Intent pttIntent = new Intent(PTT_DETECTED);
		pttIntent.putExtra(EXTRA_PTT_TYPE, pttType.getTypeName());
		pttIntent.putExtra(EXTRA_PTT_STATE, pttState.getTypeName());
		
		broadcastManager.sendBroadcast(pttIntent);
		
		if(log.isInfoEnabled()) {
			log.info(
				"Broadcast event PTT_DETECTED(State=" + pttState.getTypeName() + ")"
			);
		}
	}
	
	private PTTResult isDetectPTT(@NonNull final KeyEvent keyEvent, final int pttKeyCode) {
		for(final PTTType pttType : PTTType.getAllType()) {
			final PTTResult result = pttType.isPTTDetect(keyEvent, pttKeyCode);
			if(result.isDetected()){return result;}
		}
		
		return null;
	}
	
	private PTTResult isDetectPTT(@NonNull final Object[] vendorSpecificEventArg) {
		for(final PTTType pttType : PTTType.getAllType()) {
			final PTTResult result = pttType.isPTTDetect(vendorSpecificEventArg);
			if(result.isDetected()){return result;}
		}
		
		return null;
	}
	
	private PTTResult isDetectPTT(@NonNull final ZmCmdLink.ZmUserEvent event) {
		for(final PTTType pttType : PTTType.getAllType()) {
			final PTTResult result = pttType.isPTTDetect(event);
			if(result.isDetected()){return result;}
		}
		
		return null;
	}
}
