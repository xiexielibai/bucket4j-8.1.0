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

import java.util.concurrent.ScheduledExecutorService;

public class LimitChecker {

    public static long INFINITY_DURATION = Long.MAX_VALUE;
    public static long UNLIMITED_AMOUNT = Long.MAX_VALUE;

    public static void checkTokensToAdd(long tokensToAdd) {
        if (tokensToAdd <= 0) {
            throw new IllegalArgumentException("tokensToAdd should be >= 0");
        }
    }

    public static void checkTokensToConsume(long tokensToConsume) {
        if (tokensToConsume <= 0) {
            throw BucketExceptions.nonPositiveTokensToConsume(tokensToConsume);
        }
    }

    public static void checkMaxWaitTime(long maxWaitTimeNanos) {
        if (maxWaitTimeNanos <= 0) {
            throw BucketExceptions.nonPositiveNanosToWait(maxWaitTimeNanos);
        }
    }

    public static void checkScheduler(ScheduledExecutorService scheduler) {
        if (scheduler == null) {
            throw BucketExceptions.nullScheduler();
        }
    }

    public static void checkConfiguration(BucketConfiguration newConfiguration) {
        if (newConfiguration == null) {
            throw BucketExceptions.nullConfiguration();
        }
    }

    public static void checkMigrationMode(TokensInheritanceStrategy tokensInheritanceStrategy) {
        if (tokensInheritanceStrategy == null) {
            throw BucketExceptions.nullTokensInheritanceStrategy();
        }
    }

}
