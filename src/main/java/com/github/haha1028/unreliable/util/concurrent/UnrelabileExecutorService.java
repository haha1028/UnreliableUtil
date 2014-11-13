package com.github.haha1028.unreliable.util.concurrent;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * an ExecutorService that schedule task with a random delay, and randomly drop task from being executed when the task is put to execution, .
 * <P>
 * The randomly delay is no more than twice of avgDelay time, but actually time before task is executed is not guaranteed.
 * <P>
 * If task is very slow or too many task are scheduled, there could be lag.
 * <P>
 * It is caller's responsibility to check run time status such as getLag==0 to ensure the executor is running as expected
 * 
 * @author Wentao Liu
 * 
 */
public interface UnrelabileExecutorService extends ExecutorService {
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
	public <T> ScheduledFuture<T> submit(final Callable<T> task);

	/**
	 * @see #submit
	 */
	@Override
	public Future<?> submit(final Runnable command);

	/**
	 * @see #submit
	 */
	@Override
	public void execute(Runnable command);

	/**
	 * @see #submit
	 */
	@Override
	public <T> Future<T> submit(final Runnable task, final T result);

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException;

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException;

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException;

	/**
	 * an UnsupportedOperationException is always thrown.
	 */
	@Override
	public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException;

	@Override
	public void shutdown();

	@Override
	public List<Runnable> shutdownNow();

	@Override
	public boolean isShutdown();

	@Override
	public boolean isTerminated();

	@Override
	public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException;
}
