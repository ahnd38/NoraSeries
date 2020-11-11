package org.jp.illg.noravrclient.util;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

public class AlertDialogFragment extends DialogFragmentBase {
	
	private static final String ARG_POSITIVE_LABEL = "positiveLabel";
	private static final String ARG_NEGATIVE_LABEL = "negativeLabel";
	private static final String ARG_NEUTRAL_LABEL = "neutralLabel";
	private static final String ARG_TITLE = "title";
	private static final String ARG_MESSAGE = "message";
	private static final String ARG_POSITIVE_LABEL_ID = "positiveLabelId";
	private static final String ARG_NEGATIVE_LABEL_ID = "negativeLabelId";
	private static final String ARG_NEUTRAL_LABEL_ID = "neutralLabelId";
	private static final String ARG_TITLE_ID = "titleId";
	private static final String ARG_MESSAGE_ID = "messageId";
	private static final String ARG_ICON_ID = "iconId";
	
	@NonNull
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
		
		Bundle args = getArguments();
		String positiveLabel = args.getString(ARG_POSITIVE_LABEL);
		String negativeLabel = args.getString(ARG_NEGATIVE_LABEL);
		String neutralLabel = args.getString(ARG_NEUTRAL_LABEL);
		String title = args.getString(ARG_TITLE);
		String message = args.getString(ARG_MESSAGE);
		int positiveLabelId = args.getInt(ARG_POSITIVE_LABEL_ID);
		int negativeLabelId = args.getInt(ARG_NEGATIVE_LABEL_ID);
		int neutralLabelId = args.getInt(ARG_NEUTRAL_LABEL_ID);
		int titleId = args.getInt(ARG_TITLE_ID);
		int messageId = args.getInt(ARG_MESSAGE_ID);
		int iconId = args.getInt(ARG_ICON_ID);
		
		DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				notifyDialogResult(which, null);
			}
		};
		
		if (positiveLabel != null) {
			builder.setPositiveButton(positiveLabel, listener);
		}
		if (negativeLabel != null) {
			builder.setNegativeButton(negativeLabel, listener);
		}
		if (neutralLabel != null) {
			builder.setNeutralButton(neutralLabel, listener);
		}
		if (title != null) {
			builder.setTitle(title);
		}
		if (message != null) {
			builder.setMessage(message);
		}
		if (positiveLabelId > 0) {
			builder.setPositiveButton(positiveLabelId, listener);
		}
		if (negativeLabelId > 0) {
			builder.setNegativeButton(negativeLabelId, listener);
		}
		if (neutralLabelId > 0) {
			builder.setNeutralButton(neutralLabelId, listener);
		}
		if (titleId > 0) {
			builder.setTitle(titleId);
		}
		if (messageId > 0) {
			builder.setMessage(messageId);
		}
		if (iconId > 0) {
			builder.setIcon(iconId);
		}
		
		return builder.create();
	}
	
	public static class Builder extends DialogFragmentBase.Builder {
		String message;
		String title;
		String positiveLabel;
		String negativeLabel;
		String neutralLabel;
		
		int messageId;
		int titleId;
		int positiveLabelId;
		int negativeLabelId;
		int neutralLabelId;
		int iconId;
		
		public Builder setIcon(@DrawableRes int iconId) {
			this.iconId = iconId;
			return this;
		}
		
		public Builder setMessage(String message) {
			this.message = message;
			return this;
		}
		
		public Builder setNegativeButton(String label) {
			negativeLabel = label;
			return this;
		}
		
		public Builder setNeutralButton(String label) {
			neutralLabel = label;
			return this;
		}
		
		public Builder setPositiveButton(String label) {
			positiveLabel = label;
			return this;
		}
		
		public Builder setTitle(String title) {
			this.title = title;
			return this;
		}
		
		public Builder setMessage(@StringRes int resId) {
			messageId = resId;
			return this;
		}
		
		public Builder setNegativeButton(@StringRes int resId) {
			negativeLabelId = resId;
			return this;
		}
		
		public Builder setNeutralButton(@StringRes int resId) {
			neutralLabelId = resId;
			return this;
		}
		
		public Builder setPositiveButton(@StringRes int resId) {
			positiveLabelId = resId;
			return this;
		}
		
		public Builder setTitle(@StringRes int title) {
			titleId = title;
			return this;
		}
		
		@NonNull
		@Override
		public Builder setCancelable(boolean cancelable) {
			super.setCancelable(cancelable);
			return this;
		}
		
		@NonNull
		@Override
		protected DialogFragmentBase build() {
			Bundle args = new Bundle();
			args.putString(ARG_MESSAGE, message);
			args.putString(ARG_TITLE, title);
			args.putString(ARG_NEGATIVE_LABEL, negativeLabel);
			args.putString(ARG_NEUTRAL_LABEL, neutralLabel);
			args.putString(ARG_POSITIVE_LABEL, positiveLabel);
			args.putInt(ARG_MESSAGE_ID, messageId);
			args.putInt(ARG_TITLE_ID, titleId);
			args.putInt(ARG_NEGATIVE_LABEL_ID, negativeLabelId);
			args.putInt(ARG_NEUTRAL_LABEL_ID, neutralLabelId);
			args.putInt(ARG_POSITIVE_LABEL_ID, positiveLabelId);
			args.putInt(ARG_ICON_ID, iconId);
			
			DialogFragmentBase dialog = new AlertDialogFragment();
			dialog.setArguments(args);
			return dialog;
		}
	}
}
