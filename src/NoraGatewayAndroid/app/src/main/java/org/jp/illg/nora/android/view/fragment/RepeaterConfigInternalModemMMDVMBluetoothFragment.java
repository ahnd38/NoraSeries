package org.jp.illg.nora.android.view.fragment;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
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
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigInternalModemMMDVMBluetoothData;
import org.jp.illg.nora.android.view.model.ModemMMDVMBluetoothConfig;
import org.jp.illg.nora.android.view.model.ModemMMDVMBluetoothConfigBundler;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.bluetooth.BluetoothSerial;
import org.jp.illg.util.android.view.EventBusEvent;
import org.jp.illg.util.android.view.ViewUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RepeaterConfigInternalModemMMDVMBluetoothFragment extends FragmentBase {
	
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;
	
	
	@BindView(R.id.spinnerRepeaterConfigModemMMDVMBluetoothPort)
	Spinner spinnerRepeaterConfigModemMMDVMBluetoothPort;
	
	@BindView(R.id.buttonRepeaterConfigModemMMDVMBluetoothRefresh)
	Button buttonRepeaterConfigModemMMDVMBluetoothRefresh;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT)
	Switch switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMBluetoothDuplex)
	Switch switchRepeaterConfigModemMMDVMBluetoothDuplex;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMBluetoothRxInvert)
	Switch switchRepeaterConfigModemMMDVMBluetoothRxInvert;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMBluetoothTxInvert)
	Switch switchRepeaterConfigModemMMDVMBluetoothTxInvert;
	
	@BindView(R.id.switchRepeaterConfigModemMMDVMBluetoothPTTInvert)
	Switch switchRepeaterConfigModemMMDVMBluetoothPTTInvert;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothTxDelay)
	EditText editTextRepeaterConfigModemMMDVMBluetoothTxDelay;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothRxFrequency)
	EditText editTextRepeaterConfigModemMMDVMBluetoothRxFrequency;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset)
	EditText editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothTxFrequency)
	EditText editTextRepeaterConfigModemMMDVMBluetoothTxFrequency;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset)
	EditText editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset)
	EditText editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset)
	EditText editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothRfLevel)
	EditText editTextRepeaterConfigModemMMDVMBluetoothRfLevel;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothRxLevel)
	EditText editTextRepeaterConfigModemMMDVMBluetoothRxLevel;
	
	@BindView(R.id.editTextRepeaterConfigModemMMDVMBluetoothTxLevel)
	EditText editTextRepeaterConfigModemMMDVMBluetoothTxLevel;
	
	@Getter
	@Setter
	@State(ModemMMDVMBluetoothConfigBundler.class)
	ModemMMDVMBluetoothConfig modemMMDVMBluetoothConfig;
	
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
			void apply(RepeaterConfigInternalModemMMDVMBluetoothFragment fragment, Object attachment){
				if(
						attachment != null &&
						attachment instanceof RepeaterConfigInternalModemMMDVMBluetoothData
				){
					RepeaterConfigInternalModemMMDVMBluetoothData data =
							(RepeaterConfigInternalModemMMDVMBluetoothData)attachment;
					
					fragment.serviceRunning = data.isServiceRunning();
					fragment.setModemMMDVMBluetoothConfig(data.getModemMMDVMBluetoothConfig());
					
					fragment.updateView();
				}
			}
		},
		RequestSaveConfig{
			void apply(RepeaterConfigInternalModemMMDVMBluetoothFragment fragment, Object attachment){
				
				fragment.sendConfigToParent();
			}
		}
		;
		
		abstract void apply(RepeaterConfigInternalModemMMDVMBluetoothFragment fragment, Object attachment);
	}
	
	{
		modemMMDVMBluetoothConfig = new ModemMMDVMBluetoothConfig();
	}
	
	public RepeaterConfigInternalModemMMDVMBluetoothFragment(){
		super();
		
		if(log.isTraceEnabled())
			log.trace(RepeaterConfigInternalModemAccessPointFragment.class.getSimpleName() + " : Create instance.");
		
		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}
	
	public static RepeaterConfigInternalModemMMDVMBluetoothFragment getInstance(final EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}
		
		RepeaterConfigInternalModemMMDVMBluetoothFragment instance =
				new RepeaterConfigInternalModemMMDVMBluetoothFragment();
		RepeaterConfigInternalModemMMDVMBluetoothFragment.setEventBus(eventBus);
		
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
		View view = inflater.inflate(R.layout.repeater_config_internal_mmdvmbluetooth, null);
		
		ButterKnife.bind(this, view);
		
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		buttonRepeaterConfigModemMMDVMBluetoothRefresh.setOnClickListener(
				buttonRepeaterConfigModemMMDVMBluetoothRefreshOnClickListener
		);

		spinnerRepeaterConfigModemMMDVMBluetoothPort.setOnItemSelectedListener(
				spinnerRepeaterConfigModemMMDVMBluetoothOnItemSelectedListener
		);

		switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothAllowDIRECTOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothDuplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothDuplexOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothRxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothRxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothTxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothTxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothPTTInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothPTTInvertOnCheckedChangeListener
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxDelay.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDelayTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRfLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRfLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxLevelTextWatcher
		);

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
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMBluetoothFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMBluetoothFragmentEventType.OnFragmentCreated,
						this
				)
		);
		
		updateUartPorts();
		updateView();
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
	
	private Button.OnClickListener buttonRepeaterConfigModemMMDVMBluetoothRefreshOnClickListener =
			new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					updateUartPorts();
					
					updateView();
				}
			};
	
	private Spinner.OnItemSelectedListener spinnerRepeaterConfigModemMMDVMBluetoothOnItemSelectedListener =
			new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					final String portName =
							(String)spinnerRepeaterConfigModemMMDVMBluetoothPort.getItemAtPosition(position);
					
					getModemMMDVMBluetoothConfig().setPortName(portName);
				}
				
				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMBluetoothAllowDIRECTOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemMMDVMBluetoothConfig().setAllowDIRECT(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMBluetoothDuplexOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemMMDVMBluetoothConfig().setDuplex(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMBluetoothRxInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemMMDVMBluetoothConfig().setRxInvert(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMBluetoothTxInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemMMDVMBluetoothConfig().setTxInvert(isChecked);
				}
			};
	
	private Switch.OnCheckedChangeListener switchRepeaterConfigModemMMDVMBluetoothPTTInvertOnCheckedChangeListener =
			new Switch.OnCheckedChangeListener(){
				public void onCheckedChanged(CompoundButton switch_, boolean isChecked){
					
					getModemMMDVMBluetoothConfig().setPttInvert(isChecked);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothTxDelayTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothTxDelay.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMBluetoothTxDelay.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setTxDelay(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.setError("Illegal format error.");
						return;
					}
					
					if(
							(value > 440000000 || value < 430000000) &&
							(value > 146000000 || value < 144000000)
					){
						editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setRxFrequency(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffsetTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.setError("Illegal format error.");
						return;
					}
					
					if(
							value < -10000 || value > 10000
					){
						editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setRxFrequencyOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.setError("Illegal format error.");
						return;
					}
					
					if(
							(value > 440000000 || value < 430000000) &&
							(value > 146000000 || value < 144000000)
					){
						editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setTxFrequency(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffsetTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.setError("Illegal format error.");
						return;
					}
					
					if(
							value < -10000 || value > 10000
					){
						editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.setError("Illegal frequency range error.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setTxFrequencyOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothRxDCOffsetTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setRxDCOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothTxDCOffsetTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 255){
						editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setTxDCOffset(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothRfLevelTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothRfLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMBluetoothRfLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setRfLevel(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothRxLevelTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothRxLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMBluetoothRxLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setRxLevel(value);
				}
			};
	
	private TextWatcher editTextRepeaterConfigModemMMDVMBluetoothTxLevelTextWatcher =
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
						editTextRepeaterConfigModemMMDVMBluetoothTxLevel.setError("Illegal format error.");
						return;
					}
					
					if(value < 0 || value > 100){
						editTextRepeaterConfigModemMMDVMBluetoothTxLevel.setError("Illegal value range.");
						return;
					}
					
					getModemMMDVMBluetoothConfig().setTxLevel(value);
				}
			};
	
	private void updateUartPorts(){
		BluetoothManager bluetoothManager = (BluetoothManager) getContext().getSystemService(Context.BLUETOOTH_SERVICE);
		BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
		if(bluetoothAdapter == null){return;}
		
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		
		List<String> pairedDeviceNames = new ArrayList<>();
		for(final BluetoothDevice pairedDevice : pairedDevices){
			pairedDeviceNames.add(pairedDevice.getName());
		}
		
		updateUartPorts(pairedDeviceNames.toArray(new String[pairedDeviceNames.size()]));
	}
	
	private void updateUartPorts(String[] ports){
		if(ports == null){ports = new String[]{};}
		
		List<String> portList = new LinkedList<>();
		for(String port : ports){portList.add(port);}
		
		String selectedPort =
				getModemMMDVMBluetoothConfig().getPortName();
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
		Spinner.OnItemSelectedListener listener =
			spinnerRepeaterConfigModemMMDVMBluetoothPort.getOnItemSelectedListener();
		spinnerRepeaterConfigModemMMDVMBluetoothPort.setOnItemSelectedListener(null);
		spinnerRepeaterConfigModemMMDVMBluetoothPort.setAdapter(portAdapter);
		spinnerRepeaterConfigModemMMDVMBluetoothPort.setOnItemSelectedListener(listener);
	}
	
	private boolean updateView(){
		
		ViewUtil.consumerWhileViewDisabled(
				spinnerRepeaterConfigModemMMDVMBluetoothPort,
				new Consumer<Spinner>() {
					@Override
					public void accept(Spinner spinner) {
						SpinnerAdapter portAdapter = spinner.getAdapter();
						
						if(portAdapter != null) {
							int portCount = portAdapter.getCount();
							
							for (int index = 0; index < portCount; index++) {
								String port = (String) portAdapter.getItem(index);
								
								if (port.equals(getModemMMDVMBluetoothConfig().getPortName())) {
									spinner.setOnItemSelectedListener(null);
									spinner.setSelection(index, false);
									spinner.setOnItemSelectedListener(spinnerRepeaterConfigModemMMDVMBluetoothOnItemSelectedListener);
									break;
								}
							}
						}
					}
				}
		);
		

		switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT.setChecked(getModemMMDVMBluetoothConfig().isAllowDIRECT());
		switchRepeaterConfigModemMMDVMBluetoothAllowDIRECT.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothAllowDIRECTOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothDuplex.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMBluetoothDuplex.setChecked(getModemMMDVMBluetoothConfig().isDuplex());
		switchRepeaterConfigModemMMDVMBluetoothDuplex.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothDuplexOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothRxInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMBluetoothRxInvert.setChecked(getModemMMDVMBluetoothConfig().isRxInvert());
		switchRepeaterConfigModemMMDVMBluetoothRxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothRxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothTxInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMBluetoothTxInvert.setChecked(getModemMMDVMBluetoothConfig().isTxInvert());
		switchRepeaterConfigModemMMDVMBluetoothTxInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothTxInvertOnCheckedChangeListener
		);
		
		switchRepeaterConfigModemMMDVMBluetoothPTTInvert.setOnCheckedChangeListener(null);
		switchRepeaterConfigModemMMDVMBluetoothPTTInvert.setChecked(getModemMMDVMBluetoothConfig().isPttInvert());
		switchRepeaterConfigModemMMDVMBluetoothPTTInvert.setOnCheckedChangeListener(
				switchRepeaterConfigModemMMDVMBluetoothPTTInvertOnCheckedChangeListener
		);
		

		editTextRepeaterConfigModemMMDVMBluetoothTxDelay.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDelayTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxDelay.setText(String.valueOf(getModemMMDVMBluetoothConfig().getTxDelay()));
		editTextRepeaterConfigModemMMDVMBluetoothTxDelay.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDelayTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.setText(String.valueOf(getModemMMDVMBluetoothConfig().getRxFrequency()));
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.setText(String.valueOf(getModemMMDVMBluetoothConfig().getRxFrequencyOffset()));
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.setText(String.valueOf(getModemMMDVMBluetoothConfig().getTxFrequency()));
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.setText(String.valueOf(getModemMMDVMBluetoothConfig().getTxFrequencyOffset()));
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.setText(String.valueOf(getModemMMDVMBluetoothConfig().getRxDCOffset()));
		editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDCOffsetTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.setText(String.valueOf(getModemMMDVMBluetoothConfig().getTxDCOffset()));
		editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxDCOffsetTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRfLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRfLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRfLevel.setText(String.valueOf(getModemMMDVMBluetoothConfig().getRfLevel()));
		editTextRepeaterConfigModemMMDVMBluetoothRfLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRfLevelTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothRxLevel.setText(String.valueOf(getModemMMDVMBluetoothConfig().getRxLevel()));
		editTextRepeaterConfigModemMMDVMBluetoothRxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothRxLevelTextWatcher
		);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxLevel.removeTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxLevelTextWatcher
		);
		editTextRepeaterConfigModemMMDVMBluetoothTxLevel.setText(String.valueOf(getModemMMDVMBluetoothConfig().getTxLevel()));
		editTextRepeaterConfigModemMMDVMBluetoothTxLevel.addTextChangedListener(
				editTextRepeaterConfigModemMMDVMBluetoothTxLevelTextWatcher
		);
		
		final boolean enableControl = !serviceRunning;
		
		spinnerRepeaterConfigModemMMDVMBluetoothPort.setEnabled(enableControl);
		buttonRepeaterConfigModemMMDVMBluetoothRefresh.setEnabled(enableControl);
		
		switchRepeaterConfigModemMMDVMBluetoothDuplex.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMBluetoothRxInvert.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMBluetoothTxInvert.setEnabled(enableControl);
		switchRepeaterConfigModemMMDVMBluetoothPTTInvert.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMBluetoothTxDelay.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequency.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothRxFrequencyOffset.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequency.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothTxFrequencyOffset.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMBluetoothRxDCOffset.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothTxDCOffset.setEnabled(enableControl);
		
		editTextRepeaterConfigModemMMDVMBluetoothRfLevel.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothRxLevel.setEnabled(enableControl);
		editTextRepeaterConfigModemMMDVMBluetoothTxLevel.setEnabled(enableControl);
		
		return true;
	}
	
	private void sendConfigToParent(){
		getEventBus().post(
				new RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMBluetoothFragmentEvent(
						RepeaterConfigInternalFragment.RepeaterConfigInternalModemMMDVMBluetoothFragmentEventType.UpdateConfig,
						getModemMMDVMBluetoothConfig().clone()
				)
		);
	}
}

