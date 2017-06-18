package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.postgis.Geometry;
import org.postgis.PGgeometryLW;

import conv.osm.postgis.model.Entity;
import conv.osm.postgis.model.SpatialReference;

public abstract class AbstractDAO<T extends Entity> implements
        DAO<T, Long>, SpatialReference
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    static final String SQL_WHERE_ID = " WHERE id=?";

    public static final String SQL_GEOM_COL = "geom";

    protected OutputTarget target = null;

    private final String schemaName;

    private final String relationName;

    private final String sqlDelete;

    protected AbstractDAO(String schemaName, String relationName) {
        this.schemaName = schemaName;
        this.relationName = relationName;

        sqlDelete = "DELETE FROM " + getFullName() + SQL_WHERE_ID;

        PostGISDAOFactory.getInstance().register(this);
    }

    @Override
    public String getSchemaName() {
        return schemaName;
    }

    @Override
    public final String getRelationName() {
        return relationName;
    }

    @Override
    public final String getFullName() {
        return schemaName + "." + relationName;
    }

    protected final String getSequenceName() {
        return getSchemaName() + ".seq_" + getRelationName() + "_id";
    }

    protected final String getPKIndexName() {
        return getRelationName() + "_pkey";
    }

    @Override
    public final void setTarget(OutputTarget target) {
        this.target = target;
    }

    /**
     * 
     * @throws SQLException
     */
    public int delete(Long key) throws SQLException {
        return deleteById(key, target.cacheableStatement(sqlDelete));
    }

    protected int deleteById(long key, PreparedStatement pstmt)
            throws SQLException {
        pstmt.setLong(1, key);
        int result = pstmt.executeUpdate();
        if (result > 2000) {
            logger.fine(pstmt + "; affected " + result
                    + " rows (indicating a suspiciously large entity.)");
        }
        return result;
    }

    protected ResultSet executeSelect(String sql, long key)
            throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement(sql);
        pstmt.setLong(1, key);
        return pstmt.executeQuery();
    }

    public int updateStatus(Entity e) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("UPDATE "
                + getFullName() + " SET status=? WHERE id=?");
        pstmt.setInt(1, e.getStatus().code());
        pstmt.setLong(2, e.getId());
        return pstmt.executeUpdate();
    }

    public final float spheroidLength(Geometry geometry) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("SELECT ST_length_spheroid(?,'"
                + WKT_SPHEROID + "')");

        pstmt.setObject(1, new PGgeometryLW(geometry));
        ResultSet rs = pstmt.executeQuery();

        float length;
        if (rs.next()) {
            length = rs.getFloat(1);
        }
        else {
            throw new SQLException("Did not receive result from " + pstmt);
        }
        rs.close();
        return length;
    }
}
