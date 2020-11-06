package org.jp.illg.util.socketio.support;

import java.net.InetAddress;
import java.util.Objects;

import lombok.Getter;

public class HostIdent {

	@Getter
	private HostIdentType identType;

	@Getter
	private InetAddress localAddress;

	@Getter
	private int localPort;

	@Getter
	private InetAddress remoteAddress;

	@Getter
	private int remotePort;

	private HostIdent() {
		super();
	}

	private HostIdent(HostIdentType identType) {
		this();

		if (identType == null)
			throw new IllegalArgumentException("identType must not null.");

		this.identType = identType;
	}

	public HostIdent(
			HostIdentType identType,
			InetAddress localAddress, int localPort,
			InetAddress remoteAddress, int remotePort
	) {
		this(identType);

		if(
			(
				identType == HostIdentType.LocalAddressOnly ||
				identType == HostIdentType.LocalAddressPort
			) && localAddress == null
		) {
				throw new IllegalArgumentException(
						"localAddress must not null at ident type LocalAddressOnly and LocalAddressPort."
				);
		}
		else if(
			(
				identType == HostIdentType.RemoteAddressOnly ||
				identType == HostIdentType.RemoteAddressPort
			) && remoteAddress == null
		) {
			throw new IllegalArgumentException(
					"remoteAddress must not null at ident type RemoteAddressOnly and RemoteAddressPort."
			);
		}
		else if(
			(
				identType == HostIdentType.RemoteLocalAddressOnly ||
				identType == HostIdentType.RemoteLocalAddressPort
			) && (remoteAddress == null || localAddress == null)
		) {
				throw new IllegalArgumentException(
						"localAddress and remoteAddress must not null"
						+ "at ident type RemoteLocalAddressOnly and RemoteLocalAddressPort."
			);
		}

		this.localAddress = localAddress;
		this.localPort = localPort;
		this.remoteAddress = remoteAddress;
		this.remotePort = remotePort;
	}



	@Override
	public boolean equals(Object o) {
		if (this == o) return true;

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

//		@SuppressWarnings("unchecked")
		HostIdent hostIdent = (HostIdent) o;

		switch(identType) {
		case LocalAddressOnly:
			return Objects.equals(localAddress, hostIdent.localAddress);

		case LocalPortOnly:
			return localPort == hostIdent.localPort;

		case LocalAddressPort:
			return
					Objects.equals(localAddress, hostIdent.localAddress) &&
					localPort == hostIdent.localPort;

		case RemoteAddressOnly:
			return Objects.equals(remoteAddress, hostIdent.remoteAddress);

		case RemotePortOnly:
			return remotePort == hostIdent.remotePort;

		case RemoteAddressPort:
			return
					Objects.equals(remoteAddress, hostIdent.remoteAddress) &&
					remotePort == hostIdent.remotePort;

		case RemoteLocalAddressOnly:
			return
					Objects.equals(localAddress, hostIdent.localAddress) &&
					Objects.equals(remoteAddress, hostIdent.remoteAddress);

		case RemoteLocalPortOnly:
			return
					localPort == hostIdent.localPort &&
					remotePort == hostIdent.remotePort;

		case RemoteLocalAddressPort:
			return
					Objects.equals(localAddress, hostIdent.localAddress) &&
					Objects.equals(remoteAddress, hostIdent.remoteAddress) &&
					localPort == hostIdent.localPort &&
					remotePort == hostIdent.remotePort;

		default:
			return false;
		}
	}

	@Override
	public int hashCode() {
		switch(identType) {
		case LocalAddressOnly:
			return Objects.hash(localAddress);

		case LocalPortOnly:
			return Objects.hash(localPort);

		case LocalAddressPort:
			return Objects.hash(localAddress, localPort);

		case RemoteAddressOnly:
			return Objects.hash(remoteAddress);

		case RemotePortOnly:
			return Objects.hash(remotePort);

		case RemoteAddressPort:
			return Objects.hash(remoteAddress, remotePort);

		case RemoteLocalAddressOnly:
			return Objects.hash(localAddress, remoteAddress);

		case RemoteLocalPortOnly:
			return Objects.hash(localPort, remotePort);

		case RemoteLocalAddressPort:
			return Objects.hash(localAddress, remoteAddress, localPort, remotePort);

		default:
			return -1;
		}
	}
}
