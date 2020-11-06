package org.jp.illg.noragateway;


import android.os.Bundle;
import android.view.MenuItem;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;
import com.crashlytics.android.Crashlytics;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.tabs.TabLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.dstar.util.DStarUtils;
import org.jp.illg.nora.android.view.fragment.GatewayConfigFragment;
import org.jp.illg.nora.android.view.fragment.RepeaterConfigFragment;
import org.jp.illg.nora.android.view.fragment.StatusFragment;
import org.jp.illg.nora.android.view.fragment.model.GatewayConfigFragmentData;
import org.jp.illg.nora.android.view.fragment.model.RepeaterConfigFragmentData;
import org.jp.illg.nora.android.view.fragment.model.StatusFragmentData;
import org.jp.illg.nora.android.view.model.ApplicationConfig;
import org.jp.illg.nora.android.view.model.GatewayConfig;
import org.jp.illg.nora.android.view.model.GatewayConfigBundler;
import org.jp.illg.nora.android.view.model.RepeaterConfig;
import org.jp.illg.nora.android.view.model.RepeaterConfigBundler;
import org.jp.illg.nora.android.view.model.RepeaterModuleConfig;
import org.jp.illg.nora.android.view.model.StatusConfig;
import org.jp.illg.nora.android.view.model.StatusConfigBundler;
import org.jp.illg.util.android.view.AlertDialogFragment;
import org.jp.illg.util.android.view.EventBusEvent;

