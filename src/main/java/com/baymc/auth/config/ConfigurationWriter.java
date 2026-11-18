package com.baymc.auth.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ConfigurationWriter {

  private ConfigurationWriter() {
  }

  static void save(List<Change> changes) {
    List<Staged> staged = new ArrayList<>();
    List<Staged> committed = new ArrayList<>();
    List<Path> temporary = new ArrayList<>();
    Set<Path> retained = new HashSet<>();
    Set<Path> targets = new HashSet<>();
    Path current = null;
    String failure = null;
    try {
      for (Change change : changes) {
        if (change.path() == null || change.after() == null) {
          throw new IOException();
        }
        if (Objects.equals(change.before(), change.after())) {
          continue;
        }
        current = change.path().toAbsolutePath().normalize();
        Staged item = stage(change, current, temporary);
        if (!targets.add(item.target)) {
          throw new IOException();
        }
        staged.add(item);
      }

      // 两份文件全部暂存后再复核, 避免准备第二份文件失败时修改第一份
      for (Staged item : staged) {
        current = item.requested;
        verify(item, item.change.before(), item.original);
      }
      for (Staged item : staged) {
        current = item.requested;
        verify(item, item.change.before(), item.original);
        if (item.change.before() == null) {
          // 不允许覆盖校验后由其他程序创建的文件
          Files.move(item.replacement, item.target);
        } else {
          Files.move(item.replacement, item.target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        committed.add(item);
        item.written = attributes(item.target);
      }
    } catch (IOException | RuntimeException e) {
      failure = "无法保存配置文件, 文件可能已被其他程序修改或无法写入: " + location(current);
      List<String> recovery = rollback(committed, temporary, retained);
      if (!recovery.isEmpty()) {
        failure += "; " + String.join("; ", recovery);
      }
    }

    List<String> leftovers = cleanup(temporary, retained);
    if (!leftovers.isEmpty()) {
      String message = "无法清理临时文件: " + String.join(", ", leftovers);
      failure = failure == null ? "配置已保存, " + message : failure + "; " + message;
    }
    if (failure != null) {
      throw new ConfigurationException(failure);
    }
  }

  private static Staged stage(Change change, Path requested, List<Path> temporary) throws IOException {
    Path target;
    BasicFileAttributes original = null;
    if (change.before() == null) {
      if (Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
        throw new IOException();
      }
      Path directory = parent(requested);
      Files.createDirectories(directory);
      target = directory.toRealPath().resolve(requested.getFileName());
    } else {
      // 替换链接指向的真实文件, 保留原路径上的符号链接
      target = requested.toRealPath();
      original = attributes(target);
      if (!original.isRegularFile() || !change.before().equals(Files.readString(target, StandardCharsets.UTF_8))) {
        throw new IOException();
      }
    }

    Path backup = null;
    if (change.before() != null) {
      backup = temporary(target, ".baymcauth-before-", temporary);
      Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
      copyAccess(target, backup);
      if (!change.before().equals(Files.readString(backup, StandardCharsets.UTF_8))) {
        throw new IOException();
      }
    }
    Path replacement = temporary(target, ".baymcauth-after-", temporary);
    Files.writeString(replacement, change.after(), StandardCharsets.UTF_8);
    if (backup != null) {
      copyAccess(backup, replacement);
    }
    Staged item = new Staged(change, requested, target, replacement, backup, original);
    verify(item, change.before(), original);
    return item;
  }

  private static void verify(Staged item, String expected, BasicFileAttributes expectedAttributes) throws IOException {
    if (expected == null) {
      if (Files.exists(item.requested, LinkOption.NOFOLLOW_LINKS)
          || !parent(item.requested).toRealPath().resolve(item.requested.getFileName()).equals(item.target)) {
        throw new IOException();
      }
      return;
    }
    if (!item.requested.toRealPath().equals(item.target)) {
      throw new IOException();
    }
    BasicFileAttributes beforeRead = attributes(item.target);
    if (!beforeRead.isRegularFile() || expectedAttributes != null && !sameFileState(expectedAttributes, beforeRead)
        || !expected.equals(Files.readString(item.target, StandardCharsets.UTF_8))
        || !sameFileState(beforeRead, attributes(item.target)) || !item.requested.toRealPath().equals(item.target)) {
      throw new IOException();
    }
  }

  private static BasicFileAttributes attributes(Path path) throws IOException {
    return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
  }

  private static boolean sameFileState(BasicFileAttributes first, BasicFileAttributes second) {
    return Objects.equals(first.fileKey(), second.fileKey()) && first.size() == second.size()
        && first.lastModifiedTime().equals(second.lastModifiedTime());
  }

  private static void copyAccess(Path source, Path target) throws IOException {
    PosixFileAttributeView sourcePosix = Files.getFileAttributeView(source, PosixFileAttributeView.class);
    PosixFileAttributeView targetPosix = Files.getFileAttributeView(target, PosixFileAttributeView.class);
    if (sourcePosix != null && targetPosix != null) {
      PosixFileAttributes wanted = sourcePosix.readAttributes();
      PosixFileAttributes current = targetPosix.readAttributes();
      if (!wanted.owner().equals(current.owner())) {
        targetPosix.setOwner(wanted.owner());
      }
      if (!wanted.group().equals(current.group())) {
        targetPosix.setGroup(wanted.group());
      }
      targetPosix.setPermissions(wanted.permissions());
    }
    AclFileAttributeView sourceAcl = Files.getFileAttributeView(source, AclFileAttributeView.class);
    AclFileAttributeView targetAcl = Files.getFileAttributeView(target, AclFileAttributeView.class);
    if (sourceAcl != null && targetAcl != null) {
      if (!sourceAcl.getOwner().equals(targetAcl.getOwner())) {
        targetAcl.setOwner(sourceAcl.getOwner());
      }
      targetAcl.setAcl(sourceAcl.getAcl());
    }
  }

  private static List<String> rollback(List<Staged> committed, List<Path> temporary, Set<Path> retained) {
    List<String> failures = new ArrayList<>();
    for (int i = committed.size() - 1; i >= 0; --i) {
      Staged item = committed.get(i);
      try {
        verify(item, item.change.after(), item.written);
        if (item.change.before() == null) {
          Files.delete(item.target);
        } else {
          Files.move(item.backup, item.target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
      } catch (IOException | RuntimeException e) {
        // 外部程序再次修改过的文件不回写, 留下副本供管理员恢复
        Path recovery = item.backup;
        try {
          if (recovery == null || !Files.isRegularFile(recovery, LinkOption.NOFOLLOW_LINKS)) {
            recovery = temporary(item.target, ".baymcauth-recovery-", temporary);
            Files.writeString(recovery, item.change.before() == null ? item.change.after() : item.change.before(), StandardCharsets.UTF_8);
          }
          retained.add(recovery);
          String kind = item.change.before() == null ? "本次写入内容副本" : "原文件恢复副本";
          failures.add("无法回滚 " + item.requested + ", " + kind + ": " + recovery);
        } catch (IOException | RuntimeException recoveryFailure) {
          failures.add("无法回滚且无法保留恢复副本: " + item.requested);
        }
      }
    }
    return failures;
  }

  private static Path parent(Path path) throws IOException {
    Path directory = path.getParent();
    if (directory == null) {
      throw new IOException();
    }
    return directory;
  }

  private static Path temporary(Path target, String prefix, List<Path> temporary) throws IOException {
    Path path = Files.createTempFile(parent(target), prefix, ".yml.tmp");
    temporary.add(path);
    return path;
  }

  private static List<String> cleanup(List<Path> temporary, Set<Path> retained) {
    List<String> failures = new ArrayList<>();
    for (Path path : temporary) {
      if (retained.contains(path)) {
        continue;
      }
      try {
        Files.deleteIfExists(path);
      } catch (IOException | RuntimeException e) {
        failures.add(path.toString());
      }
    }
    return failures;
  }

  private static String location(Path path) {
    return path == null ? "配置目录" : path.toString();
  }

  record Change(Path path, String before, String after) {
  }

  private static final class Staged {

    private final Change change;
    private final Path requested;
    private final Path target;
    private final Path replacement;
    private final Path backup;
    private final BasicFileAttributes original;
    private BasicFileAttributes written;

    private Staged(Change change, Path requested, Path target, Path replacement, Path backup, BasicFileAttributes original) {
      this.change = change;
      this.requested = requested;
      this.target = target;
      this.replacement = replacement;
      this.backup = backup;
      this.original = original;
    }
  }
}
