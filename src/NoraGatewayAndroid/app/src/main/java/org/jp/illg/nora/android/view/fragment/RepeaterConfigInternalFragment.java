package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.dstar.model.defines.ModemTypes;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DSTARUtils;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemMMDVMBluetoothData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemMMDVMData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemAccessPointFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData;
import org.jp.illg.nora.android.view.model.InternalRepeaterConfig;
import org.jp.illg.nora.android.view.model.InternalRepeaterConfigBundler;
import org.jp.illg.nora.android.view.model.ModemAccessPointConfig;
import org.jp.illg.nora.android.view.model.ModemMMDVMBluetoothConfig;
import org.jp.illg.nora.android.view.model.ModemMMDVMConfig;
import org.jp.illg.nora.android.view.model.ModemNewAccessPointBluetoothConfig;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.AlertDialogFragment;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigInternalFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter
	@State(InternalRepeaterConfigBundler.class)
	InternalRepeaterConfig internalRepeaterConfig;

	@BindView(R.id.spinnerRepeaterConfigInternalModemType)
	Spinner spinnerRepeaterConfigInternalModemType;

	@BindView(R.id.editTextRepeaterConfigInternalDirectMyCallsigns)
	EditText editTextRepeaterConfigInternalDirectMyCallsigns;

	@BindView(R.id.viewPagerRepeaterConfigInternalModem)
	ViewPager viewPagerRepeaterConfigInternalModem;


