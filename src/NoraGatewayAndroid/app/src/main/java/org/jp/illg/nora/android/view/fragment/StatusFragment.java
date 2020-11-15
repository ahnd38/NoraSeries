package org.jp.illg.nora.android.view.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;

import androidx.fragment.app.FragmentTransaction;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.ToLongFunction;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.dstar.DSTARDefines;
import org.jp.illg.nora.NoraGatewayForAndroid;
import org.jp.illg.nora.android.reporter.model.GatewayRouteStatusReport;
import org.jp.illg.nora.android.reporter.model.GatewayStatusReport;
import org.jp.illg.nora.android.reporter.model.NoraGatewayStatusInformation;
import org.jp.illg.nora.android.reporter.model.RepeaterRouteStatusReport;
import org.jp.illg.nora.android.reporter.model.RepeaterStatusReport;
import org.jp.illg.nora.android.view.fragment.model.LogFragmentData;
import org.jp.illg.nora.android.view.fragment.model.StatusFragmentData;
import org.jp.illg.nora.android.view.model.StatusConfig;
import org.jp.illg.nora.android.view.model.StatusConfigBundler;
import org.jp.illg.nora.MainActivity;
import org.jp.illg.noragateway.R;
import org.jp.illg.util.android.FragmentUtil;
import org.jp.illg.util.android.view.EventBusEvent;
import org.parceler.Parcels;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Bundler;
import icepick.Icepick;
import icepick.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StatusFragment extends FragmentBase {

	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus eventBus;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(StatusConfigBundler.class)
	StatusConfig statusConfig;

	@BindView(R.id.buttonStart)
	Button buttonStart;

	@BindView(R.id.buttonStop)
	Button buttonStop;

	@BindView(R.id.textViewStatusMyCall)
	TextView textViewStatusMyCall;

	@BindView(R.id.textViewStatusYourCall)
	TextView textViewStatusYourCall;

	@BindView(R.id.textViewStatusRPT1Call)
	TextView textViewStatusRPT1Call;

	@BindView(R.id.textViewStatusRPT2Call)
	TextView textViewStatusRPT2Call;

	@BindView(R.id.textViewStatus)
	TextView textViewStatus;

	@BindView(R.id.buttonViewLog)
	Button buttonViewLog;

	@BindView(R.id.checkboxDisableDisplaySleep)
	Switch checkboxDisableDisplaySleep;

	@BindView(R.id.textviewApplicationVersion)
	TextView textViewApplicationVersion;

	@State
	boolean serviceRunning;

	@State(NoraGatewayStatusInformationBundler.class)
	NoraGatewayStatusInformation statusInformation;
	public static class NoraGatewayStatusInformationBundler implements Bundler<NoraGatewayStatusInformation>{
		@Override
		public void put(String key, NoraGatewayStatusInformation item, Bundle bundle) {
			bundle.putParcelable(key, Parcels.wrap(item));
		}

		@Override
		public NoraGatewayStatusInformation get(String key, Bundle bundle) {
			return Parcels.unwrap(bundle.getParcelable(key));
		}
	}


	public static class MainActivityEvent extends EventBusEvent<MainActivityEventType>{
		public MainActivityEvent(MainActivityEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum MainActivityEventType{
		UpdateData{
			@Override
			void apply(StatusFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof StatusFragmentData){
					StatusFragmentData data = (StatusFragmentData)attachment;

					fragment.serviceRunning = data.isServiceRunning();
					fragment.statusConfig = data.getStatusConfig();

					if(!data.isServiceRunning()){fragment.statusInformation = null;}

					fragment.updateView();
				}
			}
		},
		NotifyLog{
			@Override
			void apply(StatusFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof String){
					fragment.getEventBus().post(
							new LogFragment.StatusFragmentEvent(
									LogFragment.StatusFragmentEventType.UpdateData,
									new LogFragmentData(fragment.serviceRunning, attachment.toString())
							)
					);
				}
			}
		},
		NotifyStatusReport{
			@Override
			void apply(StatusFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof NoraGatewayStatusInformation){
					NoraGatewayStatusInformation statusInfo = (NoraGatewayStatusInformation)attachment;

					fragment.statusInformation = statusInfo;

					fragment.updateView();
				}
			}
		},
		NotifySavedLogs{
			@Override
			void apply(StatusFragment fragment, Object attachment){
				if(attachment != null && attachment instanceof String[]){
					String[] savedLogs = (String[])attachment;

					fragment.getEventBus().post(
							new LogFragment.StatusFragmentEvent(
									LogFragment.StatusFragmentEventType.UpdateData,
									new LogFragmentData(fragment.serviceRunning, savedLogs)
							)
					);
				}
			}
		},
		ResponseGatewayStart {
			void apply(StatusFragment fragment, Object attachment){
				boolean startSuccess = false;

				if(attachment != null && attachment instanceof Boolean)
					startSuccess = (boolean)attachment;

				if(startSuccess){
					fragment.buttonStart.setEnabled(false);
					fragment.buttonStop.setEnabled(true);
				}else{
					new AlertDialog.Builder(fragment.getContext())
							.setTitle("Failed start gateway...")
							.setMessage("Could not start gateway.\nplease check Log.")
							.setPositiveButton("OK", null)
							.show();

					fragment.buttonStart.setEnabled(true);
					fragment.buttonStop.setEnabled(false);
				}
			}
		},
		ResponseGatewayStop {
			void apply(StatusFragment fragment, Object attachment) {
				fragment.buttonStart.setEnabled(true);
				fragment.buttonStop.setEnabled(false);
			}
		},
		UpdateConfig{
			@Override
			void apply(StatusFragment fragment, Object attachment){
				log.trace(
						"Receive event " +
								GatewayConfigFragment.MainActivityEvent.class.getSimpleName() + "." +
								GatewayConfigFragment.MainActivityEventType.UpdateConfig
				);

				if(attachment != null && attachment instanceof StatusConfig){
					fragment.setStatusConfig((StatusConfig)attachment);

					fragment.updateView();
				}
			}
		},
		;

		abstract void apply(StatusFragment fragment, Object attachment);
	}

	public static class LogFragmentEvent extends EventBusEvent<LogFragmentEventType>{
		public LogFragmentEvent(LogFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum LogFragmentEventType {
		RequestSavedLogs {
			@Override
			void apply(StatusFragment fragment, Object attachment) {
				fragment.getEventBus().post(
						new MainActivity.StatusFragmentEvent(
								MainActivity.StatusFragmentEventType.RequestSavedLogs,
								this
						)
				);
			}
		},
		;

		abstract void apply(StatusFragment fragment, Object attachment);
	}

	{
		statusConfig = new StatusConfig();
	}

	@Subscribe
	public void onMainActivityEvent(MainActivityEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onLogFragmentEvent(LogFragmentEvent event){
		if(event.getEventType() != null && FragmentUtil.isAliveFragment(this))
			event.getEventType().apply(this, event.getAttachment());
	}

	public StatusFragment(){
		super();

		if(log.isTraceEnabled())
			log.trace(StatusFragment.class.getSimpleName() + " : Create instance.");

		if(getEventBus() == null){setEventBus(EventBus.getDefault());}
	}

	public static StatusFragment getInstance(EventBus eventBus){
		if(eventBus == null){throw new IllegalArgumentException();}

		StatusFragment instance = new StatusFragment();
		StatusFragment.setEventBus(eventBus);

		return instance;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Icepick.restoreInstanceState(this, savedInstanceState);

		getEventBus().post(
				new MainActivity.StatusFragmentEvent(
						MainActivity.StatusFragmentEventType.OnNewFragment,
						this
				)
		);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	                         Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.status_layout, null);

		ButterKnife.bind(this, view);

		return view;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		buttonStart.setOnClickListener(buttonStartOnClickListener);

		buttonStop.setOnClickListener(buttonStopOnClickListener);

		buttonViewLog.setOnClickListener(buttonViewLogOnClickListener);

		checkboxDisableDisplaySleep.setOnCheckedChangeListener(checkboxDisableDisplaySleepOnCheckedChangeListener);
	}
	
	@Override
	public void onStart(){
		super.onStart();
		
		getEventBus().register(this);
	}
	
	@Override
	public void onResume(){
		super.onResume();;

		getEventBus().post(
				new MainActivity.StatusFragmentEvent(
						MainActivity.StatusFragmentEventType.OnFragmentCreated,
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

	View.OnClickListener buttonStartOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			serviceRunning = true;
			
			getEventBus().post(
					new MainActivity.RepeaterConfigFragmentEvent(
							MainActivity.RepeaterConfigFragmentEventType.RequestUartPorts, null
					)
			);

			getEventBus().post(
				new MainActivity.StatusFragmentEvent(
						MainActivity.StatusFragmentEventType.OnStartRequest, null
				)
			);
		}
	};

	View.OnClickListener buttonStopOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			serviceRunning = false;

			getEventBus().post(
					new MainActivity.StatusFragmentEvent(
							MainActivity.StatusFragmentEventType.OnStopRequest, null
					)
			);
		}
	};

	private Switch.OnCheckedChangeListener checkboxDisableDisplaySleepOnCheckedChangeListener
			= new CompoundButton.OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			log.trace(
					StatusFragment.this.getClass().getSimpleName() +
							".checkboxDisableDisplaySleepOnCheckedChangeListener.onCheckedChanged()"
			);

			if(statusConfig != null){
				statusConfig.setDisableDisplaySleep(isChecked);
			}
			
			sendConfigToParent();
		}
	};

	View.OnClickListener buttonViewLogOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View v) {
			viewLogFragment();
		}
	};

	private void viewLogFragment(){
		LogFragment log = LogFragment.getInstance(getEventBus());

		FragmentTransaction transaction = getFragmentManager().beginTransaction();
		transaction.replace(R.id.mainDrawer, log);
		transaction.addToBackStack(null);
		transaction.commit();
	}

	private void updateHeardView(){

		if(statusInformation != null && statusInformation.getGatewayStatusReport() != null){
			GatewayStatusReport gatewayInfo = statusInformation.getGatewayStatusReport();

			if (gatewayInfo.getRouteReports() != null) {
				Stream.of(gatewayInfo.getRouteReports())
				.sorted(ComparatorCompat.comparingLong(new ToLongFunction<GatewayRouteStatusReport>() {
					@Override
					public long applyAsLong(GatewayRouteStatusReport gatewayRouteStatusReport) {
						return gatewayRouteStatusReport.getFrameSequenceStartTime();
					}
				}))
				.findFirst()
				.ifPresentOrElse(
						new Consumer<GatewayRouteStatusReport>() {
							@Override
							public void accept(GatewayRouteStatusReport gatewayRouteStatusReport) {
								textViewStatusMyCall.setText(
										String.format(
												"%s/%s",
												gatewayRouteStatusReport.getMyCallsign(),
												gatewayRouteStatusReport.getMyCallsignAdd()
										)
								);
								textViewStatusYourCall.setText(
										gatewayRouteStatusReport.getYourCallsign()
								);
								textViewStatusRPT1Call.setText(
										gatewayRouteStatusReport.getRepeater1Callsign()
								);
								textViewStatusRPT2Call.setText(
										gatewayRouteStatusReport.getRepeater2Callsign()
								);
							}
						},
						new Runnable() {
							@Override
							public void run() {
								clearHeardView();
							}
						}
				);

			}
			else{
				clearHeardView();
			}
		}
		else{
			clearHeardView();
		}
	}

	private void clearHeardView(){
		textViewStatusMyCall.setText(
				DSTARDefines.EmptyLongCallsign
		);
		textViewStatusYourCall.setText(
				DSTARDefines.EmptyLongCallsign
		);
		textViewStatusRPT1Call.setText(
				DSTARDefines.EmptyLongCallsign
		);
		textViewStatusRPT2Call.setText(
				DSTARDefines.EmptyLongCallsign
		);
	}

	private void updateStatusInfoView(){
		if(statusInformation != null){
			StringBuilder sb = new StringBuilder();

			sb.append("[Gateway] ");
			GatewayStatusReport gatewayInfo = statusInformation.getGatewayStatusReport();
			if(gatewayInfo != null){
				sb.append("Callsign:");
				if(gatewayInfo.getGatewayCallsign() != null)
					sb.append(gatewayInfo.getGatewayCallsign());
				else
					sb.append("-");

				sb.append("\n");

				if(gatewayInfo.getRouteReports() != null){
					for(GatewayRouteStatusReport routeStatus : gatewayInfo.getRouteReports()){
						sb.append(" -> ");

						sb.append("ID:");
						sb.append(String.format("%04X", routeStatus.getFrameID()));

						sb.append("/");

						sb.append("Mode:");
						sb.append(routeStatus.getRouteMode());

						sb.append("\n");

						sb.append("    ");
						sb.append("UR:");
						sb.append(routeStatus.getYourCallsign());
						sb.append("/");
						sb.append("R1:");
						sb.append(routeStatus.getRepeater1Callsign());
						sb.append("/");
						sb.append(routeStatus.getRepeater2Callsign());
						sb.append("/");
						sb.append("MY:");
						sb.append(routeStatus.getMyCallsign());
						sb.append(" ");
						sb.append(routeStatus.getMyCallsignAdd());

						sb.append("\n");
					}
				}
			}

			sb.append("\n");

			sb.append("[Repeaters]\n");

			List<RepeaterStatusReport> repeaterStatusReports =
					statusInformation.getRepeaterStatusReports();
			if(repeaterStatusReports != null){
				for(RepeaterStatusReport repeaterStatusReport : repeaterStatusReports){
					sb.append(" |- ");

					sb.append("Callsign:");
					sb.append(repeaterStatusReport.getRepeaterCallsign());

					sb.append("/");

					sb.append("Reflector:");
					sb.append(
							String.format(
									"%-" + DSTARDefines.CallsignFullLength + "S",
									repeaterStatusReport.getLinkedReflectorCallsign()
							)
					);

					sb.append("/");

					sb.append("Routing:");
					sb.append(repeaterStatusReport.getRoutingService().getTypeName());

					sb.append("\n");

					List<RepeaterRouteStatusReport> repeaterRouteStatusReports =
							repeaterStatusReport.getRouteReports();
					if(repeaterRouteStatusReports != null){
						for(RepeaterRouteStatusReport repeaterRouteStatusReport : repeaterRouteStatusReports){
							sb.append("    |-> ");

							sb.append("ID:");
							sb.append(String.format("%04X", repeaterRouteStatusReport.getFrameID()));

							sb.append("/");

							sb.append("Mode:");
							sb.append(repeaterRouteStatusReport.getRouteMode());

							sb.append("\n");

							sb.append("        ");

							sb.append("UR:");
							sb.append(repeaterRouteStatusReport.getYourCallsign());
							sb.append("/");
							sb.append("R1:");
							sb.append(repeaterRouteStatusReport.getRepeater1Callsign());
							sb.append("/");
							sb.append(repeaterRouteStatusReport.getRepeater2Callsign());
							sb.append("/");
							sb.append("MY:");
							sb.append(repeaterRouteStatusReport.getMyCallsign());
							sb.append(" ");
							sb.append(repeaterRouteStatusReport.getMyCallsignAdd());

							sb.append("\n");
						}
					}
				}
			}

			textViewStatus.setText(sb.toString());
		}
		else
			textViewStatus.setText("Not started gateway.");
	}

	private void updateView(){
		textViewApplicationVersion.setText("Ver" + NoraGatewayForAndroid.getApplicationVersion());
		
		if(serviceRunning){
			buttonStart.setEnabled(false);
			buttonStop.setEnabled(true);
		}
		else {
			buttonStart.setEnabled(true);
			buttonStop.setEnabled(false);
		}
		
		updateHeardView();
		updateStatusInfoView();
		
		if(statusConfig != null){
			checkboxDisableDisplaySleep.setChecked(statusConfig.isDisableDisplaySleep());
		}
	}

	private boolean sendConfigToParent(){
		if(getContext() == null || isDetached() || getStatusConfig() == null){return false;}

		getEventBus().post(
				new MainActivity.StatusFragmentEvent(
						MainActivity.StatusFragmentEventType.UpdateConfig, getStatusConfig()
				)
		);

		return true;
	}
}
