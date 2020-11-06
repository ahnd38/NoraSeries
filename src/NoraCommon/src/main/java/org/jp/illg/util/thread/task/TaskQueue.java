package org.jp.illg.util.thread.task;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jp.illg.util.thread.RunnableTask;
import org.jp.illg.util.thread.ThreadUncaughtExceptionListener;

import com.annimon.stream.function.Consumer;
import com.annimon.stream.function.Function;

import lombok.Getter;
import lombok.NonNull;

public class TaskQueue<T, R> {

	private static class TaskEntry<T, R>{
		@Getter
		private Function<T, R> taskWithResult;

		@Getter
		private Consumer<T> task;

		@Getter
		private T attachment;

		@Getter
		private ThreadUncaughtExceptionListener exceptionListener;

		@Getter
		private Consumer<R> onResultHandler;

		@Getter
		private Consumer<R> onFailHandler;

		@Getter
		private Consumer<R> onSuccessHandler;

		public TaskEntry(
			final Function<T, R> taskWithResult,
			final Consumer<T> task,
			final T attachment,
			final ThreadUncaughtExceptionListener exceptionListener,
			final Consumer<R> onResultHandler,
			final Consumer<R> onFailHandler,
			final Consumer<R> onSuccessHandler
		) {
			this.taskWithResult = taskWithResult;
			this.task = task;
			this.attachment = attachment;
			this.exceptionListener = exceptionListener;
			this.onResultHandler = onResultHandler;
			this.onFailHandler = onFailHandler;
			this.onSuccessHandler = onSuccessHandler;
		}
	}

	private final Lock locker;

	private final ExecutorService executor;

	private final Queue<TaskEntry<T, R>> queue;

	private Future<?> currentTask;

	public TaskQueue(@NonNull ExecutorService executor) {
		super();

		locker = new ReentrantLock();
		queue = new LinkedList<>();
		currentTask = null;

		this.executor = executor;
	}

	private boolean addEventQueue(
		final Function<T, R> taskWithResult,
		final Consumer<T> task,
		final T attachment,
		final ThreadUncaughtExceptionListener exceptionListener,
		final Consumer<R> onResultHandler,
		final Consumer<R> onSuccessHandler,
		final Consumer<R> onFailHandler
	) {
		if(taskWithResult == null && task == null)
			throw new IllegalArgumentException();

		locker.lock();
		try {
			if(
				!queue.add(new TaskEntry<>(
					taskWithResult,
					task,
					attachment,
					exceptionListener,
					onResultHandler,
					onFailHandler,
					onSuccessHandler
				)
			)) {return false;}

			if(currentTask == null) {processEventQueue();}
		}finally {
			locker.unlock();
		}

		return true;
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task,
		final T attachment
	) {
		return addEventQueue(task, null, attachment, null, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task,
		final T attachment,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		return addEventQueue(task, null, attachment, exceptionListener, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Consumer<T> task,
		final T attachment,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		return addEventQueue(null, task, attachment, exceptionListener, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task
	) {
		return addEventQueue(task, null, null, null, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Consumer<T> task
	) {
		return addEventQueue(null, task, null, null, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		return addEventQueue(task, null, null, exceptionListener, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Consumer<T> task,
		final ThreadUncaughtExceptionListener exceptionListener
	) {
		return addEventQueue(null, task, null, exceptionListener, null, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task,
		final ThreadUncaughtExceptionListener exceptionListener,
		final Consumer<R> onResultHandler
	) {
		return addEventQueue(task, null, null, exceptionListener, onResultHandler, null, null);
	}

	public boolean addEventQueue(
		@NonNull final Function<T, R> task,
		final ThreadUncaughtExceptionListener exceptionListener,
		final Consumer<R> onResultHandler,
		final Consumer<R> onSuccessHandler,
		final Consumer<R> onFailHandler
	) {
		return addEventQueue(
			task, null,null, exceptionListener,
			onResultHandler, onSuccessHandler, onFailHandler
		);
	}

	public void shutdown() {
		locker.lock();
		try {
			if(currentTask != null)
				currentTask.cancel(true);

			currentTask = null;
		}finally {
			locker.unlock();
		}
	}

	private void processEventQueue() {
		locker.lock();
		try {
			final TaskEntry<T, R> event = queue.poll();
			if(event == null) {
				currentTask = null;

				return;
			}

			currentTask = executor.submit(new RunnableTask(event.getExceptionListener()) {
				@Override
				public void task() {
					try {
						if(event.getTask() != null) {
							event.getTask().accept(event.getAttachment());
						}
						else if(event.getTaskWithResult() != null) {
							final R result = event.getTaskWithResult().apply(event.getAttachment());

							if(event.getOnResultHandler() != null)
								event.getOnResultHandler().accept(result);

							if(result != null && result instanceof Boolean) {
								final boolean isSuccess = (Boolean)result;

								if(isSuccess && event.getOnSuccessHandler() != null)
									event.getOnSuccessHandler().accept(result);
								else if(!isSuccess && event.getOnFailHandler() != null)
									event.getOnFailHandler().accept(result);
							}
						}
					}finally {
						processEventQueue();
					}
				}
			});
		}finally {
			locker.unlock();
		}
	}
}