//	NoraFragmentPagerAdapter modemPagerAdapter;

	@State
	boolean serviceRunning;

	{
		internalRepeaterConfig = new InternalRepeaterConfig();
		serviceRunning = false;
	}


	public static class RepeaterConfigFragmentEvent extends EventBusEvent<RepeaterConfigFragmentEventType> {
		public RepeaterConfigFragmentEvent(RepeaterConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}


	public enum RepeaterConfigFragmentEventType{
		UpdateData{
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof RepeaterConfigInternalFragmentData){
					RepeaterConfigInternalFragmentData data = (RepeaterConfigInternalFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					if(data.getInternalRepeaterConfig() != null)
						fragment.setInternalRepeaterConfig(data.getInternalRepeaterConfig());

					fragment.updateView();
					fragment.updateFragmentData();
				}
			}
		},
		ResultUartPorts{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				log.trace("Receive event " +
						RepeaterConfigFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigFragmentEventType.ResultUartPorts.toString());
				
				fragment.getEventBus().post(
						new RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEvent(
								RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEventType.ResultUartPorts,
								attachment
						)
				);
				fragment.getEventBus().post(
						new RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEvent(
								RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEventType.ResultUartPorts,
								attachment
						)
				);
				
			}
		},
		RequestSaveConfig {
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment) {
				ModemTypes modemType =
						ModemTypes.getTypeByTypeName(fragment.getInternalRepeaterConfig().getModemType());
				if(modemType == null){return;}
				
				switch(modemType){
					case AccessPoint:
					case NewAccessPoint:
						fragment.getEventBus().post(
								new RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEvent(
										RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEventType.RequestSaveConfig,
										null
								)
						);
						break;
					case MMDVM:
						fragment.getEventBus().post(
								new RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEvent(
										RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEventType.RequestSaveConfig,
										null
								)
						);
						break;
					case MMDVMBluetooth:
						fragment.getEventBus().post(
								new RepeaterConfigInternalModemMMDVMBluetoothFragment.RepeaterConfigInternalFragmentEvent(
										RepeaterConfigInternalModemMMDVMBluetoothFragment.RepeaterConfigInternalFragmentEventType.RequestSaveConfig,
										null
								)
						);
						break;
						
					case NewAccessPointBluetooth:
						fragment.getEventBus().post(
								new RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEvent(
										RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEventType.RequestSaveConfig,
										null
								)
						);
						break;
				}
			}
		},
		;

		abstract void apply(RepeaterConfigInternalFragment fragment, Object attachment);
	}


	public static class RepeaterConfigInternalModemAccessPointFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemAccessPointFragmentEventType> {
		public RepeaterConfigInternalModemAccessPointFragmentEvent(
				RepeaterConfigInternalModemAccessPointFragmentEventType eventType,
				Object attachment
		){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigInternalModemAccessPointFragmentEventType{
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				fragment.updateRepeaterConfigInternalModemAccessPointFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof ModemAccessPointConfig){
					fragment.getInternalRepeaterConfig().setModemAccessPointConfig(
							(ModemAccessPointConfig)attachment
					);
					
					fragment.sendConfigToParent();
				}
			}
		},
		RequestUartPorts{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				log.trace("Receive event " +
					RepeaterConfigInternalModemAccessPointFragmentEvent.class.getSimpleName() + "." +
					RepeaterConfigInternalModemAccessPointFragmentEventType.RequestUartPorts.toString());
				
				fragment.getEventBus().post(
						new RepeaterConfigFragment.RepeaterConfigInternalFragmentEvent(
								RepeaterConfigFragment.RepeaterConfigInternalFragmentEventType.RequestUartPorts,
								attachment
						)
				);
			}
		},
		;

		abstract void apply(RepeaterConfigInternalFragment fragment, Object attachment);
	}
	
	public static class RepeaterConfigInternalModemMMDVMFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemMMDVMFragmentEventType> {
		public RepeaterConfigInternalModemMMDVMFragmentEvent(
				RepeaterConfigInternalModemMMDVMFragmentEventType eventType,
				Object attachment
		){
			super(eventType, attachment);
		}
	}
	
	public enum RepeaterConfigInternalModemMMDVMFragmentEventType{
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				fragment.updateRepeaterConfigInternalModemMMDVMFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof ModemMMDVMConfig){
					fragment.getInternalRepeaterConfig().setModemMMDVMConfig(
							(ModemMMDVMConfig)attachment
					);
					
					fragment.sendConfigToParent();
				}
			}
		},
		RequestUartPorts{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				log.trace("Receive event " +
						RepeaterConfigInternalModemMMDVMFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigInternalModemMMDVMFragmentEventType.RequestUartPorts.toString());
				
				fragment.getEventBus().post(
						new RepeaterConfigFragment.RepeaterConfigInternalFragmentEvent(
								RepeaterConfigFragment.RepeaterConfigInternalFragmentEventType.RequestUartPorts,
								attachment
						)
				);
			}
		},
		;
		
		abstract void apply(RepeaterConfigInternalFragment fragment, Object attachment);
	}
	
	public static class RepeaterConfigInternalModemMMDVMBluetoothFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemMMDVMBluetoothFragmentEventType> {
		public RepeaterConfigInternalModemMMDVMBluetoothFragmentEvent(
				RepeaterConfigInternalModemMMDVMBluetoothFragmentEventType eventType,
				Object attachment
		){
			super(eventType, attachment);
		}
	}
	
	public enum RepeaterConfigInternalModemMMDVMBluetoothFragmentEventType{
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				fragment.updateRepeaterConfigInternalModemMMDVMBluetoothFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof ModemMMDVMBluetoothConfig){
					fragment.getInternalRepeaterConfig().setModemMMDVMBluetoothConfig(
							(ModemMMDVMBluetoothConfig)attachment
					);
					
					fragment.sendConfigToParent();
				}
			}
		},
		;
		
		abstract void apply(RepeaterConfigInternalFragment fragment, Object attachment);
	}
	
	public static class RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEventType> {
		public RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent(
				RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEventType eventType,
				Object attachment
		){
			super(eventType, attachment);
		}
	}
	
	public enum RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEventType{
		OnFragmentCreated{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				fragment.updateRepeaterConfigInternalModemNewAccessPointBluetoothFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(RepeaterConfigInternalFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof ModemNewAccessPointBluetoothConfig){
					fragment.getInternalRepeaterConfig().setModemNewAccessPointBluetoothConfig(
							(ModemNewAccessPointBluetoothConfig)attachment
					);
					
					fragment.sendConfigToParent();
				}
			}
		},
		;
		
		abstract void apply(RepeaterConfigInternalFragment fragment, Object attachment);
	}

	private TextWatcher editTextRepeaterConfigInternalDirectMyCallsignsTextWatcher =
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
						RepeaterConfigInternalFragment.this.getClass().getSimpleName() +
							".editTextRepeaterConfigInternalDirectMyCallsignsTextWatcher.afterTextChanged()"
					);
				}

				final String directMyCallsignsString = s.toString().toUpperCase(Locale.ENGLISH);

				final StringBuilder illegalCallsignsB = new StringBuilder();
				final StringBuilder validCallsignsB = new StringBuilder();

				final List<String> directCallsigns =
					Arrays.asList(directMyCallsignsString.split(","));
				for(final Iterator<String> it = directCallsigns.iterator(); it.hasNext();){
					final String directCallsign = DSTARUtils.formatFullCallsign(it.next(), ' ');

					if(
						!CallSignValidator.isValidUserCallsign(directCallsign) ||
						CallSignValidator.isValidJARLRepeaterCallsign(directCallsign)
					) {
						if(illegalCallsignsB.length() > 0){illegalCallsignsB.append(',');}

						illegalCallsignsB.append(directCallsign);
					}
					else{
						if(validCallsignsB.length() > 0){validCallsignsB.append(',');}

						validCallsignsB.append(directCallsign.trim());
					}
				}

				final String illegalCallsigns = illegalCallsignsB.toString();

				if(illegalCallsigns.length() > 0)
					editTextRepeaterConfigInternalDirectMyCallsigns.setError("Input callsign is invalid = " + illegalCallsigns);
				else
					editTextRepeaterConfigInternalDirectMyCallsigns.setError(null);

				getInternalRepeaterConfig().setDirectMyCallsigns(validCallsignsB.toString());
			}
		};

	public RepeaterConfigInternalFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(RepeaterConfigInternalFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static RepeaterConfigInternalFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		RepeaterConfigInternalFragment instance = new RepeaterConfigInternalFragment();
		RepeaterConfigInternalFragment.setEventBus(eventBus);

		return instance;
	}

	@Subscribe
	public void onRepeaterConfigFragmentEvent(RepeaterConfigFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigInternalModemAccessPointFragmentEvent(RepeaterConfigInternalModemAccessPointFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}
	
	@Subscribe
	public void onRepeaterConfigInternalModemMMDVMFragmentEvent(RepeaterConfigInternalModemMMDVMFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}
	
	@Subscribe
	public void onRepeaterConfigInternalModemMMDVMBluetoothFragmentEvent(RepeaterConfigInternalModemMMDVMBluetoothFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}
	
	@Subscribe
	public void onRepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent(RepeaterConfigInternalModemNewAccessPointBluetoothFragmentEvent event){
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
		View view = inflater.inflate(R.layout.repeater_config_internal_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		List<String> modemTypes = new LinkedList<>();
		modemTypes.add(ModemTypes.AccessPoint.getTypeName());
		modemTypes.add(ModemTypes.NewAccessPoint.getTypeName());
		modemTypes.add(ModemTypes.MMDVM.getTypeName());
		modemTypes.add(ModemTypes.MMDVMBluetooth.getTypeName());
		modemTypes.add(ModemTypes.NewAccessPointBluetooth.getTypeName());

		ArrayAdapter<String> modemTypeAdapter = new ArrayAdapter<String>(
				getContext(),
				R.layout.spinner_list_style,
				modemTypes
			);
		modemTypeAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinnerRepeaterConfigInternalModemType.setAdapter(modemTypeAdapter);
/*
		modemPagerAdapter =
				new NoraFragmentPagerAdapter(getChildFragmentManager(), view.getContext(), getEventBus());

		modemPagerAdapter.addPage(RepeaterConfigInternalModemAccessPointFragment.class, ModemTypes.AccessPoint.getTypeName());
		modemPagerAdapter.addPage(RepeaterConfigInternalModemAccessPointFragment.class, ModemTypes.NewAccessPoint.getTypeName());
*/
		FragmentPagerAdapter modemPagerAdapter =
				new FragmentPagerAdapter(getChildFragmentManager(), FragmentPagerAdapter.BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
					@Override
					public Fragment getItem(int i) {
						switch(i){
							case 0:
							case 1:
								return RepeaterConfigInternalModemAccessPointFragment.getInstance(getEventBus());
							case 2:
								return RepeaterConfigInternalModemMMDVMFragment.getInstance(getEventBus());
							case 3:
								return RepeaterConfigInternalModemMMDVMBluetoothFragment.getInstance(getEventBus());
							case 4:
								return RepeaterConfigInternalModemNewAccessPointBluetoothFragment.getInstance(getEventBus());
							default:
								return null;
						}
					}
					
					@Override
					public int getCount() {
						return 5;
					}
				};
		viewPagerRepeaterConfigInternalModem.setAdapter(modemPagerAdapter);
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
				new RepeaterConfigFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigInternalFragmentEventType.OnFragmentCreated,
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

	private Spinner.OnItemSelectedListener spinnerRepeaterConfigInternalModemTypeOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					String selectedModemType = (String)parent.getItemAtPosition(position);

					getInternalRepeaterConfig().setModemType(selectedModemType);
					
					updateView();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {

				}
			};

	private void updateView(){
		if(serviceRunning){
			spinnerRepeaterConfigInternalModemType.setEnabled(false);
		}
		else{
			spinnerRepeaterConfigInternalModemType.setEnabled(true);
		}
		
		SpinnerAdapter modemTypeAdapter = spinnerRepeaterConfigInternalModemType.getAdapter();
		if(modemTypeAdapter != null) {
			int itemCount = modemTypeAdapter.getCount();
			int targetIndex = 0;
			for (int index = 0; index < itemCount; index++) {
				String type = (String)modemTypeAdapter.getItem(index);
				if(type.equals(getInternalRepeaterConfig().getModemType())){targetIndex = index;break;}
			}
			spinnerRepeaterConfigInternalModemType.setOnItemSelectedListener(null);
			spinnerRepeaterConfigInternalModemType.setSelection(targetIndex, false);
			spinnerRepeaterConfigInternalModemType.setOnItemSelectedListener(spinnerRepeaterConfigInternalModemTypeOnItemSelectedListener);
			
			viewPagerRepeaterConfigInternalModem.setCurrentItem(targetIndex);
		}

		ViewUtil.consumerWhileViewDisabled(
			editTextRepeaterConfigInternalDirectMyCallsigns,
			new Consumer<TextView>() {
				@Override
				public void accept(TextView textView) {
					textView.removeTextChangedListener(editTextRepeaterConfigInternalDirectMyCallsignsTextWatcher);
					textView.setText(getInternalRepeaterConfig().getDirectMyCallsigns());
					textView.addTextChangedListener(editTextRepeaterConfigInternalDirectMyCallsignsTextWatcher);
				}
			}
		);
	}

	private void updateFragmentData(){

		final String modemType = getInternalRepeaterConfig().getModemType();
		if(modemType == null){return;}
		
		if(
				modemType.equals(ModemTypes.AccessPoint.getTypeName()) ||
				modemType.equals(ModemTypes.NewAccessPoint.getTypeName())
		){
				updateRepeaterConfigInternalModemAccessPointFragmentData();

		}
		else if(modemType.equals(ModemTypes.MMDVM.getTypeName())){
				updateRepeaterConfigInternalModemMMDVMFragmentData();;
		}
		else if(modemType.equals(ModemTypes.MMDVMBluetooth.getTypeName())){
			updateRepeaterConfigInternalModemMMDVMBluetoothFragmentData();
		}
		else if(modemType.equals(ModemTypes.NewAccessPointBluetooth.getTypeName())){
			updateRepeaterConfigInternalModemNewAccessPointBluetoothFragmentData();
		}
	}

	private void updateRepeaterConfigInternalModemAccessPointFragmentData(){
		getEventBus().post(
				new RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigInternalModemAccessPointFragment.RepeaterConfigInternalFragmentEventType.UpdateData,
						new RepeaterConfigInternalModemAccessPointFragmentData(
								serviceRunning, getInternalRepeaterConfig().getModemAccessPointConfig().clone()
						)
				)
		);
	}
	
	private void updateRepeaterConfigInternalModemMMDVMFragmentData(){
		getEventBus().post(
				new RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigInternalModemMMDVMFragment.RepeaterConfigInternalFragmentEventType.UpdateData,
						new RepeaterConfigInternalModemMMDVMData(
								serviceRunning, getInternalRepeaterConfig().getModemMMDVMConfig().clone()
						)
				)
		);
	}
	
	private void updateRepeaterConfigInternalModemMMDVMBluetoothFragmentData(){
		getEventBus().post(
				new RepeaterConfigInternalModemMMDVMBluetoothFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigInternalModemMMDVMBluetoothFragment.RepeaterConfigInternalFragmentEventType.UpdateData,
						new RepeaterConfigInternalModemMMDVMBluetoothData(
								serviceRunning, getInternalRepeaterConfig().getModemMMDVMBluetoothConfig().clone()
						)
				)
		);
	}
	
	private void updateRepeaterConfigInternalModemNewAccessPointBluetoothFragmentData(){
		getEventBus().post(
				new RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigInternalModemNewAccessPointBluetoothFragment.RepeaterConfigInternalFragmentEventType.UpdateData,
						new RepeaterConfigInternalModemNewAccessPointBluetoothFragmentData(
								serviceRunning, getInternalRepeaterConfig().getModemNewAccessPointBluetoothConfig().clone()
						)
				)
		);
	}

	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigFragment.RepeaterConfigInternalFragmentEvent(
						RepeaterConfigFragment.RepeaterConfigInternalFragmentEventType.UpdateConfig,
						getInternalRepeaterConfig().clone()
				)
		);
		
		new AlertDialogFragment.Builder()
				.setTitle("Save Config")
				.setMessage("Config saved.")
				.setPositiveButton("OK")
				.setStyle(R.style.NoraGatewayTheme_Default_AlertDialogStyle)
				.build(0x3021)
				.showOn(this, RepeaterConfigFragment.class.getSimpleName() + ".SAVE_CONFIG");
	}
}
