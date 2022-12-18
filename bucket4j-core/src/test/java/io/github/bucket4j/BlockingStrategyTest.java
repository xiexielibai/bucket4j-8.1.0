package io.github.bucket4j;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

public class BlockingStrategyTest {

    @Test(expected = InterruptedException.class, timeout = 1000)
    public void sleepShouldThrowExceptionWhenThreadInterrupted() throws InterruptedException {
        Thread.currentThread().interrupt();
        BlockingStrategy.PARKING.park(TimeUnit.SECONDS.toNanos(10));
    }

    @Test(timeout = 3000)
    public void sleepShouldHandleSpuriousWakeup() throws InterruptedException {
        // two lines bellow lead to spurious wakeup at first invocation of park
        Thread.currentThread().interrupt();
        Thread.interrupted();

        long startNanos = System.nanoTime();
        long nanosToPark = TimeUnit.SECONDS.toNanos(1);
        BlockingStrategy.PARKING.park(nanosToPark);
        assertTrue(System.nanoTime() - startNanos >= nanosToPark);
    }

    @Test(timeout = 10000)
    public void sleepUniterruptibleShouldNotThrowInterruptedException() {
        long nanosToPark = TimeUnit.SECONDS.toNanos(1);
        Thread.currentThread().interrupt();
        long startNanos = System.nanoTime();
        UninterruptibleBlockingStrategy.PARKING.parkUninterruptibly(nanosToPark);
        assertTrue(System.nanoTime() - startNanos >= nanosToPark);
        assertTrue(Thread.currentThread().isInterrupted());
    }

}