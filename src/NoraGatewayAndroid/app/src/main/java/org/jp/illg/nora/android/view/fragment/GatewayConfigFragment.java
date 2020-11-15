package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.nora.android.view.fragment.model.GatewayConfigFragmentData;
import org.jp.illg.nora.android.view.model.GatewayConfig;
import org.jp.illg.nora.android.view.model.GatewayConfigBundler;
import org.jp.illg.nora.MainActivity;
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
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayConfigFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(GatewayConfigBundler.class)
	GatewayConfig gatewayConfig;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextGatewayCallsign)
	EditText editTextGatewayCallsign;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.switchUseProxyGateway)
	Switch switchUseProxyGateway;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextProxyGatewayAddress)
	EditText editTextProxyGatewayAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextProxyGatewayPort)
	EditText editTextProxyGatewayPort;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.switchRoutingServiceJapanTrust)
	Switch switchRoutingServiceJapanTrust;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextRoutingServiceJapanTrustServerAddress)
	EditText editTextRoutingServiceJapanTrustServerAddress;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.switchRoutingServiceIrcDDB)
	Switch switchRoutingServiceIrcDDB;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextRoutingServiceIrcDDBServerAddress)
	EditText editTextRoutingServiceIrcDDBServerAddress;
/*
	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextRoutingServiceIrcDDBUserName)
	EditText editTextRoutingServiceIrcDDBUserName;
*/
	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.editTextRoutingServiceIrcDDBPassword)
	EditText editTextRoutingServiceIrcDDBPassword;
