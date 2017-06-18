package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

import org.postgis.PGgeometryLW;

import conv.osm.postgis.model.RouteSegment;

public class RouteSegmentDAO extends AbstractDAO<RouteSegment>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    public RouteSegmentDAO(String schema, String relation) {
        super(schema, relation);
    }

    @Override
    public Class<RouteSegment> getEntityClass() {
        return RouteSegment.class;
    }

    @Override
    public int create(RouteSegment route) throws SQLException {

        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName()
                + " (way,source,target,cost,reverse_cost,x1,y1,x2,y2,"
                + "rule,to_cost,length," + SQL_GEOM_COL + ") "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)");
        pstmt.setLong(1, route.getWayId());
        pstmt.setInt(2, (int) route.getFirstNd());
        pstmt.setInt(3, (int) route.getLastNd());
        pstmt.setDouble(4, route.getCost());
        pstmt.setDouble(5, route.getReverseCost());
        pstmt.setDouble(6, route.getX1());
        pstmt.setDouble(7, route.getY1());
        pstmt.setDouble(8, route.getX2());
        pstmt.setDouble(9, route.getY2());
        pstmt.setString(10, route.getRule());
        pstmt.setDouble(11, route.getToCost());
        pstmt.setFloat(12, route.getGeodesicLength());
        pstmt.setObject(13, new PGgeometryLW(route.getGeometry()));

        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);

            pstmt = target.cacheableStatement("SELECT lastval() FROM "
                    + getSequenceName());
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                route.setId(rs.getInt(1));
            }
            rs.close();
            logger.finest("Created " + getFullName() + " " + route);
        }

        return result;
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
             * Creates the graph edges relation for this navigation network.
             * Note that the geometry column is added separately.
             */
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + "id integer PRIMARY KEY DEFAULT NEXTVAL('"
                    + getSequenceName() + "')," + "way bigint NOT NULL,"
                    + "source integer NOT NULL,"
                    + "target integer NOT NULL," + "cost float8 NOT NULL,"
                    + "reverse_cost float8 NOT NULL,"
                    + "x1 float8 NOT NULL," + "y1 float8 NOT NULL,"
                    + "x2 float8 NOT NULL," + "y2 float8 NOT NULL,"
                    + "rule varchar," + "to_cost float8,"
                    + "length float8 NOT NULL" + ")");
            // Add the spatial column using the PostGIS method:
            stmt.executeQuery("SELECT AddGeometryColumn('"
                    + getSchemaName() + "','" + getRelationName() + "','"
                    + PrimitiveDAO.SQL_GEOM_COL + "'," + SRID
                    + ",'LINESTRING',2)");
            // Make geometry mandatory for route segments:
            stmt.executeUpdate("ALTER TABLE " + getFullName() + " ALTER "
                    + PrimitiveDAO.SQL_GEOM_COL + " SET NOT NULL");
            // Helps selecting the route segments by OSM way id.
            stmt.executeUpdate("CREATE INDEX idx_" + getRelationName()
                    + "_way ON " + getFullName() + " (way)");
        }
        rs.close();

        stmt.close();
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
    public int deleteSegments(long id) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("DELETE FROM "
                + getFullName() + " WHERE way=?");
        return deleteById(id, pstmt);
    }

    @Override
    public RouteSegment read(Long key) throws SQLException {
        throw new UnsupportedOperationException(); // TODO:
    }

    @Override
    public Iterable<RouteSegment> readAll() {
        throw new UnsupportedOperationException(); // TODO:
    }

    @Override
    public int update(RouteSegment object) throws SQLException {
        throw new UnsupportedOperationException(); // TODO:
    }
}
