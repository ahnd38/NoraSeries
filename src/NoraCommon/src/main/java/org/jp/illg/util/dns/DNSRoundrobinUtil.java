package org.jp.illg.util.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.ProcessResult;

import com.annimon.stream.ComparatorCompat;
import com.annimon.stream.Optional;
import com.annimon.stream.Stream;
import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Predicate;
import com.annimon.stream.function.ToIntFunction;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DNSRoundrobinUtil {

	private static final String logHeader;

	@Getter
	private String hostname;

	private final List<HostInfo> hosts;

	private InetAddress currentHostAddress;

	private final Lock locker;

	static {
		logHeader = DNSRoundrobinUtil.class.getSimpleName() + " : ";
	}

	public DNSRoundrobinUtil() {
		super();

		locker = new ReentrantLock();

		hostname = "";

		hosts = new ArrayList<>();

		currentHostAddress = null;
	}

	public DNSRoundrobinUtil(String hostname) {
		this();

		if(hostname == null) {hostname = "";}

		setHostname(hostname);
	}

	public void setHostname(String hostname) {
		locker.lock();
		try {
			if(!this.hostname.equals(hostname)) {
				hosts.clear();
				currentHostAddress = null;
			}

			this.hostname = hostname;
		}finally {locker.unlock();}
	}

	public Optional<InetAddress> getCurrentHostAddress(){
		locker.lock();
		try {
			return
				currentHostAddress != null ?
					Optional.of(currentHostAddress) : Optional.ofNullable(choiceNextHostAddress());
		}finally {
			locker.unlock();
		}
	}

	public Optional<InetAddress> getNextHostAddress(){
		return Optional.ofNullable(choiceNextHostAddress());
	}

	public boolean nextHostAddress(){
		return Optional.ofNullable(choiceNextHostAddress()).isPresent();
	}

	public void notifyDeadHostAddress(InetAddress hostAddress) {
		if(hostAddress == null) {return;}

		notifyHostAddressStatus(hostAddress, true);
	}

	public void notifyDeadHostAddress() {
		notifyHostAddressStatus(currentHostAddress, true);
	}

	public void notifyAliveHostAddress(InetAddress hostAddress) {
		if(hostAddress == null) {return;}

		notifyHostAddressStatus(hostAddress, false);
	}

	public void notifyAliveHostAddress() {
		notifyHostAddressStatus(currentHostAddress, false);
	}

	private void notifyHostAddressStatus(final InetAddress hostAddress, final boolean dead) {
		assert hostAddress != null;

		locker.lock();
		try {
			Stream.of(hosts)
			.filter(new Predicate<HostInfo>() {
				@Override
				public boolean test(HostInfo hostInfo) {
					return hostInfo.getHostAddress().equals(hostAddress);
				}
			})
			.forEach(new Consumer<HostInfo>() {
				@Override
				public void accept(HostInfo hostInfo) {
					int currentPoint = hostInfo.getPoint();

					if(dead) {
						if(currentPoint < Integer.MAX_VALUE) {currentPoint++;}
					}
					else {
						currentPoint = 0;
					}

					hostInfo.setPoint(currentPoint);
				}
			});
		}finally {
			locker.unlock();
		}
	}

	private InetAddress choiceNextHostAddress() {
		if(!resolveHostAddresses()) {
			if(log.isWarnEnabled()) {log.warn(logHeader + "Failed resolve host name " + getHostname() + ".");}
			return currentHostAddress;
		}

		locker.lock();
		try {
			findHostInfo()
			.filter(new Predicate<HostInfo>() {
				@Override
				public boolean test(HostInfo hostInfo) {
					return
						currentHostAddress == null ||
						!hostInfo.getHostAddress().equals(currentHostAddress);
				}
			})
			.min(ComparatorCompat.comparingInt(new ToIntFunction<HostInfo>() {
				@Override
				public int applyAsInt(HostInfo hostInfo) {
					return hostInfo.getPoint();
				}
			}))
			.ifPresent(
				new Consumer<HostInfo>() {
					@Override
					public void accept(HostInfo hostInfo) {
						if(log.isDebugEnabled()) {
							log.debug(
								logHeader + "Choice new host address " +
									currentHostAddress + " -> " + hostInfo.getHostAddress() + "."
							);
						}
						currentHostAddress = hostInfo.getHostAddress();
					}
				}
			);
		}finally {
			locker.unlock();
		}

		return currentHostAddress;
	}

	private boolean resolveHostAddresses() {
		InetAddress[] raddr = null;

		try {
			raddr = InetAddress.getAllByName(getHostname());
		}catch(UnknownHostException ex) {
			if(log.isErrorEnabled())
				log.error(logHeader + "Could not resolve host name " + getHostname() + ".");

			return false;
		}

		final List<HostInfo> newHosts = new ArrayList<>();

		locker.lock();
		try {
			for(final InetAddress hostAddress : raddr) {
				final ProcessResult<HostInfo> newHost = new ProcessResult<>();

				findHostInfo(hostAddress)
				.findSingle()
				.ifPresentOrElse(
					new Consumer<HostInfo>() {
						@Override
						public void accept(HostInfo hostInfo) {
							newHost.setResult(hostInfo);
						}
					},
					new Runnable() {
						@Override
						public void run() {
							newHost.setResult(new HostInfo(hostAddress));
						}
					}
				);

				if(
					!Stream.of(newHosts)
					.anyMatch(new Predicate<HostInfo>() {
						@Override
						public boolean test(HostInfo hostInfo) {
							return hostInfo.getHostAddress().equals(newHost.getResult().getHostAddress());
						}
					})
				) {
					newHosts.add(newHost.getResult());
				}
			}

			if(log.isTraceEnabled()) {
				StringBuilder sb = new StringBuilder(logHeader);
				sb.append("Update roungrobin hosts.\n");
				for(Iterator<HostInfo> it = newHosts.iterator(); it.hasNext();) {
					sb.append(it.next().toString(4));
					if(it.hasNext()) {sb.append("\n");}
				}

				log.trace(sb.toString());
			}

			hosts.clear();
			return hosts.addAll(newHosts);
		}finally {
			locker.unlock();
		}
	}



	private Stream<HostInfo> findHostInfo(){
		return findHostInfo(null);
	}

	private Stream<HostInfo> findHostInfo(final InetAddress hostAddress){
		locker.lock();
		try {
			return
				Stream.of(hosts)
				.filter(new Predicate<HostInfo>() {
					@Override
					public boolean test(HostInfo hostInfo) {
						boolean match =
							(
								hostAddress == null ||
								hostInfo.getHostAddress().equals(hostAddress)
							);
						return match;
					}
				});
		}finally {
			locker.unlock();
		}
	}
}
