package org.jp.illg.util.socketio.napi.model;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.FormatUtil;
import org.jp.illg.util.SystemUtil;
import org.jp.illg.util.Timer;
import org.jp.illg.util.socketio.model.BufferProtocol;
import org.jp.illg.util.socketio.support.HostIdent;
import org.jp.illg.util.socketio.support.HostIdentType;

import com.annimon.stream.Stream;
import com.annimon.stream.function.Predicate;

import android.annotation.SuppressLint;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class KeyEntry<BUFT extends BufferEntry> {

	private static final long bufferTimeoutMillis = 60000;

	private static final String logTag;

	@Getter
	private final long createdTimestamp;

	@Getter
	private final Lock locker;

	@Getter
	private final BufferProtocol protocol;

	@Getter
	private final SelectionKey key;

	@Getter
	private HostIdentType hostIdentType;

	@Getter
	@Setter
	private int bufferSizeUDP;
	private static final int bufferSizeUDPDefault = 1024 * 8;

	@Getter
	@Setter
	private int bufferSizeTCP;
	private static final int bufferSizeTCPDefault = 1024 * 96;

	private final Timer activityTimestamp;

	private final Map<HostIdent, BUFT> udpBuffers;

	private final BUFT tcpBuffer;

	@Getter
	private final boolean directBuffer;

	@Getter
	private final Class<BUFT> bufferClass;

	static {
		logTag = KeyEntry.class.getSimpleName() + " : ";
	}

	public KeyEntry(
		@NonNull SelectionKey key,
		@NonNull HostIdentType hostIdentType,
		@NonNull Class<BUFT> bufferClass,
		final int bufferSizeTCP,
		final int bufferSizeUDP,
		final boolean directBuffer
	) {
		this(key, hostIdentType, bufferClass, bufferSizeTCP, bufferSizeUDP, directBuffer, null, null);
	}

	public KeyEntry(
		@NonNull SelectionKey key,
		@NonNull HostIdentType hostIdentType,
		@NonNull Class<BUFT> bufferClass,
		final int bufferSizeTCP,
		final int bufferSizeUDP,
		final boolean directBuffer,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress
	) {
		super();

		createdTimestamp = System.currentTimeMillis();

		this.key = key;
		this.hostIdentType = hostIdentType;

		this.bufferClass = bufferClass;

		if(key.channel() instanceof SocketChannel)
			protocol = BufferProtocol.TCP;
		else if(key.channel() instanceof DatagramChannel)
			protocol = BufferProtocol.UDP;
		else
			throw new UnsupportedOperationException("Channel support type is SocketChannel and DatagramChannel only.");

		locker = new ReentrantLock();

		activityTimestamp = new Timer();
		activityTimestamp.updateTimestamp();

		if(bufferSizeTCP <= 0)
			this.bufferSizeTCP = bufferSizeTCPDefault;
		else
			this.bufferSizeTCP = bufferSizeTCP;

		if(bufferSizeUDP <= 0)
			this.bufferSizeUDP = bufferSizeUDPDefault;
		else
			this.bufferSizeUDP = bufferSizeUDP;

		this.directBuffer = directBuffer;

		if(protocol == BufferProtocol.TCP) {
			udpBuffers = null;
			tcpBuffer =
				getNewClassBUFTInstance(
					key, HostIdentType.RemoteLocalAddressPort, bufferSizeTCP, directBuffer,
					remoteAddress, localAddress
				);
		}
		else {
			udpBuffers = new ConcurrentHashMap<>();
			tcpBuffer = null;
		}
	}

	public boolean isAccessTimeout(final long duration, @NonNull TimeUnit timeUnit) {
		return activityTimestamp.isTimeout(duration, timeUnit);
	}

	public void updateActivityTime() {
		activityTimestamp.updateTimestamp();
	}

	public BUFT getTCPBuffer() {
		if(protocol == BufferProtocol.TCP) {
			return tcpBuffer;
		}
		else
			return null;
	}

	public BUFT getBuffer(
		@NonNull final InetSocketAddress remoteAddress,
		@NonNull final InetSocketAddress localAddress
	) {
		BUFT entry = null;

		switch(protocol) {
			case TCP:
				entry = getTCPBuffer();
				break;

			case UDP:{
				final HostIdent host = new HostIdent(
					hostIdentType,
					localAddress.getAddress(), localAddress.getPort(),
					remoteAddress.getAddress(), remoteAddress.getPort()
				);
				synchronized(udpBuffers) {
					entry = udpBuffers.get(host);

					if(entry == null && (entry = createBuffer(remoteAddress)) != null) {
						udpBuffers.put(host, entry);
					}
				}
				break;
			}
			default:
				throw new RuntimeException();
		}

		return entry;
	}

	public List<BUFT> getUDPBuffer() {
		if(protocol == BufferProtocol.UDP) {
			locker.lock();
			try {
				return new ArrayList<>(udpBuffers.values());
			}finally {
				locker.unlock();
			}
		}
		else {
			return new ArrayList<>();
		}
	}

	public void cleanTimeoutBuffer() {
		if(protocol == BufferProtocol.TCP) {
			if(tcpBuffer.isActivityTimeout(bufferTimeoutMillis, TimeUnit.MILLISECONDS)) {
				if(log.isDebugEnabled()) {
					log.debug(
						logTag +
						"Remove inactive buffer entry.\n" + tcpBuffer.toString(4)
					);
				}

				tcpBuffer.clear();
			}
		}
		else if(protocol == BufferProtocol.UDP) {
			locker.lock();
			try{
				for(Iterator<BUFT> it = udpBuffers.values().iterator();it.hasNext();) {
					final BufferEntry entry = it.next();

					if(entry.isActivityTimeout(bufferTimeoutMillis, TimeUnit.MILLISECONDS)) {
						if(log.isDebugEnabled()) {
							log.debug(
								logTag +
								"Remove inactive buffer entry.\n" + entry.toString(4)
							);
						}

						entry.clear();

						it.remove();
					}
				}
			}finally {
				locker.unlock();
			}
		}
	}

	public boolean isBufferEmpty() {
		if(protocol == BufferProtocol.TCP) {
			tcpBuffer.getLocker().lock();
			try {
				return !tcpBuffer.getBuffer().hasRemaining();
			}finally {
				tcpBuffer.getLocker().unlock();
			}
		}
		else if(protocol == BufferProtocol.UDP) {
			synchronized(udpBuffers) {
				return Stream.of(udpBuffers.values())
				.allMatch(new Predicate<BUFT>() {
					@Override
					public boolean test(BUFT bufferEntry) {
						bufferEntry.getLocker().lock();
						try {
							return !bufferEntry.getBuffer().hasRemaining();
						}finally {
							bufferEntry.getLocker().unlock();
						}
					}
				});
			}
		}

		return false;
	}

	public void clear() {
		if(protocol == BufferProtocol.TCP)
			tcpBuffer.clear();
		else if(protocol == BufferProtocol.UDP) {
			locker.lock();
			try {
				for(Iterator<BUFT> it = udpBuffers.values().iterator();it.hasNext();) {
					final BufferEntry entry = it.next();

					entry.clear();

					it.remove();
				}
			}finally {
				locker.unlock();
			}
		}
	}

	@SuppressLint("NewAPI")
	private BUFT createBuffer(final InetSocketAddress remoteAddress) {
		if(remoteAddress == null && protocol != BufferProtocol.TCP)
			throw new IllegalArgumentException("Null remotehost value allowed using tcp protocol.");

		final BUFT entry = getNewClassBUFTInstance(
			getKey(),
			hostIdentType,
			protocol == BufferProtocol.TCP ? getBufferSizeTCP() : getBufferSizeUDP(),
			isDirectBuffer()
		);

		try {
			if(remoteAddress != null)
				entry.setRemoteAddress(remoteAddress);
			else if(protocol == BufferProtocol.TCP) {
				if(SystemUtil.IS_Android)
					entry.setRemoteAddress((InetSocketAddress) ((SocketChannel) getKey().channel()).socket().getRemoteSocketAddress());
				else
					entry.setRemoteAddress((InetSocketAddress) ((SocketChannel) getKey().channel()).getRemoteAddress());
			}
			else
				throw new IllegalStateException();

			if(protocol == BufferProtocol.TCP) {
				if(SystemUtil.IS_Android)
					entry.setLocalAddress((InetSocketAddress) ((SocketChannel) getKey().channel()).socket().getLocalSocketAddress());
				else
					entry.setLocalAddress((InetSocketAddress) ((SocketChannel) getKey().channel()).getLocalAddress());
			}
			else if(protocol == BufferProtocol.UDP) {
				if(SystemUtil.IS_Android)
					entry.setLocalAddress((InetSocketAddress) ((DatagramChannel) getKey().channel()).socket().getLocalSocketAddress());
				else
					entry.setLocalAddress((InetSocketAddress) ((DatagramChannel) getKey().channel()).getLocalAddress());
			}
			else
				throw new IllegalStateException();

			return entry;
		}catch(IOException ex) {
			if(log.isDebugEnabled())
				log.debug(logTag + "Could not get local/remote address.");
		}

		return null;
	}

	private BUFT getNewClassBUFTInstance(
		@NonNull SelectionKey key, @NonNull HostIdentType hostIdentType,
		final int bufferCapacity, final boolean directBuffer
	) {
		return getNewClassBUFTInstance(key, hostIdentType, bufferCapacity, directBuffer, null, null);
	}

	private BUFT getNewClassBUFTInstance(
		@NonNull SelectionKey key, @NonNull HostIdentType hostIdentType,
		final int bufferCapacity, final boolean directBuffer,
		final InetSocketAddress remoteAddress,
		final InetSocketAddress localAddress
	) {

		BUFT instance = null;

		if(remoteAddress != null || localAddress != null) {
			//通常の方法でインスタンス生成
			try {
				final Constructor<BUFT> constructor = bufferClass.getConstructor(
					SelectionKey.class,
					HostIdentType.class,
					int.class,
					boolean.class,
					InetSocketAddress.class,
					InetSocketAddress.class
				);

				instance = constructor.newInstance(
					key, hostIdentType, bufferCapacity, directBuffer, remoteAddress, localAddress
				);

			} catch (ReflectiveOperationException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to create BufferEntry.", ex);
			}
		}
		else {
			//通常の方法でインスタンス生成
			try {
				final Constructor<BUFT> constructor = bufferClass.getConstructor(
					SelectionKey.class,
					HostIdentType.class,
					int.class,
					boolean.class
				);

				instance = constructor.newInstance(
					key, hostIdentType, bufferCapacity, directBuffer
				);

			} catch (ReflectiveOperationException ex) {
				if(log.isErrorEnabled())
					log.error(logTag + "Failed to create BufferEntry.", ex);
			}
		}

		if(log.isDebugEnabled()) {
			log.debug(logTag + "Creating buffer entry.\n" + (instance != null ? instance.toString(4) : "null"));
		}

		return instance;
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int indentLevel) {
		if(indentLevel < 0) {indentLevel = 0;}

		final StringBuilder sb = new StringBuilder();
		for(int c = 0; c < indentLevel; c++) {sb.append(' ');}

		sb.append("CreatedTimestamp:");
		sb.append(FormatUtil.dateFormat(createdTimestamp));

		sb.append('/');

		sb.append("BufferProtocol:");
		sb.append(protocol);

		sb.append('/');

		sb.append("Key:");
		sb.append(key);

		sb.append('/');

		sb.append("HostIdentType:");
		sb.append(hostIdentType);

		sb.append('/');

		sb.append("BufferSizeUDP:");
		sb.append(bufferSizeUDP);

		sb.append('/');

		sb.append("BufferSizeTCP:");
		sb.append(bufferSizeTCP);

		sb.append('/');

		sb.append("isDirectBuffer:");
		sb.append(directBuffer);

		sb.append('/');

		if(protocol == BufferProtocol.TCP) {
			sb.append("TCPBuffer:");

			tcpBuffer.getLocker().lock();
			try {
				sb.append(tcpBuffer.toString());
			}finally {
				tcpBuffer.getLocker().unlock();
			}
		}
		else if(protocol == BufferProtocol.UDP) {
			sb.append("\n");
			for(int c = 0; c < (indentLevel + 4); c++) {sb.append(' ');}
			sb.append("UDPBuffers:\n");

			synchronized(udpBuffers) {
				for(final Iterator<BUFT> it = udpBuffers.values().iterator(); it.hasNext();) {
					final BUFT buffer = it.next();

					sb.append(buffer.toString(indentLevel + 4));

					if(it.hasNext()) {sb.append("\n");}
				}
			}
		}

		return sb.toString();
	}
}
