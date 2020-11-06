package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigEchoAutoReplyFragmentData;
import org.jp.illg.nora.android.view.model.EchoAutoReplyRepeaterConfig;
import org.jp.illg.nora.android.view.model.EchoAutoReplyRepeaterConfigBundler;
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
public class RepeaterConfigEchoAutoReplyFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(EchoAutoReplyRepeaterConfigBundler.class)
	EchoAutoReplyRepeaterConfig echoAutoReplyRepeaterConfig;

	@BindView(R.id.editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsign)
	EditText editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsign;

	@State
	boolean serviceRunning;


	{
		echoAutoReplyRepeaterConfig = new EchoAutoReplyRepeaterConfig();
	}

	public RepeaterConfigEchoAutoReplyFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigEchoAutoReplyFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static RepeaterConfigEchoAutoReplyFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		RepeaterConfigEchoAutoReplyFragment instance = new RepeaterConfigEchoAutoReplyFragment();
		RepeaterConfigEchoAutoReplyFragment.setEventBus(eventBus);

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
			void apply(RepeaterConfigEchoAutoReplyFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof RepeaterConfigEchoAutoReplyFragmentData){
					RepeaterConfigEchoAutoReplyFragmentData data =
							(RepeaterConfigEchoAutoReplyFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.setEchoAutoReplyRepeaterConfig(data.getEchoAutoReplyRepeaterConfig());

					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			@Override
			void apply(RepeaterConfigEchoAutoReplyFragment fragment, Object attachment){
				fragment.sendConfigToParent();
			}
		},
		/*
		UpdateConfig{
			@Override
			void apply(RepeaterConfigEchoAutoReplyFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof EchoAutoReplyRepeaterConfig){
					fragment.setEchoAutoReplyRepeaterConfig((EchoAutoReplyRepeaterConfig)attachment);

					fragment.updateView();
				}
			}
		},
		*/
		;

		abstract void apply(RepeaterConfigEchoAutoReplyFragment fragment, Object attachment);
	}

	@Subscribe
	public void onRepeaterConfigFragmentEvent(RepeaterConfigFragmentEvent event){
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
		View view = inflater.inflate(R.layout.repeater_config_echo_autoreply_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onResume(){
		super.onResume();

		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigEchoAutoReplyFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigEchoAutoReplyFragmentEventType.OnFragmentCreated,
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

	private TextWatcher editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsignTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					getEchoAutoReplyRepeaterConfig().setAutoReplyOperatorCallsign(s.toString());
				}
			};

	private void updateView(){
		ViewUtil.consumerWhileViewDisabled(
				editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsign,
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(
								editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsignTextWatcher
						);
						editText.setText(
								getEchoAutoReplyRepeaterConfig().getAutoReplyOperatorCallsign()
						);
						editText.addTextChangedListener(
								editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsignTextWatcher
						);
					}
				}
		);
		
		if(serviceRunning){
			editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsign.setEnabled(false);
		}
		else{
			editTextRepeaterConfigVoiceroidEchoAutoReplyOperatorCallsign.setEnabled(true);
		}
	}

	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigEchoAutoReplyFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigEchoAutoReplyFragmentEventType.UpdateConfig,
						getEchoAutoReplyRepeaterConfig()
				)
		);
	}
}
