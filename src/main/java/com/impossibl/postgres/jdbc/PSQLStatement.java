package com.impossibl.postgres.jdbc;

import static com.impossibl.postgres.protocol.ServerObjectType.Statement;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.impossibl.postgres.protocol.BindExecCommand;
import com.impossibl.postgres.protocol.CloseCommand;
import com.impossibl.postgres.protocol.ResultField;
import com.impossibl.postgres.protocol.ServerObjectType;
import com.impossibl.postgres.types.Type;



public abstract class PSQLStatement implements Statement {

	
	
	PSQLConnection connection;
	int type;
	int concurrency;
	int holdability;
	int fetchDirection;
	String name;
	List<ResultField> resultFields;
	Integer maxRows;
	Integer fetchSize;
	Integer maxFieldSize;
	BindExecCommand command;
	boolean autoClose;
	List<PSQLResultSet> activeResultSets;
	SQLWarning warningChain;

	
	
	PSQLStatement(PSQLConnection connection, String name, List<ResultField> resultFields) {
		this.connection = connection;
		this.name = name;
		this.resultFields = resultFields;
		this.activeResultSets = new ArrayList<>();
	}

	/**
	 * Ensure the connection is not closed
	 * 
	 * @throws SQLException
	 * 					If the connection is closed
	 */
	void checkClosed() throws SQLException {
		
		if(isClosed())
			throw new SQLException("closed statement");
	}
	
	/**
	 * Disposes of the named server object
	 * 
	 * @param objectType
	 * 					Type of object to dispose of
	 * @param objectName
	 * 					Name of the object to dispose of
	 * @throws SQLException
	 * 					If an error occurs during disposal
	 */
	void dispose(ServerObjectType objectType, String objectName) throws SQLException {
		
		if(objectName == null)
			return;
	
		CloseCommand close = connection.getProtocol().createClose(objectType, objectName);
		
		connection.execute(close);		
	}
	
	/**
	 * Closes all active result sets for this statement
	 * 
	 * @throws SQLException
	 * 					If an error occurs closing a result set
	 */
	void closeResultSets() throws SQLException {
		
		for(PSQLResultSet rs : activeResultSets) {
			
			rs.internalClose();
			
		}
		
	}
	
	/**
	 * Called by result sets to notify the statement of their closure. Removes
	 * the result set from the active set of result sets. If auto-close is
	 * enabled this closes the statement when the last result set is closed.
	 * 
	 * @param resultSet
	 * 					The result set that is closing
	 * @throws SQLException
	 * 					If an error occurs closing a result set
	 */
	void handleResultSetClosure(PSQLResultSet resultSet) throws SQLException {
		
		activeResultSets.remove(resultSet);
		
		if(autoClose && activeResultSets.isEmpty()) {
			
			close();
			
		}
		
	}
	
	/**
	 * Determines whether or not the current statement state requires a named 
	 * portal or could use the unnamed portal instead
	 * 
	 * @return true when a named portal is required and false when it is not
	 */
	boolean needsNamedPortal() {

		return fetchSize != null;
	}

	/**
	 * Cleans up all resources, including active result sets
	 * 
	 * @throws SQLException
	 * 					If an error occurs closing result sets or 
	 */
	void internalClose() throws SQLException {

		closeResultSets();
		
		dispose(Statement, name);
		
		connection = null;
		command = null;
		resultFields = null;
	}

	/**
	 * Execute the named statement. It must have previously been parsed and
	 * ready to be bound and executed.
	 * 
	 * @param statementName Name of backend statement to execute or null
	 * @param parameterTypes List of parameter types
	 * @param parameterValues List of parmaeter values
	 * @return true if command returned results or false if not
	 * @throws SQLException
	 * 					If an error occurred durring statement execution
	 */
	public boolean executeStatement(String statementName, List<Type> parameterTypes, List<Object> parameterValues) throws SQLException {
		
		closeResultSets();

		String portalName = null;
		
		if (needsNamedPortal()) {
			portalName = connection.getNextPortalName();
		}

		command = connection.getProtocol().createBindExec(portalName, statementName, parameterTypes, parameterValues, resultFields, Object[].class);

		if(fetchSize != null)
			command.setMaxRows(fetchSize);
		
		if(maxFieldSize != null)
			command.setMaxFieldLength(maxFieldSize);

		warningChain = connection.execute(command);

		return !command.getResultFields().isEmpty();
		
	}
	
