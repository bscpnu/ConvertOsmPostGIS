package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.postgis.Geometry;
import org.postgis.LineString;
import org.postgis.PGgeometry;
import org.postgis.PGgeometryLW;
import org.postgis.Point;

import conv.osm.postgis.Util;
import conv.osm.postgis.model.Segment;

public class SegmentDAO extends AbstractDAO<Segment>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    public static final SegmentDAO[] getInstances() {
        DAO<Segment, Long>[] daos = DAOFactory.getDefaultDAOFactory().getRegisteredDAOs(
                Segment.class);
        SegmentDAO[] array = new SegmentDAO[daos.length];
        for (int i = 0; i < array.length; i++) {
            array[i] = (SegmentDAO) daos[i];
        }
        return array;
    }

    public SegmentDAO(String schema, String relation) {
        super(schema, relation);
    }

    @Override
    public Class<Segment> getEntityClass() {
        return Segment.class;
    }

    @Override
    public void createModel() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + getRelationName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE SEQUENCE " + getSequenceName());
            /*
             * Creates the graph edges relation for this topology.
             * Note that the geometry column is added separately.
             */
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + "id bigint PRIMARY KEY DEFAULT NEXTVAL('"
                    + getSequenceName() + "')," + "way bigint NOT NULL,"
                    + "nodes integer NOT NULL,"
                    + "first_nd bigint NOT NULL,"
                    + "last_nd bigint NOT NULL," + "left_face bigint,"
                    + "right_face bigint," + "x1 float8 NOT NULL,"
                    + "y1 float8 NOT NULL," + "x2 float8 NOT NULL,"
                    + "y2 float8 NOT NULL," + "length float8 NOT NULL"
                    + ")");
            // Add the spatial column using the PostGIS method:
            stmt.executeQuery("SELECT AddGeometryColumn('"
                    + getSchemaName() + "','" + getRelationName() + "','"
                    + PrimitiveDAO.SQL_GEOM_COL + "'," + SRID
                    + ",'LINESTRING',2)");
            // Make geometry mandatory for segments:
            stmt.executeUpdate("ALTER TABLE " + getFullName() + " ALTER "
                    + PrimitiveDAO.SQL_GEOM_COL + " SET NOT NULL");
            // Helps selecting the segments by OSM way id.
            stmt.executeUpdate("CREATE INDEX idx_" + getRelationName()
                    + "_way ON " + getFullName() + " (way)");
        }
        rs.close();

        stmt.close();
    }

    @Override
    public int create(Segment segment) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName()
                + " (way,nodes,first_nd,last_nd,left_face,right_face,x1,y1,x2,y2,length,"
                + SQL_GEOM_COL + ") " + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)");
        pstmt.setLong(1, segment.getWayId());
        pstmt.setLong(2, segment.length());
        pstmt.setLong(3, segment.getFirstNd());
        pstmt.setLong(4, segment.getLastNd());
        pstmt.setLong(5, segment.getLeftFace());
        pstmt.setLong(6, segment.getRightFace());
        pstmt.setDouble(7, segment.getX1());
        pstmt.setDouble(8, segment.getY1());
        pstmt.setDouble(9, segment.getX2());
        pstmt.setDouble(10, segment.getY2());
        pstmt.setFloat(11, segment.getGeodesicLength());
        pstmt.setObject(12, new PGgeometryLW(segment.getGeometry()));

        int result = pstmt.executeUpdate();
        if (result > 0) {
            Counter.countInsert(this);

            pstmt = target.cacheableStatement("SELECT lastval() FROM "
                    + getSequenceName());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                segment.setId(rs.getInt(1));
            }
            rs.close();
            logger.finest("Created " + getFullName() + " " + segment);
        }
        return result;
    }

    /**
     * 
     * @throws SQLException
     */
    public void deleteAll() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        stmt.executeUpdate("DELETE FROM " + getFullName()
                + "; ALTER SEQUENCE " + getSequenceName() + " RESTART 1");
        stmt.close();
    }

    /**
     * 
     * @throws SQLException
     */
    public int deleteSegments(long wayId) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("DELETE FROM "
                + getFullName() + " WHERE way=?");
        return deleteById(wayId, pstmt);
    }

    @Override
    public Segment read(Long key) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("SELECT"
                + " way,nodes,first_nd,last_nd,left_face,right_face,length,"
                + SQL_GEOM_COL + " FROM " + getFullName() + SQL_WHERE_ID);
        pstmt.setLong(1, key);
        ResultSet rs = pstmt.executeQuery();
        Segment s = null;
        if (rs.next()) {
            s = read(key, rs, 1);
        }
        return s;
    }

    private Segment read(long key, ResultSet rs, int offset)
            throws SQLException {
        Segment s = new Segment(rs.getLong(offset + 2));
        s.setId(key);
        s.setWayId(rs.getLong(offset));
        s.setLength(rs.getInt(offset + 1));
        s.setLastNd(rs.getLong(offset + 3));
        s.setLeftFace(rs.getLong(offset + 4));
        s.setRightFace(rs.getLong(offset + 5));
        s.setGeodesicLength(rs.getFloat(offset + 6));
        PGgeometry geom = (PGgeometry) rs.getObject(offset + 7);
        if (null == geom) {
            throw new IllegalStateException("Segment.id=" + key
                    + " has no geometry in the database.");
        }
        else if (geom.getGeoType() == Geometry.LINESTRING) {
            s.setGeometry((LineString) geom.getGeometry());
        }
        else {
            throw new IllegalStateException("Segment.id=" + key
                    + " has strange geometry in the database: " + geom);
        }
        return s;
    }

    @Override
    public Iterable<Segment> readAll() throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("SELECT"
                + " id,way,nodes,first_nd,last_nd"
                + ",left_face,right_face,length," + SQL_GEOM_COL + " FROM "
                + getFullName());
        ResultSet rs = pstmt.executeQuery();
        return new IterableResultSet<Segment>(rs) {
            @Override
            public Segment next() {
                ResultSet rs = advance();
                try {
                    return read(rs.getLong(1), rs, 2);
                }
                catch (SQLException e) {
                    throw new IllegalStateException(e.getMessage(), e);
                }
            }
        };
    }

    @Override
    public int update(Segment object) throws SQLException {
        throw new UnsupportedOperationException(); // TODO:
    }

    public Deque<Segment> readJoining(Segment seg, boolean forward)
            throws SQLException {
        logger.finest("Assembling the whole from segment " + seg + ".");
        
        Segment current = seg;
        /*
         * The path taken so far.
         */
        Deque<Segment> path = new ArrayDeque<Segment>();
       
        HashMap<Long, Set<Segment>> memory = new HashMap<Long, Set<Segment>>();

        long startNd, endNd;
        if (forward) {
            startNd = current.getFirstNd();
            endNd = current.getLastNd();
        }
        else {
            startNd = current.getLastNd();
            endNd = current.getFirstNd();
        }
        while (true) {
            if (endNd == startNd) {
                path.push(current);
                break;
            }
            // See where we can go next.
            Set<Segment> choices = choicesFrom(endNd);

            // Don't go back immediately.
            if (!choices.remove(current)) {
                throw new IllegalStateException("How did we ever get here?");
            }

            // Remember our choices in case we need to come back here later.
            memory.put(current.getId(), choices);

            // Reverse as long as we don't have any choices to go forward.
            while (choices.isEmpty()) {

                // See if we can go back.
                if (path.isEmpty()) {
                    // Nowhere to go anymore. Tried all alternatives.
                    logger.fine("Could not find a whole shape ("
                            + (forward ? "forwards" : "reverse")
                            + ") from " + seg + ".");
                    return null;
                }
                else {
                    // Recall previous alternative routes.
                    Segment previous = path.pop();
                    choices = memory.get(previous.getId());

                    // Eliminate the dead end from memory.
                    choices.remove(current);
                    memory.remove(current.getId());

                    // Step back to the other end where we came from.
                    if (startsAt(endNd, current)) {
                        endNd = current.getLastNd();
                    }
                    else {
                        endNd = current.getFirstNd();
                    }
                    current = previous;
                }
            }
            // This path seems to continue.
            path.push(current);

            // Let's find the leftmost choice, for a counter-clockwise path
            double maxAngle = -Math.PI; // extreme (180 degrees) right
            for (Segment next : choices) {
                Point behind, here, ahead;
                if (startsAt(endNd, current)) {
                    here = Util.sphericalToCartesian(current.getPoint(0));
                    behind = Util.sphericalToCartesian(current.getPoint(1));
                }
                else {
                    here = Util.sphericalToCartesian(current.getPoint(current.length() - 1));
                    behind = Util.sphericalToCartesian(current.getPoint(current.length() - 2));
                }
                if (startsAt(endNd, next)) {
                    ahead = Util.sphericalToCartesian(next.getPoint(1));
                }
                else {
                    ahead = Util.sphericalToCartesian(next.getPoint(next.length() - 2));
                }
                double angle = Util.angle3D(behind, here, ahead);
                if (angle > maxAngle) {
                    // This seems to be the best choice so far.
                    current = next;
                    maxAngle = angle;
                }
            }
            // The current segment now points to the leftmost next choice.

            if (memory.containsKey(current)) {
                
                break;
            }
            // Find the other end.
            if (startsAt(endNd, current)) {
                endNd = current.getLastNd();
            }
            else {
                endNd = current.getFirstNd();
            }
        }
        if (path.size() > 1) {
            Counter.countIgnore(this);
            logger.finer("Joined " + path.size()
                    + " segments starting from " + seg + ".");
        }
        return path;
    }

    private boolean startsAt(long fromNd, Segment toSegment) {
        if (fromNd == toSegment.getFirstNd()) {
            return true;
        }
        else if (fromNd == toSegment.getLastNd()) {
            return false;
        }
        else {
            throw new IllegalArgumentException("Lost direction.");
        }
    }

    private Set<Segment> choicesFrom(long nodeId) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("SELECT"
                + " id,way,nodes,first_nd,last_nd"
                + ",left_face,right_face,length," + SQL_GEOM_COL + " FROM "
                + getFullName() + " WHERE first_nd=? OR last_nd=?");
        pstmt.setLong(1, nodeId);
        pstmt.setLong(2, nodeId);
        ResultSet rs = pstmt.executeQuery();
        HashSet<Segment> choices = new HashSet<Segment>();
        while (rs.next()) {
            Segment s = read(rs.getLong(1), rs, 2);
            choices.add(s);
        }
        return choices;
    }
}
