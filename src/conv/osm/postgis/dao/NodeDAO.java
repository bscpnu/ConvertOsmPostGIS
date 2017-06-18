package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.postgis.PGgeometryLW;

import conv.osm.postgis.Util;
import conv.osm.postgis.model.OSMNode;
import conv.osm.postgis.model.Status;

public class NodeDAO extends PrimitiveDAO<OSMNode>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

   /* private static final NodeDAO INSTANCE = new NodeDAO(
            OutputTarget.getDefaultSchemaName());

    public static final NodeDAO getInstance() {
        return INSTANCE;
    }*/
    
    NodeDAO(String schema) {
        super(schema, "osm_nodes");
        
    }

    @Override
    final String getTagsName() {
        return "osm_node_tags";
    }

    @Override
    public Class<OSMNode> getEntityClass() {
        return OSMNode.class;
    }

    @Override
    public void createModel() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT tablename FROM pg_tables"
                + " WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + getRelationName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + SQL_PRIMITIVE_COLS + ",lat integer NOT NULL"
                    + ",lon integer NOT NULL" + ",status smallint DEFAULT "
                    + Status.NONE + " NOT NULL" + ")");
            // Add the spatial column using the PostGIS method:
            stmt.executeQuery("SELECT AddGeometryColumn('"
                    + getSchemaName() + "','" + getRelationName() + "','"
                    + SQL_GEOM_COL + "'," + SRID + ",'POINT',2)");
            // Make geometry mandatory for nodes:
            stmt.executeUpdate("ALTER TABLE " + getFullName() + " ALTER "
                    + SQL_GEOM_COL + " SET NOT NULL");
        }
        rs.close();

        rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + getTagsName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + getSchemaName() + "."
                    + getTagsName() + " ("
                    + "id bigint NOT NULL REFERENCES " + getFullName()
                    + "(id)," + SQL_TAG_COLS + ")");
            stmt.executeUpdate("ALTER TABLE " + getSchemaName() + "."
                    + getTagsName() + " ADD CONSTRAINT " + getTagsName()
                    + "_pkey PRIMARY KEY (id, k)");
        }
        rs.close();

        stmt.close();
    }

    @Override
    public int create(OSMNode node) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName() + " (" + SQL_PRIMITIVE_FIELDS
                + ",lat,lon,status," + SQL_GEOM_COL + ") VALUES ("
                + SQL_PRIMITIVE_PARAMS + ",?,?,?,?)");
        preparePrimitiveInsert(node, pstmt);
        pstmt.setInt(5, Util.intify(node.getLatitude()));
        pstmt.setInt(6, Util.intify(node.getLongitude()));
        pstmt.setInt(7, node.getStatus().code());
        PGgeometryLW g = new PGgeometryLW(node.getGeometry());
        pstmt.setObject(8, g);
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);
            result += insertTags(node);
        }
        return result;
    }

    @Override
    public OSMNode read(Long key) throws SQLException {
        ResultSet rs = executeSelect("SELECT "
                + "changeset,timestamp AT TIME ZONE 'UTC',version,lat,lon FROM "
                + getFullName() + SQL_WHERE_ID, key);
        OSMNode node = null;
        if (rs.next()) {
            node = new OSMNode(Util.doublify(rs.getInt(5)),
                    Util.doublify(rs.getInt(4)));
            node.setId(key);
            node.setChangeSet(rs.getLong(1));
            node.setTime(rs.getTimestamp(2).getTime());
            node.setVersion(rs.getInt(3));

            selectTags(node);
        }
        return node;
    }

    @Override
    public int update(OSMNode node) throws SQLException {
        final Boolean old = isOld(node);
        if(null == old) return 0;
        if(old) return -1;

        PreparedStatement pstmt = target.cacheableStatement("UPDATE "
                + getFullName() + " SET " + SQL_PRIMITIVE_UPDATE
                + ",lat=?,lon=?,status=?," + SQL_GEOM_COL + "=?"
                + SQL_WHERE_ID);
        preparePrimitiveUpdate(node, pstmt);
        pstmt.setInt(4, Util.intify(node.getLatitude()));
        pstmt.setInt(5, Util.intify(node.getLongitude()));
        pstmt.setInt(6, node.getStatus().code());
        PGgeometryLW g = new PGgeometryLW(node.getGeometry());
        pstmt.setObject(7, g);
        pstmt.setLong(8, node.getId());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countUpdate(this);
            deleteTags(node.getId());

            result += insertTags(node);
        }
        return result;
    }

    @Override
    public Iterable<OSMNode> readAll() {
        throw new UnsupportedOperationException(); // TODO: Implement
    }
}
