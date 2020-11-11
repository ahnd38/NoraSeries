package org.jp.illg.noravrclient;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import org.jp.illg.noravrclient.util.DialogFragmentBase;

import butterknife.BindView;
import butterknife.ButterKnife;
import lombok.Getter;

public class NoraVRYourCallsignSelectorDialog extends DialogFragmentBase {
	
	public static final String IDEXT_HISTORYCALLSIGNS = "HistoryCallsigns";
	public static final String IDEXT_SELECTEDCALLSIGN = "SelectedCallsign";
	
	@BindView(R.id.linearLayoutYourCallsignHistory)
	LinearLayout linearLayoutYourCallsignHistory;
	
	RadioGroup radioGroup;
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		
		LayoutInflater inflater = getActivity().getLayoutInflater();
		View content = inflater.inflate(R.layout.yourcallsignselector_dialog, null);
		
		ButterKnife.bind(this, content);
		
		if(getArguments() != null){
			final String[] history = getArguments().getStringArray(IDEXT_HISTORYCALLSIGNS);
			
			if(history != null && history.length >= 1){
				radioGroup = new RadioGroup(getContext());
				
				for(final String callsign : history){
					final RadioButton radioButton = new RadioButton(getContext());
					radioButton.setText(callsign);
					radioButton.setTextSize(16);
					
					radioGroup.addView(radioButton);
				}
				
				linearLayoutYourCallsignHistory.addView(radioGroup);
				
				if(radioGroup.getChildCount() >= 1){
					((RadioButton)radioGroup.getChildAt(0)).setChecked(true);
				}
			}
		}
		
		
		builder.setView(content);
		
		builder
				.setTitle("Callsign Selector")
				.setMessage(null)
				.setNegativeButton(
						"Cancel",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								NoraVRYourCallsignSelectorDialog.this.getDialog().cancel();
							}
						}
				)
				.setPositiveButton(
						"OK",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int id) {
								final Intent data  = new Intent();

								if(radioGroup != null){
									final int selectedRadioButtonID =
											radioGroup.getCheckedRadioButtonId();
									final int idx =
											radioGroup.indexOfChild(radioGroup.findViewById(selectedRadioButtonID));
									final RadioButton radioButton =
											(RadioButton) radioGroup.getChildAt(idx);
									
									data.putExtra(IDEXT_SELECTEDCALLSIGN, radioButton.getText());
								}
								
								notifyDialogResult(id, data);
							}
						}
				);
		
		return builder.create();
	}
	
	public static class Builder extends DialogFragmentBase.Builder {
		
		@Getter
		private String[] historyCallsigns;
		
		public NoraVRYourCallsignSelectorDialog.Builder setHistoryCallsigns(final String[] historyCallsigns){
			this.historyCallsigns = historyCallsigns;
			
			return this;
		}
		
		@Override
		protected DialogFragmentBase build(){
			final DialogFragmentBase dialog = new NoraVRYourCallsignSelectorDialog();
			
			final Bundle data = new Bundle();
			
			if(getHistoryCallsigns() != null)
				data.putStringArray(IDEXT_HISTORYCALLSIGNS, getHistoryCallsigns());
			
			dialog.setArguments(data);
			
			return dialog;
		}
	}
}