/*
	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxRoutingServiceIrcDDBAnonymous)
	CheckBox checkBoxRoutingServiceIrcDDBAnonymous;
*/
	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxReflectorProtocolDExtra)
	Switch checkBoxReflectorProtocolDExtra;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxReflectorProtocolDPlus)
	Switch checkBoxReflectorProtocolDPlus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxReflectorProtocolJARLMultiForward)
	Switch checkBoxReflectorProtocolJARLMultiForward;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxReflectorProtocolDCS)
	Switch checkBoxReflectorProtocolDCS;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@BindView(R.id.checkBoxRemoteControlEnable)
	Switch checkBoxRemoteControlEnable;

	@State
	boolean serviceRunning;

	{
		gatewayConfig = new GatewayConfig();
	}

	public GatewayConfigFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(GatewayConfigFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static GatewayConfigFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		GatewayConfigFragment instance = new GatewayConfigFragment();
		GatewayConfigFragment.setEventBus(eventBus);
		
		instance.setGatewayConfig(new GatewayConfig());

		return instance;
	}

	public static class MainActivityEvent extends EventBusEvent<MainActivityEventType>{
		public MainActivityEvent(MainActivityEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum MainActivityEventType{
		UpdateData{
			@Override
			void apply(final GatewayConfigFragment fragment, final Object attachment){
				if(log.isTraceEnabled()){
					log.trace(
						"Receive event " +
							MainActivityEvent.class.getSimpleName() + "." +
							MainActivityEventType.UpdateData
					);
				}

				if(attachment != null && attachment instanceof GatewayConfigFragmentData){
					GatewayConfigFragmentData data = (GatewayConfigFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.setGatewayConfig(data.getGatewayConfig());
					
					fragment.updateConfigToView();
				}
			}
		},
		UpdateConfig{
			@Override
			void apply(final GatewayConfigFragment fragment, final Object attachment){
				if(log.isTraceEnabled()){
					log.trace(
						"Receive event " +
							MainActivityEvent.class.getSimpleName() + "." +
							MainActivityEventType.UpdateConfig
					);
				}

				if(attachment != null && attachment instanceof GatewayConfig){
					fragment.setGatewayConfig((GatewayConfig)attachment);
					
					fragment.updateConfigToView();

				}
			}
		},
		;

		abstract void apply(GatewayConfigFragment fragment, Object attachment);
	}

	@Subscribe
	@SuppressWarnings("unused")
	public void onMainActivityEvent(MainActivityEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onCreate()");

		Icepick.restoreInstanceState(this, savedInstanceState);
	}

	@Override
	public View onCreateView(
		@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState
	) {
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onCreateView()");

		View view = inflater.inflate(R.layout.gateway_config_layout, null);
		
		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onViewCreated()");
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onStop(){
		super.onStop();
		
		getEventBus().unregister(this);
	}

	@Override
	public void onResume(){
		super.onResume();
		
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onResume()");


		getEventBus().post(
				new MainActivity.GatewayConfigFragmentEvent(
						MainActivity.GatewayConfigFragmentEventType.OnFragmentCreated,
						this
				)
		);
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onSaveInstanceState()");

		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public void onDestroyView() {
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onDestroyView()");

		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		if(log.isTraceEnabled())
			log.trace(this.getClass().getSimpleName() + ".onDestroy()");

		super.onDestroy();
	}

	private TextWatcher editTextGatewayCallsignTextWatcher =
		new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {

			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {

			}

			@Override
			public void afterTextChanged(Editable s) {
				if(log.isTraceEnabled()){
					log.trace(
						GatewayConfigFragment.this.getClass().getSimpleName() +
							".editTextGatewayCallsignTextWatcher.afterTextChanged()"
					);
				}

				final String gatewayCallsign =
						String.format("%-" + DSTARDefines.CallsignFullLength + "S",s.toString());
				
				if(
					!CallSignValidator.isValidUserCallsign(gatewayCallsign) ||
					CallSignValidator.isValidJARLRepeaterCallsign(gatewayCallsign)
				)
					editTextGatewayCallsign.setError("Input callsign is invalid.");
				else
					editTextGatewayCallsign.setError(null);

				getGatewayConfig().setGatewayCallsign(s.toString());
				sendConfigToRoot();
			}
		};

	private Switch.OnCheckedChangeListener switchUseProxyGatewayOnCheckedChangeListener
			= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if(log.isTraceEnabled()){
				log.trace(
					GatewayConfigFragment.this.getClass().getSimpleName() +
						".switchUseProxyGatewayOnCheckedChangeListener.onCheckedChanged()"
				);
			}

			getGatewayConfig().setUseProxyGateway(isChecked);
			sendConfigToRoot();
		}
	};

	private TextWatcher editTextProxyGatewayAddressTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextProxyGatewayAddressTextWatcher.afterTextChanged()"
						);
					}

					getGatewayConfig().setProxyGatewayAddress(s.toString());
					sendConfigToRoot();
				}
			};

	private TextWatcher editTextProxyGatewayPortTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextProxyGatewayPortTextWatcher.afterTextChanged()"
						);
					}
					
					int portNumber = 0;
					try{
						portNumber = Integer.valueOf(s.toString());
						editTextProxyGatewayPort.setError(null);
					}catch(NumberFormatException ex){
						editTextProxyGatewayPort.setError("Illegal port number !");
					}

					getGatewayConfig().setProxyGatewayPort(portNumber);
					sendConfigToRoot();
				}
			};

	private Switch.OnCheckedChangeListener switchRoutingServiceJapanTrustOnCheckedChangeListener
		= new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				if(log.isTraceEnabled()){
					log.trace(
						GatewayConfigFragment.this.getClass().getSimpleName() +
							".switchRoutingServiceJapanTrustOnCheckedChangeListener.onCheckedChanged()"
					);
				}

				getGatewayConfig().setEnableJapanTrust(isChecked);
				sendConfigToRoot();
			}
		};

	private TextWatcher editTextRoutingServiceServerAddressTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextRoutingServiceServerAddressTextWatcher.afterTextChanged()"
						);
					}

					getGatewayConfig().setJapanTrustServerAddress(s.toString());
					sendConfigToRoot();
				}
			};

	private Switch.OnCheckedChangeListener switchRoutingServiceIrcDDBOnCheckedChangeListener
			= new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".switchRoutingServiceIrcDDBOnCheckedChangeListener.onCheckedChanged()"
						);
					}

					getGatewayConfig().setEnableIrcDDB(isChecked);
					sendConfigToRoot();
				}
			};

	private TextWatcher editTextRoutingServiceIrcDDBServerAddressTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextRoutingServiceIrcDDBServerAddressTextWatcher.afterTextChanged()"
						);
					}

					getGatewayConfig().setIrcDDBServerAddress(s.toString());
					sendConfigToRoot();
				}
			};

	private TextWatcher editTextRoutingServiceIrcDDBUserNameTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextRoutingServiceIrcDDBUserNameTextWatcher.afterTextChanged()"
						);
					}

					getGatewayConfig().setIrcDDBUserName(s.toString());
					sendConfigToRoot();
				}
			};

	private TextWatcher editTextRoutingServiceIrcDDBPasswordTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {

				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

				@Override
				public void afterTextChanged(Editable s) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".editTextRoutingServiceIrcDDBPasswordTextWatcher.afterTextChanged()"
						);
					}

					getGatewayConfig().setIrcDDBPassword(s.toString());
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxRoutingServiceIrcDDBAnonymousOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxRoutingServiceIrcDDBAnonymousOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setIrcDDBAnonymous(isChecked);
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxReflectorProtocolDExtraOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxReflectorProtocolDExtraOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setEnableDExtra(isChecked);
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxReflectorProtocolDPlusOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxReflectorProtocolDPlusOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setEnableDPlus(isChecked);
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxReflectorProtocolJARLMultiForwardOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxReflectorProtocolJARLMultiForwardOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setEnableJARLMultiForward(isChecked);
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxReflectorProtocolDCSOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxReflectorProtocolDCSOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setEnableDCS(isChecked);
					sendConfigToRoot();
				}
			};

	private CheckBox.OnCheckedChangeListener checkBoxRemoteControlEnableOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					if(log.isTraceEnabled()){
						log.trace(
							GatewayConfigFragment.this.getClass().getSimpleName() +
								".checkBoxRemoteControlEnableOnCheckedChangeListener.onCheckedChanged()"
						);
					}
					
					getGatewayConfig().setEnableRemoteControl(isChecked);
					sendConfigToRoot();
				}
			};
	
	private boolean updateConfigToView(){
		if(getContext() == null || isDetached() || getGatewayConfig() == null){return false;}

		//Gateway Callsign
		ViewUtil.consumerWhileViewDisabled(
				getEditTextGatewayCallsign(),
				new Consumer<TextView>() {
					@Override
					public void accept(TextView textView) {
						textView.removeTextChangedListener(editTextGatewayCallsignTextWatcher);
						textView.setText(getGatewayConfig().getGatewayCallsign());
						textView.addTextChangedListener(editTextGatewayCallsignTextWatcher);
					}
				}
		);
		
		ViewUtil.consumerWhileViewDisabled(
				getSwitchUseProxyGateway(),
				new Consumer<Switch>() {
					@Override
					public void accept(Switch switchObj) {
						switchObj.setOnCheckedChangeListener(null);
						switchObj.setChecked(getGatewayConfig().isUseProxyGateway());
						switchObj.setOnCheckedChangeListener(switchUseProxyGatewayOnCheckedChangeListener);
					}
				}
		);
		
		ViewUtil.consumerWhileViewDisabled(
				getEditTextProxyGatewayAddress(),
				new Consumer<TextView>() {
					@Override
					public void accept(TextView textView) {
						textView.removeTextChangedListener(editTextProxyGatewayAddressTextWatcher);
						textView.setText(getGatewayConfig().getProxyGatewayAddress());
						textView.addTextChangedListener(editTextProxyGatewayAddressTextWatcher);
					}
				}
		);
		
		ViewUtil.consumerWhileViewDisabled(
				getEditTextProxyGatewayPort(),
				new Consumer<TextView>() {
					@Override
					public void accept(TextView textView) {
						textView.removeTextChangedListener(editTextProxyGatewayPortTextWatcher);
						textView.setText(String.valueOf(getGatewayConfig().getProxyGatewayPort()));
						textView.addTextChangedListener(editTextProxyGatewayPortTextWatcher);
					}
				}
		);
		
		
		//Japan Trust
		ViewUtil.consumerWhileViewDisabled(
				getSwitchRoutingServiceJapanTrust(),
				new Consumer<Switch>() {
					@Override
					public void accept(Switch switchObj) {
						switchObj.setOnCheckedChangeListener(null);
						switchObj.setChecked(getGatewayConfig().isEnableJapanTrust());
						switchObj.setOnCheckedChangeListener(switchRoutingServiceJapanTrustOnCheckedChangeListener);
					}
				}
		);
		ViewUtil.consumerWhileViewDisabled(
				getEditTextRoutingServiceJapanTrustServerAddress(),
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(editTextRoutingServiceServerAddressTextWatcher);
						editText.setText(getGatewayConfig().getJapanTrustServerAddress());
						editText.addTextChangedListener(editTextRoutingServiceServerAddressTextWatcher);
					}
				}
		);
		
		//ircDDB
		ViewUtil.consumerWhileViewDisabled(
				getSwitchRoutingServiceIrcDDB(),
				new Consumer<Switch>() {
					@Override
					public void accept(Switch switchObj) {
						switchObj.setOnCheckedChangeListener(null);
						switchObj.setChecked(getGatewayConfig().isEnableIrcDDB());
						switchObj.setOnCheckedChangeListener(switchRoutingServiceIrcDDBOnCheckedChangeListener);
					}
				}
		);
		ViewUtil.consumerWhileViewDisabled(
				getEditTextRoutingServiceIrcDDBServerAddress(),
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(editTextRoutingServiceIrcDDBServerAddressTextWatcher);
						editText.setText(getGatewayConfig().getIrcDDBServerAddress());
						editText.addTextChangedListener(editTextRoutingServiceIrcDDBServerAddressTextWatcher);
					}
				}
		);

		ViewUtil.consumerWhileViewDisabled(
				getEditTextRoutingServiceIrcDDBPassword(),
				new Consumer<EditText>() {
					@Override
					public void accept(EditText editText) {
						editText.removeTextChangedListener(editTextRoutingServiceIrcDDBPasswordTextWatcher);
						editText.setText(getGatewayConfig().getIrcDDBPassword());
						editText.addTextChangedListener(editTextRoutingServiceIrcDDBPasswordTextWatcher);
					}
				}
		);
		
		//DExtra
		ViewUtil.consumerWhileViewDisabled(
				getCheckBoxReflectorProtocolDExtra(),
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch checkBox) {
						checkBox.setOnCheckedChangeListener(null);
						checkBox.setChecked(getGatewayConfig().isEnableDExtra());
						checkBox.setOnCheckedChangeListener(checkBoxReflectorProtocolDExtraOnCheckedChangeListener);
					}
				}
		);
		
		//DPlus
		ViewUtil.consumerWhileViewDisabled(
				getCheckBoxReflectorProtocolDPlus(),
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch checkBox) {
						checkBox.setOnCheckedChangeListener(null);
						checkBox.setChecked(getGatewayConfig().isEnableDPlus());
						checkBox.setOnCheckedChangeListener(checkBoxReflectorProtocolDPlusOnCheckedChangeListener);
					}
				}
		);
		
		//JARL MultiForward
		ViewUtil.consumerWhileViewDisabled(
				getCheckBoxReflectorProtocolJARLMultiForward(),
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch checkBox) {
						checkBox.setOnCheckedChangeListener(null);
						checkBox.setChecked(getGatewayConfig().isEnableJARLMultiForward());
						checkBox.setOnCheckedChangeListener(checkBoxReflectorProtocolJARLMultiForwardOnCheckedChangeListener);
					}
				}
		);
		
		//DCS
		ViewUtil.consumerWhileViewDisabled(
				getCheckBoxReflectorProtocolDCS(),
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch checkBox) {
						checkBox.setOnCheckedChangeListener(null);
						checkBox.setChecked(getGatewayConfig().isEnableDCS());
						checkBox.setOnCheckedChangeListener(checkBoxReflectorProtocolDCSOnCheckedChangeListener);
					}
				}
		);
		
		//RemoteControl
		ViewUtil.consumerWhileViewDisabled(
				getCheckBoxRemoteControlEnable(),
				new Consumer<Switch>() {
					@Override
					public void accept(final Switch checkBox) {
						checkBox.setOnCheckedChangeListener(null);
						checkBox.setChecked(getGatewayConfig().isEnableRemoteControl());
						checkBox.setOnCheckedChangeListener(checkBoxRemoteControlEnableOnCheckedChangeListener);
					}
				}
		);
		
		if(serviceRunning){
			getEditTextGatewayCallsign().setEnabled(false);
			
			getSwitchRoutingServiceJapanTrust().setEnabled(false);
			getEditTextRoutingServiceJapanTrustServerAddress().setEnabled(false);
			
			getSwitchUseProxyGateway().setEnabled(false);
			getEditTextProxyGatewayAddress().setEnabled(false);
			getEditTextProxyGatewayPort().setEnabled(false);
			
			getSwitchRoutingServiceIrcDDB().setEnabled(false);
			getEditTextRoutingServiceIrcDDBServerAddress().setEnabled(false);
			getEditTextRoutingServiceIrcDDBPassword().setEnabled(false);
			
			getCheckBoxReflectorProtocolDExtra().setEnabled(false);
			getCheckBoxReflectorProtocolDPlus().setEnabled(false);
			getCheckBoxReflectorProtocolJARLMultiForward().setEnabled(false);
			getCheckBoxReflectorProtocolDCS().setEnabled(false);
			
			getCheckBoxRemoteControlEnable().setEnabled(false);
		}
		else{
			getEditTextGatewayCallsign().setEnabled(true);
			
			getSwitchRoutingServiceJapanTrust().setEnabled(true);
			getEditTextRoutingServiceJapanTrustServerAddress().setEnabled(true);
			
			getSwitchUseProxyGateway().setEnabled(true);
			getEditTextProxyGatewayAddress().setEnabled(true);
			getEditTextProxyGatewayPort().setEnabled(true);
			
			getSwitchRoutingServiceIrcDDB().setEnabled(true);
			getEditTextRoutingServiceIrcDDBServerAddress().setEnabled(true);
			getEditTextRoutingServiceIrcDDBPassword().setEnabled(true);
			
			getCheckBoxReflectorProtocolDExtra().setEnabled(true);
			getCheckBoxReflectorProtocolDPlus().setEnabled(true);
			getCheckBoxReflectorProtocolJARLMultiForward().setEnabled(true);
			getCheckBoxReflectorProtocolDCS().setEnabled(true);
			
			getCheckBoxRemoteControlEnable().setEnabled(true);
		}
		
		return true;
	}

	private boolean sendConfigToRoot(){
		if(getContext() == null || isDetached() || getGatewayConfig() == null){return false;}

		getEventBus().post(
				new MainActivity.GatewayConfigFragmentEvent(
						MainActivity.GatewayConfigFragmentEventType.UpdateConfig, getGatewayConfig()
				)
		);

		return true;
	}
}
