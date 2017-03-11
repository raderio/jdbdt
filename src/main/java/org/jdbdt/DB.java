package org.jdbdt;

import java.io.File;
import java.io.PrintStream;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Database handle.
 * 
 * <p>
 * An object of this kind is used to encapsulate access
 * to a database connection.
 * </p>
 * 
 * @see JDBDT#database(Connection)
 * @since 0.1
 *
 */
public final class DB {
  /**
   * Database options.
   *
   * @since 0.1
   */
  public enum Option {
    /**
     * Log assertions (both failed and passed).
     */
    LOG_ASSERTIONS,
    /**
     * Log failed assertions (enabled initially by default).
     */
    LOG_ASSERTION_ERRORS,
    /**
     * Log database queries. 
     */
    LOG_QUERIES,
    /**
     * Log database setup operations.
     */
    LOG_SETUP,
    /**
     * Log database snapshots. 
     */
    LOG_SNAPSHOTS,
    /**
     * Reuse statements (enabled initially by default).
     */
    REUSE_STATEMENTS,
    /**
     * Batch updates (enabled initially by default).
     * 
     * This should provide better performance for row insertion when using
     * {@link JDBDT#insert(DataSet)} and {@link JDBDT#populate(DataSet)}.
     * The option has no effect though if the database driver does not support batch
     * updates (as indicated by {@link DatabaseMetaData#supportsBatchUpdates()}.
     */
    BATCH_UPDATES;
  }

  /**
   * Connection.
   */
  private final Connection connection;

  /**
   * Associated metadata.
   */
  private final DatabaseMetaData dbMetaData;

  /**
   * Trace options.
   */
  private final EnumSet<Option> optionSet = EnumSet.noneOf(Option.class);

  /**
   * Flag indicating if batch updates are supported.
   */
  private final boolean batchUpdateSupport;
  
  /**
   * Flag indicating if save points are supported.
   */
  private final boolean savepointSupport;
  
  /**
   * Log to use. 
   */
  private Log log = null;

  /**
   * Statement pool.
   */
  private Map<String, WrappedStatement> pool;

  /**
   * Savepoint, if set.
   */
  private Savepoint savepoint;

  /**
   * Maximum operations for batch updates.
   */
  private int maxBatchUpdateSize = 0;

  /**
   * Default value for maximum batch update size (if batch updates enabled).
   * @see #setMaximumBatchUpdateSize(int)
   * @see #getMaximumBatchUpdateSize()
   */
  public static final int DEFAULT_MAX_BATCH_UPDATE_SIZE = 1000;

  /**
   * Constructor.
   * @param connection Database connection.
   */
  DB(Connection connection) {
    try {
      this.connection = connection;
      dbMetaData = connection.getMetaData();
      log = Log.create(System.err);
      enable(Option.REUSE_STATEMENTS, 
             Option.LOG_ASSERTION_ERRORS);
      
      batchUpdateSupport = dbMetaData.supportsBatchUpdates();
      savepointSupport = dbMetaData.supportsSavepoints();
      
      if (batchUpdateSupport) {
        maxBatchUpdateSize = DEFAULT_MAX_BATCH_UPDATE_SIZE;
        enable(Option.BATCH_UPDATES);
      } else {
        maxBatchUpdateSize = 0;
      }
    } catch (SQLException e) {
      throw new DBExecutionException(e);
    }
  }

  /**
   * Enable options.
   * @param options Options to enable.
   */
  public void enable(Option... options) {
    for (Option o : options) {
      optionSet.add(o);
    }
  }

  /**
   * Set maximum size for batch updates.
   * @param size The size to set.
   * @see #getMaximumBatchUpdateSize()
   * @see #DEFAULT_MAX_BATCH_UPDATE_SIZE
   */
  public void setMaximumBatchUpdateSize(int size) {
    if (!batchUpdateSupport) {
      throw new InvalidOperationException("Batch updates not allowed by database driver.");
    }
    if (! isEnabled(DB.Option.BATCH_UPDATES)) {
      throw new InvalidOperationException(DB.Option.BATCH_UPDATES + "option is not enabled.");
    }
    if (size < 1) {
      throw new InvalidOperationException("Invalid batch update size: " + size);
    }
    maxBatchUpdateSize = size;
  }
  
  /**
   * Get current setting for maximum batch update size.
   * @return The value set, which will be 0 if batch updates are not supported
   * by the database driver.
   * @see #setMaximumBatchUpdateSize(int)
   * @see #DEFAULT_MAX_BATCH_UPDATE_SIZE
   */
  public int getMaximumBatchUpdateSize() {
    return batchUpdateSupport ? maxBatchUpdateSize : 0;
  }
  
  /** 
   * Enable all logging options.
   */
  public void enableFullLogging() {
    enable(DB.Option.LOG_ASSERTION_ERRORS,
           DB.Option.LOG_ASSERTIONS,
           DB.Option.LOG_SETUP,
           DB.Option.LOG_QUERIES,
           DB.Option.LOG_SNAPSHOTS);
  }

  /**
   * Disable options.
   * @param options Options to enable.
   * @see #enable(Option...)
   * @see #isEnabled(Option)
   */
  public void disable(Option... options) {
    for (Option o : options) {
      optionSet.remove(o);
    }
  }


  /**
   * Check if option is enabled.
   * @param o Option.
   * @return <code>true</code> if <code>o</code> is enabled.
   * @see #enable(Option...)
   * @see #disable(Option...)
   */
  public boolean isEnabled(Option o) {
    return optionSet.contains(o);
  }

  /**
   * Get connection.
   * @return The connection associated to this instance.
   */
  public Connection getConnection() {
    return connection;
  }


