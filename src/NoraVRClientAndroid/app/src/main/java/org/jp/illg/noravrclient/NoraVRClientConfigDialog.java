package org.jp.illg.noravrclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.jp.illg.nora.vr.model.NoraVRCodecType;
import org.jp.illg.noravrclient.util.DialogFragmentBase;
import org.jp.illg.noravrclient.util.RegexInputFilter;

import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.Getter;

public class NoraVRClientConfigDialog extends DialogFragmentBase implements DialogFragmentBase.Callback {
	
	public static final String IDEXT_SERVERADDRESS = "ServerAddress";
	public static final String IDEXT_SERVERPORT = "ServerPort";
	public static final String IDEXT_LOGINCALLSIGN = "LoginCallsign";
	public static final String IDEXT_LOGINPASSWORD = "LoginPassword";
	public static final String IDEXT_CODECTYPE = "CodecType";
	public static final String IDEXT_MYCALLSIGNLONG = "MyCallsignLong";
	public static final String IDEXT_MYCALLSIGNSHORT = "MyCallsignShort";
	public static final String IDEXT_ENABLESHORTMESSAGE = "EnableShortMessage";
	public static final String IDEXT_SHORTMESSAGE = "ShortMessage";
	public static final String IDEXT_ENABLETRANSMITGPS = "EnableTransmitGPS";
	public static final String IDEXT_ENABLEBEEPONRECEIVESTART = "EnableBeepOnReceiveStart";
	public static final String IDEXT_ENABLEBEEPONRECEIVEEND = "EnableBeepOnReceiveEnd";
	public static final String IDEXT_ENABLEGPSLOCATIONPOPUP = "EnableGPSLocationPopup";
	public static final String IDEXT_DISABLEDISPLAYSLEEP = "DisableDisplaySleep";
	public static final String IDEXT_EXTERNALPTTKEYCODE = "ExternalPTTKeycode";
	public static final String IDEXT_EXTERNALPTTTYPE = "ExternalPTTType";
	public static final String IDEXT_TOUCHPTTTRANSMIT = "TouchPTTTransmit";
	public static final String IDEXT_PTTTOGGLEMODE = "PTTToggleMode";
	
	private final int IDDIAG_BLUETOOTHBUTTONREGISTER = 0x52ad32;
	
	@BindView(R.id.editTextConfigServerAddress)
	EditText editTextConfigServerAddress;
	
	@BindView(R.id.editTextConfigServerPort)
	EditText editTextConfigServerPort;
	
	@BindView(R.id.editTextConfigServerLoginCallsign)
	EditText editTextConfigServerLoginCallsign;
	
	@BindView(R.id.editTextConfigServerLoginPassword)
	EditText editTextConfigServerLoginPassword;
	
	@BindView(R.id.spinnerConfigCodecType)
	Spinner spinnerConfigCodecType;
	
	@BindView(R.id.editTextConfigMyCallsignLong)
	EditText editTextConfigMyCallsignLong;
	
	@BindView(R.id.editTextConfigMyCallsignShort)
	EditText editTextConfigMyCallsignShort;
	
	@BindView(R.id.switchConfigEnableTransmitShortMessage)
	Switch switchConfigEnableTransmitShortMessage;
	
	@BindView(R.id.editTextConfigShortMessage)
	EditText editTextConfigShortMessage;
	
	@BindView(R.id.switchConfigEnableTransmitGPS)
	Switch switchConfigEnableTransmitGPS;
	
	@BindView(R.id.switchConfigEnableBeepOnReceiveStart)
	Switch switchConfigEnableBeepOnReceiveStart;
	
	@BindView(R.id.switchConfigEnableBeepOnReceiveEnd)
	Switch switchConfigEnableBeepOnReceiveEnd;
	
	@BindView(R.id.switchConfigEnableGPSLocationPopup)
	Switch switchConfigEnableGPSLocationPopup;
	
	@BindView(R.id.switchConfigDisableDisplaySleep)
	Switch switchConfigDisableDisplaySleep;
	
	@BindView(R.id.buttonConfigExternalPTT)
	Button buttonConfigExternalPTT;
	
	@BindView(R.id.textViewConfigExternalPTTKeycode)
	TextView textViewConfigExternalPTTKeycode;
	
