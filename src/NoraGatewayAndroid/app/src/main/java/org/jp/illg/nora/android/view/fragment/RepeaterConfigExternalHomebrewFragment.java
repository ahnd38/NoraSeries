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
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigExternalHomebrewFragmentData;
import org.jp.illg.nora.android.view.model.ExternalHomebrewRepeaterConfig;
import org.jp.illg.nora.android.view.model.ExternalHomebrewRepeaterConfigBundler;
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
public class RepeaterConfigExternalHomebrewFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter
	@State(ExternalHomebrewRepeaterConfigBundler.class)
	ExternalHomebrewRepeaterConfig externalHomebrewRepeaterConfig;

	@BindView(R.id.editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddress)
	EditText editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddress;

	@BindView(R.id.editTextRepeaterConfigExternalHomebrewRemoteRepeaterPort)
	EditText editTextRepeaterConfigExternalHomebrewRemoteRepeaterPort;

	@BindView(R.id.editTextRepeaterConfigExternalHomebrewLocalPort)
	EditText editTextRepeaterConfigExternalHomebrewLocalPort;

	@State
	boolean serviceRunning;

	{
		externalHomebrewRepeaterConfig = new ExternalHomebrewRepeaterConfig();
		serviceRunning = false;
	}

	public static class RepeaterConfigFragmentEvent extends EventBusEvent<RepeaterConfigFragmentEventType> {
		public RepeaterConfigFragmentEvent(RepeaterConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigFragmentEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigExternalHomebrewFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof RepeaterConfigExternalHomebrewFragmentData){
					RepeaterConfigExternalHomebrewFragmentData data =
							(RepeaterConfigExternalHomebrewFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.setExternalHomebrewRepeaterConfig(data.getExternalHomebrewRepeaterConfig());

					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			@Override
			void apply(RepeaterConfigExternalHomebrewFragment fragment, Object attachment){
				fragment.sendConfigToParent();
			}
		},
		/*
		UpdateConfig{
			@Override
			void apply(RepeaterConfigExternalHomebrewFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof ExternalHomebrewRepeaterConfig){
					fragment.setExternalHomebrewRepeaterConfig((ExternalHomebrewRepeaterConfig)attachment);

					fragment.updateView();
				}
			}
		},
		*/
		;

		abstract void apply(RepeaterConfigExternalHomebrewFragment fragment, Object attachment);
	}

	public RepeaterConfigExternalHomebrewFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigExternalHomebrewFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}
		
		public static RepeaterConfigExternalHomebrewFragment getInstance(EventBus eventBus){
			if(eventBus == null){throw new IllegalArgumentException();}
			
			RepeaterConfigExternalHomebrewFragment instance = new RepeaterConfigExternalHomebrewFragment();
			RepeaterConfigExternalHomebrewFragment.setEventBus(eventBus);

		return instance;
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
	public View onCreateView(
			LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState
	) {
		View view = inflater.inflate(R.layout.repeater_config_external_homebrew_layout, null);
		
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
				new RepeaterConfigFragment.RepeaterConfigExternalHomebrewFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigExternalHomebrewFragmentEventType.OnFragmentCreated,
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

	private TextWatcher editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddressTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					getExternalHomebrewRepeaterConfig().setRemoteRepeaterAddress(s.toString());
				}
			};

	private TextWatcher editTextRepeaterConfigExternalHomebrewRemoteRepeaterPortTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					int repeaterPort = 0;
					try {
						repeaterPort = Integer.valueOf(s.toString());
					}catch (NumberFormatException ex){
						return;
					}

					getExternalHomebrewRepeaterConfig().setRemoteRepeaterPort(repeaterPort);
				}
			};

	private TextWatcher editTextRepeaterConfigExternalHomebrewLocalPortTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					int localPort = 0;
					try{
						localPort = Integer.valueOf(s.toString());
					}catch (NumberFormatException ex){
						return;
					}

					getExternalHomebrewRepeaterConfig().setLocalPort(localPort);
				}
			};

	private void updateView(){
		ViewUtil.consumerWhileViewDisabled(
				editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddress,
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(
								editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddressTextWatcher
						);
						editText.setText(
								getExternalHomebrewRepeaterConfig().getRemoteRepeaterAddress()
						);
						editText.addTextChangedListener(
								editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddressTextWatcher
						);
					}
				}
		);
		ViewUtil.consumerWhileViewDisabled(
				editTextRepeaterConfigExternalHomebrewRemoteRepeaterPort,
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(
								editTextRepeaterConfigExternalHomebrewRemoteRepeaterPortTextWatcher
						);
						editText.setText(
								String.valueOf(getExternalHomebrewRepeaterConfig().getRemoteRepeaterPort())
						);
						editText.addTextChangedListener(
								editTextRepeaterConfigExternalHomebrewRemoteRepeaterPortTextWatcher
						);
					}
				}
		);
		ViewUtil.consumerWhileViewDisabled(
				editTextRepeaterConfigExternalHomebrewLocalPort,
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(
								editTextRepeaterConfigExternalHomebrewLocalPortTextWatcher
						);
						editText.setText(
								String.valueOf(getExternalHomebrewRepeaterConfig().getLocalPort())
						);
						editText.addTextChangedListener(
								editTextRepeaterConfigExternalHomebrewLocalPortTextWatcher
						);
					}
				}
		);
		
		if(serviceRunning){
			editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddress.setEnabled(false);
			editTextRepeaterConfigExternalHomebrewRemoteRepeaterPort.setEnabled(false);
			editTextRepeaterConfigExternalHomebrewLocalPort.setEnabled(false);
		}
		else{
			editTextRepeaterConfigExternalHomebrewRemoteRepeaterAddress.setEnabled(true);
			editTextRepeaterConfigExternalHomebrewRemoteRepeaterPort.setEnabled(true);
			editTextRepeaterConfigExternalHomebrewLocalPort.setEnabled(true);
		}
	}

	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigExternalHomebrewFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigExternalHomebrewFragmentEventType.UpdateConfig,
						getExternalHomebrewRepeaterConfig()
				)
		);
	}
}
