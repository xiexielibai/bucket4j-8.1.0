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

package io.github.bucket4j.distributed.proxy.optimization.batch;

import io.github.bucket4j.distributed.proxy.AsyncCommandExecutor;
import io.github.bucket4j.distributed.proxy.optimization.OptimizationListener;
import io.github.bucket4j.distributed.remote.CommandResult;
import io.github.bucket4j.distributed.remote.RemoteCommand;
import io.github.bucket4j.distributed.remote.commands.MultiCommand;
import io.github.bucket4j.distributed.remote.MultiResult;
import io.github.bucket4j.util.concurrent.BatchHelper;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class AsyncBatchingExecutor implements AsyncCommandExecutor {

    private final BatchHelper<RemoteCommand<?>, CommandResult<?>, MultiCommand, CommandResult<MultiResult>> batchingHelper;
    private final AsyncCommandExecutor wrappedExecutor;
    private final OptimizationListener listener;

    private final Function<List<RemoteCommand<?>>, MultiCommand> taskCombiner = new Function<List<RemoteCommand<?>>, MultiCommand>() {
        @Override
        public MultiCommand apply(List<RemoteCommand<?>> commands) {
            listener.incrementMergeCount(commands.size() - 1);
            return new MultiCommand(commands);
        }
    };

    private final Function<MultiCommand, CompletableFuture<CommandResult<MultiResult>>> combinedTaskExecutor = new Function<MultiCommand, CompletableFuture<CommandResult<MultiResult>>>() {
        @Override
        public CompletableFuture<CommandResult<MultiResult>> apply(MultiCommand multiCommand) {
            return wrappedExecutor.executeAsync(multiCommand);
        }
    };

    private final Function<RemoteCommand<?>, CompletableFuture<CommandResult<?>>> taskExecutor = new Function<RemoteCommand<?>, CompletableFuture<CommandResult<?>>>() {
        @Override
        public CompletableFuture<CommandResult<?>> apply(RemoteCommand<?> remoteCommand) {
            CompletableFuture<? extends CommandResult<?>> future = wrappedExecutor.executeAsync(remoteCommand);
            return (CompletableFuture<CommandResult<?>>) future;
        }
    };

    private final Function<CommandResult<MultiResult>, List<CommandResult<?>>> combinedResultSplitter = new Function<CommandResult<MultiResult>, List<CommandResult<?>>>() {
        @Override
        public List<CommandResult<?>> apply(CommandResult<MultiResult> multiResult) {
            return multiResult.getData().getResults();
        }
    };


    public AsyncBatchingExecutor(AsyncCommandExecutor originalExecutor, OptimizationListener listener) {
        this.wrappedExecutor = originalExecutor;
        this.listener = listener;
        this.batchingHelper = BatchHelper.async(taskCombiner, combinedTaskExecutor, taskExecutor, combinedResultSplitter);
    }


    @Override
    public <T> CompletableFuture<CommandResult<T>> executeAsync(RemoteCommand<T> command) {
        CompletableFuture<T> future = (CompletableFuture<T>) batchingHelper.executeAsync(command);
        return (CompletableFuture<CommandResult<T>>) future;
    }

}