	@BindView(R.id.buttonConfigExternalPTTClear)
	Button buttonConfigExternalPTTClear;
	
	@BindView(R.id.switchConfigTouchPTTTransmit)
	Switch switchConfigTouchPTTTransmit;
	
	@BindView(R.id.switchConfigPTTToggleMode)
	Switch switchConfigPTTToggleMode;
	
	@State
	int externalPTTKeycode;
	
	@State
	String externalPTTType;
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		if(savedInstanceState != null)
			Icepick.restoreInstanceState(this, savedInstanceState);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View content = inflater.inflate(R.layout.config_layout, null);
		
		ButterKnife.bind(this, content);
		
		editTextConfigServerAddress.setFilters(
				new InputFilter[]{
						new InputFilter.LengthFilter(128),
						new RegexInputFilter("^[a-zA-Z0-9-_.]+$")
				});
		editTextConfigServerPort.setFilters(
				new InputFilter[]{
						new InputFilter.LengthFilter(5),
						new RegexInputFilter("^[0-9]+$")
				});
		
		editTextConfigServerLoginPassword.setFilters(
				new InputFilter[]{
						new InputFilter.LengthFilter(128)
				});
		
		editTextConfigServerLoginCallsign.setFilters(
				new InputFilter[]{
						new InputFilter.AllCaps(),
						new InputFilter.LengthFilter(8),
						new RegexInputFilter("^[A-Z0-9 ]+$")
				});
		
		editTextConfigMyCallsignLong.setFilters(
				new InputFilter[]{
						new InputFilter.AllCaps(),
						new InputFilter.LengthFilter(8),
						new RegexInputFilter("^[A-Z0-9 ]+$")
				});
		editTextConfigMyCallsignShort.setFilters(
				new InputFilter[]{
						new InputFilter.AllCaps(),
						new InputFilter.LengthFilter(4),
						new RegexInputFilter("^[A-Z0-9 ]+$")
				});
		
		final ArrayAdapter<String> codecTypeAdapter = new ArrayAdapter<String>(
				getContext(),
				R.layout.support_simple_spinner_dropdown_item,
				getResources().getStringArray(R.array.codecTypes)
		);
		spinnerConfigCodecType.setAdapter(codecTypeAdapter);
		
		editTextConfigShortMessage.setFilters(
				new InputFilter[]{
						new InputFilter.LengthFilter(20),
						new RegexInputFilter("^[a-zA-Z0-9 \\!\\\"#$%&'()\\*+,\\-./:;<=>?@\\[\\\\\\]~_{|}]+$")
				});
		