	@Override
	public Connection getConnection() throws SQLException {
		checkClosed();
		
		return connection;
	}

	@Override
	public int getResultSetType() throws SQLException {
		checkClosed();
		
		return type;
	}

	@Override
	public int getResultSetConcurrency() throws SQLException {
		checkClosed();
		
		return concurrency;
	}

	@Override
	public int getResultSetHoldability() throws SQLException {
		checkClosed();
		
		return holdability;
	}

	@Override
	public boolean isPoolable() throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setPoolable(boolean poolable) throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isCloseOnCompletion() throws SQLException {
		checkClosed();
		
		return autoClose;
	}

	@Override
	public void closeOnCompletion() throws SQLException {
		checkClosed();
		
		autoClose = true;
	}

	@Override
	public int getMaxFieldSize() throws SQLException {
		checkClosed();
		
		return maxFieldSize != null ? maxFieldSize : 0;
	}

	@Override
	public void setMaxFieldSize(int max) throws SQLException {
		checkClosed();
		
		if(max < 0)
			throw new SQLException("illegal argument");
		
		maxFieldSize = max;
	}

	@Override
	public int getMaxRows() throws SQLException {
		checkClosed();
		return maxRows != null ? maxRows : 0;
	}

	@Override
	public void setMaxRows(int max) throws SQLException {
		checkClosed();

		if(max < 0)
			throw new SQLException("illegal argument");
		
		maxRows = max;
	}

	@Override
	public int getFetchDirection() throws SQLException {
		checkClosed();
		
		return fetchDirection;
	}

	@Override
	public void setFetchDirection(int direction) throws SQLException {
		checkClosed();
		
		if (direction != ResultSet.FETCH_FORWARD ||
				direction != ResultSet.FETCH_REVERSE ||
				direction != ResultSet.FETCH_UNKNOWN)
			throw new SQLException("illegal argument");
			
		fetchDirection = direction;
	}

	@Override
	public int getFetchSize() throws SQLException {
		checkClosed();
		
		return fetchSize != null ? fetchSize : 0;
	}

	@Override
	public void setFetchSize(int rows) throws SQLException {
		checkClosed();
		
		if(rows < 0)
			throw new SQLException("illegal argument");
		
		fetchSize = rows;
	}

	@Override
	public void setEscapeProcessing(boolean enable) throws SQLException {
		checkClosed();
		
		throw new UnsupportedOperationException();
	}

	@Override
	public int getQueryTimeout() throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void setQueryTimeout(int seconds) throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
	}

	@Override
	public void setCursorName(String name) throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub

	}

	@Override
	public ResultSet getResultSet() throws SQLException {
		checkClosed();

		if (command.getResultFields().isEmpty()) {
			throw new SQLException("no result set available");
		}

		PSQLResultSet rs = new PSQLResultSet(this, command);
		this.activeResultSets.add(rs);
		return rs;
	}

	@Override
	public int getUpdateCount() throws SQLException {
		checkClosed();

		if (command.getResultRowsAffected() == null) {
			throw new SQLException("no update count available");
		}

		return (int) (long) command.getResultRowsAffected();
	}

	@Override
	public boolean getMoreResults() throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public boolean getMoreResults(int current) throws SQLException {
		checkClosed();
		return false;
	}

	@Override
	public ResultSet getGeneratedKeys() throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void cancel() throws SQLException {
		checkClosed();
		// TODO Auto-generated method stub
	}

	@Override
	public boolean isClosed() throws SQLException {
		return connection == null;
	}

	@Override
	public void close() throws SQLException {

		// Ignore multiple closes
		if(isClosed())
			return;

		connection.handleStatementClosure(this);
		
		internalClose();
	}
	
	@Override
	public SQLWarning getWarnings() throws SQLException {
		checkClosed();
		
		return warningChain;
	}

	@Override
	public void clearWarnings() throws SQLException {
		checkClosed();
		
		warningChain = null;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return iface.cast(this);
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return iface.isAssignableFrom(getClass());
	}

}
