package org.jp.illg.util.thread.task;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.annimon.stream.function.Consumer;

@RunWith(JUnit4.class)
public class TaskQueueTest {


	@Test
	public void test() {
		final ExecutorService executor = Executors.newFixedThreadPool(4);
		final TaskQueue<Object, Boolean> q = new TaskQueue<>(executor);

		for(int i = 0; i < 10; i++) {
			final int number = i + 1;
			q.addEventQueue(new Consumer<Object>() {
				@Override
				public void accept(Object t) {
					System.out.println(Thread.currentThread().getName() +  " Start task " + number);

					try {
						Thread.sleep(1000);
					}catch(InterruptedException ex) {}

					System.out.println(Thread.currentThread().getName() +  " End task " + number);
				}
			});
		}

		try {
			Thread.sleep(5000);
		}catch(InterruptedException ex) {}

		for(int i = 10; i < 20; i++) {
			final int number = i + 1;
			q.addEventQueue(new Consumer<Object>() {
				@Override
				public void accept(Object t) {
					System.out.println(Thread.currentThread().getName() +  " Start task " + number);

					try {
						Thread.sleep(1000);
					}catch(InterruptedException ex) {}

					System.out.println(Thread.currentThread().getName() +  " End task " + number);
				}
			});
		}

		try {
			Thread.sleep(20000);
		}catch(InterruptedException ex) {}

		for(int i = 20; i < 30; i++) {
			final int number = i + 1;
			q.addEventQueue(new Consumer<Object>() {
				@Override
				public void accept(Object t) {
					System.out.println(Thread.currentThread().getName() +  " Start task " + number);

					try {
						Thread.sleep(1000);
					}catch(InterruptedException ex) {}

					System.out.println(Thread.currentThread().getName() +  " End task " + number);
				}
			});
		}

		try {
			Thread.sleep(10000);
		}catch(InterruptedException ex) {}
	}
}
