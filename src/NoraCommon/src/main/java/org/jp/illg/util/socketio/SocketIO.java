package org.jp.illg.util.socketio;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.model.OperationRequest;
import org.jp.illg.util.socketio.model.OperationSet;
import org.jp.illg.util.socketio.model.SocketIOTask;
import org.jp.illg.util.socketio.model.SocketIOTaskQueueEntry;
import org.jp.illg.util.thread.ThreadBase;
import org.jp.illg.util.thread.ThreadProcessResult;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.Optional;
import com.annimon.stream.function.Consumer;

import android.annotation.SuppressLint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocketIO extends ThreadBase implements AutoCloseable{

	public interface SocketIOProcessingHandlerInterface{
		public OperationRequest socketIOReadEvent(SocketIOEntry<? extends SelectableChannel> entry);
		public OperationRequest socketIOWriteEvent(SocketIOEntry<? extends SelectableChannel> entry);
		public void socketIOErrorEvent(SocketIOEntry<? extends SelectableChannel> entry, Exception ex);
		public OperationRequest socketIOAcceptedEvent(SocketIOEntry<? extends SelectableChannel> entry);
		public OperationRequest socketIOConnectedEvent( SocketIOEntry<? extends SelectableChannel> entry);
	}

	public static enum ChannelType{
		Unknown,
		TCPServer,
		TCPServerClient,
		TCPClient,
		UDP,
	}

	public static enum ChannelDirection{
		Unknown,
		IN,
		OUT,
	}

	private class ChannelRegistrationEntry{
		public UUID id;
		public Condition registrationCompletedCondition;
		public boolean completed;
		public boolean error;
		public int operationSet;
		public SocketIOEntry<? extends SelectableChannel> attachment;
		public Exception ex;
		public String description;

		public final Timer inactivityTimer;

		public ChannelRegistrationEntry(
			Lock lock, UUID id, int operationSet,
			SocketIOEntry<? extends SelectableChannel> attachment,
			String description
		) {
			this.registrationCompletedCondition = lock.newCondition();
			this.id = id;
			this.completed = false;
			this.error = false;
			this.operationSet = operationSet;
			this.attachment = attachment;
			this.description = description;
			this.ex = null;
			this.inactivityTimer = new Timer();
			this.inactivityTimer.updateTimestamp();
		}
	}

	@AllArgsConstructor
	private class KeyEntry{
		public WeakReference<SelectionKey> key;
		public String description;
	}

	private final Lock locker;

	private Selector selector;

	private final Queue<ChannelRegistrationEntry> channelRegistrationQueue;

	private final Map<SelectionKey, SocketIOTaskQueueEntry> taskQueue;
	private final Timer taskQueueCleanupIntervalTimekeeper;

	private final OperationRequest operationRequest;

	private final List<KeyEntry> keyList;
	private final Timer keyListOutputPeriodKeeper;

	private static final String logHeader;

	@Getter(AccessLevel.PROTECTED)
	private final ExecutorService workerExecutor;

	static {
		logHeader = SocketIO.class.getSimpleName() + " : ";
	}

	public SocketIO(
		ThreadUncaughtExceptionListener exceptionListener,
		@NonNull ExecutorService workerExecutor
	) {
		super(exceptionListener, SocketIO.class.getSimpleName(), -1);

		this.workerExecutor = workerExecutor;

		channelRegistrationQueue = new LinkedList<ChannelRegistrationEntry>();
		taskQueue = new HashMap<>(10);
		taskQueueCleanupIntervalTimekeeper = new Timer();
		taskQueueCleanupIntervalTimekeeper.updateTimestamp();

		locker = new ReentrantLock();

		operationRequest = new OperationRequest();

		keyList = new ArrayList<>();
		keyListOutputPeriodKeeper = new Timer();
		keyListOutputPeriodKeeper.updateTimestamp();
	}

	@Override
	public boolean start() {
		if(isRunning()){
			if(log.isDebugEnabled()) {log.debug(logHeader + "Already running.");}

			return true;
		}

		if(!super.start()) {return false;}

		return true;
	}

	@Override
	public boolean isRunning() {
		if(
			super.isRunning() &&
			selector != null && selector.isOpen()
		)
			return true;
		else
			return false;
	}

	@Override
	public void stop() {
		super.stop();
	}

	@Override
	public void close() {
		stop();
	}

	@Override
	protected ThreadProcessResult threadInitialize(){
		try {
			selector = Selector.open();
		}catch(IOException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not open selector.",ex);

			return super.threadFatalError("", ex);
		}

		return ThreadProcessResult.NoErrors;
	}

	@Override
	protected void threadFinalize() {
		//全てのチャンネルをクローズ
		closeAllChannelRegistrationQueue();

		try {
			if(selector != null && selector.isOpen()) {selector.close();}
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Error occurred at selector close()",ex);
		}
	}

	@SuppressLint("NewAPI")
	public SocketIOEntryTCPServer registTCPServer(
		@NonNull final InetSocketAddress bindAddress,
		@NonNull final SocketIOProcessingHandlerInterface handler,
		final String description
	) {
		if(selector == null || !selector.isOpen()) {return null;}

		ServerSocketChannel channel = null;
		try {
			channel = ServerSocketChannel.open();
			channel.configureBlocking(false);

			if(SystemUtil.IS_Android){
				channel.socket().setReuseAddress(true);
				channel.socket().bind(bindAddress);
			}
			else {
				channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
				channel.bind(bindAddress);
			}

			final SocketIOEntryTCPServer entry = new SocketIOEntryTCPServer(channel, handler);
			entry.setLocalAddress((InetSocketAddress)channel.socket().getLocalSocketAddress());

			return registrationRequest(
				SelectionKey.OP_ACCEPT, entry,
				description != null ? description : ""
			) ? entry : null;

		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not register TCP server channel.", ex);

			try {
				if(channel != null && channel.isOpen()) {channel.close();}
			}catch(IOException ex2) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Exception occurred at channel close.",ex2);
			}
			return null;
		}
	}

	public SocketIOEntryTCPClient registTCPClient(
		@NonNull final InetSocketAddress dstAddress,
		@NonNull final SocketIOProcessingHandlerInterface handler,
		final String description
	) {
		if(selector == null || !selector.isOpen()) {return null;}

		SocketChannel channel = null;
		try {
			channel = SocketChannel.open();
			channel.configureBlocking(false);

			final SocketIOEntryTCPClient entry = new SocketIOEntryTCPClient(channel, handler);
			entry.setRemoteAddress(dstAddress);
			entry.setLocalAddress((InetSocketAddress)channel.socket().getLocalSocketAddress());

			return registrationRequest(
				SelectionKey.OP_CONNECT, entry,
				description != null ? description : ""
			) ? entry : null;

		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not regist TCP client channel.", ex);

			try {
				if(channel != null && channel.isOpen()) {channel.close();}
			}catch(IOException ex2) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Exception occurred at channel close.", ex2);
			}

			return null;
		}
	}

	@SuppressLint("NewAPI")
	public SocketIOEntryUDP registUDP(
		final InetSocketAddress bindAddress,
		@NonNull final SocketIOProcessingHandlerInterface handler,
		final String description
	) {
		if(selector == null || !selector.isOpen()) {return null;}

		DatagramChannel channel = null;
		try {
			channel = DatagramChannel.open();
			channel.configureBlocking(false);

			if(SystemUtil.IS_Android) {
				channel.socket().setReuseAddress(true);
				channel.socket().bind(bindAddress != null ? bindAddress : new InetSocketAddress(0));
			}
			else {
				channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
				channel.bind(bindAddress != null ? bindAddress : new InetSocketAddress(0));
			}

			SocketIOEntryUDP entry = new SocketIOEntryUDP(channel, handler);
			entry.setLocalAddress((InetSocketAddress)channel.socket().getLocalSocketAddress());

			return registrationRequest(
				SelectionKey.OP_READ, entry,
				description != null ? description : ""
			) ? entry : null;

		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not regist UDP channel.", ex);

			try {
				if(channel != null && channel.isOpen()) {channel.close();}
			}catch(IOException ex2) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Exception occurred at channel close.",ex2);
			}
			return null;
		}
	}

	public SocketIOEntryUDP registUDP(
		@NonNull final SocketIOProcessingHandlerInterface handler,
		final String description
	){
		return registUDP(null, handler, description != null ? description : "");
	}

	public boolean interestOps(SelectionKey key, OperationRequest ops) throws CancelledKeyException {
		if(key == null || ops == null) {return false;}

		return interestOps(key, ops, null);
	}

	public ThreadProcessResult process() {

		ThreadProcessResult result;

		//セレクタプロセス
		result = processSelector();
		if(result != ThreadProcessResult.NoErrors) {return result;}

		//セレクタ登録処理
		result = processChannelRegistrationQueue();
		if(result != ThreadProcessResult.NoErrors) {return result;}

		//回収されない登録エントリを削除
		cleanupChannelRegistrationQueue();

		//キーリストログ出力
		outputKeyList();

		return ThreadProcessResult.NoErrors;
	}

	private boolean registrationRequest(
		final int operationSet,
		final SocketIOEntry<? extends SelectableChannel> entry,
		final String description
	){

		final UUID id = UUID.randomUUID();
		final ChannelRegistrationEntry registRequestEntry =
				new ChannelRegistrationEntry(locker, id, operationSet, entry, description);

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"Added registration task to entry queue\n" +
				"Local:" + entry.getLocalAddress() +
				(entry.getChannelType() == ChannelType.TCPClient ? ("/Remote:" + entry.getRemoteAddress()) : "") + "\n" +
				"ID:" + id.toString()
			);
		}

		synchronized(channelRegistrationQueue) {
			channelRegistrationQueue.add(registRequestEntry);
		}

		selector.wakeup();

		final Timer registrationLimitTime =
				new Timer(TimeUnit.SECONDS.toMillis(10));

		boolean completed = false;
		boolean error = false;
		boolean timeout = false;
		do {
			try {
				locker.lock();
				try {
					registRequestEntry.registrationCompletedCondition.await(1, TimeUnit.SECONDS);
				}finally {
					locker.unlock();
				}
			} catch(InterruptedException ex) {

				//割り込みが入ったので、登録中止
				synchronized(channelRegistrationQueue) {
					for(final Iterator<ChannelRegistrationEntry> it = channelRegistrationQueue.iterator();it.hasNext();) {
						final ChannelRegistrationEntry removeEntry = it.next();

						if(id.equals(removeEntry.id)) {
							it.remove();

							if(removeEntry.completed) {closeRegistrationEntry(removeEntry);}

							break;
						}
					}
				}

				return false;
			}

			synchronized(channelRegistrationQueue) {
				for(final Iterator<ChannelRegistrationEntry> it = channelRegistrationQueue.iterator(); it.hasNext();) {
					final ChannelRegistrationEntry reqEntry = it.next();
					if(id.equals(reqEntry.id)) {
						if(reqEntry.completed || reqEntry.error) {
							it.remove();

							completed = reqEntry.completed;
							error = reqEntry.error;

							if(reqEntry.error) {
								closeRegistrationEntry(reqEntry);

								if(log.isWarnEnabled())
									log.warn(logHeader + "Failed channel registration", reqEntry.ex);
							}
						}

						break;
					}
				}
			}

			timeout = registrationLimitTime.isTimeout();

		} while(!completed && !error && !timeout);

		if(log.isTraceEnabled()) {
			log.trace(
				logHeader +
				"End of registration task\n" +
				"Local:" + entry.getLocalAddress() +
				(entry.getChannelType() == ChannelType.TCPClient ? ("/Remote:" + entry.getRemoteAddress()) : "") + "\n" +
				"ID:" + id.toString()
			);
		}

		return completed && !error;
	}

	private ThreadProcessResult processSelector() {

		try {
			selector.select(100);
		} catch (IOException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not select() selector.",ex);

			return super.threadFatalError("", ex);
		}

		final Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
		while(keys.hasNext()) {
			final SelectionKey key = keys.next();
			keys.remove();

			if(!key.isValid()) {continue;}

			try {
				if(key.isAcceptable()) {
					// TCPサーバーで新規接続がある
					keyIsAcceptable(key);
				}
				else if(key.isConnectable()) {
					//TCPクライアント接続完了
					keyIsConnected(key);
				}
				else {
					//読み込み可能なチャネルがある
					if(key.isValid() && key.isReadable()) {keyIsReadable(key);}

					//書き込み可能なチャネルがある
					if(key.isValid() && key.isWritable()) {keyIsWritable(key);}
				}

			}catch(final CancelledKeyException ignore) {
/*
				if(log.isDebugEnabled())
					log.debug(logHeader + "Calcelled key.",  ex);

				getEntry(key)
				.ifPresent(new Consumer<SocketIOEntry<? extends SelectableChannel>>() {
					@Override
					public void accept(SocketIOEntry<? extends SelectableChannel> entry) {
						dispatchTask(key, new Runnable() {
							@Override
							public void run() {
								entry.getHandler().socketIOErrorEvent(entry, ex);
							}
						});
					}
				});
*/
			}
		}

		processTaskQueue();

		return ThreadProcessResult.NoErrors;
	}

	private ThreadProcessResult processChannelRegistrationQueue() {
		synchronized(channelRegistrationQueue) {
			for(ChannelRegistrationEntry registEntry : channelRegistrationQueue) {

				if(registEntry.completed || registEntry.error) {continue;}

				if(log.isTraceEnabled()) {
					log.trace(
						logHeader +
						"Start registration entry\n" +
						"Local:" + registEntry.attachment.getLocalAddress() +
						(registEntry.attachment.getChannelType() == ChannelType.TCPClient ?
								("/Remote:" + registEntry.attachment.getRemoteAddress()) : "") + "\n" +
						"ID:" + registEntry.id.toString()
					);
				}

				SelectionKey registKey = null;
				try {
					registKey =
						registEntry.attachment.getChannel().register(
							selector, registEntry.operationSet, registEntry.attachment
						);
				} catch(IOException | CancelledKeyException ex) {
					registEntry.error = true;
					registEntry.ex = ex;
				}
				registEntry.attachment.setKey(registKey);
				registEntry.completed = true;

				keyList.add(new KeyEntry(new WeakReference<SelectionKey>(registKey), registEntry.description));

				if(!registEntry.error && registEntry.attachment instanceof SocketIOEntryTCPClient) {
					// 登録完了したのでTCPクライアント接続の場合には接続を行う
					try {
						((SocketIOEntryTCPClient)registEntry.attachment).getChannel().configureBlocking(false);
						((SocketIOEntryTCPClient)registEntry.attachment).getChannel().connect(
							registEntry.attachment.getRemoteAddress()
						);
					}catch(IOException | UnresolvedAddressException ex) {
						if(log.isWarnEnabled())
							log.warn(logHeader + "TCP client connect error.", ex);

						registEntry.error = true;
					}
				}

				registEntry.inactivityTimer.updateTimestamp();

				if(log.isTraceEnabled()) {
					log.trace(
						"End of registration entry\n" +
						"Local:" + registEntry.attachment.getLocalAddress() +
						(registEntry.attachment.getChannelType() == ChannelType.TCPClient ?
								("/Remote:" + registEntry.attachment.getRemoteAddress()) : "") + "\n" +
						"ID:" + registEntry.id.toString()
					);
				}

				locker.lock();
				try {
					registEntry.registrationCompletedCondition.signalAll();
				}finally {
					locker.unlock();
				}
			}
		}

		return ThreadProcessResult.NoErrors;
	}

	private void cleanupChannelRegistrationQueue() {
		synchronized(channelRegistrationQueue) {
			for(final Iterator<ChannelRegistrationEntry> it = channelRegistrationQueue.iterator(); it.hasNext();) {
				final ChannelRegistrationEntry registEntry = it.next();

				if(
					registEntry.completed &&
					registEntry.inactivityTimer.isTimeout(30, TimeUnit.SECONDS)
				) {
					it.remove();

					closeRegistrationEntry(registEntry);

					if(log.isWarnEnabled()) {
						log.warn(
							logHeader +
							"Removed uncollected registration entry = " + registEntry.attachment.getChannelType()
						);
					}
				}
			}
		}
	}

	private void closeAllChannelRegistrationQueue() {
		synchronized(channelRegistrationQueue) {
			for(final Iterator<ChannelRegistrationEntry> it = channelRegistrationQueue.iterator(); it.hasNext();) {
				final ChannelRegistrationEntry registEntry = it.next();
				it.remove();

				closeRegistrationEntry(registEntry);
			}
		}
	}

	private static void closeRegistrationEntry(ChannelRegistrationEntry registEntry) {
		try {
			registEntry.attachment.getChannel().close();
		} catch (IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logHeader + "Error occurred at close() for channel registration entry.", ex);
		}
	}

	private boolean keyIsAcceptable(SelectionKey key) {
		if(key.attachment() == null || !(key.attachment() instanceof SocketIOEntryTCPServer)) {return false;}

		final SocketIOEntryTCPServer serverEntry = (SocketIOEntryTCPServer)key.attachment();
		SocketChannel channel = null;
		try {
			channel = serverEntry.getChannel().accept();
			if(channel == null) {return false;}	// could not found accepted connection

			channel.configureBlocking(false);
		} catch(IOException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "error occurred at server socket accept(), ignore connection.", ex);

			serverEntry.getHandler().socketIOErrorEvent(serverEntry, ex);

			return false;
		}

		final SocketIOEntryTCPServerClient serverClientEntry =
				new SocketIOEntryTCPServerClient(serverEntry.getChannel(), channel, serverEntry.getHandler());
		serverClientEntry.setLocalAddress(serverEntry.getLocalAddress());
		serverClientEntry.setRemoteAddress(
				new InetSocketAddress(channel.socket().getInetAddress().getHostAddress(), channel.socket().getPort())
		);

		SelectionKey serverClientKey = null;
		try {
			serverClientKey =
				channel.register(selector, SelectionKey.OP_READ, serverClientEntry);
		}catch(ClosedChannelException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Client channel is closed.", ex);

			serverEntry.getHandler().socketIOErrorEvent(serverEntry, ex);

			return false;
		}

		serverClientEntry.setKey(serverClientKey);

		dispatchTask(key, new Runnable() {
			@Override
			public void run() {
				final OperationRequest operationSet =
					serverClientEntry.getHandler().socketIOAcceptedEvent(serverClientEntry);

				if(operationSet != null)
					interestOps(serverClientEntry.getKey(), operationSet, serverClientEntry);
			}
		});

		return true;
	}

	private boolean keyIsConnected(SelectionKey key) {
		if(
			key.attachment() == null ||
			!(key.attachment() instanceof SocketIOEntryTCPClient)
		) {
			return false;
		}

		final SocketIOEntryTCPClient clientEntry = (SocketIOEntryTCPClient)key.attachment();
		try {
			clientEntry.getChannel().finishConnect();

			clientEntry.setLocalAddress(
				new InetSocketAddress(
					clientEntry.getChannel().socket().getLocalAddress().getHostAddress(),
					clientEntry.getChannel().socket().getLocalPort()
				)
			);
		} catch(IOException ex) {
			if(log.isWarnEnabled())
				log.warn(logHeader + "Error occurred at client socket finishConnect(), connection failed.", ex);

			clientEntry.getHandler().socketIOErrorEvent(clientEntry, ex);

			try {
				clientEntry.getChannel().close();
			} catch (IOException ex2) {
				if(log.isDebugEnabled())
					log.debug(logHeader + "Error occurred at channel close().", ex2);
			}

			return false;
		}

		dispatchTask(key, new Runnable() {
			@Override
			public void run() {
				final OperationRequest operationSet = new OperationRequest();
				final OperationRequest operationRequest =
					clientEntry.getHandler().socketIOConnectedEvent(clientEntry);
				if(operationRequest != null) {operationSet.combine(operationRequest);}
				operationSet.addUnsetRequest(OperationSet.CONNECT);
				operationSet.addSetRequests(OperationSet.READ);

				if(operationSet != null)
					interestOps(clientEntry.getKey(), operationSet, clientEntry);
			}
		});

		return true;
	}

	private boolean keyIsReadable(SelectionKey key) {
		if(key.attachment() == null) {return false;}

		if(key.attachment() instanceof SocketIOEntryTCPServerClient) {
			final SocketIOEntryTCPServerClient serverClientEntry =
				(SocketIOEntryTCPServerClient)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						serverClientEntry.getHandler().socketIOReadEvent(serverClientEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else if(key.attachment() instanceof SocketIOEntryTCPClient){
			final SocketIOEntryTCPClient clientEntry = (SocketIOEntryTCPClient)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						clientEntry.getHandler().socketIOReadEvent(clientEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else if(key.attachment() instanceof SocketIOEntryUDP) {
			final SocketIOEntryUDP udpEntry = (SocketIOEntryUDP)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						udpEntry.getHandler().socketIOReadEvent(udpEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else {
			if(log.isErrorEnabled())
				log.error(logHeader + "Unknown entry detected.");

			try {
				key.channel().close();
			} catch(IOException ex) {}

			return false;
		}

		return true;
	}

	private boolean keyIsWritable(SelectionKey key) {
		if(key.attachment() == null) {return true;}

		if(key.attachment() instanceof SocketIOEntryTCPServerClient) {
			final SocketIOEntryTCPServerClient serverClientEntry =
				(SocketIOEntryTCPServerClient)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						serverClientEntry.getHandler().socketIOWriteEvent(serverClientEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else if(key.attachment() instanceof SocketIOEntryTCPClient){
			final SocketIOEntryTCPClient clientEntry = (SocketIOEntryTCPClient)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						clientEntry.getHandler().socketIOWriteEvent(clientEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else if(key.attachment() instanceof SocketIOEntryUDP) {
			final SocketIOEntryUDP udpEntry = (SocketIOEntryUDP)key.attachment();

			dispatchTask(key, new Runnable() {
				@Override
				public void run() {
					final OperationRequest operationSet =
						udpEntry.getHandler().socketIOWriteEvent(udpEntry);

					if(operationSet != null)
						interestOps(key, operationSet, null);
				}
			});
		}
		else {
			if(log.isErrorEnabled())
				log.error(logHeader + "Unknown entry detected.");

			try {
				key.channel().close();
			}catch(IOException ex) {}

			return false;
		}

		return true;
	}

	private Optional<SocketIOEntry<? extends SelectableChannel>> getEntry(SelectionKey key) {
		if(key.attachment() != null && key.attachment() instanceof SocketIOEntry<?>) {
			@SuppressWarnings("unchecked")
			SocketIOEntry<? extends SelectableChannel> entry =
				(SocketIOEntry<? extends SelectableChannel>)key.attachment();

			return Optional.<SocketIOEntry<? extends SelectableChannel>>ofNullable(entry);
		}
		else {
			return Optional.empty();
		}
	}

	private boolean interestOps(
		@NonNull SelectionKey key, @NonNull OperationRequest ops,
		SocketIOEntry<? extends SelectableChannel> entry
	) {
		if(ops != null && key.isValid()) {
			try{
				locker.lock();
				try {
					operationRequest.combine(ops);
					operationRequest.processRequests(key);
				}finally {locker.unlock();}

				if(super.getWorkerThread() != Thread.currentThread()) {
					selector.wakeup();

					if(log.isTraceEnabled()) { log.trace(logHeader + "Selector wakeup.");}
				}

				return true;

			}catch(final CancelledKeyException ex){
				if(log.isDebugEnabled())
					log.debug(logHeader + "Key is cancelled.", ex);

				if(entry != null)
					entry.getHandler().socketIOErrorEvent(entry, ex);
				else {
					getEntry(key)
					.ifPresent(new Consumer<SocketIOEntry<? extends SelectableChannel>>() {
						@Override
						public void accept(SocketIOEntry<? extends SelectableChannel> entry) {
							entry.getHandler().socketIOErrorEvent(entry, ex);
						}
					});
				}

			}
		}

		return false;
	}

	private void outputKeyList() {
		if(keyListOutputPeriodKeeper.isTimeout(5, TimeUnit.MINUTES)) {
			keyListOutputPeriodKeeper.updateTimestamp();

			if(log.isDebugEnabled()) {
				StringBuilder sb = new StringBuilder(logHeader);
				sb.append("Output active key list...\n");

				final String indent = "    ";

				sb.append(indent);
				sb.append("=== Active key list ===\n");
				for(Iterator<KeyEntry> it = keyList.iterator(); it.hasNext();) {
					final KeyEntry keyEntry = it.next();
					final SelectionKey key = keyEntry.key.get();

					if(key == null) {
						it.remove();

						continue;
					}

					final boolean validKey = key.isValid();

					int ops = 0;
					if(!validKey) {
						it.remove();
					}
					else {
						try {
							ops = key.interestOps();
						}catch(final CancelledKeyException ex) {
							if(log.isWarnEnabled())
								log.warn(logHeader + "Key is cancelled.", ex);

							getEntry(key)
							.ifPresent(new Consumer<SocketIOEntry<? extends SelectableChannel>>() {
								@Override
								public void accept(SocketIOEntry<? extends SelectableChannel> entry) {
									entry.getHandler().socketIOErrorEvent(entry, ex);
								}
							});
						}
					}

					sb.append(indent);

					if(!validKey) {sb.append("[REMOVE]");}

					@SuppressWarnings("unchecked")
					final SocketIOEntry<? extends SelectableChannel> entry =
						(SocketIOEntry<? extends SelectableChannel>)key.attachment();

					sb.append("Type:");
					sb.append(entry.getChannelType());
					sb.append("/");
					sb.append("LocalAddress:");
					sb.append(entry.getLocalAddress());
					sb.append("/");
					sb.append("RemoteAddress:");
					if(
						entry.getChannelType() == ChannelType.TCPClient ||
						entry.getChannelType() == ChannelType.TCPServerClient
					) {
						sb.append(entry.getRemoteAddress());
					}
					else {
						sb.append("-");
					}
					sb.append("/");
					sb.append(String.format("interestOps=0x%08X", ops));
					sb.append("/");
					sb.append("A:");
					sb.append((ops & SelectionKey.OP_ACCEPT) != 0 ? "1" : "0");
					sb.append("/");
					sb.append("C:");
					sb.append((ops & SelectionKey.OP_CONNECT) != 0 ? "1" : "0");
					sb.append("/");
					sb.append("R:");
					sb.append((ops & SelectionKey.OP_READ) != 0 ? "1" : "0");
					sb.append("/");
					sb.append("W:");
					sb.append((ops & SelectionKey.OP_WRITE) != 0 ? "1" : "0");
					sb.append("/");
					sb.append("Description:");
					sb.append(keyEntry.description);

					if(it.hasNext()) {sb.append("\n");}
				}

				log.debug(sb.toString());
			}
		}
	}

	private boolean processTaskQueue() {
		boolean success = true;

		locker.lock();
		try {
			for(final Entry<SelectionKey, SocketIOTaskQueueEntry> e : taskQueue.entrySet()) {
				final SelectionKey key = e.getKey();

				success &= processTaskQueue(key);
			}
		}finally {
			locker.unlock();
		}

		cleanupTaskQueue();

		return success;
	}

	private boolean processTaskQueue(
		@NonNull final SelectionKey key, final Runnable task
	) {

		boolean success = true;

		locker.lock();
		try {
			SocketIOTaskQueueEntry queue = taskQueue.get(key);
			if(queue == null) {
				queue = new SocketIOTaskQueueEntry(key);

				taskQueue.put(key, queue);
			}

			//タスクキューが空でない場合には、キューにあるタスクを実行
			//新しいタスクがある場合には、キューへ追加
			if(!queue.getQueue().isEmpty()) {
				//キューのタスクを実行中でなければタスクにあるキューを実行
				if(!queue.isProcessing()) {
					final Runnable pickupTask = queue.getQueue().poll();

					if(executeTask(key, pickupTask))
						queue.setProcessing(true);
					else
						success = false;
				}
				//タスクを実行中であれば、新規タスクをキューへ追加
				else if(task != null) {success &= queue.getQueue().add(task);}
			}
			//すぐに実行可能であればキューを使用せずに即実行
			else if(queue.getQueue().isEmpty() && !queue.isProcessing() && task != null) {
				if(executeTask(key, task))
					queue.setProcessing(true);
				else
					success = false;
			}

		}finally {
			locker.unlock();
		}

		return success;
	}

	private boolean processTaskQueue(final SelectionKey key) {
		return processTaskQueue(key, null);
	}

	private boolean dispatchTask(
		@NonNull final SelectionKey key, final Runnable task
	) {
		return processTaskQueue(key, task);
	}

	private boolean executeTask(final SelectionKey key, final Runnable task) {
		final Future<?> r =
			workerExecutor.submit(new SocketIOTask(key, getExceptionListener()) {
				@Override
				public void task() {
					try {
						task.run();
					}finally {
						//キュー実行終了をマーク
						locker.lock();
						try {
							final SocketIOTaskQueueEntry queue = taskQueue.get(key);
							if(queue != null) {queue.setProcessing(false);}
						}finally {
							locker.unlock();
						}

						//次のタスクがあれば実行
						processTaskQueue(getKey());
					}
				}
			});

		return r != null;
	}

	private void cleanupTaskQueue() {
		locker.lock();
		try {
			if(!taskQueueCleanupIntervalTimekeeper.isTimeout(3, TimeUnit.MINUTES)) {return;}
			taskQueueCleanupIntervalTimekeeper.updateTimestamp();

			for(
				final Iterator<Entry<SelectionKey, SocketIOTaskQueueEntry>> it = taskQueue.entrySet().iterator();
				it.hasNext();
			) {
				final Entry<SelectionKey, SocketIOTaskQueueEntry> e = it.next();
				final SelectionKey key = e.getKey();
				final SocketIOTaskQueueEntry q = e.getValue();

				if(!key.isValid() || q.getQueue().isEmpty()) {it.remove();}
			}
		}finally {
			locker.unlock();
		}
	}
}
