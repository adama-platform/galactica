package ape.contracts;

import java.sql.ResultSet;
import java.sql.SQLException;

/** for walking a result set or executing on a single row */
@FunctionalInterface
public interface SQLConsumer {
  void accept(ResultSet rs) throws SQLException;
}
