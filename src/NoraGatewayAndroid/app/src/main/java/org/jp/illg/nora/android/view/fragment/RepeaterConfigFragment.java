package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Switch;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.dstar.model.defines.RepeaterTypes;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigEchoAutoReplyFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigExternalHomebrewFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigVoiceroidAutoReplyFragmentData;
import org.jp.illg.nora.android.view.model.EchoAutoReplyRepeaterConfig;
import org.jp.illg.nora.android.view.model.ExternalHomebrewRepeaterConfig;
import org.jp.illg.nora.android.view.model.InternalRepeaterConfig;
import org.jp.illg.nora.android.view.model.RepeaterConfig;
import org.jp.illg.nora.android.view.model.RepeaterConfigBundler;
import org.jp.illg.nora.android.view.model.RepeaterModuleConfig;
import org.jp.illg.nora.android.view.model.VoiceroidAutoReplyRepeaterConfig;
import org.jp.illg.nora.MainActivity;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;

import java.util.LinkedHashMap;
import java.util.Map;

import butterknife.BindArray;
import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(RepeaterConfigBundler.class)
	RepeaterConfig repeaterConfig;

	@State
	boolean serviceRunning;

	@BindView(R.id.viewPagerRepeaterConfig)
	ViewPager viewPagerRepeaterConfig;

	@BindView(R.id.spinnerRepeaterModule)
	Spinner spinnerRepeaterModule;
	
	@BindView(R.id.buttonRepeaterConfigSave)
	Button buttonRepeaterConfigSave;

	@BindView(R.id.switchRepeaterModuleEnabled)
	Switch switchRepeaterModuleEnabled;

	@BindView(R.id.spinnerRepeaterType)
	Spinner spinnerRepeaterType;

	@BindArray(R.array.arrayRepeaterModules)
	String[] arrayRepeaterModules;

	@Getter
	private static final Map<String, Class<? extends Fragment>> repeaterTypes;

