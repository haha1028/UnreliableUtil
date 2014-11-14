package com.github.haha1028.unreliable.util.concurrent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.github.haha1028.unreliable.util.UnreliablePolicy;

public final class UnreliableScheduledThreadPoolExecutor implements UnrelabileExecutorService {

	/**
	 * actual pool to exec task.
	 */
	private int poolSize = Runtime.getRuntime().availableProcessors();;
	/**
	 * workers.
	 */
	ScheduledThreadPoolExecutor[] scheduledThreadPoolExecutors = new ScheduledThreadPoolExecutor[poolSize];

	final AtomicLong finishTaskCount = new AtomicLong();
	final AtomicLong totalDelayedTime = new AtomicLong();
	final AtomicLong failedTaskCount = new AtomicLong();

	/**
	 * Lock held on access to workers set and related bookkeeping.
	 */
	private final ReentrantLock mainLock = new ReentrantLock();
	/**
	 * Wait condition to support awaitTermination
	 */
	private final Condition termination = mainLock.newCondition();

	private boolean TERMINATED = false;
	private boolean SHUTDOWN = false;
	private UnreliablePolicy policy;

	public UnreliableScheduledThreadPoolExecutor() {
		this(UnreliablePolicy.RELIABLE_POLICY());
	}

	/**
	 * 
	 * task are expected be executed within twice of avgDelay time, but this is not guaranteed. if task exec is very slow or too many task were scheduled, the actual avg dealy
	 * could be much longer than expected.
	 * <P>
	 * It is caller's responsibility to check totalDelayedTime to ensure the Executor is running as expected
	 */
	public UnreliableScheduledThreadPoolExecutor(UnreliablePolicy policy) {
		this.policy = policy;

		for (int i = 0; i < scheduledThreadPoolExecutors.length; i++) {
			ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = createScheduledPoolExecutor();
			scheduledThreadPoolExecutors[i] = scheduledThreadPoolExecutor;
		}
	}

	private ScheduledThreadPoolExecutor createScheduledPoolExecutor() {
		ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(poolSize) {
			protected void terminated() {
				mainLock.lock();
				try {
					termination.signalAll();

				} finally {
					mainLock.unlock();
				}
				super.terminated();
			}
		};
		return scheduledThreadPoolExecutor;
	}

	/**
	 * 
	 * @return tasks already executed. does not contain those have been dropped.
	 */
	public long getFinishTaskCount() {
		return finishTaskCount.get();
	}

	/**
	 * @return total delayed time for already executed tasks
	 */
	public long getTotalDelayedTime() {
		return totalDelayedTime.get();
	}

	/**
	 * @return number of task those have been dropped
	 */
	public long getFailedTaskCount() {
		return failedTaskCount.get();
	}

