/*
*    jETeL/Clover - Java based ETL application framework.
*    Copyright (C) 2002-04  David Pavlis <david_pavlis@hotmail.com>
*    
*    This library is free software; you can redistribute it and/or
*    modify it under the terms of the GNU Lesser General Public
*    License as published by the Free Software Foundation; either
*    version 2.1 of the License, or (at your option) any later version.
*    
*    This library is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU    
*    Lesser General Public License for more details.
*    
*    You should have received a copy of the GNU Lesser General Public
*    License along with this library; if not, write to the Free Software
*    Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*
*/
package org.jetel.database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.WeakHashMap;
import java.util.Map;

import org.jetel.data.DataRecord;
import org.jetel.data.HashKey;
import org.jetel.data.RecordKey;
import org.jetel.data.lookup.LookupTable;
import org.jetel.exception.JetelException;
import org.jetel.metadata.DataRecordMetadata;

/**
 *  Database table/SQLquery based lookup table which gets data by performing SQL
 *  query. No caching is performed, except the one done on DB side.
 * 
 *  Example using DBLookupTable:
 * 
 *
 *@author     dpavlis
 *@created    25. kv�ten 2003
 *@since      May 22, 2003
 */
public class DBLookupTable implements LookupTable {

	private DataRecordMetadata dbMetadata;
	private DBConnection dbConnection;
	private PreparedStatement pStatement;
	private RecordKey lookupKey;
	private int[] keyFields;
	private String[] keys;
	private String sqlQuery;
	private ResultSet resultSet;
	private CopySQLData[] transMap;
	private CopySQLData[] keyTransMap;
	private DataRecord dbDataRecord;
	private DataRecord keyDataRecord = null;
	private Map resultCache;
	private int maxCached;
	protected HashKey cacheKey;
	
	
  
	/**
	 *  Constructor for the DBLookupTable object
	 *
	 *@param  dbConnection      Description of the Parameter
	 *@param  dbRecordMetadata  Description of the Parameter
	 *@param  sqlQuery          Description of the Parameter
   *@param dbFieldTypes      List containing the types of the final record
	 */
  public DBLookupTable(DBConnection dbConnection,
                       DataRecordMetadata dbRecordMetadata, String sqlQuery) {
		this.dbConnection = dbConnection;
		this.dbMetadata = dbRecordMetadata;
		this.sqlQuery = sqlQuery;
	}

  
  public DBLookupTable(DBConnection dbConnection,
          DataRecordMetadata dbRecordMetadata, String sqlQuery, int numCached) {
      this.dbConnection = dbConnection;
      this.dbMetadata = dbRecordMetadata;
      this.sqlQuery = sqlQuery;
      if (numCached>0){
          this.resultCache= new WeakHashMap(numCached);
          this.maxCached=numCached;
      }
}
  
	/**
	 *  Gets the dbMetadata attribute of the DBLookupTable object.<br>
	 *  <i>init() should be called prior to calling this method (unless dbMetadata
	 * was passed in using appropriate constructor.</i>
	 *
	 *@return    The dbMetadata value
	 */
	public DataRecordMetadata getMetadata() {
		return dbMetadata;
	}

	/**
	 *  Looks-up data based on speficied key.<br>
	 *  The key value is taken from data record
	 *
	 *@return                     Associated DataRecord or NULL if not found
	 *@since                      May 2, 2002
	 */
	public DataRecord get(DataRecord keyRecord) {
	    // if cached, then try to query cache first
	    if (cacheKey!=null){
	        cacheKey.setDataRecord(keyRecord);
	        DataRecord data=(DataRecord)resultCache.get(cacheKey);
	        if (data!=null){
	            return data;
	        }
	    }
	    
	    try {
	        pStatement.clearParameters();
	        
	        if (keyTransMap==null){
	            if (lookupKey != null) {
	                throw new RuntimeException("RecordKey was not defined for lookup !");
	            }
	            
	            try {
	                keyTransMap = CopySQLData.jetel2sqlTransMap(
	                        SQLUtil.getFieldTypes(pStatement.getParameterMetaData()),
	                        keyRecord,lookupKey.getKeyFields());
	            } catch (Exception ex) {
	                throw new RuntimeException("Can't create keyRecord transmap: "+ex.getMessage());
	            }
	        }
	        for (int i = 0; i < transMap.length; i++) {
	            keyTransMap[i].jetel2sql(pStatement);
	        }
	        
	    //execute query
	    resultSet = pStatement.executeQuery();
	    fetch();
	} catch (SQLException ex) {
	    throw new RuntimeException(ex.getMessage());
	}
	
	// if cache exists, add this newly found to cache
	if (maxCached>0){
	    DataRecord storeRecord=dbDataRecord.duplicate();
	    resultCache.put(new HashKey(lookupKey, storeRecord), storeRecord);
	}
	
	return dbDataRecord;
}

