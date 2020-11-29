package org.jp.illg.dstar.gateway.tool.reflectorlink;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.commons.lang3.time.DateFormatUtils;
import org.jp.illg.dstar.model.DSTARGateway;
import org.jp.illg.dstar.model.DSTARRepeater;
import org.jp.illg.dstar.model.config.ReflectorBlackListEntry;
import org.jp.illg.dstar.model.config.ReflectorLinkManagerProperties;
import org.jp.illg.dstar.model.defines.ConnectionDirectionType;
import org.jp.illg.dstar.reflector.ReflectorCommunicationService;
import org.jp.illg.dstar.reflector.ReflectorCommunicationServiceManager;
import org.jp.illg.dstar.reflector.model.ReflectorHostInfo;
import org.jp.illg.dstar.reflector.model.ReflectorLinkInformation;
import org.jp.illg.dstar.reflector.model.events.ReflectorEvent;
import org.jp.illg.dstar.reflector.model.events.ReflectorEvent.ReflectorEventTypes;
import org.jp.illg.dstar.reflector.protocol.model.ReflectorConnectionStates;
import org.jp.illg.dstar.service.reflectorname.ReflectorNameService;
import org.jp.illg.util.Timer;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToLongFunction;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ReflectorLinkManagerImpl implements ReflectorLinkManager{

	private static final String logHeader;

	@Getter(AccessLevel.PACKAGE)
	private final DSTARGateway gateway;

	@Getter(AccessLevel.PRIVATE)
	private final ReflectorNameService reflectorNameService;

	@Getter(AccessLevel.PRIVATE)
	private final ReflectorLinkManagerHelper helper;

	@Getter
	@Setter
	private boolean autoConnectEnable;

	private enum TaskMode{
		Connect,
		Disconnect,
		;
	}

	private enum TaskState{
		Unknown,
		TaskAdded,
		Processing,
		Complete,
		;
	}

	private enum TaskResult{
		Unknown,
		Success,
		Error,
		;
	}

	private class OutgoingMonitoringEntry{
		@Getter
		private final DSTARRepeater repeater;

		@Getter
		private final Timer timer;

		@Getter
		@Setter
		private long startTime;

		public OutgoingMonitoringEntry(@NonNull DSTARRepeater repeater) {
			this.repeater = repeater;

			timer = new Timer();
			startTime = 0;
		}
	}

	private class TaskEntry{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long createdTime;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private TaskMode taskMode;

		@Getter
		@Setter
		private TaskState taskState;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private Timer taskStateTimeKeeper;

		@Getter
		@Setter
		private TaskResult taskResult;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private String reflectorCallsign;

		@Getter
		@Setter(AccessLevel.PRIVATE)
		private ReflectorHostInfo reflectorHostInfo;;

		@Getter
		@Setter
		private ReflectorCommunicationService reflectorService;

		@Getter
		@Setter
		private UUID serviceTaskId;

		private TaskEntry() {
			super();

			setCreatedTime(System.currentTimeMillis());

			setTaskState(TaskState.Unknown);
			setTaskStateTimeKeeper(new Timer());
			setTaskResult(TaskResult.Unknown);

			setReflectorCallsign("");
		}

		public TaskEntry(
			TaskMode taskMode, ReflectorCommunicationService reflectorService,
			String reflectorCallsign
		) {
			this();

			setTaskMode(taskMode);

			setReflectorService(reflectorService);
			setReflectorCallsign(reflectorCallsign);
			setReflectorHostInfo(reflectorHostInfo);
		}

		public TaskEntry(
			TaskMode taskMode, ReflectorCommunicationService reflectorService,
			String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
		) {
			this(taskMode, reflectorService, reflectorCallsign);

			setReflectorHostInfo(reflectorHostInfo);
		}

		@Override
		public String toString() {
			return toString(0);
		}

		public String toString(int indent) {
			if(indent < 0) {indent = 0;}

			StringBuffer sb = new StringBuffer();
			String datePtn = "yyyy/MM/dd HH:mm:ss.SSS";

			for(int c = 0; c < indent; c++)
				sb.append(' ');

			sb.append("TaskMode=");
			sb.append(getTaskMode().toString());

			sb.append("/");

			sb.append("TaskState=");
			sb.append(getTaskMode().toString());

			sb.append("/");

			sb.append("TaskResult=");
			sb.append(getTaskResult().toString());

			sb.append("/");

			sb.append("CreatedTime=");
			sb.append(DateFormatUtils.format(getCreatedTime(), datePtn));


			sb.append("/");

			sb.append("ReflectorCallsign=");
			if(getReflectorCallsign() != null)
				sb.append(getReflectorCallsign());
			else
				sb.append("null");

			sb.append("/");

			sb.append("ReflectorService=");
			if(getReflectorService() != null)
				sb.append(getReflectorService().getProcessorType());
			else
				sb.append("null");

			sb.append("/");

			sb.append("ServiceTaskId=");
			if(getServiceTaskId() != null)
				sb.append(getServiceTaskId().toString());
			else
				sb.append("null");

			return sb.toString();
		}
	}

	@ToString
	private class RepeaterTaskEntry{
		@Getter
		@Setter(AccessLevel.PRIVATE)
		private long CreatedTime;

		@Getter
		private final DSTARRepeater repeater;

		@Getter
		private final Queue<TaskEntry> repeaterTasks;

		@Getter
		private final Queue<TaskEntry> repeaterTaskRemoveQueue;

		@Getter
		@Setter
		private TaskEntry processingTask;

		@Getter
		private final OutgoingMonitoringEntry outgoingMonitoringEntry;

		@Getter
		@Setter
		private ReflectorHostInfo outgoingReflectorHostInfo;

		private RepeaterTaskEntry(@NonNull DSTARRepeater repeater) {
			super();

			this.repeater = repeater;

			setCreatedTime(System.currentTimeMillis());

			repeaterTasks = new LinkedList<>();
			setProcessingTask(null);

			repeaterTaskRemoveQueue = new LinkedList<>();

			outgoingMonitoringEntry = new OutgoingMonitoringEntry(repeater);

			setOutgoingReflectorHostInfo(null);
		}

		public void removeRequestedTaskQueue() {
			for(Iterator<TaskEntry> it = repeaterTaskRemoveQueue.iterator(); it.hasNext();) {
				TaskEntry taskEntry = it.next();
				it.remove();

				repeaterTasks.remove(taskEntry);

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader +
						"Remove task entry.\n    [" + getRepeater().getRepeaterCallsign() + "] " + taskEntry.toString()
					);
				}
			}
		}

		public boolean addTask(TaskEntry taskEntry) {
			if(
				taskEntry == null ||
				getRepeaterTasks().contains(taskEntry)
			) {return false;}

			getRepeaterTasks().add(taskEntry);

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Added task entry.\n    [" + getRepeater().getRepeaterCallsign() + "] " + taskEntry.toString()
				);
			}

			return true;
		}

		public boolean startTask(TaskEntry taskEntry) {
			if(
				taskEntry == null ||
				getProcessingTask() != null
			) {return false;}

			setProcessingTask(taskEntry);

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Start task entry.\n    [" + getRepeater().getRepeaterCallsign() + "] " + taskEntry.toString()
				);
			}

			return true;
		}

		public boolean stopTask(TaskEntry taskEntry) {
			if(
				taskEntry == null ||
				getProcessingTask() == null
			) {return false;}

			setProcessingTask(null);

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"Stop task entry.\n    [" + getRepeater().getRepeaterCallsign() + "] " + taskEntry.toString()
				);
			}

			return true;
		}

		public boolean endTask(TaskEntry taskEntry) {
			if(taskEntry == null) {return false;}

			stopTask(taskEntry);

			if(getRepeaterTasks().contains(taskEntry))
				repeaterTaskRemoveQueue.add(taskEntry);

			if(log.isTraceEnabled()) {
				log.trace(
					logHeader +
					"add remove task entry.\n    [" + getRepeater().getRepeaterCallsign() + "] " + taskEntry.toString()
				);
			}

			return true;
		}
	}

	@SuppressWarnings("unused")
	private final ExecutorService workerExecutor;

	@Getter
	private final UUID systemID;

	private final Map<DSTARRepeater, RepeaterTaskEntry> tasks;
	private final Lock tasksLock;

	private ReflectorLinkManagerProperties properties;

	static {
		logHeader = ReflectorLinkManagerImpl.class.getSimpleName() + " : ";
	}

	public ReflectorLinkManagerImpl(
		@NonNull final UUID systemID,
		@NonNull final DSTARGateway gateway,
		@NonNull final ExecutorService workerExecutor,
		@NonNull final ReflectorNameService reflectorNameService
	) {
		super();

		this.systemID = systemID;
		this.gateway = gateway;
		this.workerExecutor = workerExecutor;
		this.reflectorNameService = reflectorNameService;

		this.helper = new ReflectorLinkManagerHelper(this);

		tasks = new HashMap<>();
		tasksLock = new ReentrantLock();

		properties = null;

		setAutoConnectEnable(false);
	}

	@Override
	public boolean setProperties(@NonNull ReflectorLinkManagerProperties properties) {
		if(this.properties != null) {return false;}

		this.properties = properties;

		if(properties.getDefaultReflectorPreferredProtocols().size() >= 1) {
			reflectorNameService.setDefaultReflectorPreferredProtocol(
				properties.getDefaultReflectorPreferredProtocols()
			);
		}

		if(properties.getReflectorPreferredProtocols().size() >= 1) {
			reflectorNameService.setReflectorPreferredProtocol(
				properties.getReflectorPreferredProtocols()
			);
		}

		boolean success = true;

		if(!getHelper().setProperties(properties.getAutoConnectProperties()))
			success = false;

		return success;
	}

	@Override
	public void notifyUseReflector(
		@NonNull DSTARRepeater repeater, @NonNull ConnectionDirectionType dir
	) {
		tasksLock.lock();
		try {
			final RepeaterTaskEntry repeaterTask = tasks.get(repeater);
			if(repeaterTask == null) {return;}

			if(
				repeater.getAutoDisconnectFromReflectorOutgoingUnusedMinutes() > 0 &&
				(
					dir == ConnectionDirectionType.OUTGOING ||
					dir == ConnectionDirectionType.BIDIRECTIONAL
				) &&
				repeaterTask.getOutgoingMonitoringEntry().getStartTime() > 0
			) {
				repeaterTask.getOutgoingMonitoringEntry().getTimer().updateTimestamp();
			}
		}finally {
			tasksLock.unlock();
		}
	}

	@Override
	public boolean linkReflector(
		DSTARRepeater repeater, String reflectorCallsign, ReflectorHostInfo reflectorHostInfo
	) {
		return linkReflector(false, repeater, reflectorCallsign, reflectorHostInfo);
	}

	public boolean linkReflector(
		boolean autoControl, DSTARRepeater repeater, String reflectorCallsign
	){
		final Optional<ReflectorHostInfo> reflectorHostInfo =
			getGateway().findReflectorByCallsign(reflectorCallsign);

		if(reflectorHostInfo.isPresent())
			return linkReflector(autoControl, repeater, reflectorCallsign, reflectorHostInfo.get());
		else
			return false;
	}

	public boolean linkReflector(
		boolean autoControl, @NonNull DSTARRepeater repeater,
		@NonNull String reflectorCallsign,
		@NonNull ReflectorHostInfo reflectorHostInfo
	){
		if(!checkLinkReflector(autoControl, repeater, reflectorCallsign, reflectorHostInfo)) {
			return false;
		}

		tasksLock.lock();
		try {
			RepeaterTaskEntry repeaterTask = tasks.get(repeater);
			if(repeaterTask == null) {
				repeaterTask = new RepeaterTaskEntry(repeater);
				tasks.put(repeater, repeaterTask);
			}

			ReflectorCommunicationService reflectorService = null;

			//プロトコルから対象のリフレクタサービスを探す
			final Optional<ReflectorCommunicationService> reflectorServiceOp =
				ReflectorCommunicationServiceManager.getService(
					getSystemID(), reflectorHostInfo.getReflectorProtocol()
				);

			if(!reflectorServiceOp.isPresent()) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Could not found reflector communication service protocol " +
						reflectorHostInfo.getReflectorProtocol() + " by reflector callsign = " +
						reflectorCallsign + "."
					);
				}

				return false;
			}

			reflectorService = reflectorServiceOp.get();


			//接続モードかつ処理中以外を削除(ただし完了したものを除く)
			for(Iterator<TaskEntry> it = repeaterTask.getRepeaterTasks().iterator(); it.hasNext();) {
				TaskEntry task = it.next();

				if(
					(
						task.getTaskMode() != TaskMode.Connect ||
						task.getTaskState() != TaskState.Processing
					) && task.getTaskState() != TaskState.Complete
				) {
					it.remove();

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader +
							"Removed task entry.\n    [" + repeater.getRepeaterCallsign() + "] " + task.toString()
						);
					}
				}
			}

			List<TaskEntry> disconnectTasks = new ArrayList<>();

			//接続処理中のタスクがあるかチェック
			List<TaskEntry> connectingTasks =
				Stream.of(repeaterTask.getRepeaterTasks())
				.filter(new Predicate<TaskEntry>() {
					@Override
					public boolean test(TaskEntry value) {
						return
							value.getTaskMode() == TaskMode.Connect &&
							value.getTaskState() == TaskState.Processing;
					}
				})
				.toList();

			//接続中タスクは問答無用で切る
			for(TaskEntry task : connectingTasks) {
				TaskEntry disconnectTask =
						new TaskEntry(
							TaskMode.Disconnect, task.getReflectorService(),
							task.getReflectorCallsign(), task.getReflectorHostInfo()
						);
				disconnectTask.setTaskState(TaskState.TaskAdded);

				disconnectTasks.add(disconnectTask);
			}

			//既にリンクされているリフレクターリンク情報を取得
			final List<ReflectorLinkInformation> linkedReflectorInfo =
					ReflectorCommunicationServiceManager.getLinkInformationOutgoing(getSystemID(), repeater);

			//既にリンクされているリフレクターを切断する
			//ただし、処理中タスクと重複するものを除く
			for(ReflectorLinkInformation linkInfo : linkedReflectorInfo) {
				final Optional<ReflectorCommunicationService> opService =
					ReflectorCommunicationServiceManager.getService(getSystemID(), linkInfo.getLinkProtocol());
				if(!opService.isPresent()) {continue;}

				final ReflectorCommunicationService service = opService.get();

				final boolean overlap = false;
					Stream.of(disconnectTasks)
					.anyMatch(new Predicate<TaskEntry>() {
						@Override
						public boolean test(TaskEntry task) {
							boolean match =
									task.getReflectorService() == service;
							return match;
						}
					});

				if(overlap) {continue;}	//重複の場合にはスキップ

				final TaskEntry disconnectTask =
						new TaskEntry(TaskMode.Disconnect, service, linkInfo.getCallsign());
				disconnectTask.setTaskState(TaskState.TaskAdded);

				disconnectTasks.add(disconnectTask);
			}

			//切断タスクを追加
			if(!disconnectTasks.isEmpty()) {
				for(TaskEntry disconnectTask : disconnectTasks) {
					repeaterTask.addTask(disconnectTask);
				}
			}

			//接続タスクを追加
			final TaskEntry connectTask =
				new TaskEntry(
					TaskMode.Connect, reflectorService,
					reflectorCallsign, reflectorHostInfo
				);
			connectTask.setTaskState(TaskState.TaskAdded);
			repeaterTask.addTask(connectTask);

			//
			repeaterTask.setOutgoingReflectorHostInfo(reflectorHostInfo);

			//Outgoing接続自動切断タイマーセット
			if(repeater.getAutoDisconnectFromReflectorOutgoingUnusedMinutes() > 0) {
				repeaterTask.getOutgoingMonitoringEntry().setStartTime(System.currentTimeMillis());
				repeaterTask.getOutgoingMonitoringEntry().getTimer().updateTimestamp();
			}

		}finally {
			tasksLock.unlock();
		}

		return true;
	}

	@Override
	public boolean unlinkReflector(DSTARRepeater repeater) {
		return unlinkReflector(false, repeater);
	}

	public boolean unlinkReflector(boolean autoControl, DSTARRepeater repeater) {
		if(repeater == null) {return false;}


		if(	//自動制御下のレピータであれば、自動制御を無効にする
			!autoControl &&
			getHelper().isAutoControlledRepeater(repeater)
		) {
			setAutoControlEnable(repeater, false);
		}

		tasksLock.lock();
		try {
			RepeaterTaskEntry repeaterTask = tasks.get(repeater);
			if(repeaterTask == null) {return false;}

			//リフレクタサービスを探す
			if(repeaterTask.getOutgoingReflectorHostInfo() == null) {return false;}
			final Optional<ReflectorCommunicationService> reflectorServiceOp =
				getGateway().getReflectorCommunicationService(
					repeaterTask.getOutgoingReflectorHostInfo().getReflectorProtocol()
				);
			if(!reflectorServiceOp.isPresent()) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Could not found reflector communication service by reflector callsign = " +
						repeaterTask.getOutgoingReflectorHostInfo().getReflectorCallsign() + "."
					);
				}
				return false;
			}
			final ReflectorCommunicationService reflectorService = reflectorServiceOp.get();

			//処理中以外を削除(ただし完了したものを除く)
			for(Iterator<TaskEntry> it = repeaterTask.getRepeaterTasks().iterator(); it.hasNext();) {
				TaskEntry task = it.next();

				if(
					task.getTaskState() != TaskState.Processing &&
					task.getTaskState() != TaskState.Complete
				) {
					it.remove();

					if(log.isTraceEnabled()) {
						log.trace(
							logHeader +
							"Removed task entry.\n[    " + repeater.getRepeaterCallsign() + "] " + task.toString()
						);
					}
				}
			}

			//切断処理中のタスクがあるかチェック
			boolean disconnectingReflector =
				Stream.of(repeaterTask.getRepeaterTasks())
				.anyMatch(new Predicate<TaskEntry>() {
					@Override
					public boolean test(TaskEntry value) {
						return
							value.getTaskMode() == TaskMode.Disconnect &&
							(
								value.getTaskState() == TaskState.TaskAdded ||
								value.getTaskState() == TaskState.Processing

							) &&
							value.getReflectorService() == reflectorService;
					}

				});

			//既に切断中のタスクがあるので新たにタスクを追加しない
			if(disconnectingReflector) {return true;}

			final TaskEntry disconnectEntry =
				new TaskEntry(
					TaskMode.Disconnect, reflectorService,
					repeater.getLinkedReflectorCallsign(),
					repeaterTask.getOutgoingReflectorHostInfo()
				);
			disconnectEntry.setTaskState(TaskState.TaskAdded);
			repeaterTask.addTask(disconnectEntry);

			//Outgoing接続自動切断タイマーセット
			if(repeater.getAutoDisconnectFromReflectorOutgoingUnusedMinutes() > 0) {
				repeaterTask.getOutgoingMonitoringEntry().setStartTime(0);
			}

		}finally {
			tasksLock.unlock();
		}

		return true;
	}

	@Override
	public boolean isReflectorLinked(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	) {
		final List<String> reflectorCallsigns = getLinkedReflectorCallsign(repeater, dir);

		return !reflectorCallsigns.isEmpty();
	}

	@Override
	public List<String> getLinkedReflectorCallsign(
		@NonNull final DSTARRepeater repeater,
		@NonNull final ConnectionDirectionType dir
	) {
		final List<String> reflectors = new ArrayList<String>();

		final List<ReflectorLinkInformation> linkInfo = new ArrayList<ReflectorLinkInformation>();

		for(
			final ReflectorCommunicationService service : getGateway().getReflectorCommunicationServiceAll()
		) {
			switch(dir) {
			case INCOMING:
				final List<ReflectorLinkInformation> incomming = service.getLinkInformationIncoming(repeater);
				if(incomming != null) {linkInfo.addAll(incomming);}
				break;

			case OUTGOING:
				service.getLinkInformationOutgoing(repeater)
				.ifPresent(new Consumer<ReflectorLinkInformation>() {
					@Override
					public void accept(ReflectorLinkInformation outgoing) {
						linkInfo.add(outgoing);
					}
				});
				break;

			case BIDIRECTIONAL:
				final List<ReflectorLinkInformation> bidir = service.getLinkInformation(repeater);
				if(bidir != null) {linkInfo.addAll(bidir);}
				break;

			default:
				return reflectors;
			}
		}

		for(final ReflectorLinkInformation info : linkInfo) {
			if(
				info.getRepeater() == repeater &&
				(
					info.getConnectionDirection() == dir ||
					(
						dir == ConnectionDirectionType.BIDIRECTIONAL &&
						(
							info.getConnectionDirection() == ConnectionDirectionType.INCOMING ||
							info.getConnectionDirection() == ConnectionDirectionType.OUTGOING
						)
					)
				)
			) {reflectors.add(info.getCallsign());}
		}

		return reflectors;
	}

	@Override
	public ReflectorCommunicationService getOutgoingLinkedReflectorCommunicationService(
		@NonNull final DSTARRepeater repeater
	) {
		final List<ReflectorLinkInformation> linkInfo =
			ReflectorCommunicationServiceManager.getLinkInformationOutgoing(getSystemID(), repeater);
		if(linkInfo.isEmpty()) {return null;}

		final Optional<ReflectorCommunicationService> service =
			ReflectorCommunicationServiceManager.getService(getSystemID(), linkInfo.get(0).getLinkProtocol());

		if(service.isPresent())
			return service.get();
		else
			return null;
	}

	@Override
	public boolean setAutoControlEnable(DSTARRepeater repeater, boolean enable) {
		if(repeater == null) {return false;}

		boolean result = false;

		if(enable) {
			result = getHelper().setRepeaterAutoControlEnable(repeater, true);
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					"[" + repeater.getRepeaterCallsign() + "] Enabled reflector link auto control."
				);
			}
		}else {
			result = getHelper().setRepeaterAutoControlEnable(repeater, false);
			if(log.isInfoEnabled()) {
				log.info(
					logHeader +
					"[" + repeater.getRepeaterCallsign() + "] Disabled reflector link auto control."
				);
			}
		}

		return result;
	}

	@Override
	public Optional<Boolean> getAutoControlEnable(DSTARRepeater repeater){
		return getHelper().isRepeaterAutoControlEnable(repeater);
	}

	@Override
	public void processReflectorLinkManagement() {

		final List<ReflectorCommunicationService> reflectorServices =
				ReflectorCommunicationServiceManager.getServices(getSystemID());

		for(ReflectorCommunicationService reflectorService : reflectorServices) {

			ReflectorEvent event = null;
			while((event = reflectorService.getReflectorEvent()) != null) {
				switch(event.getEventType()) {
				case ConnectionStateChange:{

					if(event.getConnectionState() == ReflectorConnectionStates.LINKFAILED) {
						boolean found = false;
						tasksLock.lock();
						try {
							for(RepeaterTaskEntry repeaterEntry : this.tasks.values()) {
								final ReflectorEvent eventInt = event;

								if(
									Stream.of(repeaterEntry.getRepeaterTasks())
									.anyMatch(new Predicate<TaskEntry>() {
										@Override
										public boolean test(TaskEntry value) {
											return
												eventInt.getConnectionId() != null &&
												eventInt.getConnectionId().equals(value.getServiceTaskId());
										}
									})
								) {
									found = true;

									break;
								}
							}

							//同一IDが無ければ、リンクが失われたことを通知する
							if(!found) {
								getGateway().notifyLinkFailedReflector(
									event.getRepeaterCallsign(),
									event.getReflectorCallsign(),
									event.getReflectorHostInfo()
								);
							}
						}finally {
							tasksLock.unlock();
						}
					}

					tasksLock.lock();
					try {
						processStateRepeaters(event);
					}finally {
						tasksLock.unlock();
					}
					break;
				}

				default:
					break;
				}
			}
		}

		tasksLock.lock();
		try {
			processStateRepeaters();

			processOutgoingAutoDisconnect();

			for(RepeaterTaskEntry repeaterEntry : tasks.values())
				repeaterEntry.removeRequestedTaskQueue();
		}finally {
			tasksLock.unlock();
		}

		getHelper().processAutoConnectProcess();
	}

	@Override
	public boolean isAllowReflectorIncomingConnectionWithLocalRepeater(
		@NonNull String localRepeaterCallsign
	) {
		final DSTARRepeater repeater = getGateway().getRepeater(localRepeaterCallsign);

		return repeater != null && repeater.isAllowIncomingConnection();
	}

	@Override
	public boolean isAllowReflectorIncomingConnectionWithRemoteRepeater(
		@NonNull String remoteRepeaterCallsign
	) {
		properties.getLocker().lock();
		try {
			final ReflectorBlackListEntry blackList =
				properties.getReflectorBlackList().get(remoteRepeaterCallsign);

			final boolean matched =
				blackList != null &&
				(
					blackList.getDir() == ConnectionDirectionType.INCOMING ||
					blackList.getDir() == ConnectionDirectionType.BIDIRECTIONAL
				);

			return !matched;

		}finally {
			properties.getLocker().unlock();
		}
	}

	public DSTARRepeater getRepeater(@NonNull final String repeaterCallsign) {
		return gateway.getRepeater(repeaterCallsign);
	}

	private void processOutgoingAutoDisconnect() {
		for(RepeaterTaskEntry repeaterEntry : tasks.values()) {
			if(
				repeaterEntry.getRepeater().getAutoDisconnectFromReflectorOutgoingUnusedMinutes() > 0 &&
				repeaterEntry.getOutgoingMonitoringEntry().getStartTime() > 0 &&
				repeaterEntry.getOutgoingMonitoringEntry().getTimer().isTimeout(
					repeaterEntry.getRepeater().getAutoDisconnectFromReflectorOutgoingUnusedMinutes(),
					TimeUnit.MINUTES
				) &&
				!getHelper().isAutoControlledRepeater(repeaterEntry.getRepeater())
			) {
				repeaterEntry.getOutgoingMonitoringEntry().setStartTime(0);

				final ReflectorHostInfo reflectorHostInfo =
					repeaterEntry.getOutgoingReflectorHostInfo();
				if(reflectorHostInfo == null) {continue;}

				unlinkReflector(repeaterEntry.getRepeater());

				if(log.isInfoEnabled()) {
					log.info(logHeader + "Auto disconnected from " + reflectorHostInfo.getReflectorCallsign() + ".");
				}
			}
		}
	}

	private void processStateRepeaters() {
		for(DSTARRepeater repeater : gateway.getRepeaters())
			processStateByRepeater(repeater);
	}

	private void processStateRepeaters(ReflectorEvent event) {
		for(DSTARRepeater repeater : gateway.getRepeaters())
			processStateByRepeater(repeater, event);
	}

	private boolean processStateByRepeater(DSTARRepeater repeater) {
		return processStateByRepeater(repeater, null);
	}

	private boolean processStateByRepeater(DSTARRepeater repeater, ReflectorEvent event) {
		if(repeater == null) {return false;}

		final RepeaterTaskEntry repeaterEntry = tasks.get(repeater);
		if(repeaterEntry == null) {return false;}

		for(TaskEntry task : repeaterEntry.getRepeaterTasks())
			processState(repeaterEntry, task, event);

		return true;
	}

	private void processState(RepeaterTaskEntry repeaterEntry, TaskEntry task, ReflectorEvent event) {

		switch(task.getTaskMode()) {
		case Connect:{
			processStateConnect(repeaterEntry, task, event);
			break;
		}

		case Disconnect:
			processStateDisconnect(repeaterEntry, task, event);
			break;
		}

	}

	private void processStateConnect(RepeaterTaskEntry repeaterEntry, final TaskEntry task, ReflectorEvent event) {
		if(task.getTaskMode() != TaskMode.Connect) {return;}

		switch(task.getTaskState()) {
		case TaskAdded:{
			if(!pickupNewTaskEntry(repeaterEntry, task)) {return;}

			/*
			final Optional<ReflectorHostInfo> opReflectorHostInfo =
				gateway.findReflectorByCallsign(task.getReflectorCallsign());
			if(!opReflectorHostInfo.isPresent()) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Could not found reflector host address = " + task.getReflectorCallsign() + "."
					);
				}
				task.setTaskState(TaskState.Complete);
				task.setTaskResult(TaskResult.Error);

				repeaterEntry.endTask(task);
			}else {
				final ReflectorHostInfo reflectorHostInfo = opReflectorHostInfo.get();

				InetAddress reflectorAddress = null;
				try {
					reflectorAddress = InetAddress.getByName(reflectorHostInfo.getReflectorAddress());
				} catch (UnknownHostException ex) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Could not resolve host address " + reflectorHostInfo.getReflectorAddress() + "."
						);
					}
				}

				if(reflectorAddress != null) {
*/
				final UUID id =
					task.getReflectorService().linkReflector(
						task.getReflectorCallsign(),
						task.getReflectorHostInfo(),
						repeaterEntry.getRepeater()
					);

				if(log.isTraceEnabled())
					log.trace(logHeader + "Requesting link request...\n" + task.toString(4));

				if(id == null) {
					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Error occurred at reflector link process = " + task.getReflectorCallsign() + "."
						);
					}
					task.setTaskState(TaskState.Complete);
					task.setTaskResult(TaskResult.Error);

					repeaterEntry.endTask(task);
				}else {
					task.setServiceTaskId(id);
					task.setTaskState(TaskState.Processing);
					task.getTaskStateTimeKeeper().setTimeoutTime(10, TimeUnit.SECONDS);
				}

			break;
		}
		case Processing:{
			if(task.getTaskStateTimeKeeper().isTimeout()) {
				if(log.isWarnEnabled())
					log.warn(logHeader + "Connect task timeout.\n" + task.toString(4));

				task.setTaskState(TaskState.Complete);
				task.setTaskResult(TaskResult.Error);
				task.getTaskStateTimeKeeper().setTimeoutTime(20, TimeUnit.SECONDS);

				//リンクエラーを通知する
				getGateway().notifyLinkFailedReflector(
					repeaterEntry.getRepeater().getRepeaterCallsign(),
					task.getReflectorCallsign(),
					task.getReflectorHostInfo()
				);
			}
			else if(
				event != null && task.getServiceTaskId().equals(event.getConnectionId()) &&
				event.getEventType() == ReflectorEventTypes.ConnectionStateChange
			){
				if(event.getConnectionState() == ReflectorConnectionStates.LINKED) {
					task.setTaskState(TaskState.Complete);
					task.setTaskResult(TaskResult.Success);

					//自分自身のタスクを除き、他にタスクがあるか
					boolean availableTask =
						Stream.of(repeaterEntry.getRepeaterTasks())
						.anyMatch(new Predicate<TaskEntry>() {
							@Override
							public boolean test(TaskEntry value) {
								return
									value != task &&
									value.getTaskState() != TaskState.Complete;
							}
						});

					//リフレクタの接続を通知する
					if(!availableTask) {
						getGateway().notifyLinkReflector(
							repeaterEntry.getRepeater().getRepeaterCallsign(),
							task.getReflectorCallsign(),
							task.getReflectorHostInfo()
						);
					}
				}
				else if(event.getConnectionState() == ReflectorConnectionStates.LINKFAILED){
					task.setTaskState(TaskState.Complete);
					task.setTaskResult(TaskResult.Error);

					//リンクエラーを通知する
					getGateway().notifyLinkFailedReflector(
						repeaterEntry.getRepeater().getRepeaterCallsign(),
						task.getReflectorCallsign(),
						task.getReflectorHostInfo()
					);
				}

				repeaterEntry.stopTask(task);
			}
		}
		case Complete:{
			if(task.getTaskStateTimeKeeper().isTimeout()) {
				repeaterEntry.endTask(task);
			}
		}
		case Unknown:{
			break;
		}

		}
	}

	private void processStateDisconnect(
		RepeaterTaskEntry repeaterEntry, final TaskEntry task, ReflectorEvent event
	) {
		if(task.getTaskMode() != TaskMode.Disconnect) {return;}

		switch(task.getTaskState()) {
		case TaskAdded:{
			if(!pickupNewTaskEntry(repeaterEntry, task)) {return;}

			final UUID id =
				task.getReflectorService().unlinkReflector(
					repeaterEntry.getRepeater()
				);

			if(log.isTraceEnabled())
				log.trace(logHeader + "Requesting unlink request...\n" + task.toString(4));

			if(id == null) {
				if(log.isWarnEnabled()) {
					log.warn(
						logHeader +
						"Error occurred at reflector unlink process = " + task.getReflectorCallsign() + "."
					);
				}
				task.setTaskState(TaskState.Complete);
				task.setTaskResult(TaskResult.Error);

				repeaterEntry.endTask(task);

				//エラーが発生したら問答無用で全てのOutgoingを切断
				for(final ReflectorCommunicationService service : getGateway().getReflectorCommunicationServiceAll()) {
					service.unlinkReflector(repeaterEntry.getRepeater());
				}

			}else {
				task.setServiceTaskId(id);
				task.setTaskState(TaskState.Processing);
				task.getTaskStateTimeKeeper().setTimeoutTime(10, TimeUnit.SECONDS);
			}

			break;
		}
		case Processing:{
			if(task.getTaskStateTimeKeeper().isTimeout()) {
				if(log.isWarnEnabled()) {
					log.warn(logHeader + "Disconnect task timeout.\n" + task.toString(4));
				}
				task.setTaskState(TaskState.Complete);
				task.setTaskResult(TaskResult.Error);
				task.getTaskStateTimeKeeper().setTimeoutTime(10, TimeUnit.SECONDS);
			}
			else if(
				event != null &&
				task.getServiceTaskId().equals(event.getConnectionId()) &&
				event.getEventType() == ReflectorEventTypes.ConnectionStateChange
			){
				if(event.getConnectionState() == ReflectorConnectionStates.UNLINKED) {
					task.setTaskState(TaskState.Complete);
					task.setTaskResult(TaskResult.Success);

					//自分自身のタスクを除き、他にタスクがあるか
					boolean availableTask =
						Stream.of(repeaterEntry.getRepeaterTasks())
						.anyMatch(new Predicate<TaskEntry>() {
							@Override
							public boolean test(TaskEntry value) {
								return
									value != task &&
									value.getTaskState() != TaskState.Complete;
							}
						});

					//リフレクタの切断を通知する
					if(!availableTask) {
						getGateway().notifyUnlinkReflector(
							repeaterEntry.getRepeater().getRepeaterCallsign(),
							task.getReflectorCallsign(),
							task.getReflectorHostInfo()
						);
					}
				}
				else {
					task.setTaskState(TaskState.Complete);
					task.setTaskResult(TaskResult.Error);

					//リンクエラーを通知する
					getGateway().notifyLinkFailedReflector(
						repeaterEntry.getRepeater().getRepeaterCallsign(),
						task.getReflectorCallsign(),
						task.getReflectorHostInfo()
					);
				}

				repeaterEntry.stopTask(task);
			}
		}
		case Complete:{
			if(task.getTaskStateTimeKeeper().isTimeout()) {
				repeaterEntry.endTask(task);
			}
		}
		case Unknown:{
			break;
		}

		}
	}

	private boolean pickupNewTaskEntry(RepeaterTaskEntry repeaterEntry, TaskEntry task) {
		assert repeaterEntry != null && task != null;

		if(repeaterEntry.getProcessingTask() != null) {return false;}

		final Optional<TaskEntry> processTask =
			Stream.of(repeaterEntry.getRepeaterTasks())
			.filter(new Predicate<TaskEntry>() {
				@Override
				public boolean test(TaskEntry value) {
					return value.getTaskState() == TaskState.TaskAdded;
				}
			})
			.min(ComparatorCompat.comparingLong(new ToLongFunction<TaskEntry>() {
				@Override
				public long applyAsLong(TaskEntry t) {
					return t.getCreatedTime();
				}
			}));

		if(processTask.isPresent() && processTask.get() == task) {
			repeaterEntry.startTask(task);

			return true;
		}
		else {return false;}
	}

	private boolean checkLinkReflector(
		final boolean autoControl, final DSTARRepeater repeater,
		final String reflectorCallsign, final ReflectorHostInfo reflectorHostInfo
	) {
		if(!repeater.isAllowOutgoingConnection()) {
			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Could not link reflector, repeater " +
					repeater.getRepeaterCallsign() + " is not allowed outgoing connection."
				);
			}

			return false;
		}

		final Optional<Boolean> autocontrolEnable =
			getHelper().isRepeaterAutoControlEnable(repeater);
		if(
			!autoControl &&
			(
				getHelper().isAutoControlledRepeater(repeater) &&
				autocontrolEnable.isPresent() &&
				autocontrolEnable.get()
			)
		) {
			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Could not link reflector, repeater " + repeater.getRepeaterCallsign() +
					" is enabled auto link control."
				);
			}

			return false;
		}

		properties.getLocker().lock();
		try {
			final ReflectorBlackListEntry blacklist =
				properties.getReflectorBlackList().get(reflectorCallsign);
			if(
				blacklist != null &&
				blacklist.isEnable() &&
				(
					blacklist.getDir() == ConnectionDirectionType.OUTGOING ||
					blacklist.getDir() == ConnectionDirectionType.BIDIRECTIONAL
				)
			) {
				if(log.isErrorEnabled()) {
					log.error(
						logHeader + "Blocked link reflector = " + reflectorCallsign +
						" by config."
					);
				}

				return false;
			}
		}finally {
			properties.getLocker().unlock();
		}

		//自局配下に同じコールサインのレピータがあればブロック
		if(
			Stream.of(getGateway().getRepeaters())
			.anyMatch(new Predicate<DSTARRepeater>() {
				@Override
				public boolean test(DSTARRepeater repeater) {
					return repeater.getRepeaterCallsign().equals(reflectorCallsign);
				}
			})
		) {
			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Could not link reflector = " + reflectorCallsign +
					", Same callsign under this gateway."
				);
			}

			return false;
		}

		//Incomingに同じコールサインからの接続があればブロック
		if(
			Stream.of(getGateway().getRepeaters())
			.anyMatch(new Predicate<DSTARRepeater>() {
				@Override
				public boolean test(DSTARRepeater repeater) {
					return Stream.of(
						ReflectorCommunicationServiceManager.getLinkInformationIncoming(getSystemID(), repeater)
					)
					.anyMatch(new Predicate<ReflectorLinkInformation>() {
						@Override
						public boolean test(ReflectorLinkInformation info) {
							return reflectorCallsign.equals(info.getCallsign());
						}
					});
				}
			})
		) {
			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Could not link reflector = " + reflectorCallsign +
					", Already incoming linked from " + reflectorCallsign + "."
				);
			}

			return false;
		}

		//ローカルでループしていればブロック
		InetAddress reflectorAddress = null;
		try {
			reflectorAddress = InetAddress.getByName(reflectorHostInfo.getReflectorAddress());
		}catch(UnknownHostException ex) {
			if(log.isErrorEnabled()) {
				log.error(
					logHeader + "Could not link reflector = " + reflectorCallsign +
					", Unknown host = " + reflectorHostInfo
				);
			}

			return false;
		}
		if(reflectorAddress.isLoopbackAddress()) {
			final Optional<ReflectorCommunicationService> service =
				getGateway().getReflectorCommunicationService(reflectorHostInfo.getReflectorProtocol());
			if(
				service.isPresent() &&
				service.get().isEnableIncomingLink() &&
				service.get().getIncomingLinkPort() == reflectorHostInfo.getReflectorPort()
			) {
				if(log.isErrorEnabled()) {
					log.error(
						logHeader + "Could not link reflector = " + reflectorCallsign +
						", Local loop detected = " + reflectorHostInfo
					);
				}

				return false;
			}
		}

		return true;
	}
}