	/**
	 * @return approximate number of task that are pending.
	 */
	public int getQueueSize() {

		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {
			int size = 0;
			for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
				size += scheduledThreadPoolExecutor.getQueue().size();

			}
			return size;
		} finally {
			mainLock.unlock();
		}

	}

	/**
	 * 
	 * submit a task to exec. this task will be executed statistically after avgDelay and have a lostRate of chance to be dropped from being executed
	 * 
	 * @param task
	 *            function to exec .
	 *            <P>
	 *            task are usually will executed within twice of avgDelay time if was not dropped.
	 * @return ScheduledFuture that can be used to extract result or cancel.
	 *         <P>
	 *         Note: UnreliableScheduledThreadPoolExecutor is not aware of this future object.
	 *         <P>
	 *         So if you use this returned future object to call cancel(true) to cancel task, UnreliableScheduledThreadPoolExecutor will not be able to keep accurate lostRate or
	 *         avgDelay.
	 */
	@Override
	public <T> ScheduledFuture<T> submit(final Callable<T> task) {

		final long scheduledAt = System.currentTimeMillis();

		Callable<T> callable = new Callable<T>() {
			@Override
			public T call() throws Exception {
				try {
					if (shouldDrop()) {
						taskDropped();
						return null;
					}
					statFinishedTask(scheduledAt);
					T t = task.call();
					return t;
				} catch (Exception e) {
					e.printStackTrace();
				}
				return null;
			}
		};
		final long delay = this.checkAndGetDelay();
		ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = pickScheduledThreadPoolExecutor(scheduledAt + delay);
		return scheduledThreadPoolExecutor.schedule(callable, delay, TimeUnit.MILLISECONDS);

	}

	/**
	 * @see #submit
	 */
	@Override
	public Future<?> submit(final Runnable command) {
		return submit(command, null);
	}

	/**
	 * @see #submit
	 */
	@Override
	public void execute(Runnable command) {
		submit(command, null);
	}

	/**
	 * @see #submit
	 */
	@Override
	public <T> Future<T> submit(final Runnable task, final T result) {

		final long delay = this.checkAndGetDelay();

		final long scheduledAt = System.currentTimeMillis();
		final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = pickScheduledThreadPoolExecutor(scheduledAt + delay);
		Callable<T> callable = new Callable<T>() {
			@Override
			public T call() throws Exception {
				if (shouldDrop()) {
					taskDropped();
					return null;
				}
				statFinishedTask(scheduledAt);

				scheduledThreadPoolExecutor.execute(task);
				return result;
			}

		};
		return scheduledThreadPoolExecutor.schedule(callable, delay, TimeUnit.MILLISECONDS);

	}

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
		throw new java.lang.UnsupportedOperationException("NOT yet implemented .");

	}

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
		throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
	}

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
	}

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		throw new java.lang.UnsupportedOperationException("NOT yet implemented .");
	}

	/**
	 * stat finish task counter and total delay upon task exectution.
	 * 
	 * @param scheduledAt
	 */
	private void statFinishedTask(final long scheduledAt) {

		long now = System.currentTimeMillis();
		long delta = now - scheduledAt;
		finishTaskCount.incrementAndGet();
		totalDelayedTime.addAndGet(delta);
	}

	private boolean shouldDrop() {
		double lost = ThreadLocalRandom.current().nextDouble();
		if (lost < policy.getLostRate()) {

			return true;
		}
		return false;
	}

	/**
	 * check whether task should be send or not.
	 * 
	 * @return -1 if don't need to exec. else the delay to use.
	 * @throws InterruptedException
	 */
	private long checkAndGetDelay() {
		long delay = 0;

		if (this.policy.getMaxDelay() > 0) {
			delay = ThreadLocalRandom.current().nextInt((int) policy.getMaxDelay());

			/**
			 * adjust delay time according to lag.
			 */
			long lag = getLag();
			delay = delay - 2 * lag;
			if (delay < 0) {
				delay = 0;
			}

		}

		return delay;
	}

	private void taskDropped() {
		failedTaskCount.incrementAndGet();
	}

	/**
	 * 
	 * @return current delay - expect delay.
	 */
	long getLag() {
		long currentAvgDelay = (long) (1.0 * getTotalDelayedTime() / (this.getFinishTaskCount() + 1));
		long expectAvgDelay = this.policy.getAvgDelay() / 2;
		long lag = currentAvgDelay - expectAvgDelay;

		return lag;
	}

	ScheduledThreadPoolExecutor pickScheduledThreadPoolExecutor(long scheduledSendTime) {
		int index = (int) (scheduledSendTime % scheduledThreadPoolExecutors.length);
		return scheduledThreadPoolExecutors[index];

	}

	@Override
	public void shutdown() {

		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {

			for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
				scheduledThreadPoolExecutor.shutdown();
			}
			SHUTDOWN = true;

		} finally {
			mainLock.unlock();
		}

	}

	@Override
	public List<Runnable> shutdownNow() {
		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {
			List<Runnable> tasks = new ArrayList<Runnable>();
			for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
				List<Runnable> subTasks = scheduledThreadPoolExecutor.shutdownNow();
				tasks.addAll(subTasks);
			}
			SHUTDOWN = true;
			return tasks;
		} finally {
			mainLock.unlock();
		}
	}

	@Override
	public boolean isShutdown() {

		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {

			return SHUTDOWN;

		} finally {
			mainLock.unlock();
		}
	}

	@Override
	public boolean isTerminated() {
		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {

			return TERMINATED;
		} finally {
			mainLock.unlock();
		}
	}

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {

		long nanos = unit.toNanos(timeout);
		final ReentrantLock mainLock = this.mainLock;
		mainLock.lock();
		try {
			for (;;) {
				if (TERMINATED)
					return true;
				boolean poolTerminated = true;
				for (ScheduledThreadPoolExecutor scheduledThreadPoolExecutor : scheduledThreadPoolExecutors) {
					poolTerminated = poolTerminated && scheduledThreadPoolExecutor.isTerminated();
					if (!poolTerminated) {
						break;
					}
				}
				if (poolTerminated) {
					TERMINATED = true;
					return true;
				}
				if (nanos <= 0)
					return false;
				nanos = termination.awaitNanos(nanos);
			}
		} finally {
			mainLock.unlock();
		}
	}

}