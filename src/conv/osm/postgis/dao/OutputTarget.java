package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import conv.osm.postgis.Util;
import conv.osm.postgis.model.OSMPrimitive;
import conv.osm.postgis.model.Status;

public class OutputTarget
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private static final String URL_PREFIX = "jdbc:postgresql_postGIS://";
    static {
        try {
            Class.forName("org.postgis.DriverWrapper");
        }
        catch (ClassNotFoundException ex) {
            String msg = "Please include postgis_1.3.6.jar ";
            logger.log(Level.SEVERE, msg, ex);
            System.err.println(msg);
        }
    }

    public static boolean merge = true; // TODO: Should not be public static

    
    private static boolean modelCreated = false;

    // INSTANCE

    private String host;

    
    private int port = 5432;

    private String databaseName;

    private static String defaultSchemaName = "public";

    private Connection db = null;
    private final Map<String, PreparedStatement> statementCache = new HashMap<String, PreparedStatement>();

    private String url;
    private String username;
    private String password;

    private long rows = 0L;

    public OutputTarget(String dbURL, String username, String password)
            throws SQLException {
        setPostGISURL(dbURL);
        this.username = username;
        this.password = password;
        open();
    }

    public Connection getConnection() {
        return db;
    }

    
    public PreparedStatement cacheableStatement(String sql)
            throws SQLException {
        PreparedStatement pstmt = statementCache.get(sql);
        if (null == pstmt) {
            logger.finer("Caching statement " + sql + ";");
            pstmt = db.prepareStatement(sql);
            statementCache.put(sql, pstmt);
        }
        return pstmt;
    }

    
    public void closeCached(String sql) {
        PreparedStatement pstmt = statementCache.remove(sql);
        if (null != pstmt) {
            logger.finer("Closing cached statement: " + sql + ";");
            try {
                pstmt.close();
            }
            catch (SQLException e) {
                logger.log(Level.WARNING,
                        "Unclean closing of " + sql + ";", e);
            }
        }
    }

    void open() throws SQLException {
        db = DriverManager.getConnection(url, username, password);
        logger.info("Database driver: " + db.getClass());
        db.setAutoCommit(false);
        db.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    }

    void close() {
        // Let's first close all the cached statements.
        String[] sqls = new String[statementCache.size()];
        statementCache.keySet().toArray(sqls);
        for (String sql : sqls) {
            closeCached(sql);
        }
        if (null != db) {
            try {
                db.close();
            }
            catch (Throwable e) {
                do {
                    logger.log(Level.FINE, "Closing failed.", e);
                } while ((e = e.getCause()) != null);
            }
            db = null;
        }
    }

    public void createModel() throws SQLException {
        if (modelCreated) return;
        modelCreated = true;
        int i=0;
        for (DAO dao : DAOFactory.getDefaultDAOFactory().getRegisteredDAOs()) {
            dao.setTarget(this);
            dao.createModel();
            i++;
        }
        db.commit();
    }

    public void setPostGISURL(String dbURL) {
        url = URL_PREFIX + dbURL;
        String[] split = dbURL.split("/");
        String[] again = split[0].split(":");
        if ("".equals(again[0])) {
            setHost("localhost");
        }
        else {
            setHost(again[0]);
        }
        if (again.length > 1) {
            setPort(Integer.valueOf(again[1]));
        }
        setDatabaseName(split[1]);
        logger.config("host=" + host + ", port=" + port + ", database="
                + databaseName + ", schema=" + getDefaultSchemaName());
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHost() {
        return host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getPort() {
        return port;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getDatabaseName() {
        return databaseName;
    }

    public static String getDefaultSchemaName() {
        return defaultSchemaName;
    }

    private int write(OSMPrimitive p) throws SQLException {
        DAO dao = DAOFactory.getDefaultDAOFactory().getDAO(p.getClass());
        try {
            // If the data has changed, it needs to be reanalyzed.
            p.setStatus(Status.RECOGNIZE);

            int result = 0;
            if (merge) {
                result = dao.update(p);
                if (result > 0) {
                    commit(result);
                    return result;
                }
                else if (result < 0) {
                    commit(0);
                    return result;
                }
            }
            result = dao.create(p);
            if (result > 0) {
                commit(result);
                return result;
            }
            else if (result < 0) {
                commit(0);
                return result;
            }
            
            rollback();
            return 0;
        }
        catch (SQLException ex) {
            
            rollback();
            throw ex;
        }
    }

    /**
     * 
     * @throws SQLException
     */
    public void commit(int rows) throws SQLException {
        this.rows += rows;
        db.commit();
    }

    /**
     * 
     * @throws SQLException
     */
    public void rollback() {
        try {
            db.rollback();
        }
        catch (SQLException e) {
            logger.log(Level.WARNING, "Rollback failed.", e);
        }
    }

    public int retryWrite(OSMPrimitive el, int times) throws SQLException {
        for (int i = 0; i < times; i++) {
            try {
                int result = write(el);
                return result; // Return immediately when successful.
            }
            catch (SQLException ex) {
                logger.log(Level.WARNING, "Problems writing " + el
                        + ". Retry attempt " + i + " of " + times + ".", ex);
            }
            if (keepAlive()) {
                // The database connection seems to be okay.
            }
            else {
                retryReconnect(times);
            }
            try {
                Thread.sleep(2000);
            }
            catch (InterruptedException e) {
            }
        }
        throw new SQLException("Failed to write " + el + " after " + times
                + " tries.");
    }

    /**
     * 
     * 
     * @throws SQLException
     */
    private void reconnect() throws SQLException {
        close();
        open();
        if (keepAlive()) {
            logger.log(Level.INFO, "Database reconnect successful.");
        }
        else {
            close();
            throw new SQLException("Database check failed after reconnect.");
        }
    }

    /**
     * 
     * @throws SQLException
     */
    private void retryReconnect(int times) throws SQLException {
        long delay = 1000;
        for (int i = 0; i < times; i++) {
            try {
                reconnect();
                return; // immediately when successful
            }
            catch (SQLException ex) {
                logger.log(Level.WARNING,
                        "Problems reconnecting. Retry attempt " + (i + 1)
                                + " of " + times + ". Waiting "
                                + Util.oneDecimal(delay / 1000f) + " s.",
                        ex);
            }
            try {
                Thread.sleep(delay);
                if (delay < 16000) {
                    delay *= 2;
                }
            }
            catch (InterruptedException e) {
            }
        }
        throw new SQLException("Failed to reconnect after " + times
                + " tries.");
    }

    public boolean keepAlive() {
        try {
            if (null == db) {
                logger.warning("No database connection.");
                return false;
            }
            else if (db.isClosed()) {
                logger.warning("The database connection is closed.");
                return false;
            }
            else {
                Statement stmt = db.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT 1");
                boolean result = rs.next();
                if (result) {
                    result = (1 == rs.getInt(1));
                }
                rs.close();
                stmt.close();
                if (result) {
                    logger.fine("The database connection is alive.");
                }
                else {
                    logger.warning("Abnormal database response.");
                }
                return result;
            }
        }
        catch (SQLException ex) {
            logger.warning("The database connection failed.");
            logger.fine(ex.toString());
            return false;
        }
    }

    public void logStats() {
        logger.info("Database rows inserted or updated total: " + rows);
    }

    @Override
    protected final void finalize() throws Throwable {
        close();
        super.finalize();
    }
}
