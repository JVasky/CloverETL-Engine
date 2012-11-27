/*
 * jETeL/CloverETL - Java based ETL application framework.
 * Copyright (c) Javlin, a.s. (info@cloveretl.com)
 *  
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package org.jetel.connection.jdbc.specific.conn;

import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Driver;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetel.connection.jdbc.DBConnection;
import org.jetel.connection.jdbc.driver.JdbcDriver;
import org.jetel.connection.jdbc.specific.JdbcSpecific.AutoGeneratedKeysType;
import org.jetel.connection.jdbc.specific.JdbcSpecific.OperationType;
import org.jetel.exception.JetelException;
import org.jetel.exception.JetelRuntimeException;

/**
 * 
 * Default adapter for common java.sql.Connection class
 * It is directly used by the DefaultJdbcSpecific or as a ascendant of other Connection implementation.
 * 
 * @author Martin Zatopek (martin.zatopek@javlinconsulting.cz)
 *         (c) Javlin Consulting (www.javlinconsulting.cz)
 *
 * @created May 29, 2008
 */
public class DefaultConnection implements Connection {

	protected final static Log logger = LogFactory.getLog(DefaultConnection.class);

	protected final static int DEFAULT_FETCH_SIZE = 50;

	protected Connection connection;
	
	protected OperationType operationType;
	
	protected AutoGeneratedKeysType autoGeneratedKeysType;

	private boolean conservative = false;
	
	private DBConnection dbConnection;
	
	private boolean initialized = false;
	
	public DefaultConnection(DBConnection dbConnection, OperationType operationType, AutoGeneratedKeysType autoGeneratedKeysType) throws JetelException {
		this.operationType = operationType;
		this.autoGeneratedKeysType = autoGeneratedKeysType;
		this.dbConnection = dbConnection;
	}

	public void init() throws JetelException {
		initialized = true;
		
		if (connection == null) {
			connection = connect(dbConnection);
		}
		if (!conservative) {
			try {
				optimizeConnection(operationType);
			} catch (Exception e1) {
				logger.warn("Optimizing connection failed: " + e1.getMessage());
				logger.warn("Try to use another jdbc specific");
			}
			
			try {
				if (dbConnection.getHoldability() != null) {
					setHoldability(dbConnection.getHoldability());
				}
				if (dbConnection.getTransactionIsolation() != null) {
					setTransactionIsolation(dbConnection.getTransactionIsolation());
				}
			} catch (SQLException e) {
				throw new JetelException("Wrong connection configuration.", e);
			}
		}
	}
	
	public void setInnerConnection(Connection connection) {
		if (initialized) {
			throw new UnsupportedOperationException("inner connection cannot be changed after initilization");
		}
		this.connection = connection;
	}
	
	/**
	 * @return the comprised inner java.sql.Connection
	 */
	public Connection getInnerConnection() {
		return connection;
	}
	
	public void setConservative(boolean conservative) {
		this.conservative = conservative;
	}
	
