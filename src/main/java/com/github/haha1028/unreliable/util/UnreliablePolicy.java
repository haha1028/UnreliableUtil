package com.github.haha1028.unreliable.util;

import java.util.concurrent.TimeUnit;

public class UnreliablePolicy {
	private static UnreliablePolicy RELIABLE_POLICY = new UnreliablePolicy(0, 0, TimeUnit.DAYS);

	/**
	 * 
	 * @param lostRate
	 *            chance of drop task. 0.2= 20% chance
	 * @param avgDelay
	 *            . avgDelay to actually call task; linear; min delay start from 0.
	 * @param delayUnit
	 */
	public UnreliablePolicy(double lostRate, int avgDelay, TimeUnit delayUnit) {
		this.lostRate = lostRate;
		this.avgDelay = avgDelay;
		this.delayUnit = delayUnit;
		this.maxDelay = getDelayUnit().toMillis(avgDelay) * 2;

	}

	/**
	 * chance of drop task. 0.2= 20% chance.
	 */
	private volatile double lostRate = 0;

	/**
	 * avgDelay to actually call task.
	 */
	private int avgDelay = 0;

	/**
	 * time unit to delay.
	 */
	/**
	 * implementation internal use. maxDelay =2*avgDelay in milliseconds
	 */
	private long maxDelay;

	public long getMaxDelay() {
		return maxDelay;
	}

	TimeUnit delayUnit;

	public double getLostRate() {
		return lostRate;
	}

	public int getAvgDelay() {
		return avgDelay;
	}

	public TimeUnit getDelayUnit() {
		return delayUnit;
	}

	/**
	 * 0 avg dealy and 0 lostRate.
	 */
	public static UnreliablePolicy RELIABLE_POLICY() {
		return RELIABLE_POLICY;
	}
}
