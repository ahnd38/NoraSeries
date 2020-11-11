package org.jp.illg.noravrclient.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

public abstract class DialogFragmentBase extends DialogFragment {
	
	public interface Callback {

		void onDialogResult(int requestCode, int resultCode, Intent data);
		
		void onDialogCancelled(int requestCode);
	}
	
	private enum HostType {
		UNSPECIFIED,
		ACTIVITY,
		TARGET_FRAGMENT,
		PARENT_FRAGMENT
	}
	
	private static final String ARG_PREFIX = DialogFragmentBase.class.getName() + ".";
	private static final String ARG_REQUEST_CODE = ARG_PREFIX + "RequestCode";
	private static final String ARG_CALLBACK_HOST = ARG_PREFIX + "CallbackHostSpec";
	
	private int requestCode;
	private HostType callbackHostSpec;
	
	
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		checkArguments(getArguments());
		
		Bundle args = getArguments();
		requestCode = args.getInt(ARG_REQUEST_CODE);
		callbackHostSpec = (HostType) args.getSerializable(ARG_CALLBACK_HOST);
	}
	
	
	@Override
	public void onCancel(DialogInterface dialog) {
		super.onCancel(dialog);
		notifyDialogCancelled();
	}
	
	private boolean shouldCallback(HostType target) {
		return callbackHostSpec == HostType.UNSPECIFIED || callbackHostSpec == target;
	}
	
	protected final void notifyDialogResult(int resultCode, Intent data) {
		Activity activity = getActivity();
		if (shouldCallback(HostType.ACTIVITY) && activity instanceof Callback) {
			Callback callback = (Callback) activity;
			callback.onDialogResult(requestCode, resultCode, data);
		}
		
		Fragment target = getTargetFragment();
		if (shouldCallback(HostType.TARGET_FRAGMENT) && target instanceof Callback) {
			Callback callback = (Callback) target;
			callback.onDialogResult(requestCode, resultCode, data);
		}
		
		Fragment parent = getParentFragment();
		if (shouldCallback(HostType.PARENT_FRAGMENT) && parent instanceof Callback) {
			Callback callback = (Callback) parent;
			callback.onDialogResult(requestCode, resultCode, data);
		}
	}
	
	protected final void notifyDialogCancelled() {
		Activity activity = getActivity();
		if (shouldCallback(HostType.ACTIVITY) && activity instanceof Callback) {
			Callback callback = (Callback) activity;
			callback.onDialogCancelled(requestCode);
		}
		
		Fragment target = getTargetFragment();
		if (shouldCallback(HostType.TARGET_FRAGMENT) && target instanceof Callback) {
			Callback callback = (Callback) target;
			callback.onDialogCancelled(requestCode);
		}
		
		Fragment parent = getParentFragment();
		if (shouldCallback(HostType.PARENT_FRAGMENT) && parent instanceof Callback) {
			Callback callback = (Callback) parent;
			callback.onDialogCancelled(requestCode);
		}
	}
	
	public abstract static class Builder {
		
		private boolean cancelable = true;
		
		@NonNull
		public Builder setCancelable(boolean cancelable) {
			this.cancelable = cancelable;
			return this;
		}
		
		@NonNull
		protected abstract DialogFragmentBase build();
		
		@NonNull
		public final DialogFragmentBase build(int requestCode) {
			DialogFragmentBase dialog = build();
			Bundle args = (dialog.getArguments() != null) ? dialog.getArguments() : new Bundle();
			
			args.putInt(ARG_REQUEST_CODE, requestCode);
			
			args.putSerializable(ARG_CALLBACK_HOST, HostType.UNSPECIFIED);
			
			dialog.setArguments(args);
			dialog.setCancelable(cancelable);
			
			return dialog;
		}
	}
	
	@Override
	public int show(FragmentTransaction transaction, String tag) {
		return super.show(transaction, tag);
	}
	
	@Override
	public void show(FragmentManager manager, String tag) {
		super.show(manager, tag);
	}
	
	public void showOn(@NonNull Activity host, String tag) {
		checkAppCompatActivity(host);
		checkArguments(getArguments());
		
		getArguments().putSerializable(ARG_CALLBACK_HOST, HostType.ACTIVITY);
		
		AppCompatActivity hostCompat = (AppCompatActivity) host;
		FragmentManager manager = hostCompat.getSupportFragmentManager();
		super.show(manager, tag);
	}
	
	public void showOn(@NonNull Fragment host, String tag) {
		checkArguments(getArguments());
		
		getArguments().putSerializable(ARG_CALLBACK_HOST, HostType.TARGET_FRAGMENT);
		
		setTargetFragment(host, getArguments().getInt(ARG_REQUEST_CODE));
		
		FragmentManager manager = host.getFragmentManager();
		super.show(manager, tag);
	}
	
	public void showChildOn(@NonNull Fragment host, String tag) {
		checkArguments(getArguments());
		
		getArguments().putSerializable(ARG_CALLBACK_HOST, HostType.PARENT_FRAGMENT);
		
		FragmentManager manager = host.getChildFragmentManager();
		super.show(manager, tag);
	}
	
	
	private static void checkAppCompatActivity(Activity activity) {
		if (!(activity instanceof AppCompatActivity)) {
			throw new IllegalArgumentException("host activity only supports AppCompatActivity.");
		}
	}
	
	private static void checkArguments(Bundle bundle) {
		if (bundle == null) {
			throw new IllegalStateException("Don't clear setArguments()");
		}
	}
}
