/**
 * 
 */
package org.gusdb.wdk.model.query;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import javax.xml.rpc.ServiceException;

import org.apache.log4j.Logger;
import org.gusdb.wdk.model.Column;
import org.gusdb.wdk.model.ColumnType;
import org.gusdb.wdk.model.WdkModelException;
import org.gusdb.wdk.model.WdkUserException;
import org.gusdb.wdk.model.dbms.ArrayResultList;
import org.gusdb.wdk.model.dbms.CacheFactory;
import org.gusdb.wdk.model.dbms.DBPlatform;
import org.gusdb.wdk.model.dbms.ResultList;
import org.gusdb.wsf.client.WsfService;
import org.gusdb.wsf.client.WsfServiceServiceLocator;
import org.gusdb.wsf.plugin.WsfResult;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author Jerric Gao
 * 
 */
public class ProcessQueryInstance extends QueryInstance {

    private static final Logger logger = Logger.getLogger(ProcessQueryInstance.class);

    private ProcessQuery query;
    private int signal;

    /**
     * @param query
     * @param values
     * @throws WdkModelException
     */
    public ProcessQueryInstance(ProcessQuery query, Map<String, Object> values)
            throws WdkModelException {
        super(query, values);
        this.query = query;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.QueryInstance#appendSJONContent(org.json.JSONObject
     * )
     */
    @Override
    protected void appendSJONContent(JSONObject jsInstance)
            throws JSONException {
        jsInstance.put("signal", signal);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.QueryInstance#insertToCache(java.sql.Connection
     * , java.lang.String)
     */
    @Override
    public void insertToCache(Connection connection, String tableName,
            int instanceId) throws WdkModelException, SQLException,
            NoSuchAlgorithmException, JSONException, WdkUserException {
        Column[] columns = query.getColumns();

        // prepare the sql
        StringBuffer sql = new StringBuffer("INSERT INTO ");
        sql.append(tableName);
        sql.append(" (");
        sql.append(CacheFactory.COLUMN_INSTANCE_ID);
        for (Column column : columns) {
            sql.append(", ");
            sql.append(column.getName());
        }
        sql.append(") VALUES (");
        sql.append(instanceId);
        for (int i = 0; i < columns.length; i++) {
            sql.append(", ?");
        }
        sql.append(")");

        DBPlatform platform = query.getWdkModel().getQueryPlatform();
        PreparedStatement ps = null;
        try {
            ps = connection.prepareStatement(sql.toString());
            ResultList resultList = getUncachedResults();
            int rowId = 0;
            while (resultList.next()) {
                int columnId = 1;
                for (Column column : columns) {
                    String value = (String) resultList.get(column.getName());

                    // determine the type
                    ColumnType type = column.getType();
                    if (type == ColumnType.BOOLEAN) {
                        ps.setBoolean(columnId, Boolean.parseBoolean(value));
                    } else if (type == ColumnType.CLOB) {
                        platform.updateClobData(ps, columnId, value, false);
                    } else if (type == ColumnType.DATE) {
                        ps.setDate(columnId, Date.valueOf(value));
                    } else if (type == ColumnType.FLOAT) {
                        ps.setFloat(columnId, Float.parseFloat(value));
                    } else if (type == ColumnType.NUMBER) {
                        ps.setInt(columnId, Integer.parseInt(value));
                    } else {
                        int width = column.getWidth();
                        if (value != null && value.length() > width) {
                            logger.warn("Column [" + column.getName()
                                    + "] value truncated.");
                            value = value.substring(0, width - 3) + "...";
                        }
                        ps.setString(columnId, value);
                    }
                    columnId++;
                }
                ps.addBatch();

                rowId++;
                if (rowId % 1000 == 0) ps.executeBatch();
            }
            if (rowId % 1000 != 0) ps.executeBatch();
        } finally {
            // close the statement manually, since we need to keep the
            // connection open to finish the transaction.
            if (ps != null) ps.close();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.QueryInstance#getUncachedResults(org.gusdb.
     * wdk.model.Column[], java.lang.Integer, java.lang.Integer)
     */
    @Override
    protected ResultList getUncachedResults() throws WdkModelException,
            SQLException, NoSuchAlgorithmException, JSONException,
            WdkUserException {
        // prepare parameters and columns
        Map<String, String> paramValues = getInternalParamValues();
        String[] params = new String[paramValues.size()];
        int idx = 0;
        for (String param : paramValues.keySet()) {
            String value = paramValues.get(param);
            params[idx++] = param + "=" + value;
        }

        Column[] columns = query.getColumns();
        String[] columnNames = new String[columns.length];
        for (int i = 0; i < columns.length; i++) {
            columnNames[i] = columns[i].getName();
            // if the wsName is defined, reassign it to the columns
            if (columns[i].getWsName() != null)
                columnNames[i] = columns[i].getWsName();
        }

        String invokeKey = query.getFullName();

        StringBuffer resultMessage = new StringBuffer();
        try {
            WsfResult result = getResult(query.getProcessName(), invokeKey,
                    params, columnNames, query.isLocal());
            this.resultMessage = result.getMessage();
            this.signal = result.getSignal();

            // TEST
            logger.debug("WSQI Result Message:" + resultMessage);
            logger.info("Result Array size = " + result.getResult().length);

            return new ArrayResultList<String>(columns, result.getResult());

        } catch (RemoteException ex) {
            throw new WdkModelException(ex);
        } catch (ServiceException ex) {
            throw new WdkModelException(ex);
        } catch (MalformedURLException ex) {
            throw new WdkModelException(ex);
        }
    }

    private WsfResult getResult(String processName, String invokeKey,
            String[] params, String[] columnNames, boolean local)
            throws ServiceException, WdkModelException, RemoteException,
            MalformedURLException {
        String serviceUrl = query.getWebServiceUrl();

        // DEBUG
        logger.info("Invoking " + processName + " at " + serviceUrl);
        long start = System.currentTimeMillis();

        WsfResult result;
        if (local) { // invoke the process query locally
            org.gusdb.wsf.service.WsfService service = new org.gusdb.wsf.service.WsfService();

            // get the response from the local service
            result = service.invokeEx(processName, invokeKey, params,
                    columnNames);
        } else { // invoke the process query via web service
            // get a WSF Service client stub
            WsfServiceServiceLocator locator = new WsfServiceServiceLocator();
            WsfService client = locator.getWsfService(new URL(serviceUrl));

            // get the response from the web service
            result = client.invokeEx(processName, invokeKey, params,
                    columnNames);
        }
        long end = System.currentTimeMillis();
        logger.debug("Client took " + ((end - start) / 1000.0) + " seconds.");

        return result;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.query.QueryInstance#getSql()
     */
    @Override
    public String getSql() throws WdkModelException, SQLException,
            NoSuchAlgorithmException, JSONException, WdkUserException {
        // always get sql that queries on the cached result
        return getCachedSql();
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.gusdb.wdk.model.query.QueryInstance#createCache(java.sql.Connection,
     * java.lang.String, int)
     */
    @Override
    public void createCache(Connection connection, String tableName,
            int instanceId) throws WdkModelException, SQLException,
            NoSuchAlgorithmException, JSONException, WdkUserException {
        DBPlatform platform = query.getWdkModel().getQueryPlatform();
        Column[] columns = query.getColumns();

        StringBuffer sqlTable = new StringBuffer("CREATE TABLE ");
        sqlTable.append(tableName).append(" (");

        // define the instance id column
        sqlTable.append(CacheFactory.COLUMN_INSTANCE_ID).append(" ");
        sqlTable.append(platform.getNumberDataType(12)).append(" NOT NULL");

        // define the rest of the columns
        for (Column column : columns) {
            int width = column.getWidth();
            ColumnType type = column.getType();

            String strType;
            if (type == ColumnType.BOOLEAN) {
                strType = platform.getBooleanDataType();
            } else if (type == ColumnType.CLOB) {
                strType = platform.getClobDataType();
            } else if (type == ColumnType.DATE) {
                strType = platform.getDateDataType();
            } else if (type == ColumnType.FLOAT) {
                strType = platform.getFloatDataType(width);
            } else if (type == ColumnType.NUMBER) {
                strType = platform.getNumberDataType(width);
            } else if (type == ColumnType.STRING) {
                strType = platform.getStringDataType(width);
            } else {
                throw new WdkModelException("Unknown data type [" + type
                        + "] of column [" + column.getName() + "]");
            }

            sqlTable.append(", ").append(column.getName()).append(" ");
            sqlTable.append(strType);
        }
        sqlTable.append(")");

        Statement stmt = null;
        try {
            stmt = connection.createStatement();
            stmt.execute(sqlTable.toString());
        } finally {
            if (stmt != null) stmt.close();
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.query.QueryInstance#getResultSize()
     */
    @Override
    public int getResultSize() throws NoSuchAlgorithmException, SQLException,
            WdkModelException, JSONException, WdkUserException {
        if (!cached) {
            int count = 0;
            ResultList resultList = getResults();
            while (resultList.next()) {
                count++;
            }
            return count;
        } else return super.getResultSize();
    }

}