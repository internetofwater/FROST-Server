/*
 * Copyright (C) 2016 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.util;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jab
 */
public class ProcessorHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorHelper.class);
    private static final long SHUTDOWN_WAIT_STEP_IN_MILLIS = 100;

    private ProcessorHelper() {
    }

    public static <T> ExecutorService createProcessors(int threadCount, BlockingQueue<T> queue, Consumer<T> consumer, String name) {
        ThreadFactory factory = new ThreadFactoryBuilder().setNameFormat(name + "-%d").build();
        ExecutorService result = Executors.newFixedThreadPool(threadCount, factory);
        for (int i = 0; i < threadCount; i++) {

            result.submit(new Processor(queue, consumer, name));
        }
        return result;
    }

    public static void shutdownProcessors(ExecutorService executorService, BlockingQueue<?> queue, long timeout, TimeUnit timeUnit) {
        if (executorService != null) {
            executorService.shutdown();
            long timeoutInMillis = timeUnit.toMillis(timeout);
            /**
             * no more events can be added to the queue so simple wait till it's
             * empty.
             */
            try {
                while (queue != null && !queue.isEmpty() && timeoutInMillis > 0) {
                    Thread.sleep(SHUTDOWN_WAIT_STEP_IN_MILLIS);
                    timeoutInMillis -= SHUTDOWN_WAIT_STEP_IN_MILLIS;
                }
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    LOGGER.debug("executoreService did not terminate in time");
                }
            } catch (InterruptedException ie) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private static class Processor<T> implements Runnable {

        private static final Logger LOGGER = LoggerFactory.getLogger(Processor.class);
        private final BlockingQueue<T> queue;
        private final Consumer<T> consumer;
        private final String name;

        private Processor(BlockingQueue<T> queue, Consumer<T> consumer) {
            this(queue, consumer, null);
        }

        private Processor(BlockingQueue<T> queue, Consumer<T> consumer, String name) {
            if (queue == null) {
                throw new IllegalArgumentException("queue must be non-null");
            }
            if (consumer == null) {
                throw new IllegalArgumentException("handler must be non-null");
            }
            if (name == null || name.isEmpty()) {
                this.name = getClass().getName();
            } else {
                this.name = name;
            }
            this.queue = queue;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            LOGGER.debug("starting {}-Thread", name);
            while (!Thread.currentThread().isInterrupted()) {
                T event;
                try {
                    event = queue.take();
                    consumer.accept(event);
                } catch (InterruptedException ex) {
                    LOGGER.debug("{} interrupted", name);
                    LOGGER.trace(name + " interrupted", ex);
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ex) {
                    LOGGER.warn("Exception while executing " + name, ex);
                }
            }
            LOGGER.debug("exiting {}-Thread", name);
        }
    }
}
