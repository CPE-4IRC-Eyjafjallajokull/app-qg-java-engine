package cpe.qg.engine.database;

import java.sql.Connection;
import java.sql.SQLException;

public interface DatabaseClient extends AutoCloseable {
  void connect();

  void healthCheck();

  Connection getConnection() throws SQLException;

  @Override
  void close();
}