	//*************** java.sql.Connection interface **************//
	/* (non-Javadoc)
	 * @see java.sql.Connection#clearWarnings()
	 */
	@Override
	public void clearWarnings() throws SQLException {
		connection.clearWarnings();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#close()
	 */
	@Override
	public void close() throws SQLException {
		connection.close();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#commit()
	 */
	@Override
	public void commit() throws SQLException {
		if (isTransactionsSupported()) {
			connection.commit();
		}
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#createStatement()
	 */
	@Override
	public Statement createStatement() throws SQLException {
		Statement statement;

		switch (operationType) {
		case READ:
			try {
				statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT);
			} catch (SQLException e) {
				logger.warn(e.getMessage());
				statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			}catch (UnsupportedOperationException e) {
				logger.warn(e.getMessage());
				statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			}
			break;
		default:
			statement = connection.createStatement();
		}
		
		return optimizeStatement(statement);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#createStatement(int, int, int)
	 */
	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
		return optimizeStatement(connection.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#createStatement(int, int)
	 */
	@Override
	public Statement createStatement(int resultSetType, int resultSetConcurrency) throws SQLException {
		return optimizeStatement(connection.createStatement(resultSetType, resultSetConcurrency));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getAutoCommit()
	 */
	@Override
	public boolean getAutoCommit() throws SQLException {
		return connection.getAutoCommit();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getCatalog()
	 */
	@Override
	public String getCatalog() throws SQLException {
		return connection.getCatalog();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getHoldability()
	 */
	@Override
	public int getHoldability() throws SQLException {
		return connection.getHoldability();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getMetaData()
	 */
	@Override
	public DatabaseMetaData getMetaData() throws SQLException {
		return connection.getMetaData();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getTransactionIsolation()
	 */
	@Override
	public int getTransactionIsolation() throws SQLException {
		return connection.getTransactionIsolation();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getTypeMap()
	 */
	@Override
	public Map<String, Class<?>> getTypeMap() throws SQLException {
		return connection.getTypeMap();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#getWarnings()
	 */
	@Override
	public SQLWarning getWarnings() throws SQLException {
		return connection.getWarnings();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#isClosed()
	 */
	@Override
	public boolean isClosed() throws SQLException {
		return connection.isClosed();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#isReadOnly()
	 */
	@Override
	public boolean isReadOnly() throws SQLException {
		return connection.isReadOnly();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#nativeSQL(java.lang.String)
	 */
	@Override
	public String nativeSQL(String sql) throws SQLException {
		return connection.nativeSQL(sql);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareCall(java.lang.String, int, int, int)
	 */
	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		return (CallableStatement) optimizeStatement(connection.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareCall(java.lang.String, int, int)
	 */
	@Override
	public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return (CallableStatement) optimizeStatement(connection.prepareCall(sql, resultSetType, resultSetConcurrency));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareCall(java.lang.String)
	 */
	@Override
	public CallableStatement prepareCall(String sql) throws SQLException {
		return (CallableStatement) optimizeStatement(connection.prepareCall(sql));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int, int, int)
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
			throws SQLException {
		return (PreparedStatement) optimizeStatement(connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int, int)
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency) throws SQLException {
		return (PreparedStatement) optimizeStatement(connection.prepareStatement(sql, resultSetType, resultSetConcurrency));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int)
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
		return (PreparedStatement) optimizeStatement(connection.prepareStatement(sql, autoGeneratedKeys));
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String, int[])
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
		PreparedStatement statement;
		if(autoGeneratedKeysType == AutoGeneratedKeysType.SINGLE) {
			if (columnIndexes != null) {
				statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			}else{
				logger.warn("Columns are null");
				logger.info("Getting generated keys switched off !");
				statement = connection.prepareStatement(sql);
			}
		}else{
			statement = connection.prepareStatement(sql, columnIndexes);
		}
		optimizeStatement(statement);
		return statement;
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String, java.lang.String[])
	 */
	@Override
	public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
		PreparedStatement statement;
		if(autoGeneratedKeysType == AutoGeneratedKeysType.SINGLE) {
			if (columnNames != null) {
				statement =  connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			}else{
				logger.warn("Columns are null");
				logger.info("Getting generated keys switched off !");
				statement =  connection.prepareStatement(sql);
			}
		}else{
			statement =  connection.prepareStatement(sql, columnNames);
		}
		optimizeStatement(statement);
		return statement;
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#prepareStatement(java.lang.String)
	 */
	@Override
	public PreparedStatement prepareStatement(String sql) throws SQLException {
		PreparedStatement statement;
		switch (operationType) {
		case READ:
			try {
				statement = connection.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY, ResultSet.CLOSE_CURSORS_AT_COMMIT);
			} catch (SQLException e) {
				logger.warn(e.getMessage());
				statement = connection.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			}catch (UnsupportedOperationException e) {
				logger.warn(e.getMessage());
				statement = connection.prepareStatement(sql,ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
			}
			break;
		default:
			statement = connection.prepareStatement(sql);
		}
		optimizeStatement(statement);
		return statement;
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#releaseSavepoint(java.sql.Savepoint)
	 */
	@Override
	public void releaseSavepoint(Savepoint savepoint) throws SQLException {
		connection.releaseSavepoint(savepoint);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#rollback()
	 */
	@Override
	public void rollback() throws SQLException {
		if (isTransactionsSupported()) {
			connection.rollback();
		}
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#rollback(java.sql.Savepoint)
	 */
	@Override
	public void rollback(Savepoint savepoint) throws SQLException {
		if (isTransactionsSupported()) {
			connection.rollback(savepoint);
		}
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setAutoCommit(boolean)
	 */
	@Override
	public void setAutoCommit(boolean autoCommit) throws SQLException {
// pnajvar-
// following check for transactions support causes problems and it seems to work fine without it
// even on non-transactions-enabled servers
//		if (isTransactionsSupported()) {
			connection.setAutoCommit(autoCommit);
//		}
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setCatalog(java.lang.String)
	 */
	@Override
	public void setCatalog(String catalog) throws SQLException {
		connection.setCatalog(catalog);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setHoldability(int)
	 */
	@Override
	public void setHoldability(int holdability) throws SQLException {
		connection.setHoldability(holdability);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setReadOnly(boolean)
	 */
	@Override
	public void setReadOnly(boolean readOnly) throws SQLException {
		connection.setReadOnly(readOnly);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setSavepoint()
	 */
	@Override
	public Savepoint setSavepoint() throws SQLException {
		return connection.setSavepoint();
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setSavepoint(java.lang.String)
	 */
	@Override
	public Savepoint setSavepoint(String name) throws SQLException {
		return connection.setSavepoint(name);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setTransactionIsolation(int)
	 */
	@Override
	public void setTransactionIsolation(int level) throws SQLException {
		connection.setTransactionIsolation(level);
	}

	/* (non-Javadoc)
	 * @see java.sql.Connection#setTypeMap(java.util.Map)
	 */
	@Override
	public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
		connection.setTypeMap(map);
	}

	//*************** END of java.sql.Connection INTERFACE ******************//
	
	/**
	 * This method optimizes all java.sql.Statements returned by this interface (createStatement(?)).
	 * @param statement
	 * @return
	 * @throws SQLException
	 */
	protected Statement optimizeStatement(Statement statement) throws SQLException {
		switch (operationType) {
			case READ:
				try{
					statement.setFetchDirection(ResultSet.FETCH_FORWARD);
				}catch(SQLException ex){
					//TODO: for now, do nothing;
				}
				break;
		}
		
		return statement;
	}

	/**
	 * Sets up a java.sql.Connection according given DBConnection object.
	 * @param dbConnection
	 * @return
	 * @throws JetelException
	 */
	protected static Connection connect(DBConnection dbConnection) {
		JdbcDriver jdbcDriver = dbConnection.getJdbcDriver();
		// -pnajvar
		// this is a bad hack, workaround for issue 2668
		if (jdbcDriver == null) { 
			throw new JetelRuntimeException("JDBC driver couldn't be obtained");
		}
		Driver driver = jdbcDriver.getDriver();
		Connection connection;
		Properties connectionProperties = new Properties(jdbcDriver.getProperties());
		connectionProperties.putAll(dbConnection.createConnectionProperties());
		
        try {
            connection = driver.connect(dbConnection.getDbUrl(), connectionProperties);
        } catch (SQLException ex) {
            throw new JetelRuntimeException("Can't connect to DB: " + ex.getMessage(), ex);
        }
        if (connection == null) {
            throw new JetelRuntimeException("Not suitable driver for specified DB URL (" + driver + " / " + dbConnection.getDbUrl());
        }
//        // try to set Transaction isolation level, it it was specified
//        if (config.containsKey(TRANSACTION_ISOLATION_PROPERTY_NAME)) {
//            int trLevel;
//            String isolationLevel = config.getProperty(TRANSACTION_ISOLATION_PROPERTY_NAME);
//            if (isolationLevel.equalsIgnoreCase("READ_UNCOMMITTED")) {
//                trLevel = Connection.TRANSACTION_READ_UNCOMMITTED;
//            } else if (isolationLevel.equalsIgnoreCase("READ_COMMITTED")) {
//                trLevel = Connection.TRANSACTION_READ_COMMITTED;
//            } else if (isolationLevel.equalsIgnoreCase("REPEATABLE_READ")) {
//                trLevel = Connection.TRANSACTION_REPEATABLE_READ;
//            } else if (isolationLevel.equalsIgnoreCase("SERIALIZABLE")) {
//                trLevel = Connection.TRANSACTION_SERIALIZABLE;
//            } else {
//                trLevel = Connection.TRANSACTION_NONE;
//            }
//            try {
//                connection.setTransactionIsolation(trLevel);
//            } catch (SQLException ex) {
//                // we do nothing, if anything goes wrong, we just
//                // leave whatever was the default
//            }
//        }
        // DEBUG logger.debug("DBConenction (" + getId() +") finishes connect function to the database at " + simpleDateFormat.format(new Date()));
        
        return connection;
	}

	/**
	 * Optimizes inner java.sql.Connection according given operation type.
	 * @param operationType
	 */
	protected void optimizeConnection(OperationType operationType) throws Exception {
		switch (operationType) {
		case READ:
			connection.setAutoCommit(false);
			connection.setReadOnly(true);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			break;
		case WRITE:
		case CALL:
			connection.setAutoCommit(false);
			connection.setReadOnly(false);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			break;

		case TRANSACTION:
			connection.setAutoCommit(true);
			connection.setReadOnly(false);
			connection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
			connection.setHoldability(ResultSet.CLOSE_CURSORS_AT_COMMIT);
			break;
		}
	}

	public boolean isTransactionsSupported() {
		return isTransactionsSupported(this.connection);
	}
	
	public static boolean isTransactionsSupported(Connection connection) {
		try {
			return connection.getTransactionIsolation() != Connection.TRANSACTION_NONE;
		} catch (SQLException e) {
			return false;
		}
	}

/* JDBC_4_ANT_KEY_BEGIN */
	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
	    return iface.isAssignableFrom(getClass()) || connection.isWrapperFor(iface);
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
	    if (iface.isAssignableFrom(getClass())) {
	        return iface.cast(this);
	    } else if (iface.isAssignableFrom(connection.getClass())) {
	        return iface.cast(connection);
	    } else {
	        return connection.unwrap(iface);
	    }
	}

	@Override
	public Array createArrayOf(String typeName, Object[] elements)
			throws SQLException {
		return connection.createArrayOf(typeName, elements);
	}

	@Override
	public Blob createBlob() throws SQLException {
		return connection.createBlob();
	}

	@Override
	public Clob createClob() throws SQLException {
		return connection.createClob();
	}

	@Override
	public NClob createNClob() throws SQLException {
		return connection.createNClob();
	}

	@Override
	public SQLXML createSQLXML() throws SQLException {
		return connection.createSQLXML();
	}

	@Override
	public Struct createStruct(String typeName, Object[] attributes)
			throws SQLException {
		return connection.createStruct(typeName, attributes);
	}

	@Override
	public Properties getClientInfo() throws SQLException {
		return connection.getClientInfo();
	}

	@Override
	public String getClientInfo(String name) throws SQLException {
		return connection.getClientInfo(name);
	}

	@Override
	public boolean isValid(int timeout) throws SQLException {
		return connection.isValid(timeout);
	}

	@Override
	public void setClientInfo(Properties properties)
			throws SQLClientInfoException {
		connection.setClientInfo(properties);
	}

	@Override
	public void setClientInfo(String name, String value)
			throws SQLClientInfoException {
		connection.setClientInfo(name, value);
	}


	/* JDBC_4_ANT_KEY_END */
}
