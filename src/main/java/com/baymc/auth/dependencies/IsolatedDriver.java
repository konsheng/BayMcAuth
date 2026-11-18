package com.baymc.auth.dependencies;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

public class IsolatedDriver implements Driver {

  private final String initializer;
  private Driver original;

  public IsolatedDriver(String initializer) {
    this.initializer = initializer;
  }

  public String getInitializer() {
    return this.initializer;
  }

  public Driver getOriginal() {
    return this.original;
  }

  public void setOriginal(Driver driver) {
    this.original = driver;
  }

  @Override
  public Connection connect(String url, Properties info) throws SQLException {
    if (url.startsWith(this.initializer)) {
      return this.original.connect(url.substring(this.initializer.length()), info);
    }

    return null;
  }

  @Override
  public boolean acceptsURL(String url) throws SQLException {
    if (url.startsWith(this.initializer)) {
      if (this.original == null) {
        return false;
      }

      return this.original.acceptsURL(url.substring(this.initializer.length()));
    }

    return false;
  }

  @Override
  public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
    if (url.startsWith(this.initializer)) {
      return this.original.getPropertyInfo(url.substring(this.initializer.length()), info);
    }

    return new DriverPropertyInfo[0];
  }

  @Override
  public int getMajorVersion() {
    return this.original.getMajorVersion();
  }

  @Override
  public int getMinorVersion() {
    return this.original.getMinorVersion();
  }

  @Override
  public boolean jdbcCompliant() {
    return this.original.jdbcCompliant();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return this.original.getParentLogger();
  }
}