	/**
	 *  Looks-up record/data based on specified array of parameters
	 *
	 *@param  keys                Description of the Parameter
	 *@return                     Description of the Return Value
	 */
	public DataRecord get(Object keys[]) {
		try {
			// set up parameters for query
			// statement uses indexing from 1
			pStatement.clearParameters();
			for (int i = 0; i < keys.length; i++) {
				pStatement.setObject(i + 1, keys[i]);
			}
			//execute query
			resultSet = pStatement.executeQuery();
			if (!fetch()) {
				return null;
			}
    }
    catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage());
		}
		return dbDataRecord;
	}

	/**
	 *  Looks-up data based on specified key-string
	 *
	 *@param  keyStr              Description of the Parameter
	 *@return                     Description of the Return Value
	 */
	public DataRecord get(String keyStr) {
		try {
			// set up parameters for query
			// statement uses indexing from 1
			pStatement.clearParameters();
			pStatement.setString(1, keyStr);
			//execute query
			resultSet = pStatement.executeQuery();
			if (!fetch()) {
				return null;
			}
    }
    catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage());
		}
		return dbDataRecord;
	}

	/**
	 *  Executes query and returns data record (statement must be initialized with
	 *  parameters prior to calling this function
	 *
	 *@return                   DataRecord obtained from DB or null if not found
	 *@exception  SQLException  Description of the Exception
	 */
	private boolean fetch() throws SQLException {
		if (!resultSet.next()) {
			return false;
		}
		//get data from results
		for (int i = 0; i < transMap.length; i++) {
			transMap[i].sql2jetel(resultSet);
		}
		return true;
	}

	/**
	 *  Returns the next found record if previous get() method succeeded.<br>
	 *  If no more records, returns NULL
	 *
	 *@return                     The next found record
	 *@exception  JetelException  Description of the Exception
	 */
	public DataRecord getNext() {
        try {
            if (!fetch()) {
                return null;
            } else {
                return dbDataRecord;
            }
        } catch (SQLException ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see org.jetel.data.lookup.LookupTable#getNumFound()
     * 
     * Using this method on this implementation of LookupTable
     * can be time consuming as it requires sequential scan through
     * the whole result set returned from DB (on some DBMSs).
     * Also, it resets the position in result set. So subsequent
     * calls to getNext() will start reading the data found from
     * the first record.
     */
    public int getNumFound() {
        if (resultSet != null) {
            try {
                int curRow=resultSet.getRow();
                resultSet.last();
                int count=resultSet.getRow();
                resultSet.first();
                resultSet.absolute(curRow);
                return count;
            } catch (SQLException ex) {
                return -1;
            }
        }
        return -1;
    }

    
    /* (non-Javadoc)
     * @see org.jetel.data.lookup.LookupTable#setLookupKey(java.lang.Object)
     */
    public void setLookupKey(Object obj){
        this.keyTransMap=null; // invalidate current transmap -if it exists
        if (obj instanceof RecordKey){
	        this.lookupKey=((RecordKey)obj);
	        this.cacheKey=new HashKey(lookupKey,null);
	    }else{
            this.lookupKey=null;
        }
    }
    
	/**
	 *  Initializtaion of lookup table - loading all data into it.
	 *
	 *@exception  JetelException  Description of the Exception
	 *@since                      May 2, 2002
	 */
    public void init() throws JetelException {
        // first try to connect to db
        try {
            //dbConnection.connect();
            pStatement = dbConnection.prepareStatement(sqlQuery);
            /*ResultSet.TYPE_SCROLL_INSENSITIVE,ResultSet.CONCUR_READ_ONLY,
                    ResultSet.CLOSE_CURSORS_AT_COMMIT);*/
            
        } catch (SQLException ex) {
            throw new JetelException("Can't create SQL statement: "
                    + ex.getMessage());
        }
        // obtain dbMetadata info if needed
        if (dbMetadata == null) {
            try {
                dbMetadata = SQLUtil.dbMetadata2jetel(pStatement.getMetaData());
            } catch (SQLException ex) {
                throw new JetelException(
                        "Can't automatically obtain dbMetadata (use other constructor): "
                        + ex.getMessage());
            }
        }
        // create data record for fetching data from DB
        dbDataRecord = new DataRecord(dbMetadata);
        dbDataRecord.init();
        
        // create trans map which will be used for fetching data
        try {
            transMap = CopySQLData.sql2JetelTransMap(
                    SQLUtil.getFieldTypes(dbMetadata), dbMetadata,
                    dbDataRecord);
        } catch (Exception ex) {
            throw new JetelException(
                    "Can't automatically obtain dbMetadata/create transMap : "
                    + ex.getMessage());
        }
        
    }

	/**
	 *  Deallocates resources
	 */
	public void close() {
		try {
			pStatement.close();
    }
    catch (SQLException ex) {
			throw new RuntimeException(ex.getMessage());
		}
	}
	
	
	public void setNumCached(int numCached){
	    if (numCached>0){
	          this.resultCache= new WeakHashMap(numCached);
	          this.maxCached=numCached;
	      }
	}
}
