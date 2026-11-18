package com.baymc.auth.dependencies;

import com.j256.ormlite.jdbc.JdbcPooledConnectionSource;
import com.j256.ormlite.jdbc.db.DatabaseTypeUtils;
import com.j256.ormlite.support.ConnectionSource;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

// 统一数据库文件名和内部 JDBC 前缀
public enum DatabaseLibrary {
  H2(
      BaseLibrary.H2_V2,
          (classLoader, dir, jdbc, user, password) -> fromDriver(classLoader.loadClass("org.h2.Driver"), jdbc, null, null),
          (dir, hostname, database) -> "jdbc:h2:" + dir + "/baymcauth"
  ),
  MYSQL(
      BaseLibrary.MYSQL,
          (classLoader, dir, jdbc, user, password)
              -> fromDriver(classLoader.loadClass("com.mysql.cj.jdbc.NonRegisteringDriver"), jdbc, user, password),
          (dir, hostname, database) ->
              "jdbc:mysql://" + hostname + "/" + database
  ),
  MARIADB(
      BaseLibrary.MARIADB,
          (classLoader, dir, jdbc, user, password)
              -> fromDriver(classLoader.loadClass("org.mariadb.jdbc.Driver"), jdbc, user, password),
          (dir, hostname, database) ->
              "jdbc:mariadb://" + hostname + "/" + database
  ),
  POSTGRESQL(
      BaseLibrary.POSTGRESQL,
          (classLoader, dir, jdbc, user, password) -> fromDriver(classLoader.loadClass("org.postgresql.Driver"), jdbc, user, password),
          (dir, hostname, database) -> "jdbc:postgresql://" + hostname + "/" + database
  ),
  SQLITE(
      BaseLibrary.SQLITE,
          (classLoader, dir, jdbc, user, password) -> fromDriver(classLoader.loadClass("org.sqlite.JDBC"), jdbc, user, password),
          (dir, hostname, database) -> "jdbc:sqlite:" + dir + "/baymcauth.db"
  );

  private final BaseLibrary baseLibrary;
  private final DatabaseConnector connector;
  private final DatabaseStringGetter stringGetter;
  private final IsolatedDriver driver = new IsolatedDriver("jdbc:baymcauth_" + this.name().toLowerCase(Locale.ROOT) + ":");

  DatabaseLibrary(BaseLibrary baseLibrary, DatabaseConnector connector, DatabaseStringGetter stringGetter) {
    this.baseLibrary = baseLibrary;
    this.connector = connector;
    this.stringGetter = stringGetter;
  }

  public Connection connect(ClassLoader classLoader, Path dir, String hostname, String database, String user, String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connect(classLoader, dir, this.stringGetter.getJdbcString(dir, hostname, database), user, password);
  }

  public Connection connect(Path dir, String hostname, String database, String user, String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connect(dir, this.stringGetter.getJdbcString(dir, hostname, database), user, password);
  }

  public Connection connect(ClassLoader classLoader, Path dir, String jdbc, String user, String password)
      throws ReflectiveOperationException, SQLException, IOException {
    return this.connector.connect(classLoader, dir, jdbc, user, password);
  }

  public Connection connect(Path dir, String jdbc, String user, String password) throws IOException, ReflectiveOperationException, SQLException {
    return this.connector.connect(new IsolatedClassLoader(new URL[]{this.baseLibrary.getClassLoaderURL()}), dir, jdbc, user, password);
  }

  public ConnectionSource connectToORM(Path dir, String hostname, String database, String user, String password)
      throws ReflectiveOperationException, IOException, SQLException, URISyntaxException {
    if (this.driver.getOriginal() == null) {
      IsolatedClassLoader classLoader = new IsolatedClassLoader(new URL[] {this.baseLibrary.getClassLoaderURL()});
      Class<?> driverClass = classLoader.loadClass(
          switch (this) {
            case H2 -> "org.h2.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.NonRegisteringDriver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case POSTGRESQL -> "org.postgresql.Driver";
            case SQLITE -> "org.sqlite.JDBC";
          }
      );

      this.driver.setOriginal((Driver) driverClass.getConstructor().newInstance());
      DriverManager.registerDriver(this.driver);
    }

    String jdbc = this.stringGetter.getJdbcString(dir, hostname, database);
    boolean h2 = this.baseLibrary == BaseLibrary.H2_V2;
    return new JdbcPooledConnectionSource(this.driver.getInitializer() + jdbc,
        h2 ? null : user, h2 ? null : password, DatabaseTypeUtils.createDatabaseType(jdbc));
  }

  private static Connection fromDriver(Class<?> connectionClass, String jdbc, String user, String password)
      throws ReflectiveOperationException, SQLException {
    Constructor<?> driverConstructor = connectionClass.getConstructor();

    Properties info = new Properties();
    if (user != null) {
      info.put("user", user);
    }

    if (password != null) {
      info.put("password", password);
    }

    Object driver = driverConstructor.newInstance();

    DriverManager.deregisterDriver((Driver) driver);
    DriverManager.registerDriver((Driver) driver);

    Method connect = connectionClass.getDeclaredMethod("connect", String.class, Properties.class);
    connect.setAccessible(true);
    return (Connection) connect.invoke(driver, jdbc, info);
  }

  public interface DatabaseConnector {
    Connection connect(ClassLoader classLoader, Path dir, String jdbc, String user, String password)
        throws ReflectiveOperationException, SQLException, IOException;
  }

  public interface DatabaseStringGetter {
    String getJdbcString(Path dir, String hostname, String database);
  }
}