import butterknife.BindView;
import butterknife.ButterKnife;
import icepick.Icepick;
import icepick.State;
import io.fabric.sdk.android.Fabric;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MainActivity extends AppCompatActivity
{
	@Getter
	@Setter(AccessLevel.PRIVATE)
	private static EventBus viewEventBus;

	@BindView(R.id.mainViewpager)
	ViewPager mainViewPager;

	@BindView(R.id.mainTab)
	TabLayout mainTab;


	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(GatewayConfigBundler.class)
	GatewayConfig gatewayConfig;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(RepeaterConfigBundler.class)
	RepeaterConfig repeaterConfig;

	@Getter
	@Setter(AccessLevel.PRIVATE)
	@State(StatusConfigBundler.class)
	StatusConfig statusConfig;

	@State
	int selectedPageIndex;

	@State
	boolean serviceRunning;

	@State
	String[] savedLogs;


//	private Toolbar toolbar;
	private DrawerLayout drawerLayout;




	{
		repeaterConfig = new RepeaterConfig();
		gatewayConfig = new GatewayConfig();
		statusConfig = new StatusConfig();
	}

	public MainActivity(){
		super();
		
		if(getViewEventBus() == null){setViewEventBus(EventBus.getDefault());}
	}

	public static class ApplicationEvent extends EventBusEvent<ApplicationEventType>{
		public ApplicationEvent(ApplicationEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum ApplicationEventType{
		ResponseCheckRunningGateway{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.ResponseCheckRunningGateway.toString());

				boolean running = false;
				if(attachment != null && attachment instanceof Boolean)
					running = (Boolean)attachment;

				activity.serviceRunning = running;

				activity.updateFragmentData();
			}
		},
		ResponseGatewayStart {
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.ResponseGatewayStart.toString());

				activity.getViewEventBus().post(
						new StatusFragment.MainActivityEvent(
								StatusFragment.MainActivityEventType.ResponseGatewayStart,
								attachment
						)
				);

				activity.serviceRunning = true;

				activity.updateFragmentData();
			}
		},
		ResponseGatewayStop {
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.ResponseGatewayStop.toString());

				activity.getViewEventBus().post(
						new StatusFragment.MainActivityEvent(
								StatusFragment.MainActivityEventType.ResponseGatewayStop,
								attachment
						)
				);

				activity.serviceRunning = false;

				activity.updateFragmentData();
			}
		},
		ResponseUartPort {
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.ResponseUartPort.toString());

				activity.getViewEventBus().post(
					new RepeaterConfigFragment.MainActivityEvent(
							RepeaterConfigFragment.MainActivityEventType.ResultUartPorts,
							attachment
					)
				);
			}
		},
		NotifyLog{
			@Override
			void apply(MainActivity activity, final Object attachment){
				activity.getViewEventBus().post(
						new StatusFragment.MainActivityEvent(
								StatusFragment.MainActivityEventType.NotifyLog,
								attachment
						)
				);
			}
		},
		NotifyStatusReport{
			@Override
			void apply(MainActivity activity, final Object attachment){
//				log.trace("Receive event " +
//						ApplicationEvent.class.getSimpleName() + "." +
//						ApplicationEventType.NotifyStatusReport.toString());

				activity.getViewEventBus().post(
						new StatusFragment.MainActivityEvent(
								StatusFragment.MainActivityEventType.NotifyStatusReport,
								attachment
						)
				);
			}
		},
		NotifySavedLogs{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.NotifySavedLogs.toString());

				if(attachment != null && attachment instanceof String[])
					activity.savedLogs = (String[])attachment;

				activity.getViewEventBus().post(
						new StatusFragment.MainActivityEvent(
								StatusFragment.MainActivityEventType.NotifySavedLogs,
								attachment
						)
				);
			}
		},
		ResponseConfig{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						ApplicationEvent.class.getSimpleName() + "." +
						ApplicationEventType.ResponseConfig.toString());

				if(attachment != null && attachment instanceof ApplicationConfig){
					ApplicationConfig applicationConfig = (ApplicationConfig)attachment;
					activity.setGatewayConfig(applicationConfig.getGatewayConfig());
					activity.setRepeaterConfig(applicationConfig.getRepeaterConfig());
					activity.setStatusConfig(applicationConfig.getStatusConfig());

					activity.getViewEventBus().post(
							new GatewayConfigFragment.MainActivityEvent(
									GatewayConfigFragment.MainActivityEventType.UpdateConfig,
									activity.getGatewayConfig()
							)
					);

					for(RepeaterModuleConfig moduleConfig : activity.getRepeaterConfig().getRepeaterModules().values()){
						activity.getViewEventBus().post(
								new RepeaterConfigFragment.MainActivityEvent(
										RepeaterConfigFragment.MainActivityEventType.UpdateModuleConfig,
										moduleConfig
								)
						);
					}

					activity.getViewEventBus().post(
							new StatusFragment.MainActivityEvent(
									StatusFragment.MainActivityEventType.UpdateConfig,
									activity.getStatusConfig()
							)
					);

					activity.keepScreenOn(activity.getStatusConfig().isDisableDisplaySleep());
				}
			}
		}
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}

	public static class StatusFragmentEvent extends EventBusEvent<StatusFragmentEventType>{
		public StatusFragmentEvent(StatusFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum StatusFragmentEventType{
		OnNewFragment{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.OnNewFragment.toString());


			}
		},
		OnFragmentCreated{
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.OnFragmentCreated.toString());

				activity.updateStatusFragmentData();
			}
		},
		RequestConfig{
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.RequestConfig.toString());


			}
		},
		RequestSavedLogs{
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.RequestSavedLogs.toString());

				activity.getApplicationEventBus()
						.ifPresent(
								new Consumer<EventBus>() {
									@Override
									public void accept(EventBus eventBus) {
										eventBus.post(
												new NoraGatewayForAndroidApp.MainActivityEvent(
														NoraGatewayForAndroidApp.MainActivityEventType.RequestSavedLog,null
												)
										);
									}
								}
						);
			}
		},
		OnStartRequest{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.OnStartRequest.toString());
				
				final String gatewayCallsign =
						DStarUtils.formatFullCallsign(activity.getGatewayConfig().getGatewayCallsign());
					
				if(
						!CallSignValidator.isValidUserCallsign(gatewayCallsign)
				){
						new AlertDialogFragment.Builder()
								.setTitle("ERROR")
								.setMessage("Illegal callsign = " + gatewayCallsign)
								.setPositiveButton("OK")
								.setStyle(R.style.NoraGatewayTheme_Default_AlertDialogStyle)
								.build(0x305)
								.showOn(activity, MainActivity.class.getSimpleName() + ".ERROR_ILLEGAL_GATEWAY_CALLSIGN");
						return;
				}
				else if(CallSignValidator.isValidJARLRepeaterCallsign(gatewayCallsign))
				{
					new AlertDialogFragment.Builder()
							.setTitle("ERROR")
							.setMessage("DO NOT USE JARL REPEATER CALLSIGN !\n" + gatewayCallsign)
							.setPositiveButton("OK")
							.setStyle(R.style.NoraGatewayTheme_Default_AlertDialogStyle)
							.build(0x306)
							.showOn(activity, MainActivity.class.getSimpleName() + ".ERROR_DONOTUSE_JARL_REPEATER_CALLSIGN");
					return;
				}

				activity.getApplicationEventBus()
					.ifPresent(
						new Consumer<EventBus>() {
							@Override
							public void accept(EventBus eventBus) {
								eventBus.post(
										new NoraGatewayForAndroidApp.MainActivityEvent(
												NoraGatewayForAndroidApp.MainActivityEventType.RequestStartGateway,null
										)
								);
							}
						}
					);
			}
		},
		OnStopRequest{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.OnStopRequest.toString());

				activity.getApplicationEventBus()
					.ifPresent(
						new Consumer<EventBus>() {
							@Override
							public void accept(EventBus eventBus) {
								eventBus.post(
										new NoraGatewayForAndroidApp.MainActivityEvent(
												NoraGatewayForAndroidApp.MainActivityEventType.RequestStopGateway,null
										)
								);
							}
						}
					);
			}
		},
		UpdateConfig {
			@Override
			void apply(MainActivity activity, final Object attachment) {
				log.trace("Receive event" +
						StatusFragmentEvent.class.getSimpleName() + "." +
						StatusFragmentEventType.UpdateConfig.toString());

				if(attachment != null && attachment instanceof StatusConfig) {
					activity.setStatusConfig((StatusConfig) attachment);

					activity.sendConfigToApplication();

					activity.keepScreenOn(activity.getStatusConfig().isDisableDisplaySleep());
				}
			}
		},
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}
/*
	public static class ConfigFragmentEvent extends EventBusEvent<ConfigFragmentEventType>{
		public ConfigFragmentEvent(ConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum ConfigFragmentEventType{
		OnPortRefreshClick{
			@Override
			void apply(MainActivity activity, final Object attachment){
				activity.getApplicationEventBus()
					.ifPresent(
						new Consumer<EventBus>() {
							@Override
							public void accept(EventBus eventBus) {
								eventBus.post(
										new NoraGatewayForAndroidApp.MainActivityEvent(
												NoraGatewayForAndroidApp.MainActivityEventType.RequestPortList,null
										)
								);
							}
						}
					);
			}
		},
		OnPortSelected{
			@Override
			void apply(MainActivity activity, final Object attachment){
				activity.getApplicationEventBus()
					.ifPresent(
						new Consumer<EventBus>() {
							@Override
							public void accept(EventBus eventBus) {
								eventBus.post(
										new NoraGatewayForAndroidApp.MainActivityEvent(
												NoraGatewayForAndroidApp.MainActivityEventType.RequestSetPort, attachment
										)
								);
							}
						}
					);
			}
		},
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}
*/
	public static class GatewayConfigFragmentEvent extends EventBusEvent<GatewayConfigFragmentEventType>{
		public GatewayConfigFragmentEvent(GatewayConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum GatewayConfigFragmentEventType{
		OnFragmentCreated{
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						GatewayConfigFragmentEvent.class.getSimpleName() + "." +
						GatewayConfigFragmentEventType.OnFragmentCreated.toString());

				activity.updateGatewayConfigFragmentData();
			}
		},
		UpdateConfig{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						GatewayConfigFragmentEvent.class.getSimpleName() + "." +
						GatewayConfigFragmentEventType.UpdateConfig.toString());

				if(attachment != null && attachment instanceof GatewayConfig) {
					activity.setGatewayConfig((GatewayConfig) attachment);

					activity.sendConfigToApplication();
				}
			}
		},
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}

	public static class RepeaterConfigFragmentEvent extends EventBusEvent<RepeaterConfigFragmentEventType>{
		public RepeaterConfigFragmentEvent(RepeaterConfigFragmentEventType eventType, Object attachment){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigFragmentEventType{
		OnFragmentCreated{
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						RepeaterConfigFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigFragmentEventType.OnFragmentCreated.toString());

				activity.updateRepeaterConfigFragmentData();
			}
		},
		UpdateModuleConfig{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						RepeaterConfigFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigFragmentEventType.UpdateModuleConfig.toString());

				if(attachment != null && attachment instanceof RepeaterModuleConfig) {
					RepeaterModuleConfig moduleConfig = (RepeaterModuleConfig)attachment;

					if(activity.getRepeaterConfig().getRepeaterModules().containsKey(moduleConfig.getRepeaterModule()))
						activity.getRepeaterConfig().getRepeaterModules().remove(moduleConfig.getRepeaterModule());

					activity.getRepeaterConfig().getRepeaterModules().put(moduleConfig.getRepeaterModule(), moduleConfig);

					activity.sendConfigToApplication();
				}

			}
		},
		RequestUartPorts{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						RepeaterConfigFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigFragmentEventType.RequestUartPorts.toString());

				activity.getApplicationEventBus().ifPresent(
						new Consumer<EventBus>() {
							@Override
							public void accept(EventBus eventBus) {
								eventBus.post(
										new NoraGatewayForAndroidApp.MainActivityEvent(
												NoraGatewayForAndroidApp.MainActivityEventType.RequestPortList,null
										)
								);
							}
						}
				);
			}
		},
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}
/*
	public static class RepeaterConfigInternalModemAccessPointFragmentEvent
			extends EventBusEvent<RepeaterConfigInternalModemAccessPointFragmentEventType>{

		public RepeaterConfigInternalModemAccessPointFragmentEvent(
				RepeaterConfigInternalModemAccessPointFragmentEventType eventType,
				Object attachment
		){
			super(eventType, attachment);
		}
	}

	public enum RepeaterConfigInternalModemAccessPointFragmentEventType{
		RequestUartPorts{
			@Override
			void apply(MainActivity activity, final Object attachment){
				log.trace("Receive event" +
						RepeaterConfigInternalModemAccessPointFragmentEvent.class.getSimpleName() + "." +
						RepeaterConfigInternalModemAccessPointFragmentEventType.RequestUartPorts.toString());

				activity.getApplicationEventBus().ifPresent(
					new Consumer<EventBus>() {
						@Override
						public void accept(EventBus eventBus) {
							eventBus.post(
									new NoraGatewayForAndroidApp.MainActivityEvent(
											NoraGatewayForAndroidApp.MainActivityEventType.RequestPortList,null
									)
							);
						}
					}
				);
			}
		},
		;

		abstract void apply(MainActivity activity, final Object attachment);
	}
*/
	@Subscribe
	public void onApplicationEvent(ApplicationEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}