  /**
   * Redirect log output to a stream.
   * The log set at creation time
   * writes to <code>System.err</code>.
   * @param out Output stream.
   */
  public void setLog(PrintStream out) {
    this.log = Log.create(out);
  }

  /**
   * Set output file for log output.
   * The log set at creation time
   * writes to <code>System.err</code>.
   * @param outputFile Logging instance.
   */
  public void setLog(File outputFile) {
    this.log = Log.create(outputFile);
  }

  /**
   * Compile a SQL statement.
   * @param sql SQL code.
   * @return Wrapper for prepared statement.
   * @throws SQLException If there is a error preparing the statement.
   */
  WrappedStatement 
  compile(String sql) throws SQLException {    
    if (! isEnabled(Option.REUSE_STATEMENTS)) {
      return new WrappedStatement(connection.prepareStatement(sql), false);
    }
    if (pool == null) {
      pool = new IdentityHashMap<>();
    } 
    String sqlI = sql.intern();
    WrappedStatement ws = pool.get(sqlI);
    if (ws == null) {
      ws =  new WrappedStatement(connection.prepareStatement(sql), true);
      pool.put(sqlI, ws);
    }
    return ws;
  }

  /**
   * Set JDBDT save-point.
   * @param callInfo Call info.
   */
  void save(CallInfo callInfo) {
    if (!savepointSupport) {
      throw new InvalidOperationException("Savepoints are not supported by the database driver.");
    }
    logSetup(callInfo);
    clearSavePointIfSet();
    try {
      if (connection.getAutoCommit()) {
        throw new InvalidOperationException("Auto-commit is set for database connection.");
      }      
      savepoint = connection.setSavepoint();
    } catch (SQLException e) {
      throw new DBExecutionException(e);
    }
  }

  @SuppressWarnings("javadoc")
  private void clearSavePointIfSet() {
    if (savepoint != null) {
      ignoreSQLException( () -> connection.releaseSavepoint(savepoint));
      savepoint = null;
    }
  }

  /**
   * Commit changes in the current transaction.
   * @param callInfo Call info.
   */
  void commit(CallInfo callInfo) {
    logSetup(callInfo);
    clearSavePointIfSet();
    try {
      connection.commit();
    } 
    catch(SQLException e) {
      throw new DBExecutionException(e); 
    }
  }

  /**
   * Roll back changes to JDBDT save-point.
   * @param callInfo Call info.
   */
  void restore(CallInfo callInfo) {
    // Note: this is a conservative implementation, it sets another save-point
    // after roll-back, some engines seem to implicitly release the save point on roll-back
    // (an issue with HSQLDB)
    logSetup(callInfo);
    try {
      if (savepoint == null) {
        throw new InvalidOperationException("Save point is not set.");
      }
      connection.rollback(savepoint);
    }
    catch(SQLException e) {
      savepoint = null; // ensuring null in case of error
      throw new DBExecutionException(e); 
    }
    finally {
      clearSavePointIfSet();
    }
  }

  /**
   * Tear down the database handle, freeing any internal
   * resources. 
   * @param callInfo Call info.
   * @param closeConn Close underlying connection.
   */
  void teardown(CallInfo callInfo, boolean closeConn) {
    logSetup(callInfo);
    if (pool != null) {
      for (WrappedStatement ws : pool.values()) {
        ignoreSQLException(ws.getStatement()::close);
      }
      pool.clear();
      pool = null;
    }
    clearSavePointIfSet();
    log.close();
    log = null;
    if (closeConn) {
      ignoreSQLException(connection::close);
    }
  }
  
  /**
   * Test if batch updates should be performed.
   * @return <code>true</code> if batch updates should be performed.
   */
  boolean useBatchUpdates() {
    return batchUpdateSupport && isEnabled(DB.Option.BATCH_UPDATES); 
  }

  /**
   * Log query result.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logQuery(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_QUERIES)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log query result.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logSnapshot(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_SNAPSHOTS)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log insertion.
   * @param callInfo Call info.
   * @param data Data set.
   */
  void logInsertion(CallInfo callInfo, DataSet data) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.write(callInfo, data);
    }
  }

  /**
   * Log delta assertion.
   * @param callInfo Call info.
   * @param da Delta assertion.
   */
  void log(CallInfo callInfo, DeltaAssertion da) {
    if (isEnabled(Option.LOG_ASSERTIONS) ||
        (    ! da.passed() 
            && isEnabled(Option.LOG_ASSERTION_ERRORS) )) {
      log.write(callInfo, da);
    }
  }

  /**
   * Log state assertion.
   * @param callInfo Call info.
   * @param sa state assertion.
   */
  void log(CallInfo callInfo, DataSetAssertion sa) {
    if (isEnabled(Option.LOG_ASSERTIONS) ||
        (    ! sa.passed() 
            && isEnabled(Option.LOG_ASSERTION_ERRORS) )) {
      log.write(callInfo, sa);
    }
  }

  /**
   * Log database setup command.
   * @param callInfo Call info.
   * @param sql SQL code.
   */
  void logSetup(CallInfo callInfo, String sql) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.writeSQL(callInfo, sql);
    }
  }

  /**
   * Log database setup call.
   * @param callInfo Call info.
   */
  void logSetup(CallInfo callInfo) {
    if (isEnabled(Option.LOG_SETUP)) {
      log.writeCallInfo(callInfo);
    }
  }

  @SuppressWarnings("javadoc")
  @FunctionalInterface
  private interface SQLOperationThatMayFail {
    void run() throws SQLException;
  }

  @SuppressWarnings("javadoc")
  private void ignoreSQLException(SQLOperationThatMayFail op) {
    try {
      op.run();
    }
    catch (SQLException e) { 
      // Do nothing.
    }
  }

}
