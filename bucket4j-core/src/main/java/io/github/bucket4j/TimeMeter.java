/*-
 * ========================LICENSE_START=================================
 * Bucket4j
 * %%
 * Copyright (C) 2015 - 2020 Vladimir Bukhtoyarov
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */

package io.github.bucket4j;

import java.util.concurrent.TimeUnit;

/**
 * An abstraction over time measurement.
 *
 * @see TimeMeter#SYSTEM_NANOTIME
 * @see TimeMeter#SYSTEM_MILLISECONDS
 */
public interface TimeMeter {

    /**
     * Returns current time in nanosecond precision, but not necessarily nanosecond resolution.
     *
     * @return current time in nanoseconds
     */
    long currentTimeNanos();

    /**
     * Returns {@code true} if implementation of clock behaves the similar way as {@link System#currentTimeMillis()},
     * in other words if implementation can be used as wall clock.
     *
     * @return {@code true} if implementation can be used as wall clock
     */
    boolean isWallClockBased();

    /**
     * The implementation of {@link TimeMeter} which works around {@link java.lang.System#nanoTime}
     */
    TimeMeter SYSTEM_NANOTIME = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            return System.nanoTime();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }

        @Override
        public String toString() {
            return "SYSTEM_NANOTIME";
        }
    };

    /**
     * The implementation of {@link TimeMeter} which works around {@link java.lang.System#currentTimeMillis}
     */
    TimeMeter SYSTEM_MILLISECONDS = new TimeMeter() {
        @Override
        public long currentTimeNanos() {
            long nowMillis = System.currentTimeMillis();
            return TimeUnit.MILLISECONDS.toNanos(nowMillis);
        }

        @Override
        public boolean isWallClockBased() {
            return true;
        }

        @Override
        public String toString() {
            return "SYSTEM_MILLISECONDS";
        }
    };

}
