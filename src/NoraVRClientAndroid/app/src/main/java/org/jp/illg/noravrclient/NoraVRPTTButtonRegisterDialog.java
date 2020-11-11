package org.jp.illg.noravrclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.jp.illg.noravrclient.util.DialogFragmentBase;
import org.jp.illg.util.android.pttutil.BroadcastUtil;
import org.jp.illg.util.android.pttutil.PTTDetectService;
import org.jp.illg.util.android.pttutil.PTTDetector;
import org.jp.illg.util.android.pttutil.PTTState;
import org.jp.illg.util.android.pttutil.PTTType;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.Getter;

public class NoraVRPTTButtonRegisterDialog extends DialogFragmentBase {
	
	public static final String IDEXT_PTT_TYPE = "PTTType";
	public static final String IDEXT_PTT_KEYCODE = "KeyCode";
	
	@State
	public int enterPTTKeyCode;
	
	@State
	public PTTType enterPTTType;
	
	private Handler handler;
	private final Runnable returnResultTask = new Runnable() {
		@Override
		public void run() {
			returnResult();
		}
	};
	
	@BindView(R.id.pttButtonRegisterPttType)
	TextView pttButtonRegisterPttType;
	
	private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if(PTTDetector.KEY_DETECTED.equals(intent.getAction())) {
				final PTTType pttType =
						PTTType.getTypeByName(intent.getStringExtra(PTTDetector.EXTRA_KEY_TYPE));
				final int keyCode = intent.getIntExtra(PTTDetector.EXTRA_KEY_CODE, 0);
				final PTTState pttState =
						PTTState.getTypeByName(intent.getStringExtra(PTTDetector.EXTRA_KEY_STATE));
				
				onPTTDetected(pttType, keyCode);
			}
		}
	};
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if(savedInstanceState != null)
			Icepick.restoreInstanceState(this, savedInstanceState);
		
		handler = new Handler();
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View content = inflater.inflate(R.layout.ptt_button_register_layout, null);
		
		ButterKnife.bind(this, content);
		
		if(getArguments() != null){
			final String pttType =
					getArguments().getString(IDEXT_PTT_TYPE);
			if(pttType != null && !"".equals(pttType))
				enterPTTType = PTTType.getTypeByName(pttType);
			
			final int pttKeyCode =
					getArguments().getInt(IDEXT_PTT_KEYCODE);
			enterPTTKeyCode = pttKeyCode;
		}
		
		builder.setView(content);
		
		builder
				.setTitle("External PTT Register")
				.setMessage(null)
				.setPositiveButton(
						"OK",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int id) {
								handler.removeCallbacks(returnResultTask);
								
								returnResult();
							}
						}
				).setOnKeyListener(new DialogInterface.OnKeyListener() {
					@Override
					public boolean onKey(DialogInterface dialogInterface, int i, KeyEvent keyEvent) {
						final PTTDetectService pttDetectService =
								NoraVRClientApplication.getPttDetectService();
						pttDetectService.receiveKeyEvent(keyEvent);
						return true;
					}
				});
		
		return builder.create();
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		final IntentFilter intentFilter =
				new IntentFilter(PTTDetector.KEY_DETECTED);
		
		BroadcastUtil.getLBM(getContext()).registerReceiver(broadcastReceiver, intentFilter);
	}
	
	@Override
	public void onPause() {
		super.onPause();
		
		BroadcastUtil.getLBM(getContext()).unregisterReceiver(broadcastReceiver);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		Icepick.saveInstanceState(this, outState);
	}
	
	private void onPTTDetected(final PTTType pttType, final int pttKeyCode) {
		enterPTTType = pttType;
		enterPTTKeyCode = pttKeyCode;
		
		pttButtonRegisterPttType.setText(
				String.format(Locale.getDefault(), "Type:%s/Code:0x%X", pttType, pttKeyCode)
		);
		
		handler.postDelayed(returnResultTask, 1000);
	}
	
	private void returnResult(){
		final Intent data  = new Intent();
		if(enterPTTType != null)
			data.putExtra(IDEXT_PTT_TYPE, enterPTTType.getTypeName());
		
		if(enterPTTKeyCode != 0x0)
			data.putExtra(IDEXT_PTT_KEYCODE, enterPTTKeyCode);
		
		notifyDialogResult(getId(), data);
		
		if(getDialog() != null) {getDialog().dismiss();}
	}
	
	public static class Builder extends DialogFragmentBase.Builder {
		
		@Getter
		private String externalPTTType;
		
		@Getter
		private int externalPTTKeycode;
		
		public Builder setExternalPTTType(final String externalPTTType) {
			this.externalPTTType = externalPTTType;
			return this;
		}
		
		public Builder setExternalPTTKeycode(final int externalPTTKeycode) {
			this.externalPTTKeycode = externalPTTKeycode;
			return this;
		}
		
		@Override
		protected DialogFragmentBase build(){
			final DialogFragmentBase dialog = new NoraVRPTTButtonRegisterDialog();
			
			final Bundle data = new Bundle();
			
			data.putString(IDEXT_PTT_TYPE, getExternalPTTType());
			data.putInt(IDEXT_PTT_KEYCODE, getExternalPTTKeycode());
			
			dialog.setArguments(data);
			
			return dialog;
		}
	}
}
