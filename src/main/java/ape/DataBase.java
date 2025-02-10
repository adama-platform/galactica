package ape;

import ape.contracts.SQLConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DataBase implements AutoCloseable{
  private final HikariDataSource pool;

  public DataBase(JsonNode configNode) {
    HikariConfig config = new HikariConfig();
    config.setJdbcUrl(configNode.get("jdbc").textValue());
    config.setUsername(configNode.get("user").textValue());
    config.setPassword(configNode.get("password").textValue());

    pool = new HikariDataSource(config);
  }

  @Override
  public void close() {
    pool.close();
  }

  private void walk(String sql, SQLConsumer action) throws Exception {
    try (Connection connection = pool.getConnection()) {
      try (Statement statement = connection.createStatement()) {
        try (ResultSet row = statement.executeQuery(sql)) {
          while (row.next()) {
            action.accept(row);
          }
        }
      }
    }
  }

  public List<String> showTables() throws Exception {
    ArrayList<String> tables = new ArrayList<>();
    walk("SHOW TABLES", (rs) -> {
      tables.add(rs.getString(1));
    });
    return tables;
  }

  public String toPrompt(String table) throws Exception{
    StringBuilder p = new StringBuilder();
    p.append("In the database is table called " + table + " which has the following fields:");
    ArrayList<String> fields = new ArrayList<>();
    walk("SHOW FULL COLUMNS FROM `" + table + "`", (rs) -> {
      fields.add(rs.getString(1) + " of type " + rs.getString(2) + " having a comment of '" + rs.getString(9) + "'");
    });
    p.append(String.join(", ", fields));
    p.append(". ");
    return p.toString();
  }
}
