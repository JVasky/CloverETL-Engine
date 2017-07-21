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
package org.jetel.connection.jdbc.specific.impl;

import java.sql.Connection;
import java.sql.ResultSet;

import org.jetel.connection.jdbc.specific.conn.GenericODBCConnection;
import org.jetel.database.sql.DBConnection;
import org.jetel.database.sql.SqlConnection;
import org.jetel.exception.JetelException;

/**
 * ODBC specific behavior.
 * 
 * @author Martin Slama (martin.slama@javlin.eu) (c) Javlin, a.s (http://www.javlin.eu)
 *
 * @created January 18, 2012
 */
public class GenericODBCSpecific extends AbstractJdbcSpecific {

	private static final GenericODBCSpecific INSTANCE = new GenericODBCSpecific();
	
	public static GenericODBCSpecific getInstance() {
		return INSTANCE;
	}

	protected GenericODBCSpecific() {
		super();
	}

	@Override
	public AutoGeneratedKeysType getAutoKeyType() {
		return AutoGeneratedKeysType.SINGLE;
	}
	
	@Override
	public boolean canCloseResultSetBeforeCreatingNewOne() {
		return false;
	}

	@Override
	public SqlConnection createSQLConnection(DBConnection dbConnection, Connection connection, OperationType operationType) throws JetelException {
		return new GenericODBCConnection(dbConnection, connection, operationType);
	}

	@Override
	public ResultSet wrapResultSet(ResultSet resultSet) {
		return new OdbcResultSet(resultSet);
	}
	
 }