		if(getArguments() != null){
			editTextConfigServerAddress.setText(
					getArguments().getString(IDEXT_SERVERADDRESS, "")
			);
			editTextConfigServerPort.setText(
					String.valueOf(
						getArguments().getInt(IDEXT_SERVERPORT, 52161)
					)
			);
			editTextConfigServerLoginCallsign.setText(
					getArguments().getString(IDEXT_LOGINCALLSIGN, "")
			);
			
			editTextConfigServerLoginPassword.setText(
					getArguments().getString(IDEXT_LOGINPASSWORD, "")
			);
			final String codecType = getArguments().getString(IDEXT_CODECTYPE);
			if(codecType != null) {
				for(int i = 0; i < codecTypeAdapter.getCount(); i++){
					final String codec = codecTypeAdapter.getItem(i);
					if(codec.equals(codecType)) {
						spinnerConfigCodecType.setSelection(i, false);
						break;
					}
				}
			}
			editTextConfigMyCallsignLong.setText(
					getArguments().getString(IDEXT_MYCALLSIGNLONG, "")
			);
			editTextConfigMyCallsignShort.setText(
					getArguments().getString(IDEXT_MYCALLSIGNSHORT, "")
			);
			
			switchConfigEnableTransmitShortMessage.setChecked(
					getArguments().getBoolean(IDEXT_ENABLESHORTMESSAGE, false)
			);
			editTextConfigShortMessage.setText(
					getArguments().getString(IDEXT_SHORTMESSAGE, "")
			);
			
			switchConfigEnableTransmitGPS.setChecked(
					getArguments().getBoolean(IDEXT_ENABLETRANSMITGPS, false)
			);
			
			switchConfigEnableBeepOnReceiveStart.setChecked(
					getArguments().getBoolean(IDEXT_ENABLEBEEPONRECEIVESTART, false)
			);
			switchConfigEnableBeepOnReceiveEnd.setChecked(
					getArguments().getBoolean(IDEXT_ENABLEBEEPONRECEIVEEND, false)
			);
			
			switchConfigEnableGPSLocationPopup.setChecked(
					getArguments().getBoolean(IDEXT_ENABLEGPSLOCATIONPOPUP, true)
			);
			
			switchConfigDisableDisplaySleep.setChecked(
					getArguments().getBoolean(IDEXT_DISABLEDISPLAYSLEEP, false)
			);
			
			externalPTTType = getArguments().getString(IDEXT_EXTERNALPTTTYPE);
			externalPTTKeycode = getArguments().getInt(IDEXT_EXTERNALPTTKEYCODE, 0);
			textViewConfigExternalPTTKeycode.setText(
					String.format(Locale.getDefault(), "Type:%s\nKeyCode:0x%X", externalPTTType, externalPTTKeycode)
			);
			buttonConfigExternalPTT.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					new NoraVRPTTButtonRegisterDialog.Builder()
					.setExternalPTTKeycode(externalPTTKeycode)
					.build(IDDIAG_BLUETOOTHBUTTONREGISTER)
					.showOn(NoraVRClientConfigDialog.this, NoraVRClientConfigDialog.class.getSimpleName());
				}
			});
			buttonConfigExternalPTTClear.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					externalPTTType = null;
					externalPTTKeycode = 0x0;
					textViewConfigExternalPTTKeycode.setText(
							String.format(Locale.getDefault(), "Type:%s\nKeyCode:0x%X", externalPTTType, externalPTTKeycode)
					);
				}
			});
			
			switchConfigTouchPTTTransmit.setChecked(
					getArguments().getBoolean(IDEXT_TOUCHPTTTRANSMIT, false)
			);
			
			switchConfigPTTToggleMode.setChecked(
				getArguments().getBoolean(IDEXT_PTTTOGGLEMODE, false)
			);
		}
		
		builder.setView(content);
		
		builder
			.setTitle("NoraVR Config")
			.setMessage(null)
			.setNegativeButton(
					"Cancel",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {
							NoraVRClientConfigDialog.this.getDialog().cancel();
						}
					}
			)
			.setPositiveButton(
					"Save",
					new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int id) {
							final Intent data  = new Intent();
							data.putExtra(
									IDEXT_SERVERADDRESS,
									editTextConfigServerAddress.getText().toString()
							);
							int serverPort = -1;
							try {
								serverPort =
										Integer.valueOf(editTextConfigServerPort.getText().toString());
							}catch(NumberFormatException ex){
								serverPort = -1;
							}
							data.putExtra(IDEXT_SERVERPORT, serverPort);
							
							data.putExtra(
									IDEXT_LOGINCALLSIGN,
									editTextConfigServerLoginCallsign.getText().toString()
							);
							data.putExtra(
									IDEXT_LOGINPASSWORD,
									editTextConfigServerLoginPassword.getText().toString()
							);
							data.putExtra(
									IDEXT_CODECTYPE,
									spinnerConfigCodecType.getSelectedItem().toString()
							);
							data.putExtra(
									IDEXT_MYCALLSIGNLONG,
									editTextConfigMyCallsignLong.getText().toString()
							);
							data.putExtra(
									IDEXT_MYCALLSIGNSHORT,
									editTextConfigMyCallsignShort.getText().toString()
							);
							data.putExtra(
									IDEXT_ENABLESHORTMESSAGE,
									switchConfigEnableTransmitShortMessage.isChecked()
							);
							data.putExtra(
									IDEXT_SHORTMESSAGE,
									editTextConfigShortMessage.getText().toString()
							);
							data.putExtra(
									IDEXT_ENABLETRANSMITGPS,
									switchConfigEnableTransmitGPS.isChecked()
							);
							data.putExtra(
									IDEXT_ENABLEBEEPONRECEIVESTART,
									switchConfigEnableBeepOnReceiveStart.isChecked()
							);
							data.putExtra(
									IDEXT_ENABLEBEEPONRECEIVEEND,
									switchConfigEnableBeepOnReceiveEnd.isChecked()
							);
							data.putExtra(
									IDEXT_ENABLEGPSLOCATIONPOPUP,
									switchConfigEnableGPSLocationPopup.isChecked()
							);
							data.putExtra(
									IDEXT_DISABLEDISPLAYSLEEP,
									switchConfigDisableDisplaySleep.isChecked()
							);
							data.putExtra(
									IDEXT_EXTERNALPTTTYPE,
									externalPTTType
							);
							data.putExtra(
									IDEXT_EXTERNALPTTKEYCODE,
									externalPTTKeycode
							);
							data.putExtra(
									IDEXT_TOUCHPTTTRANSMIT,
									switchConfigTouchPTTTransmit.isChecked()
							);
							data.putExtra(
								IDEXT_PTTTOGGLEMODE,
								switchConfigPTTToggleMode.isChecked()
							);
							
							notifyDialogResult(id, data);
						}
					}
			);
		
		return builder.create();
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		Icepick.saveInstanceState(this, outState);
	}
	
	@Override
	public void onDialogResult(int requestCode, int resultCode, @Nullable Intent data) {
		switch(requestCode){
			case IDDIAG_BLUETOOTHBUTTONREGISTER:
				if(data != null){
					final String pttType =
							data.getStringExtra(NoraVRPTTButtonRegisterDialog.IDEXT_PTT_TYPE);
					if(pttType != null){
						externalPTTType = pttType;
						externalPTTKeycode =
								data.getIntExtra(NoraVRPTTButtonRegisterDialog.IDEXT_PTT_KEYCODE, 0);
						
						textViewConfigExternalPTTKeycode.setText(
								String.format(Locale.getDefault(), "Type:%s\nKeyCode:0x%X", externalPTTType, externalPTTKeycode)
						);
					}
				}
				break;
				
			default:
				break;
		}
	}
	
	@Override
	public void onDialogCancelled(int requestCode) {
	
	}
	
	public static class Builder extends DialogFragmentBase.Builder {
		
		@Getter
		private String serverAddress = "";
		
		@Getter
		private int serverPort = -1;
		
		@Getter
		private String loginCallsign = "";
		
		@Getter
		private String loginPassword = "";
		
		@Getter
		private NoraVRCodecType codecType;
		
		@Getter
		private String myCallsignLong;
		
		@Getter
		private String myCallsignShort;
		
		@Getter
		private boolean enableTransmitShortMessage;
		
		@Getter
		private String shortMessage;
		
		@Getter
		private boolean enableTransmitGPS;
		
		@Getter
		private boolean enableBeepOnReceiveStart;
		
		@Getter
		private boolean enableBeepOnReceiveEnd;
		
		@Getter
		private boolean enableGPSLocationPopup;
		
		@Getter
		private boolean disableDisplaySleep;
		
		@Getter
		private String externalPTTType;
		
		@Getter
		private int externalPTTKeyCode;
		
		@Getter
		private boolean touchPTTTransmit;
		
		@Getter
		private boolean pttToggleMode;
		
		public Builder setServerAddress(final String serverAddress){
			this.serverAddress = serverAddress;
			
			return this;
		}
		
		public Builder setServerPort(final int serverPort){
			this.serverPort = serverPort;
			
			return this;
		}
		
		public Builder setLoginCallsign(final String loginCallsign){
			this.loginCallsign = loginCallsign;
			
			return this;
		}
		
		public Builder setLoginPassword(final String loginPassword){
			this.loginPassword = loginPassword;
			
			return this;
		}
		
		public Builder setCodecType(final NoraVRCodecType codecType){
			this.codecType = codecType;
			
			return this;
		}
		
		public Builder setMyCallsignLong(final String myCallsignLong){
			this.myCallsignLong = myCallsignLong;
			
			return this;
		}
		
		public Builder setMyCallsignShort(final String myCallsignShort){
			this.myCallsignShort = myCallsignShort;
			
			return this;
		}
		
		public Builder setEnableTransmitShortMessage(final boolean enableTransmitShortMessage){
			this.enableTransmitShortMessage = enableTransmitShortMessage;
			
			return this;
		}
		
		public Builder setShortMessage(final String shortMessage) {
			this.shortMessage = shortMessage;
			
			return this;
		}
		
		public Builder setEnableTransmitGPS(final boolean enableTransmitGPS) {
			this.enableTransmitGPS = enableTransmitGPS;
			
			return this;
		}
		
		public Builder setEnableBeepOnReceiveStart(final boolean enableBeepOnReceiveStart) {
			this.enableBeepOnReceiveStart = enableBeepOnReceiveStart;
			
			return this;
		}
		
		public Builder setEnableBeepOnReceiveEnd(final boolean enableBeepOnReceiveEnd) {
			this.enableBeepOnReceiveEnd = enableBeepOnReceiveEnd;
			
			return this;
		}
		
		public Builder setEnableGPSLocationPopup(final boolean enableGPSLocationPopup) {
			this.enableGPSLocationPopup = enableGPSLocationPopup;
			
			return this;
		}
		
		public Builder setDisableDisplaySleep(final boolean disableDisplaySleep){
			this.disableDisplaySleep = disableDisplaySleep;
			
			return this;
		}
		
		public Builder setExternalPTTType(final String externalPTTType) {
			this.externalPTTType = externalPTTType;
			
			return this;
		}
		
		public Builder setExternalPTTKeyCode(final int externalPTTKeyCode) {
			this.externalPTTKeyCode = externalPTTKeyCode;
			
			return this;
		}
		
		public Builder setTouchPTTTransmit(final boolean touchPTTTransmit) {
			this.touchPTTTransmit = touchPTTTransmit;
			
			return this;
		}
		
		public Builder setPTTToggleMode(final boolean pttToggleMode) {
			this.pttToggleMode = pttToggleMode;
			
			return this;
		}
		
		@Override
		protected DialogFragmentBase build(){
			final DialogFragmentBase dialog = new NoraVRClientConfigDialog();
			
			final Bundle data = new Bundle();
			
			if(getServerAddress() != null)
				data.putString(IDEXT_SERVERADDRESS, getServerAddress());
			
			if(getServerPort() > 0)
				data.putInt(IDEXT_SERVERPORT, getServerPort());
			
			if(getLoginCallsign() != null)
				data.putString(IDEXT_LOGINCALLSIGN, getLoginCallsign());
			
			if(getLoginPassword() != null)
				data.putString(IDEXT_LOGINPASSWORD, getLoginPassword());
			
			if(getCodecType() != null)
				data.putString(IDEXT_CODECTYPE, getCodecType().getTypeName());
			
			if(getMyCallsignLong() != null)
				data.putString(IDEXT_MYCALLSIGNLONG, getMyCallsignLong());
			
			if(getMyCallsignShort() != null)
				data.putString(IDEXT_MYCALLSIGNSHORT, getMyCallsignShort());
			
			data.putBoolean(IDEXT_ENABLESHORTMESSAGE, isEnableTransmitShortMessage());
			
			if(getShortMessage() != null)
				data.putString(IDEXT_SHORTMESSAGE, getShortMessage());
			
			data.putBoolean(IDEXT_ENABLETRANSMITGPS, isEnableTransmitGPS());
			
			data.putBoolean(IDEXT_ENABLEBEEPONRECEIVESTART, isEnableBeepOnReceiveStart());
			data.putBoolean(IDEXT_ENABLEBEEPONRECEIVEEND, isEnableBeepOnReceiveEnd());
			
			data.putBoolean(IDEXT_ENABLEGPSLOCATIONPOPUP, isEnableGPSLocationPopup());
			
			data.putBoolean(IDEXT_DISABLEDISPLAYSLEEP, isDisableDisplaySleep());
			
			if(getExternalPTTType() != null)
				data.putString(IDEXT_EXTERNALPTTTYPE, getExternalPTTType());
			
			data.putInt(IDEXT_EXTERNALPTTKEYCODE, getExternalPTTKeyCode());
			
			data.putBoolean(IDEXT_TOUCHPTTTRANSMIT, isTouchPTTTransmit());
			
			data.putBoolean(IDEXT_PTTTOGGLEMODE, isPttToggleMode());
			
			dialog.setArguments(data);
			
			return dialog;
		}
	}
}
