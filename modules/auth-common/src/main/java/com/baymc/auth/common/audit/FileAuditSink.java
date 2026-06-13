package com.baymc.auth.common.audit;

import com.baymc.auth.common.model.AuditEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/*
 * 文件审计输出
 *
 * <p>按日期将审计消息追加到日志目录, 作为数据库之外的本地留痕
 */
public final class FileAuditSink implements AuditSink {
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter LINE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private final Path directory;

    public FileAuditSink(Path directory) {
        this.directory = directory;
    }

    @Override
    public void write(AuditEntry entry) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(FILE_DATE.format(entry.createdAt()) + ".log");
            String line = LINE_TIME.format(entry.createdAt()) + " " + entry.eventType() + " " + entry.result() + " " + entry.message() + System.lineSeparator();
            Files.writeString(file, line, StandardCharsets.UTF_8, Files.exists(file) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException exception) {
            throw new IllegalStateException("审计日志文件写入失败", exception);
        }
    }
}
