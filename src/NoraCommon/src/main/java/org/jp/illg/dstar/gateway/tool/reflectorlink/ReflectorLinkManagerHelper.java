package org.jp.illg.dstar.gateway.tool.reflectorlink;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.dstar.gateway.tool.reflectorlink.model.AutoConnectEntry;
import org.jp.illg.dstar.gateway.tool.reflectorlink.model.AutoConnectFixed;
import org.jp.illg.dstar.gateway.tool.reflectorlink.model.AutoConnectMode;
import org.jp.illg.dstar.gateway.tool.reflectorlink.model.AutoConnectRequestData;
import org.jp.illg.dstar.gateway.tool.reflectorlink.model.AutoConnectTimeBased;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.AutoConnectProperties;
import org.jp.illg.dstar.model.config.AutoConnectRepeaterEntry;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationServiceManager;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.util.CallSignValidator;
import org.jp.illg.util.Timer;
import org.jp.illg.util.thread.ThreadProcessResult;

import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorLinkManagerHelper {

	@Getter
	private final ReflectorLinkManagerImpl manager;

	private final Timer processPeriodKeeper;

	private final List<AutoConnectEntry> autoConnectEntries;
	private final Map<DSTARRepeater, String> autoConnectReflectorEntries;
	private final Lock autoConnectLock;

	private enum ProcessState{
		Initialize,
		MainProcess,
		Wait,
		;
	}
	private ProcessState currentState;
	private ProcessState nextState;
	private ProcessState callbackState;
	private final Timer stateTimeKeeper;

	@Getter(AccessLevel.PRIVATE)
	@Setter(AccessLevel.PRIVATE)
	private boolean stateChanged;


	public ReflectorLinkManagerHelper(ReflectorLinkManagerImpl manager) {
		super();

		this.manager = manager;

		this.processPeriodKeeper = new Timer(1000);

		autoConnectEntries = new ArrayList<>();
		autoConnectReflectorEntries = new HashMap<>();
		autoConnectLock = new ReentrantLock();

		currentState = ProcessState.Initialize;
		nextState = ProcessState.Initialize;
		callbackState = ProcessState.Initialize;
		stateTimeKeeper = new Timer();
	}

	public boolean setProperties(AutoConnectProperties properties) {
		if(properties == null) {return false;}

		final AutoConnectProperties autoConnectProperties = properties;

		getManager().setAutoConnectEnable(properties.isEnable());

		for(AutoConnectRepeaterEntry repeaterEntry : autoConnectProperties.getRepeaterEntries().values()) {

			AutoConnectMode mode = AutoConnectMode.getModeByModeString(repeaterEntry.getMode());
			if(mode == null || mode == AutoConnectMode.Unknown) {
				if(log.isWarnEnabled()) {
					log.warn(
						"Could not convert mode = " + repeaterEntry.getMode() +
						",ignore auto connect repeater " + repeaterEntry.getRepeaterCallsign() + "entry."
					);
				}
				continue;
			}

			if(mode == AutoConnectMode.TimeBased) {
				AutoConnectTimeBased timebasedEntry =
					new AutoConnectTimeBased(manager, repeaterEntry.getRepeaterCallsign());

				for(Map<String,String> entry : repeaterEntry.getEntries().values()) {
					String dayOfWeek = entry.get("dayOfWeek");
					String startTime = entry.get("startTime");
					String endTime = entry.get("endTime");
					String linkReflector = entry.get("linkReflector");


					if(!timebasedEntry.addTimeBasedEntry(dayOfWeek, startTime, endTime, linkReflector)) {
						if(log.isWarnEnabled()) {
							log.warn(
								"Error occurred at TimeBased registration.\n" +
								"    dayOfWeek=" + dayOfWeek + "/" +
								"startTime=" + startTime + "/" +
								"endTime=" + endTime + "/" +
								"linkReflector=" + linkReflector
							);
						}
					}
				}

				autoConnectLock.lock();
				try {
					autoConnectEntries.add(timebasedEntry);
				}finally {
					autoConnectLock.unlock();
				}
			}
			else if(mode == AutoConnectMode.Fixed) {


				String linkReflector = null;

				for(Map<String,String> entry : repeaterEntry.getEntries().values()) {
					String callsign = entry.get("linkReflector");

					if(
						CallSignValidator.isValidReflectorCallsign(callsign)
					) {linkReflector = callsign;}
				}

				if(linkReflector == null) {
					if(log.isWarnEnabled())
						log.warn("Could not found AutoConnect.Repeater.Fixed linkReflector calllsign");

					continue;
				}

				final AutoConnectFixed fixedEntry =
					new AutoConnectFixed(manager, repeaterEntry.getRepeaterCallsign(), linkReflector);

				autoConnectLock.lock();
				try {
					autoConnectEntries.add(fixedEntry);
				}finally {
					autoConnectLock.unlock();
				}
			}

		}

		return true;
	}

	public boolean isAutoControlledRepeater(final DSTARRepeater repeater) {
		if(repeater == null)
			return false;

		//自動接続無効時は、常に自動接続管理下のレピータは無いものとする
		if(!getManager().isAutoConnectEnable()) {return false;}

		boolean match = false;

		autoConnectLock.lock();
		try {
			match =
				Stream.of(autoConnectEntries)
				.anyMatch(new Predicate<AutoConnectEntry>() {
					@Override
					public boolean test(AutoConnectEntry value) {
						return value.getTargetRepeater() == repeater;
					}
				});

		}finally {
			autoConnectLock.unlock();
		}

		return match;
	}

	public boolean setRepeaterAutoControlEnable(final DSTARRepeater repeater, final boolean enable) {
		if(repeater == null) {return false;}

		autoConnectLock.lock();
		try {
			Stream.of(autoConnectEntries)
			.filter(new Predicate<AutoConnectEntry>() {
				@Override
				public boolean test(AutoConnectEntry value) {
					return value.getTargetRepeater() == repeater;
				}
			})
			.findSingle()
			.ifPresent(new Consumer<AutoConnectEntry>() {
				@Override
				public void accept(AutoConnectEntry t) {
					t.setEnable(enable);
				}
			});
		}finally {
			autoConnectLock.unlock();
		}

		return true;
	}

	public Optional<Boolean> isRepeaterAutoControlEnable(final DSTARRepeater repeater) {
		if(repeater == null) {return Optional.empty();}

		autoConnectLock.lock();
		try {
			Optional<AutoConnectEntry> repeaterEntry =
				Stream.of(autoConnectEntries)
				.filter(new Predicate<AutoConnectEntry>() {
					@Override
					public boolean test(AutoConnectEntry value) {
						return value.getTargetRepeater() == repeater;
					}
				})
				.findSingle();

			if(repeaterEntry.isPresent())
				return Optional.of(repeaterEntry.get().isEnable());
			else
				return Optional.empty();

		}finally {
			autoConnectLock.unlock();
		}
	}

	public ThreadProcessResult process() {
		ThreadProcessResult processResult = ThreadProcessResult.NoErrors;

		boolean reProcess;
		do {
			reProcess = false;

			setStateChanged(currentState != nextState);
			currentState = nextState;

			switch(currentState) {
			case Initialize:
				processResult = onStateInitialize();
				break;

			case MainProcess:
				processResult = onStateMainProcess();
				break;

			case Wait:
				processResult = onStateWait();
				break;
			}

			if(
				currentState != nextState &&
				processResult == ThreadProcessResult.NoErrors
			) {reProcess = true;}
		}while(reProcess);

		return processResult;
	}

	private ThreadProcessResult onStateInitialize() {
		toWaitState(10, TimeUnit.SECONDS, ProcessState.MainProcess);

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateMainProcess() {
		if(processPeriodKeeper.isTimeout()) {
			processPeriodKeeper.updateTimestamp();

			if(!getManager().isAutoConnectEnable()) {return ThreadProcessResult.NoErrors;}

			autoConnectLock.lock();
			try {
				for(final AutoConnectEntry entry : autoConnectEntries) {
					if(!entry.isEnable()) {
						if(autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()))
							autoConnectReflectorEntries.remove(entry.getTargetRepeater());

						continue;
					}

					final Optional<AutoConnectRequestData> autoConnectRequest =
						entry.getLinkDataIfAvailable();

					if(
						autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()) &&
						!autoConnectRequest.isPresent()
					) {
						// 接続するべきリフレクタが無くなった
//						String reflectorCallsign =
//							autoConnectReflectorEntries.get(entry.getRepeater());

						autoConnectReflectorEntries.remove(entry.getTargetRepeater());

						getManager().unlinkReflector(true, entry.getTargetRepeater());
					}
					else if(
						!autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()) &&
						autoConnectRequest.isPresent()
					) {
						// 接続するべきリフレクタがある
						String reflectorCallsign = autoConnectRequest.get().getLinkReflectorCallsign();

						autoConnectReflectorEntries.put(entry.getTargetRepeater(), reflectorCallsign);

						getManager().linkReflector(true, entry.getTargetRepeater(), reflectorCallsign);
					}
					else if(
						autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()) &&
						autoConnectRequest.isPresent()
					) {
						// 接続継続
						final String reflectorCallsign =
								autoConnectRequest.get().getLinkReflectorCallsign();

						final boolean diffReflectorRequest =	//異なるリフレクタへの接続リクエストがある
							!autoConnectRequest.get().getLinkReflectorCallsign().equals(
									autoConnectReflectorEntries.get(entry.getTargetRepeater())
							);

						if(diffReflectorRequest) {
							if(autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()))
								autoConnectReflectorEntries.remove(entry.getTargetRepeater());

							autoConnectReflectorEntries.put(entry.getTargetRepeater(), reflectorCallsign);
						}

						final Optional<ReflectorCommunicationService> reflectorService =
							getManager().getGateway().getReflectorCommunicationService(reflectorCallsign);

						reflectorService.ifPresent(new Consumer<ReflectorCommunicationService>() {
							@Override
							public void accept(ReflectorCommunicationService s) {
								boolean connected = false;

								final Optional<ReflectorLinkInformation> linkInfo =
										s.getLinkInformationOutgoing(entry.getTargetRepeater());
								if(linkInfo.isPresent())
									connected = reflectorCallsign.equals(linkInfo.get().getCallsign());

								if(!connected || diffReflectorRequest)
									getManager().linkReflector(true, entry.getTargetRepeater(), reflectorCallsign);
							}
						});
					}
					else if(
						!autoConnectReflectorEntries.containsKey(entry.getTargetRepeater()) &&
						!autoConnectRequest.isPresent()
					) {
						// 切断継続
						final List<ReflectorCommunicationService> reflectorServices =
								ReflectorCommunicationServiceManager.getServices(getManager().getSystemID());

						for(final ReflectorCommunicationService reflectorService : reflectorServices) {
							reflectorService.getLinkInformationOutgoing(entry.getTargetRepeater())
							.ifPresent(new Consumer<ReflectorLinkInformation>() {
								@Override
								public void accept(ReflectorLinkInformation t) {
									getManager().unlinkReflector(true, entry.getTargetRepeater());
								}
							});
						}
					}

				}
			}finally {
				autoConnectLock.unlock();
			}

		}

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult onStateWait() {
		if(stateTimeKeeper.isTimeout())
			nextState = callbackState;

		return ThreadProcessResult.NoErrors;
	}

	private void toWaitState(long waitTime, TimeUnit timeUnit, ProcessState callbackState) {
		stateTimeKeeper.setTimeoutTime(waitTime, timeUnit);

		nextState = ProcessState.Wait;
		this.callbackState = callbackState;
	}

	public ThreadProcessResult processAutoConnectProcess() {
		return process();
	}
}