/*
	@Subscribe
	public void onConfigFragmentEvent(ConfigFragmentEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}
*/
	@Subscribe
	public void onStatusFragmentEvent(StatusFragmentEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onGatewayConfigFragmentEvent(GatewayConfigFragmentEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}

	@Subscribe
	public void onRepeaterConfigFragmentEvent(RepeaterConfigFragmentEvent event){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}
/*
	@Subscribe
	public void RepeaterConfigInternalModemAccessPointFragmentEvent(
			RepeaterConfigInternalModemAccessPointFragmentEvent event
	){
		if(event.getEventType() != null)
			event.getEventType().apply(this, event.getAttachment());
	}
*/

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		Fabric.with(this, new Crashlytics());

		if(getApplication() instanceof NoraGatewayForAndroidApp) {
			EventBus applicationEventBus =
					((NoraGatewayForAndroidApp) getApplication()).getApplicationEventBus();

			if(!applicationEventBus.isRegistered(this))
				applicationEventBus.register(this);

			applicationEventBus.post(
					new NoraGatewayForAndroidApp.MainActivityEvent(
							NoraGatewayForAndroidApp.MainActivityEventType.MainActivityCreated,
							this
					)
			);
		}

		setContentView(R.layout.activity_main);

		log.trace(getClass().getSimpleName() + "." + "onCreate()");

		ButterKnife.bind(this);

		setViews();
	}

	@Override
	protected void onStart(){
		super.onStart();
		
		getViewEventBus().register(this);
		
		log.trace(getClass().getSimpleName() + "." + "onStart()");
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState){
		Icepick.restoreInstanceState(this, savedInstanceState);

		log.trace(getClass().getSimpleName() + "." + "onRestoreInstanceState()");

		mainViewPager.setCurrentItem(selectedPageIndex);
	}

	@Override
	protected void onResume() {
		super.onResume();

		log.trace(getClass().getSimpleName() + "." + "onResume()");
									
									getApplicationEventBus().ifPresent(
										new Consumer<EventBus>() {
											@Override
											public void accept(EventBus eventBus) {
												eventBus.post(
													new NoraGatewayForAndroidApp.MainActivityEvent(
														NoraGatewayForAndroidApp.MainActivityEventType.RequestCheckRunningGateway,
														null
								)
						);
					}
				}
		);
		getApplicationEventBus().ifPresent(
				new Consumer<EventBus>() {
					@Override
					public void accept(EventBus eventBus) {
						eventBus.post(
								new NoraGatewayForAndroidApp.MainActivityEvent(
										NoraGatewayForAndroidApp.MainActivityEventType.RequestConfig,
										null
								)
						);
					}
				}
		);

		getApplicationEventBus().ifPresent(
				new Consumer<EventBus>() {
					@Override
					public void accept(EventBus eventBus) {
						eventBus.post(
								new NoraGatewayForAndroidApp.MainActivityEvent(
										NoraGatewayForAndroidApp.MainActivityEventType.RequestSavedLog,
										null
								)
						);
					}
				}
		);
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);

		log.trace(getClass().getSimpleName() + "." + "onSaveInstanceState()");

		Icepick.saveInstanceState(this, outState);
	}

	@Override
	protected void onPause() {
		super.onPause();

		log.trace(getClass().getSimpleName() + "." + "onPause()");

		getApplicationEventBus().ifPresent(
				new Consumer<EventBus>() {
					@Override
					public void accept(EventBus eventBus) {
						eventBus.post(
								new NoraGatewayForAndroidApp.MainActivityEvent(
										NoraGatewayForAndroidApp.MainActivityEventType.MainActivityOnPause,
										null
								)
						);
					}
				}
		);
	}

	@Override
	protected void onStop() {
		super.onStop();
		
		getViewEventBus().unregister(this);

		log.trace(getClass().getSimpleName() + "." + "onStop()");
	}


	@Override
	protected void onDestroy(){
		log.trace(getClass().getSimpleName() + "." + "onDestroy()");

		if(getApplication() instanceof NoraGatewayForAndroidApp){
			EventBus applicationEventBus =
					((NoraGatewayForAndroidApp) getApplication()).getApplicationEventBus();

			if(applicationEventBus.isRegistered(this))
				applicationEventBus.unregister(this);
		}

		super.onDestroy();
	}

	private void setViews() {
//		toolbar = (Toolbar) findViewById(R.id.main_toolbar);
//		setSupportActionBar(toolbar);
		FragmentManager manager = getSupportFragmentManager();
		ViewPager viewPager = (ViewPager) findViewById(R.id.mainViewpager);
		/*
		NoraFragmentPagerAdapter adapter =
				new NoraFragmentPagerAdapter(manager, this, getViewEventBus());
		adapter.addPage(StatusFragment.class, "Status");
		adapter.addPage(GatewayConfigFragment.class, "Gateway");
		adapter.addPage(RepeaterConfigFragment.class, "Repeater");
		*/
		FragmentPagerAdapter adapter = new FragmentPagerAdapter(manager) {
			@Override
			public Fragment getItem(int i) {
				switch(i){
					case 0:
						return StatusFragment.getInstance(getViewEventBus());
					case 1:
						return GatewayConfigFragment.getInstance(getViewEventBus());
					case 2:
						return RepeaterConfigFragment.getInstance(getViewEventBus());
					default:
						return null;
				}
			}
			
			@Override
			public int getCount() {
				return 3;
			}
			
			@Override
			public CharSequence getPageTitle(int position) {
				switch(position){
					case 0:
						return "STATUS";
					case 1:
						return "GATEWAY";
					case 2:
						return "REPEATER";
					default:
						return "?";
				}
			}
		};

		viewPager.setAdapter(adapter);
		viewPager.addOnPageChangeListener(mainViewPagerOnPageChangeListener);

		setDrawer();
		TabLayout tabLayout = (TabLayout) findViewById(R.id.mainTab);
		tabLayout.setupWithViewPager(viewPager);

//		getSupportActionBar().hide();
	}

	private void setDrawer() {
		drawerLayout = (DrawerLayout) findViewById(R.id.mainDrawer);
		NavigationView navigationView = (NavigationView) findViewById(R.id.mainDrawerNavigation);
/*
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
				this, drawerLayout, toolbar, R.string.app_name, R.string.app_name);
		drawerLayout.addDrawerListener(toggle);
		toggle.syncState();
*/
		navigationView.setNavigationItemSelectedListener(select);
	}

	private ViewPager.OnPageChangeListener mainViewPagerOnPageChangeListener =
			new ViewPager.OnPageChangeListener() {
				@Override
				public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
					selectedPageIndex = position;
				}

				@Override
				public void onPageSelected(int position) {

				}

				@Override
				public void onPageScrollStateChanged(int state) {

				}
			};

	private NavigationView.OnNavigationItemSelectedListener select = new NavigationView.OnNavigationItemSelectedListener() {
		@Override
		public boolean onNavigationItemSelected(MenuItem item) {
			drawerLayout.closeDrawers();
			return true;
		}
	};

	private Optional<EventBus> getApplicationEventBus(){
		EventBus applicationEventBus = null;

		if(getApplication() instanceof NoraGatewayForAndroidApp)
			applicationEventBus = ((NoraGatewayForAndroidApp)getApplication()).getApplicationEventBus();

		return Optional.ofNullable(applicationEventBus);
	}

	private boolean sendConfigToApplication(){
		Optional<EventBus> eventBus = getApplicationEventBus();

		eventBus.ifPresent(
				new Consumer<EventBus>() {
					@Override
					public void accept(EventBus eventBus) {
						eventBus.post(
							new NoraGatewayForAndroidApp.MainActivityEvent(
									NoraGatewayForAndroidApp.MainActivityEventType.UpdateConfig,
									new ApplicationConfig(getGatewayConfig(), getRepeaterConfig(), getStatusConfig())
							)
						);
					}
				}
		);
		return eventBus.isPresent();
	}

	private void updateFragmentData(){

		updateStatusFragmentData();

		updateGatewayConfigFragmentData();

		updateRepeaterConfigFragmentData();
	}

	private void updateStatusFragmentData(){
		getViewEventBus().post(
				new StatusFragment.MainActivityEvent(
						StatusFragment.MainActivityEventType.UpdateData,
						new StatusFragmentData(serviceRunning, savedLogs, getStatusConfig())
				)
		);
	}

	private void updateGatewayConfigFragmentData(){
		getViewEventBus().post(
				new GatewayConfigFragment.MainActivityEvent(
						GatewayConfigFragment.MainActivityEventType.UpdateData,
						new GatewayConfigFragmentData(serviceRunning, getGatewayConfig())
				)
		);
	}

	private void updateRepeaterConfigFragmentData(){
		getViewEventBus().post(
				new RepeaterConfigFragment.MainActivityEvent(
						RepeaterConfigFragment.MainActivityEventType.UpdateData,
						new RepeaterConfigFragmentData(serviceRunning, getRepeaterConfig())
				)
		);
	}

	private void keepScreenOn(boolean on){
		if(on)
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		else
			getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		log.trace("Keep display is " + on + ".");
	}
	
}
