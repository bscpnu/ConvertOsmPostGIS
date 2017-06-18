package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgis.Geometry;
import org.postgis.LineString;
import org.postgis.PGgeometry;
import org.postgis.PGgeometryLW;
import org.postgis.Point;

import conv.osm.postgis.model.OSMDataException;
import conv.osm.postgis.model.OSMNode;
import conv.osm.postgis.model.OSMWay;
import conv.osm.postgis.model.SegmentNode;
import conv.osm.postgis.model.Status;

public class WayDAO extends PrimitiveDAO<OSMWay>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private static final WayDAO INSTANCE = new WayDAO(
            OutputTarget.getDefaultSchemaName());

    public static final WayDAO getInstance() {
        return INSTANCE;
    }

    // JDBC STATIC

    private static final String SQL_TAGS_TABLE = "osm_way_tags";
    private static final String SQL_NDREFS_TABLE = "osm_way_nodes";

    // INSTANCE

    private String nodesTable = null;

    private WayDAO(String schema) {
        super(schema, "osm_ways");
    }

    private String getNodesTable() {
        
        if (null == nodesTable) {
            nodesTable = DAOFactory.getDefaultDAOFactory().getFullName(
                    OSMNode.class);
        }
        return nodesTable;
    }

    String getWayNodesTable() {
        return getSchemaName() + "." + SQL_NDREFS_TABLE;
    }

    @Override
    public Class<OSMWay> getEntityClass() {
        return OSMWay.class;
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
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + SQL_PRIMITIVE_COLS + ",nodes integer NOT NULL"
                    + ",status smallint DEFAULT " + Status.NONE
                    + " NOT NULL)");
            // Add the spatial column using the PostGIS method:
            stmt.executeQuery("SELECT AddGeometryColumn('"
                    + getSchemaName() + "','" + getRelationName() + "','"
                    + SQL_GEOM_COL + "'," + SRID + ",'LINESTRING',2)");
            // Make geometry mandatory for ways:
            stmt.executeUpdate("ALTER TABLE " + getFullName() + " ALTER "
                    + SQL_GEOM_COL + " SET NOT NULL");
            // Create a partial index for queries based on status.
            stmt.executeUpdate("CREATE UNIQUE INDEX " + getRelationName()
                    + "_status ON " + getFullName()
                    + " (id) WHERE status <> " + Status.NONE);
        }
        rs.close();

        rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE tablename='" + SQL_TAGS_TABLE + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + SQL_TAGS_TABLE + " ("
                    + "id bigint NOT NULL REFERENCES " + getFullName()
                    + "(id)," + SQL_TAG_COLS + ")");
            stmt.executeUpdate("ALTER TABLE " + SQL_TAGS_TABLE
                    + " ADD CONSTRAINT " + SQL_TAGS_TABLE
                    + "_pkey PRIMARY KEY (id, k)");
        }
        rs.close();

        rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + SQL_NDREFS_TABLE + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + getWayNodesTable() + " ("
                    + "id bigint NOT NULL REFERENCES " + getFullName()
                    + "(id)," + "nd bigint NOT NULL REFERENCES "
                    + getNodesTable() + "(id),"
                    + "sequence integer DEFAULT 0 NOT NULL" + ")");

            // Find the Nodes of a Way in the right sequence.
            stmt.executeUpdate("ALTER TABLE " + getWayNodesTable()
                    + " ADD CONSTRAINT " + SQL_NDREFS_TABLE
                    + "_pkey PRIMARY KEY (id,sequence)");

            // Helps finding related Way objects when a OSMNode is known.
            stmt.executeUpdate("CREATE UNIQUE INDEX idx_"
                    + SQL_NDREFS_TABLE + " ON " + getWayNodesTable()
                    + " (nd,id,sequence)");
        }
        rs.close();
        stmt.close();
    }

    int insertNdRefs(OSMWay way) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getWayNodesTable() + " (id,nd,sequence) VALUES (?,?,?)");
        int result = 0, seq = 0;
        for (Long ref : way.getNodes()) {
            seq++;
            pstmt.setLong(1, way.getId());
            pstmt.setLong(2, ref);
            pstmt.setInt(3, seq);
            result += pstmt.executeUpdate();
        }
        return result;
    }

    private int deleteNdRefs(long id) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("DELETE FROM "
                + getWayNodesTable() + SQL_WHERE_ID);
        return deleteById(id, pstmt);
    }

    @Override
    public int create(OSMWay w) throws SQLException {
        LineString way;
        try {
            way = w.getLineString();
        }
        catch (OSMDataException osm) {
            logger.fine(osm.getMessage());
            return 0;
        }

        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName() + " (" + SQL_PRIMITIVE_FIELDS
                + ",nodes,status," + SQL_GEOM_COL + ") VALUES ("
                + SQL_PRIMITIVE_PARAMS + ",?,?,?)");
        super.preparePrimitiveInsert(w, pstmt);
        pstmt.setInt(5, w.getNodes().size());
        pstmt.setInt(6, w.getStatus().code());
        pstmt.setObject(7, new PGgeometryLW(way));
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);
            result += insertTags(w);
            result += insertNdRefs(w);
        }
        return result;
    }

    @Override
    public int delete(Long pk) throws SQLException {
        int result = 0;
        result += deleteNdRefs(pk);
        result += super.delete(pk);
        return result;
    }

    @Override
    public OSMWay read(Long key) throws SQLException {
        // Does not load the geometry because it's loaded on demand.
        ResultSet rs = executeSelect(
                "SELECT changeset,timestamp AT TIME ZONE 'UTC',version FROM "
                        + getFullName() + SQL_WHERE_ID, key);
        OSMWay way = null;
        if (rs.next()) {
            way = new OSMWay();
            way.setId(key);
            way.setChangeSet(rs.getLong(1));
            way.setTime(rs.getTimestamp(2).getTime());
            way.setVersion(rs.getInt(3));

            selectTags(way);
            selectNdRefs(way);
        }
        return way;
    }

    @Override
    public Iterable<OSMWay> readAll() {
        throw new UnsupportedOperationException(); // TODO:
    }

    /**
     * 
     * @throws SQLException
     */
    public Iterable<OSMWay> readUnrecognizedNodes() throws SQLException {
        return new WayNodeCursor(this);
    }

    public int update(OSMWay way) throws SQLException {
        final Boolean old = isOld(way);
        if (null == old) return 0;
        if (old) return -1;

        LineString geom;
        try {
            geom = way.getLineString();
        }
        catch (OSMDataException osm) {
            logger.log(Level.FINE, osm.getMessage(), osm);
            return 0;
        }

        PreparedStatement pstmt = target.cacheableStatement("UPDATE "
                + getFullName() + " SET " + SQL_PRIMITIVE_UPDATE
                + ",nodes=?,status=?," + SQL_GEOM_COL + "=?" + SQL_WHERE_ID);
        preparePrimitiveUpdate(way, pstmt);
        pstmt.setInt(4, way.getNodes().size());
        pstmt.setInt(5, way.getStatus().code());
        pstmt.setObject(6, new PGgeometryLW(geom));
        pstmt.setLong(7, way.getId());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countUpdate(this);
            // It did exist, so update its tags and nodes.
            deleteTags(way.getId());
            deleteNdRefs(way.getId());

            result += insertTags(way);
            result += insertNdRefs(way);
        }
        return result;
    }

    @Override
    public final String getTagsName() {
        return SQL_TAGS_TABLE;
    }

    /**
     * 
     * @throws SQLException
     */
    protected final void selectNdRefs(OSMWay way) throws SQLException {
        ResultSet rs = executeSelect("SELECT nd FROM " + getWayNodesTable()
                + SQL_WHERE_ID + " ORDER BY sequence", way.getId());
        final ArrayList<Long> nodes = way.getNodes();
        if (nodes.isEmpty()) {
        }
        else {
            logger.finer("Reloading node references of " + way + ".");
            nodes.clear();
        }
        while (rs.next()) {
            nodes.add(rs.getLong(1));
        }
        rs.close();
    }

    public Point[] validatePoints(OSMWay way) throws SQLException,
            OSMDataException {
        ArrayList<Long> wayNodes = way.getNodes();
        int nodes = wayNodes.size();
        if (nodes < 2) {
            throw new OSMDataException(way, nodes + " node reference"
                    + (1 == nodes ? "" : "s"));
        }
        else if (3 == nodes) {
            /*
             * This is a workaround for a rare special case that causes
             * OpenLayers to not be able to render the feature and fail.
             */
            if (wayNodes.get(0).equals(wayNodes.get(2))) {
                logger.fine("Way id=" + way.getId()
                        + " is a cycle between two nodes,"
                        + " but will be rendered as a simple line.");
                /*
                 * Break the cycle, by omitting the last node from the
                 * geometry.
                 */
                nodes = 2;
            }
        }
        DAOFactory.getDefaultDAOFactory().getDAO(OSMNode.class);
        
        ArrayList<Point> list = new ArrayList<Point>(nodes);
        PreparedStatement pstmt = target.cacheableStatement("SELECT "
                + SQL_GEOM_COL + " FROM " + getNodesTable() + SQL_WHERE_ID);
        int i = 0;
        while (i < nodes) {
            Long ref = wayNodes.get(i);
            pstmt.setLong(1, ref);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                PGgeometry g = (PGgeometry) rs.getObject(1);
                list.add((Point) g.getGeometry());
                i++;
            }
            else {
                logger.finest("OSMNode reference id=" + ref
                        + " had to be dropped from way id=" + way.getId()
                        + ". It had no geometry.");
                wayNodes.remove(i);
                nodes--;
            }
            rs.close();
        }
        if (list.size() < 2) {
            throw new OSMDataException(way,
                    "Not enough points have valid geometry");
        }
        return list.toArray(new Point[list.size()]);
    }

    public Map<Long, SegmentNode> readSegmentNodes(OSMWay way)
            throws SQLException, OSMDataException {
        int nodes = way.getNodes().size();

        Map<Long, SegmentNode> nodeMap = new HashMap<Long, SegmentNode>(
                nodes);

        final String sqlWaySegments = "SELECT"
                + " nd,count(*) AS crossed FROM " + getWayNodesTable()
                + " WHERE nd IN " + "(SELECT nd FROM " + getWayNodesTable()
                + " WHERE id=?)" + " GROUP BY nd";

        PreparedStatement pstmt = target.cacheableStatement("SELECT"
                + " nd,crossed," + SQL_GEOM_COL + " FROM ("
                + sqlWaySegments + ") AS wn LEFT JOIN " + getNodesTable()
                + " ON nd=" + getNodesTable() + ".id");
        pstmt.setLong(1, way.getId());

        ResultSet rs = pstmt.executeQuery();
        while (rs.next()) {
            long ndRef = rs.getLong(1);
            SegmentNode node = nodeMap.get(ndRef);
            if (null != node) {
                
                throw new RuntimeException("OSMNode.id=" + ndRef
                        + " returned as a duplicate.");
            }
            node = new SegmentNode();
            node.setCrossed(rs.getInt(2));
            PGgeometry geom = (PGgeometry) rs.getObject(3);
            if (null == geom) {
                throw new OSMDataException("OSMNode.id=" + ndRef
                        + " has no geometry in the database.");
            }
            else if (geom.getGeoType() == Geometry.POINT) {
                node.setGeometry((Point) geom.getGeometry());
            }
            else {
                throw new OSMDataException("OSMNode.id=" + ndRef
                        + " has strange geometry in the database: " + geom);
            }
            nodeMap.put(ndRef, node);
        }
        rs.close();

        return nodeMap;
    }
}