//	private NoraFragmentPagerAdapter viewPagerRepeaterConfigAdapter;

	static {
		repeaterTypes = new LinkedHashMap<>();
		repeaterTypes.put(RepeaterTypes.Internal.getTypeName(), RepeaterConfigInternalFragment.class);
//		repeaterTypes.put(RepeaterTypes.ExternalHomebrew.getTypeName(), RepeaterConfigExternalHomebrewFragment.class);
//		repeaterTypes.put(RepeaterTypes.VoiceroidAutoReply.getTypeName(), RepeaterConfigVoiceroidAutoReplyFragment.class);
//		repeaterTypes.put(RepeaterTypes.EchoAutoReply.getTypeName(), RepeaterConfigEchoAutoReplyFragment.class);
	}

	{
		repeaterConfig = new RepeaterConfig();
		serviceRunning = false;
	}

	public RepeaterConfigFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static RepeaterConfigFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		RepeaterConfigFragment instance = new RepeaterConfigFragment();
		RepeaterConfigFragment.setEventBus(eventBus);
		
		instance.setRepeaterConfig(new RepeaterConfig());

		return instance;
	}

	public static class MainActivityEvent extends EventBusEvent<MainActivityEventType> {
		public MainActivityEvent(MainActivityEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum MainActivityEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof RepeaterConfigFragmentData){
					RepeaterConfigFragmentData data = (RepeaterConfigFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					if(data.getRepeaterConfig() != null)
						fragment.setRepeaterConfig(data.getRepeaterConfig());
					
					log.trace("Update data from MainActivity...Module:" + data.getRepeaterConfig().getSelectedModule()+ ".");

					fragment.updateView();
					fragment.updateFragmentData();
				}
			}
		},
		UpdateModuleConfig{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				if(fragment != null && attachment instanceof RepeaterModuleConfig){
					RepeaterModuleConfig newRepeaterModuleConfig = (RepeaterModuleConfig)attachment;
					
					log.trace("Update module data from MainActivity...Module:" + newRepeaterModuleConfig.getRepeaterModule() + ".");
					
					Map<Character, RepeaterModuleConfig> repeaterModules =
							fragment.getRepeaterConfig().getRepeaterModules();

					if(repeaterModules.containsKey(newRepeaterModuleConfig.getRepeaterModule()))
						repeaterModules.remove(newRepeaterModuleConfig.getRepeaterModule());

					repeaterModules.put(newRepeaterModuleConfig.getRepeaterModule(), newRepeaterModuleConfig);

					RepeaterModuleConfig currentRepeaterModuleConfig = fragment.getCurrentRepeaterModuleConfig();

					if(currentRepeaterModuleConfig.getRepeaterModule() == newRepeaterModuleConfig.getRepeaterModule())
						fragment.updateViewByRepeaterModule(newRepeaterModuleConfig.getRepeaterModule());
				}
			}
		},
		ResultUartPorts{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.getEventBus().post(
						new RepeaterConfigInternalFragment.RepeaterConfigFragmentEvent(
								RepeaterConfigInternalFragment.RepeaterConfigFragmentEventType.ResultUartPorts,
								attachment
						)
				);
			}
		}
		;

		abstract void apply(RepeaterConfigFragment fragment, Object attachment);
	}

	public static class RepeaterConfigInternalFragmentEvent extends EventBusEvent<RepeaterConfigInternalFragmentEventType> {
		public RepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigInternalFragmentEventType {
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.updateRepeaterConfigInternalFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				RepeaterModuleConfig moduleConfig = fragment.getCurrentRepeaterModuleConfig();

				if(fragment != null && attachment instanceof InternalRepeaterConfig){
					moduleConfig.setInternalRepeaterConfig((InternalRepeaterConfig)attachment);

					fragment.sendCurrentRepeaterModuleConfigToParent();
				}
			}
		},
		RequestUartPorts{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.getEventBus().post(
						new MainActivity.RepeaterConfigFragmentEvent(
								MainActivity.RepeaterConfigFragmentEventType.RequestUartPorts,
								attachment
						)
				);
			}
		},
		;

		abstract void apply(RepeaterConfigFragment fragment, Object attachment);
	}

	public static class RepeaterConfigExternalHomebrewFragmentEvent extends EventBusEvent<RepeaterConfigExternalHomebrewFragmentEventType> {
		public RepeaterConfigExternalHomebrewFragmentEvent(RepeaterConfigExternalHomebrewFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigExternalHomebrewFragmentEventType {
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.updateRepeaterConfigExternalHomebrewFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				RepeaterModuleConfig moduleConfig = fragment.getCurrentRepeaterModuleConfig();

				if(fragment != null && attachment instanceof ExternalHomebrewRepeaterConfig){
					moduleConfig.setExternalHomebrewRepeaterConfig((ExternalHomebrewRepeaterConfig)attachment);

					fragment.sendCurrentRepeaterModuleConfigToParent();
				}
			}
		},
		;

		abstract void apply(RepeaterConfigFragment fragment, Object attachment);
	}


	public static class RepeaterConfigVoiceroidAutoReplyFragmentEvent extends EventBusEvent<RepeaterConfigVoiceroidAutoReplyFragmentEventType> {
		public RepeaterConfigVoiceroidAutoReplyFragmentEvent(RepeaterConfigVoiceroidAutoReplyFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigVoiceroidAutoReplyFragmentEventType {
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.updateRepeaterConfigVoiceroidAutoReplyFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				RepeaterModuleConfig moduleConfig = fragment.getCurrentRepeaterModuleConfig();

				if(attachment != null && attachment instanceof VoiceroidAutoReplyRepeaterConfig){
					moduleConfig.setVoiceroidAutoReplyRepeaterConfig((VoiceroidAutoReplyRepeaterConfig)attachment);

					fragment.sendCurrentRepeaterModuleConfigToParent();
				}
			}
		},
		;

		abstract void apply(RepeaterConfigFragment fragment, Object attachment);
	}

	public static class RepeaterConfigEchoAutoReplyFragmentEvent extends EventBusEvent<RepeaterConfigEchoAutoReplyFragmentEventType> {
		public RepeaterConfigEchoAutoReplyFragmentEvent(RepeaterConfigEchoAutoReplyFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigEchoAutoReplyFragmentEventType {
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				fragment.updateRepeaterConfigEchoAutoReplyFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigFragment fragment, Object attachment){
				RepeaterModuleConfig moduleConfig = fragment.getCurrentRepeaterModuleConfig();

				if(fragment != null && attachment instanceof EchoAutoReplyRepeaterConfig){
					moduleConfig.setEchoAutoReplyRepeaterConfig((EchoAutoReplyRepeaterConfig)attachment);

					fragment.sendCurrentRepeaterModuleConfigToParent();
				}
			}
		},
		;

		abstract void apply(RepeaterConfigFragment fragment, Object attachment);
	}

	@Subscribe
	public void onMainActivityEvent(MainActivityEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigExternalHomebrewFragmentEvent(RepeaterConfigExternalHomebrewFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigVoiceroidAutoReplyFragmentEvent(RepeaterConfigVoiceroidAutoReplyFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigEchoAutoReplyFragmentEvent(RepeaterConfigEchoAutoReplyFragmentEvent event){
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
		View view = inflater.inflate(R.layout.repeater_config_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		ArrayAdapter<String> repeaterModuleAdapter =
				new ArrayAdapter<String>(
						getContext(),
						R.layout.spinner_list_style,
						arrayRepeaterModules
				);
		repeaterModuleAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinnerRepeaterModule.setAdapter(repeaterModuleAdapter);

		ArrayAdapter<String> repeaterTypeAdapter =
				new ArrayAdapter<String>(
						getContext(),
						R.layout.spinner_list_style,
						getRepeaterTypes().keySet().toArray(new String[getRepeaterTypes().size()])
				);
		repeaterTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinnerRepeaterType.setAdapter(repeaterTypeAdapter);
/*
		viewPagerRepeaterConfigAdapter =
				new NoraFragmentPagerAdapter(getChildFragmentManager(), view.getContext(), getEventBus());

		for(Map.Entry<String, Class<? extends Fragment>> repeaterType : getRepeaterTypes().entrySet())
			viewPagerRepeaterConfigAdapter.addPage(repeaterType.getValue(), repeaterType.getKey());
*/
		
		FragmentPagerAdapter viewPagerRepeaterConfigAdapter =
				new FragmentPagerAdapter(getChildFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
					@Override
					public Fragment getItem(int i) {
						switch(i){
							case 0:
								return RepeaterConfigInternalFragment.getInstance(getEventBus());
/*
							case 1:
								return RepeaterConfigExternalHomebrewFragment.getInstance(getEventBus());
							case 2:
								return RepeaterConfigVoiceroidAutoReplyFragment.getInstance(getEventBus());
							case 3:
								return RepeaterConfigEchoAutoReplyFragment.getInstance(getEventBus());
*/
							default:
								return null;
						}
					}
					
					@Override
					public int getCount() {
						return 1;
					}
				};
		viewPagerRepeaterConfig.setAdapter(viewPagerRepeaterConfigAdapter);

		buttonRepeaterConfigSave.setOnClickListener(buttonRepeaterConfigSaveOnClickListener);
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
				new MainActivity.RepeaterConfigFragmentEvent(
						MainActivity.RepeaterConfigFragmentEventType.OnFragmentCreated,
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

	private Spinner.OnItemSelectedListener spinnerRepeaterModuleOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					String module = (String)spinnerRepeaterModule.getItemAtPosition(position);
					if(module != null && module.length() >= 1) {
						char currentModule = module.charAt(0);
						
						getRepeaterConfig().setSelectedModule(currentModule);
						updateViewByRepeaterModule(currentModule);
						
						updateFragmentData();
						
						sendCurrentRepeaterModuleConfigToParent();
					}
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {

				}
			};
	
	private Button.OnClickListener buttonRepeaterConfigSaveOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					RepeaterTypes repeaterType =
							RepeaterTypes.getTypeByTypeName(getCurrentRepeaterModuleConfig().getRepeaterType());
					if(repeaterType == null){return;}
					
					switch(repeaterType){
						case Internal:
							getEventBus().post(
									new RepeaterConfigInternalFragment.RepeaterConfigFragmentEvent(
											RepeaterConfigInternalFragment.RepeaterConfigFragmentEventType.RequestSaveConfig,
											null
									)
							);
							break;
/*
						case ExternalHomebrew:
							getEventBus().post(
									new RepeaterConfigExternalHomebrewFragment.RepeaterConfigFragmentEvent(
											RepeaterConfigExternalHomebrewFragment.RepeaterConfigFragmentEventType.RequestSaveConfig,
											null
									)
							);
							break;
						case EchoAutoReply:
							getEventBus().post(
									new RepeaterConfigEchoAutoReplyFragment.RepeaterConfigFragmentEvent(
											RepeaterConfigEchoAutoReplyFragment.RepeaterConfigFragmentEventType.RequestSaveConfig,
											null
									)
							);
							break;
						case VoiceroidAutoReply:
							getEventBus().post(
									new RepeaterConfigVoiceroidAutoReplyFragment.RepeaterConfigFragmentEvent(
											RepeaterConfigVoiceroidAutoReplyFragment.RepeaterConfigFragmentEventType.RequestSaveConfig,
											null
									)
							);
							break;
 */
					}
				}
			};

	private Switch.OnCheckedChangeListener switchRepeaterModuleEnabledOnCheckedChangeListener =
			new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
					RepeaterModuleConfig moduleConfig = getCurrentRepeaterModuleConfig();

					moduleConfig.setRepeaterEnabled(isChecked);

					sendCurrentRepeaterModuleConfigToParent();
				}
			};

	private Spinner.OnItemSelectedListener spinnerRepeaterTypeOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					String type = (String)spinnerRepeaterType.getItemAtPosition(position);
					RepeaterTypes repeaterType = RepeaterTypes.getTypeByTypeName(type);

					RepeaterModuleConfig moduleConfig = getCurrentRepeaterModuleConfig();

					moduleConfig.setRepeaterType(repeaterType.getTypeName());

					viewPagerRepeaterConfig.setCurrentItem(position);
					
					updateFragmentData();

					sendCurrentRepeaterModuleConfigToParent();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {

				}
			};

	private void updateView(){

		updateViewByRepeaterModule(getRepeaterConfig().getSelectedModule());
		
		final boolean enable = !serviceRunning;
		
		spinnerRepeaterModule.setEnabled(enable);
		switchRepeaterModuleEnabled.setEnabled(enable);
		spinnerRepeaterType.setEnabled(enable);
		buttonRepeaterConfigSave.setEnabled(enable);
	}

	private boolean updateViewByRepeaterModule(final char module){
		if(serviceRunning){
			switchRepeaterModuleEnabled.setEnabled(false);
			spinnerRepeaterType.setEnabled(false);
		}
		else{
			switchRepeaterModuleEnabled.setEnabled(true);
			spinnerRepeaterType.setEnabled(true);
		}
		log.trace("Setting repeater module" + module + "....");
		RepeaterModuleConfig moduleConfig = getRepeaterModuleConfig(module);

		Adapter adapter = spinnerRepeaterModule.getAdapter();
		for(int i = 0; i < adapter.getCount(); i++) {
			String currentModule = adapter.getItem(i).toString();
			
			if (String.valueOf(module).equals(currentModule)) {
				spinnerRepeaterModule.setOnItemSelectedListener(null);
				spinnerRepeaterModule.setSelection(i, false);
				spinnerRepeaterModule.setOnItemSelectedListener(spinnerRepeaterModuleOnItemSelectedListener);
				log.trace("Hit repeater module selection " + module + ".");
				break;
			}
		}
		
		switchRepeaterModuleEnabled.setOnCheckedChangeListener(null);
		switchRepeaterModuleEnabled.setChecked(moduleConfig.isRepeaterEnabled());
		switchRepeaterModuleEnabled.setOnCheckedChangeListener(switchRepeaterModuleEnabledOnCheckedChangeListener);
		
		String[] repeaterTypes =
				getRepeaterTypes().keySet().toArray(new String[getRepeaterTypes().size()]);
		
		for(int index = 0; index < repeaterTypes.length; index++){
			String type = repeaterTypes[index];
			
			if(type.equals(moduleConfig.getRepeaterType())){
				spinnerRepeaterType.setOnItemSelectedListener(null);
				spinnerRepeaterType.setSelection(index, false);
				spinnerRepeaterType.setOnItemSelectedListener(spinnerRepeaterTypeOnItemSelectedListener);
				viewPagerRepeaterConfig.setCurrentItem(index);
				break;
			}
		}

		return true;
	}

	private RepeaterModuleConfig getCurrentRepeaterModuleConfig(){
		return getRepeaterModuleConfig(getRepeaterConfig().getSelectedModule());
	}

	private RepeaterModuleConfig getRepeaterModuleConfig(char module){
		RepeaterModuleConfig moduleConfig =
				getRepeaterConfig().getRepeaterModules().get(module);

		if(moduleConfig == null) {
			moduleConfig = new RepeaterModuleConfig(module);
			getRepeaterConfig().getRepeaterModules().put(module, moduleConfig);
		}

		return moduleConfig;
	}

	private boolean sendCurrentRepeaterModuleConfigToParent(){
		if(getContext() == null || isDetached()){return false;}

		RepeaterModuleConfig moduleConfig = getCurrentRepeaterModuleConfig();

		getEventBus().post(
				new MainActivity.RepeaterConfigFragmentEvent(
						MainActivity.RepeaterConfigFragmentEventType.UpdateModuleConfig,
						moduleConfig.clone()
				)
		);

		return true;
	}

	private RepeaterTypes getCurrentRepeaterType(){
		String currentRepeaterType = (String)spinnerRepeaterType.getSelectedItem();

		RepeaterTypes repeaterType =
			RepeaterTypes.getTypeByTypeName(currentRepeaterType);

		return repeaterType;
	}

	private void updateFragmentData(){
		RepeaterModuleConfig moduleConfig = getCurrentRepeaterModuleConfig();

		switch (RepeaterTypes.getTypeByTypeName(moduleConfig.getRepeaterType())){
			case Internal:{
				updateRepeaterConfigInternalFragmentData();
				break;
			}
			case ExternalHomebrew:{
				updateRepeaterConfigExternalHomebrewFragmentData();
				break;
			}
			case VoiceroidAutoReply:{
				updateRepeaterConfigVoiceroidAutoReplyFragmentData();
				break;
			}
			case EchoAutoReply:{
				updateRepeaterConfigEchoAutoReplyFragmentData();
				break;
			}
		}
	}

	private void updateRepeaterConfigInternalFragmentData(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigFragmentEventType.UpdateData,
						new RepeaterConfigInternalFragmentData(
								serviceRunning,
								getCurrentRepeaterModuleConfig().getInternalRepeaterConfig().clone()
						)
				)
		);
	}

	private void updateRepeaterConfigExternalHomebrewFragmentData(){
		getEventBus().post(
				new RepeaterConfigExternalHomebrewFragment.RepeaterConfigFragmentEvent(
						RepeaterConfigExternalHomebrewFragment.RepeaterConfigFragmentEventType.UpdateData,
						new RepeaterConfigExternalHomebrewFragmentData(
								serviceRunning,
								getCurrentRepeaterModuleConfig().getExternalHomebrewRepeaterConfig().clone()
						)
				)
		);
	}

	private void updateRepeaterConfigVoiceroidAutoReplyFragmentData(){
		getEventBus().post(
				new RepeaterConfigVoiceroidAutoReplyFragment.RepeaterConfigFragmentEvent(
						RepeaterConfigVoiceroidAutoReplyFragment.RepeaterConfigFragmentEventType.UpdateData,
						new RepeaterConfigVoiceroidAutoReplyFragmentData(
								serviceRunning,
								getCurrentRepeaterModuleConfig().getVoiceroidAutoReplyRepeaterConfig().clone()
						)
				)
		);
	}

	private void updateRepeaterConfigEchoAutoReplyFragmentData(){
		getEventBus().post(
				new RepeaterConfigEchoAutoReplyFragment.RepeaterConfigFragmentEvent(
						RepeaterConfigEchoAutoReplyFragment.RepeaterConfigFragmentEventType.UpdateData,
						new RepeaterConfigEchoAutoReplyFragmentData(
								serviceRunning,
								getCurrentRepeaterModuleConfig().getEchoAutoReplyRepeaterConfig().clone()
						)
				)
		);
	}
}
