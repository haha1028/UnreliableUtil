package com.github.haha1028.unreliable.net;

import java.io.IOException;
import java.net.MulticastSocket;

public abstract class UnreliableMulticastSocket extends MulticastSocket {
	/**
	 * 
	 * @param port
	 *            socket port
	 * 
	 * @param lostRate
	 *            chance of datagram to be dropped from being sent
	 * @param avgDelay
	 *            statistically after avgDelay sent datagram to underlying socket.
	 * @throws IOException
	 */
	public UnreliableMulticastSocket(int port, double lostRate, int avgDelay) throws IOException {
		super(port);
	}
}
