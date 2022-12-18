/*-
 * ========================LICENSE_START=================================
 * Bucket4j
 * %%
 * Copyright (C) 2015 - 2022 Vladimir Bukhtoyarov
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
package io.github.bucket4j.mysql;

import com.mysql.cj.jdbc.exceptions.MySQLTransactionRollbackException;
import io.github.bucket4j.BucketExceptions;
import io.github.bucket4j.distributed.jdbc.SQLProxyConfiguration;
import io.github.bucket4j.distributed.jdbc.SQLProxyConfigurationBuilder;
import io.github.bucket4j.distributed.proxy.generic.select_for_update.AbstractSelectForUpdateBasedProxyManager;
import io.github.bucket4j.distributed.proxy.generic.select_for_update.LockAndGetResult;
import io.github.bucket4j.distributed.proxy.generic.select_for_update.SelectForUpdateBasedTransaction;
import io.github.bucket4j.distributed.remote.RemoteBucketState;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * @author Maxim Bartkov
 * The extension of Bucket4j library addressed to support <a href="https://www.mysql.com">MySQL</a>
 * To start work with the MySQL extension you must create a table, which will include the possibility to work with buckets
 * In order to do this, your table should include the next columns: id as a PRIMARY KEY (BIGINT) and state (BYTEA)
 * To define column names, {@link SQLProxyConfiguration} include {@link io.github.bucket4j.distributed.jdbc.BucketTableSettings} which takes settings for the table to work with Bucket4j
 * @see {@link SQLProxyConfigurationBuilder} to get more information how to build {@link SQLProxyConfiguration}
 */
public class MySQLSelectForUpdateBasedProxyManager extends AbstractSelectForUpdateBasedProxyManager<Long> {

    private final DataSource dataSource;
    private final SQLProxyConfiguration configuration;
    private final String removeSqlQuery;
    private final String updateSqlQuery;
    private final String insertSqlQuery;
    private final String selectSqlQuery;

    /**
     *
     * @param configuration {@link SQLProxyConfiguration} configuration.
     */
    public MySQLSelectForUpdateBasedProxyManager(SQLProxyConfiguration configuration) {
        super(configuration.getClientSideConfig());
        this.dataSource = Objects.requireNonNull(configuration.getDataSource());
        this.configuration = configuration;
        this.removeSqlQuery = MessageFormat.format("DELETE FROM {0} WHERE {1} = ?", configuration.getTableName(), configuration.getIdName());
        updateSqlQuery = MessageFormat.format("UPDATE {0} SET {1}=? WHERE {2}=?", configuration.getTableName(), configuration.getStateName(), configuration.getIdName());
        insertSqlQuery = MessageFormat.format("INSERT IGNORE INTO {0}({1}, {2}) VALUES(?, null)",
                configuration.getTableName(), configuration.getIdName(), configuration.getStateName(), configuration.getIdName());
        selectSqlQuery = MessageFormat.format("SELECT {0} FROM {1} WHERE {2} = ? FOR UPDATE", configuration.getStateName(), configuration.getTableName(), configuration.getIdName());
    }

    @Override
    protected SelectForUpdateBasedTransaction allocateTransaction(Long key) {
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new BucketExceptions.BucketExecutionException(e);
        }

        return new SelectForUpdateBasedTransaction() {
            @Override
            public void begin() {
                try {
                    connection.setAutoCommit(false);
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public void update(byte[] data, RemoteBucketState newState) {
                try {
                    try (PreparedStatement updateStatement = connection.prepareStatement(updateSqlQuery)) {
                        updateStatement.setBytes(1, data);
                        updateStatement.setLong(2, key);
                        updateStatement.executeUpdate();
                    }
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public void release() {
                try {
                    connection.close();
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public void rollback() {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public void commit() {
                try {
                    connection.commit();
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public LockAndGetResult tryLockAndGet() {
                try (PreparedStatement selectStatement = connection.prepareStatement(selectSqlQuery)) {
                    selectStatement.setLong(1, key);
                    try (ResultSet rs = selectStatement.executeQuery()) {
                        if (!rs.next()) {
                            return LockAndGetResult.notLocked();
                        }
                        byte[] bucketStateBeforeTransaction = rs.getBytes(configuration.getStateName());
                        return LockAndGetResult.locked(bucketStateBeforeTransaction);
                    }
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

            @Override
            public boolean tryInsertEmptyData() {
                try (PreparedStatement insertStatement = connection.prepareStatement(insertSqlQuery)) {
                    insertStatement.setLong(1, key);
                    insertStatement.executeUpdate();
                    return true;
                } catch (MySQLTransactionRollbackException conflict) {
                    // do nothing, because parallel transaction has been already inserted the row
                    return false;
                } catch (SQLException e) {
                    throw new BucketExceptions.BucketExecutionException(e);
                }
            }

        };
    }

    @Override
    public void removeProxy(Long key) {
        try (Connection connection = dataSource.getConnection()) {
            try(PreparedStatement removeStatement = connection.prepareStatement(removeSqlQuery)) {
                removeStatement.setLong(1, key);
                removeStatement.executeUpdate();
            }
        } catch (SQLException e) {
            throw new BucketExceptions.BucketExecutionException(e);
        }
    }

}
