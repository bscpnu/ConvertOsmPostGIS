package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import conv.osm.postgis.model.OSMWay;
import conv.osm.postgis.model.Status;

class WayNodeCursor implements Iterable<OSMWay>, Iterator<OSMWay>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private final WayDAO wayDAO;

    private static final long INTERVAL = 100000;

    private PreparedStatement pstmt;
    private ResultSet rs;
    private OSMWay way = null;
    private long nodeCount;
    private boolean hasNext = false;

    private final long minWayId, maxWayId;
    private long lastWayId;

    public WayNodeCursor(WayDAO wayDAO) throws SQLException {
        this.wayDAO = wayDAO;
        try {
            Statement stmt = wayDAO.target.getConnection().createStatement();
            
            rs = stmt.executeQuery("SELECT id FROM "
                    + DAOFactory.getDefaultDAOFactory().getFullName(
                            OSMWay.class) + " WHERE (status <> "
                    + Status.NONE + ") AND (status = " + Status.RECOGNIZE
                    + ") ORDER BY id ASC LIMIT 1");
            if (rs.next()) {
                minWayId = rs.getLong(1);
            }
            else {
                minWayId = 0;
            }
            rs.close();
            rs = null;

            rs = stmt.executeQuery("SELECT id FROM "
                    + DAOFactory.getDefaultDAOFactory().getFullName(
                            OSMWay.class) + " WHERE (status <> "
                    + Status.NONE + ") AND (status = " + Status.RECOGNIZE
                    + ") ORDER BY id DESC LIMIT 1");
            if (rs.next()) {
                maxWayId = rs.getLong(1);
            }
            else {
                maxWayId = 0;
            }
            rs.close();
            rs = null;

            stmt.close();
            stmt = null;

            // Calculates the next interval limit after minWayId.
            lastWayId = ((minWayId / INTERVAL) + 1) * INTERVAL;
            if (lastWayId > maxWayId) {
                lastWayId = maxWayId;
            }

            pstmt = wayDAO.target.cacheableStatement("SELECT"
                    + " n.id,nd,sequence FROM "
                    + wayDAO.getWayNodesTable()
                    + " AS n JOIN "
                    + DAOFactory.getDefaultDAOFactory().getFullName(
                            OSMWay.class)
                    + " AS w ON n.id=w.id WHERE status=" + Status.RECOGNIZE
                    + " AND n.id BETWEEN ? AND ?"
                    + " ORDER BY n.id,sequence");
            pstmt.setLong(1, minWayId);
            pstmt.setLong(2, lastWayId);
            logger.finer(pstmt.toString());
            rs = pstmt.executeQuery();
            hasNext = rs.next();
        }
        catch (SQLException e) {
            close();
            throw e;
        }
    }

    @Override
    public Iterator<OSMWay> iterator() {
        return this;
    }

    @Override
    public boolean hasNext() {
        return hasNext;
    }

    @Override
    public OSMWay next() {
        if (!hasNext) {
            close();
            throw new NoSuchElementException();
        }
        OSMWay next = null;
        try {
            do {
                long wayId = rs.getLong(1);
                long ndRef = rs.getLong(2);
                int sequence = rs.getInt(3);
                hasNext = rs.next();

                if (null == way) {
                    way = new OSMWay(wayId);
                    wayDAO.selectTags(way);
                    nodeCount = 0;
                }
                else if (way.getId() != wayId) {
                    next = way;
                    way = new OSMWay(wayId);
                    wayDAO.selectTags(way);
                    nodeCount = 0;
                }
                // Double check database integrity.
                nodeCount++;
                if (nodeCount != sequence) {
                    throw new RuntimeException("Way.id=" + wayId
                            + " ndref=" + ndRef + " sequence was "
                            + sequence + " when it should have been "
                            + nodeCount + ".");
                }
                way.getNodes().add(ndRef);
            } while (hasNext && (null == next));

            if (null == next) {
                // hasNext must be false
                next = way;
                way = null;
                nextInterval();
            }
        }
        catch (SQLException e) {
            close();
            throw new RuntimeException(e);
        }
        return next;
    }

    @Override
    public void remove() {
        close();
        throw new UnsupportedOperationException();
    }

    private void nextInterval() throws SQLException {
        while (lastWayId < maxWayId) {
            rs.close();
            pstmt.setLong(1, lastWayId + 1);
            lastWayId += INTERVAL;
            if (lastWayId > maxWayId) {
                lastWayId = maxWayId;
            }
            pstmt.setLong(2, lastWayId);
            logger.finer(pstmt.toString());
            rs = pstmt.executeQuery();
            hasNext = rs.next();
            if (hasNext) return;
        }
        close();
    }

    public void close() {
        try {
            if (null != rs) {
                rs.close();
            }
        }
        catch (SQLException ex) {
            ex.printStackTrace();
        }
        finally {
            rs = null;
        }
        pstmt = null;
    }

    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }
}