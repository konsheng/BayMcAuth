package com.baymc.auth.storage.mysql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/*
 * SQL 时间转换工具
 *
 * <p>在 Instant 与 JDBC Timestamp 之间做空值安全转换
 */
final class SqlTime {
    private SqlTime() {
    }

    static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
