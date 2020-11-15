package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;

import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigVoiceroidAutoReplyFragmentData;
import org.jp.illg.nora.android.view.model.VoiceroidAutoReplyRepeaterConfig;
import org.jp.illg.nora.android.view.model.VoiceroidAutoReplyRepeaterConfigBundler;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigVoiceroidAutoReplyFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@BindView(R.id.editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign)
	EditText editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(VoiceroidAutoReplyRepeaterConfigBundler.class)
	VoiceroidAutoReplyRepeaterConfig voiceroidAutoReplyRepeaterConfig;

	@State
	boolean serviceRunning;


	{
		voiceroidAutoReplyRepeaterConfig = new VoiceroidAutoReplyRepeaterConfig();
	}

	public RepeaterConfigVoiceroidAutoReplyFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigVoiceroidAutoReplyFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static RepeaterConfigVoiceroidAutoReplyFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		RepeaterConfigVoiceroidAutoReplyFragment instance =
				new RepeaterConfigVoiceroidAutoReplyFragment();
		RepeaterConfigVoiceroidAutoReplyFragment.setEventBus(eventBus);

		return instance;
	}

	public static class RepeaterConfigFragmentEvent extends EventBusEvent<RepeaterConfigFragmentEventType> {
		public RepeaterConfigFragmentEvent(RepeaterConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigFragmentEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigVoiceroidAutoReplyFragment fragment, Object attachment) {
				if (attachment != null && attachment instanceof RepeaterConfigVoiceroidAutoReplyFragmentData) {
					RepeaterConfigVoiceroidAutoReplyFragmentData data =
							(RepeaterConfigVoiceroidAutoReplyFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.setVoiceroidAutoReplyRepeaterConfig(data.getVoiceroidAutoReplyRepeaterConfig());

					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			@Override
			void apply(RepeaterConfigVoiceroidAutoReplyFragment fragment, Object attachment) {
				fragment.sendConfigToParent();
			}
		}
		/*
		UpdateConfig{
			@Override
			void apply(RepeaterConfigVoiceroidAutoReplyFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof VoiceroidAutoReplyRepeaterConfig){
					fragment.setVoiceroidAutoReplyRepeaterConfig((VoiceroidAutoReplyRepeaterConfig)attachment);

					fragment.updateView();
				}
			}
		},
		*/
		;

		abstract void apply(RepeaterConfigVoiceroidAutoReplyFragment fragment, Object attachment);
	}

	@Subscribe
	public void onMainActivityEvent(RepeaterConfigFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.repeater_config_voiceroid_autoreply_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		InputFilter[] editFilters = editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign.getFilters();
		InputFilter[] newFilters = new InputFilter[editFilters.length + 1];
		System.arraycopy(editFilters, 0, newFilters, 0, editFilters.length);
		newFilters[editFilters.length] = new InputFilter() {
			@Override
			public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
				if(source.toString().matches("^[0-9A-Z]+$"))
					return source;
				else
					return "";
			}
		};
		editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign.setFilters(newFilters);
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onResume() {
		super.onResume();

		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigVoiceroidAutoReplyFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigVoiceroidAutoReplyFragmentEventType.OnFragmentCreated,
						this
				)
		);
	}
	
	@Override
	public void onStop(){
		super.onStop();
		
		getEventBus().unregister(this);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public void onDestroyView() {

		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private TextWatcher editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsignTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					getVoiceroidAutoReplyRepeaterConfig().setAutoReplyOperatorCallsign(s.toString());
				}
			};

	private void updateView(){
		ViewUtil.consumerWhileViewDisabled(
				editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign,
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(
								editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsignTextWatcher
						);
						editText.setText(
								getVoiceroidAutoReplyRepeaterConfig().getAutoReplyOperatorCallsign()
						);
						editText.addTextChangedListener(
								editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsignTextWatcher
						);
					}
				}
		);
		
		if(serviceRunning){
			editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign.setEnabled(false);
		}
		else{
			editTextRepeaterConfigVoiceroidAutoReplyOperatorCallsign.setEnabled(true);
		}
	}

	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigVoiceroidAutoReplyFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigVoiceroidAutoReplyFragmentEventType.UpdateConfig,
						getVoiceroidAutoReplyRepeaterConfig().clone()
				)
		);
	}
}
