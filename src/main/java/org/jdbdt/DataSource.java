package org.jdbdt;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Base class for data sources.
 * 
 * @since 0.1
 * 
 */
public abstract class DataSource {

  /**
   * Database instance for this data source.
   */
  private final DB db;

  /**
   * Meta-data for query statement.
   */
  private MetaData metaData = null;

  /**
   * Column names.
   */
  private String[] columns = null;

  /**
   * Query arguments (if any).
   */
  private Object[] queryArgs = null;

  /**
   * Last snapshot (if any).
   */
  private DataSet snapshot = null;

  /**
   * The empty data set, as returned by {@link JDBDT#empty(DataSource)}
   * (computed lazily).
   */
  private DataSet theEmptyOne = null;

  /**
   * Dirty status flag.
   */
  private boolean dirty;

  /**
   * Constructor.
   * @param db Database instance.
   * @param queryArgs Optional arguments for database query.
   */
  DataSource(DB db, Object... queryArgs) {
    this.db = db;
    this.queryArgs = queryArgs;
    this.dirty = true;
  }

  /**
   * Get database instance.
   * @return Database instance associated to this data source.
   */
  public final DB getDB() {
    return db;
  }

  /**
   * Set columns for data source.
   * 
   * @param columns Column names.
   */
  final void setColumns(String[] columns) {
    if (this.columns != null) {
      throw new InvalidOperationException("Columns are already set.");
    }
    this.columns = columns.clone();    
  }

  /** 
   * Get column names.
   * @return Array of column names.
   */
  final String[] getColumns() {
    return columns;
  }

  /**
   * Get column count.
   * @return Column count.
   */
  public final int getColumnCount() {
    if (columns == null) {
      getMetaData();
    }
    return columns.length;
  }

  /**
   * Get column name.
   * @param index Column index between <code>0</code> and <code>getColumnCount() - 1</code>
   * @return Name of column.
   */
  public String getColumnName(int index) {
    if (index < 0 || columns == null || index >= columns.length) {
      throw new InvalidOperationException("Invalid column index: " + index);
    }
    return columns[index];
  }

  /**
   * Array list shared by all data set instances returned 
   * by {@link #theEmptySet()}.
   */
  private static final ArrayList<Row> EMPTY_ROW_LIST = new ArrayList<>();

  /**
   * Return an empty, read-only data set,
   * for use by {@link JDBDT#empty(DataSource)}
   * @return Empty, read-only data set.
   */
  final DataSet theEmptySet() {
    if (theEmptyOne == null) {
      theEmptyOne = new DataSet(this, EMPTY_ROW_LIST);
      theEmptyOne.setReadOnly();
    }
    return theEmptyOne;
  }

  /**
   * Get meta-data.
   * @return Meta-data for the data source query.
   */
  final MetaData getMetaData() {
    if (metaData == null) {
      try (WrappedStatement ws = db.compile(getSQLForQuery())) {
        computeMetaData(ws.getStatement());
      } 
      catch (SQLException e) {
        throw new DBExecutionException(e);
      }
    }
    return metaData;
  }
  
  /**
   * Get meta-data.
   * @param stmt Statement from which to derive the meta-data.
   */
  final void computeMetaData(PreparedStatement stmt) {
    if (metaData == null) {
      MetaData md = new MetaData(stmt);
      if (getColumns() == null) {
        String[] cols = new String[md.getColumnCount()];
        for (int i = 0; i < cols.length; i++) {
          cols[i] = md.getLabel(i);
        }
        setColumns(cols);
      }
      metaData = md;
    }
  }

  /**
   * Get query arguments.
   * @return Array of arguments if any, otherwise <code>null</code>.
   */
  final Object[] getQueryArguments() {
    return queryArgs;
  }

  /**
   * Get SQL code for query.
   * @return SQL code for the database query.
   */
  public abstract String getSQLForQuery();

  /**
   * Execute query.
   * @param callInfo Call info.
   * @param takeSnapshot Indicates that a snapshot should be taken.
   * @return Result of query.
   */
  final DataSet executeQuery(CallInfo callInfo, boolean takeSnapshot) {
    DataSet data = new DataSet(this);
    try (WrappedStatement ws = db.compile(getSQLForQuery())) {
      computeMetaData(ws.getStatement());
      executeQuery(ws.getStatement(), metaData, getQueryArguments(), data::addRow);
      if (takeSnapshot) {
        setSnapshot(data);
        getDB().logSnapshot(callInfo, data);
      } else {
        getDB().logQuery(callInfo, data);
      }
    } catch (SQLException e) {
      throw new DBExecutionException(e);
    }
    return data;
  }

  /**
   * Get last snapshot.
   * @return Last snapshot taken.
   */
  final DataSet getSnapshot() {
    if (snapshot == null) {
      throw new InvalidOperationException("No snapshot taken!");
    }
    return snapshot;
  }

  /**
   * Set snapshot data.
   * @param s Data set to assume as snapshot.
   */
  final void setSnapshot(DataSet s) {
    if (snapshot != null) {
      snapshot.clear();
    }
    s.setReadOnly();
    snapshot = s;
  }

  /**
   * Execute query.
   * @param queryStmt Query statement.
   * @param md Meta data.
   * @param queryArgs Query arguments.
   * @param action Row consumer.
   */
  static void executeQuery
  (PreparedStatement queryStmt, 
      MetaData md, 
      Object[] queryArgs, 
      Consumer<Row> action) {
    try { 
      if (queryArgs != null && queryArgs.length > 0) {
        for (int i=0; i < queryArgs.length; i++) {
          queryStmt.setObject(i + 1, queryArgs[i]);
        }
      }
      try(ResultSet rs = queryStmt.executeQuery()) {
        int colCount = md.getColumnCount();
        while (rs.next()) {
          Object[] data = new Object[colCount];
          for (int i = 0; i < colCount; i++) {  
            data[i] = rs.getObject(i+1);
          }
          action.accept(new Row(data));
        }
      }
    } 
    catch(SQLException e) {
      throw new DBExecutionException(e);
    } 
  }

  /**
   * Set dirty status.
   * 
   * <p>
   * This method is used by {@link JDBDT#populateIfChanged}
   * and {@link JDBDT#assertUnchanged(DataSource)}.
   * </p>
   * 
   * @param dirty Status.
   */
  final void setDirtyStatus(boolean dirty) {
    this.dirty = dirty;
  }

  /**
   * Get dirty status.
   * 
   * <p>
   * This method is used by {@link JDBDT#populateIfChanged}
   * to check if the table has a dirty status.
   * </p>.
   * 
   * @return The dirty status.
   */
  final boolean getDirtyStatus() {
    return dirty;
  }

}
