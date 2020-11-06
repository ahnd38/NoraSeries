package org.jp.illg.nora.android.view.fragment;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.Switch;

import com.annimon.stream.function.Consumer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemMMDVMData;
import org.jp.illg.nora.android.view.model.ModemMMDVMConfig;
import org.jp.illg.nora.android.view.model.ModemMMDVMConfigBundler;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import java.util.LinkedList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigInternalModemMMDVMFragment extends FragmentBase {
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;
	
	
	@BindView(R.id.spinnerRepeaterConfigModemMMDVMPort)
	Spinner spinnerRepeaterConfigModemMMDVMPort;
	
	@BindView(R.id.buttonRepeaterConfigModemMMDVMRefresh)
	Button buttonRepeaterConfigModemMMDVMRefresh;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMAllowDIRECT)
	Switch switchRepeaterConfigModemMMDVMAllowDIRECT;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMDuplex)
	Switch switchRepeaterConfigModemMMDVMDuplex;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMRxInvert)
	Switch switchRepeaterConfigModemMMDVMRxInvert;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMTxInvert)
	Switch switchRepeaterConfigModemMMDVMTxInvert;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMPTTInvert)
	Switch switchRepeaterConfigModemMMDVMPTTInvert;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMTxDelay)
	EditText editTextRepeaterConfigModemMMDVMTxDelay;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMRxFrequency)
	EditText editTextRepeaterConfigModemMMDVMRxFrequency;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMRxFrequencyOffset)
	EditText editTextRepeaterConfigModemMMDVMRxFrequencyOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMTxFrequency)
	EditText editTextRepeaterConfigModemMMDVMTxFrequency;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMTxFrequencyOffset)
	EditText editTextRepeaterConfigModemMMDVMTxFrequencyOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMRxDCOffset)
	EditText editTextRepeaterConfigModemMMDVMRxDCOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMTxDCOffset)
	EditText editTextRepeaterConfigModemMMDVMTxDCOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMRfLevel)
	EditText editTextRepeaterConfigModemMMDVMRfLevel;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMRxLevel)
	EditText editTextRepeaterConfigModemMMDVMRxLevel;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMTxLevel)
	EditText editTextRepeaterConfigModemMMDVMTxLevel;
	
	@Getter
	@Setter
	@State(ModemMMDVMConfigBundler.class)
	ModemMMDVMConfig modemMMDVMConfig;
	
	@State
	boolean serviceRunning;
	
	public static class RepeaterConfigInternalFragmentEvent extends EventBusEvent<RepeaterConfigInternalFragmentEventType> {
		public RepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}
	
	public enum RepeaterConfigInternalFragmentEventType{
		UpdateData{
			@Override
			void apply(RepeaterConfigInternalModemMMDVMFragment fragment, Object attachment){
				if(
						attachment != null &&
						attachment instanceof RepeaterConfigInternalModemMMDVMData
				){
					RepeaterConfigInternalModemMMDVMData data =
							(RepeaterConfigInternalModemMMDVMData)attachment;
					
					fragment.serviceRunning = data.isServiceRunning();
					fragment.setModemMMDVMConfig(data.getModemMMDVMConfig());
					
					fragment.updateView();
				}
			}
		},
		ResultUartPorts{
			@Override
			void apply(RepeaterConfigInternalModemMMDVMFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof String[]){
					fragment.updateUartPorts((String[])attachment);
					
					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			@Override
			void apply(RepeaterConfigInternalModemMMDVMFragment fragment, Object attachment){
				fragment.sendConfigToParent();
			}
		}
		;
		
		abstract void apply(RepeaterConfigInternalModemMMDVMFragment fragment, Object attachment);
	}
	
	
	
	{
		modemMMDVMConfig = new ModemMMDVMConfig();
	}
	
	public RepeaterConfigInternalModemMMDVMFragment(){
		super();
		
		if(log.isTraceEnabled())
			log.trace(RepeaterConfigInternalModemAccessPointFragment.class.getSimpleName() + " : Create instance.");
		
		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}
	
	public static RepeaterConfigInternalModemMMDVMFragment getInstance(final EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}
		
		RepeaterConfigInternalModemMMDVMFragment instance =
				new RepeaterConfigInternalModemMMDVMFragment();
		RepeaterConfigInternalModemMMDVMFragment.setEventBus(eventBus);
		
		return instance;
	}
	
	@Subscribe
	public void onRepeaterConfigInternalFragmentEvent(RepeaterConfigInternalFragmentEvent event){
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
		View view = inflater.inflate(R.layout.repeater_config_internal_mmdvm, null);
		
		ButterKnife.bind(this, view);
		
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		buttonRepeaterConfigModemMMDVMRefresh.setOnClickListener(
				buttonRepeaterConfigModemMMDVMRefreshOnClickListener
		);
/*
		spinnerRepeaterConfigModemMMDVMPort.setOnItemSelectedListener(
				spinnerRepeaterConfigModemMMDVMOnItemSelectedListener
		);
		
		switchRepeaterConfigModemMMDVMAllowDIRECT.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMAllowDIRECTOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMDuplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMDuplexOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMRxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMRxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMTxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMTxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMPTTInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMPTTInvertOnCheckedChangeListener
		);
		
		editTextRepeaterConfigModemMMDVMTxDelay.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDelayTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMTxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRfLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRfLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxLevelTextWatcher
		);
*/
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
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEventType.OnFragmentCreated,
						this
				)
		);
		
		sendRequestUartPorts();
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
	
	private Button.OnClickListener buttonRepeaterConfigModemMMDVMRefreshOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					sendRequestUartPorts();
				}
			};
	
	private Spinner.OnItemSelectedListener spinnerRepeaterConfigModemMMDVMOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					final String portName =
							(String)spinnerRepeaterConfigModemMMDVMPort.getItemAtPosition(position);
					
					getModemMMDVMConfig().setPortName(portName);
				}
				
				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMAllowDIRECTOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					getModemMMDVMConfig().setAllowDIRECT(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMDuplexOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					getModemMMDVMConfig().setDuplex(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMRxInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					getModemMMDVMConfig().setRxInvert(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMTxInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					getModemMMDVMConfig().setTxInvert(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMPTTInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					getModemMMDVMConfig().setPttInvert(isChecked);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMTxDelayTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMTxDelay.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMTxDelay.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setTxDelay(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMRxFrequencyTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					long value = 0;
					try{
						value = Long.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMRxFrequency.setError("Illegal format error.");
						return;
					}
					
					if(
						(value > 440000000 || value < 430000000) &&
						(value > 146000000 || value < 144000000)
					){
						editTextRepeaterConfigModemMMDVMRxFrequency.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMConfig().setRxFrequency(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMRxFrequencyOffsetTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					long value = 0;
					try{
						value = Long.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMRxFrequencyOffset.setError("Illegal format error.");
						return;
					}
					
					if(
						value < -10000 || value > 10000
					){
						editTextRepeaterConfigModemMMDVMRxFrequencyOffset.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMConfig().setRxFrequencyOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMTxFrequencyTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					long value = 0;
					try{
						value = Long.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMTxFrequency.setError("Illegal format error.");
						return;
					}
					
					if(
							(value > 440000000 || value < 430000000) &&
							(value > 146000000 || value < 144000000)
					){
						editTextRepeaterConfigModemMMDVMTxFrequency.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMConfig().setTxFrequency(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMTxFrequencyOffsetTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					long value = 0;
					try{
						value = Long.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMTxFrequencyOffset.setError("Illegal format error.");
						return;
					}
					
					if(
							value < -10000 || value > 10000
					){
						editTextRepeaterConfigModemMMDVMTxFrequencyOffset.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMConfig().setTxFrequencyOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMRxDCOffsetTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMRxDCOffset.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMRxDCOffset.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setRxDCOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMTxDCOffsetTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMTxDCOffset.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMTxDCOffset.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setTxDCOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMRfLevelTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMRfLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMRfLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setRfLevel(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMRxLevelTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMRxLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMRxLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setRxLevel(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMTxLevelTextWatcher =
			new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
				
				}
				
				@Override
				public void afterTextChanged(Editable editable) {
					int value = 0;
					try{
						value = Integer.valueOf(editable.toString());
					}catch(NumberFormatException ex){
						editTextRepeaterConfigModemMMDVMTxLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMTxLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMConfig().setTxLevel(value);
				}
			};
	
	private void updateUartPorts(String[] ports){
		if(ports == null){ports = new String[]{};}
		
		List<String> portList = new LinkedList<>();
		for(String port : ports){portList.add(port);}
		
		String selectedPort =
				getModemMMDVMConfig().getPortName();
		if(selectedPort == null){selectedPort = "";}
		
		boolean selectedPortAvailable = false;
		for(String port : portList) {
			if (port != null && selectedPort.equals(port))
				selectedPortAvailable = true;
		}
		
		if(!selectedPortAvailable && !"".equals(selectedPort)){
			portList.add(selectedPort);
		}
		
		ArrayAdapter<String> portAdapter =
				new ArrayAdapter<String>(
						getContext(),
						R.layout.spinner_list_style,
						ports
				);
		portAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
		spinnerRepeaterConfigModemMMDVMPort.setAdapter(portAdapter);
	}
	
	private boolean updateView(){
		ViewUtil.consumerWhileViewDisabled(
				spinnerRepeaterConfigModemMMDVMPort,
				new Consumer<Spinner>() {
					@Override
					public void accept(Spinner spinner) {
						SpinnerAdapter portAdapter = spinner.getAdapter();
						
						if(portAdapter != null) {
							int portCount = portAdapter.getCount();
							
							for (int index = 0; index < portCount; index++) {
								String port = (String) portAdapter.getItem(index);
								
								if (port.equals(getModemMMDVMConfig().getPortName())) {
									spinner.setOnItemSelectedListener(null);
									spinner.setSelection(index, false);
									spinner.setOnItemSelectedListener(spinnerRepeaterConfigModemMMDVMOnItemSelectedListener);
									break;
								}
							}
						}
					}
				}
		);
		
		switchRepeaterConfigModemMMDVMAllowDIRECT.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMAllowDIRECT.setChecked(getModemMMDVMConfig().isAllowDIRECT());
		switchRepeaterConfigModemMMDVMAllowDIRECT.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMAllowDIRECTOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMDuplex.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMDuplex.setChecked(getModemMMDVMConfig().isDuplex());
		switchRepeaterConfigModemMMDVMDuplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMDuplexOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMRxInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMRxInvert.setChecked(getModemMMDVMConfig().isRxInvert());
		switchRepeaterConfigModemMMDVMRxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMRxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMTxInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMTxInvert.setChecked(getModemMMDVMConfig().isTxInvert());
		switchRepeaterConfigModemMMDVMTxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMTxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMPTTInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMPTTInvert.setChecked(getModemMMDVMConfig().isPttInvert());
		switchRepeaterConfigModemMMDVMPTTInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMPTTInvertOnCheckedChangeListener
		);
		
		editTextRepeaterConfigModemMMDVMTxDelay.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDelayTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxDelay.setText(String.valueOf(getModemMMDVMConfig().getTxDelay()));
		editTextRepeaterConfigModemMMDVMTxDelay.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDelayTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxFrequency.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxFrequency.setText(String.valueOf(getModemMMDVMConfig().getRxFrequency()));
		editTextRepeaterConfigModemMMDVMRxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxFrequencyOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxFrequencyOffset.setText(String.valueOf(getModemMMDVMConfig().getRxFrequencyOffset()));
		editTextRepeaterConfigModemMMDVMRxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMTxFrequency.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxFrequency.setText(String.valueOf(getModemMMDVMConfig().getTxFrequency()));
		editTextRepeaterConfigModemMMDVMTxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMTxFrequencyOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxFrequencyOffset.setText(String.valueOf(getModemMMDVMConfig().getTxFrequencyOffset()));
		editTextRepeaterConfigModemMMDVMTxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxDCOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxDCOffset.setText(String.valueOf(getModemMMDVMConfig().getRxDCOffset()));
		editTextRepeaterConfigModemMMDVMRxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMTxDCOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxDCOffset.setText(String.valueOf(getModemMMDVMConfig().getTxDCOffset()));
		editTextRepeaterConfigModemMMDVMTxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRfLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMRfLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRfLevel.setText(String.valueOf(getModemMMDVMConfig().getRfLevel()));
		editTextRepeaterConfigModemMMDVMRfLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRfLevelTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMRxLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMRxLevel.setText(String.valueOf(getModemMMDVMConfig().getRxLevel()));
		editTextRepeaterConfigModemMMDVMRxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMRxLevelTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMTxLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMTxLevel.setText(String.valueOf(getModemMMDVMConfig().getTxLevel()));
		editTextRepeaterConfigModemMMDVMTxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMTxLevelTextWatcher
		);
		
		final boolean enableControl = !serviceRunning;

		spinnerRepeaterConfigModemMMDVMPort.setEnabled(enableControl);
		buttonRepeaterConfigModemMMDVMRefresh.setEnabled(enableControl);
		
		switchRepeaterConfigModemMMDVMDuplex.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMRxInvert.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMTxInvert.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMPTTInvert.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMTxDelay.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMRxFrequency.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMRxFrequencyOffset.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMTxFrequency.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMTxFrequencyOffset.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMRxDCOffset.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMTxDCOffset.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMRfLevel.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMRxLevel.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMTxLevel.setEnabled(enableControl);
		
		return true;
	}
	
	private void sendRequestUartPorts(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEventType.RequestUartPorts,
						null
				)
		);
	}
	
	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMFragmentEventType.UpdateConfig,
						getModemMMDVMConfig().clone()
				)
		);
	}
}
