package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

import conv.osm.postgis.model.OSMChangeSet;

public class ChangeSetDAO extends PrimitiveDAO<OSMChangeSet> {
    ChangeSetDAO(String schema) {
        super(schema, "osm_changesets");
    }

    @Override
    String getTagsName() {
        return "osm_changeset_tags";
    }

    @Override
    public Class<OSMChangeSet> getEntityClass() {
        return OSMChangeSet.class;
    }

    @Override
    public void createModel() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT tablename FROM pg_tables"
                + " WHERE schemaname='" + getSchemaName() + "' AND tablename='"
                + getRelationName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        } else {
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + "id bigint NOT NULL PRIMARY KEY,"
                    + "user_id bigint NOT NULL,"
                    + "created_at timestamp without time zone NOT NULL,"
                    + "min_lat integer," + "max_lat integer,"
                    + "min_lon integer," + "max_lon integer,"
                    + "closed_at timestamp without time zone,"
                    + "num_changes integer DEFAULT 0 NOT NULL" + ")");
        }
        rs.close();

        rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName() + "' AND tablename='"
                + getTagsName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        } else {
            stmt.executeUpdate("CREATE TABLE " + getSchemaName() + "."
                    + getTagsName() + " (" + "id bigint NOT NULL REFERENCES "
                    + getFullName() + "(id)," + SQL_TAG_COLS + ")");
            stmt.executeUpdate("ALTER TABLE " + getSchemaName() + "."
                    + getTagsName() + " ADD CONSTRAINT " + getTagsName()
                    + "_pkey PRIMARY KEY (id, k)");
        }
        rs.close();

        stmt.close();
    }

    @Override
    public Iterable<OSMChangeSet> readAll() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int create(OSMChangeSet cs) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName() + " (" + "id,user_id,created_at,"
                + "min_lat,max_lat,min_lon,max_lon," + "closed_at,num_changes"
                + ") VALUES (?,?,? AT TIME ZONE 'UTC',"
                + "?,?,?,?,? AT TIME ZONE 'UTC',?)");

        pstmt.setLong(1, cs.getId());
        pstmt.setInt(2, cs.getUserId());
        pstmt.setTimestamp(3, new java.sql.Timestamp(cs.getCreatedAt()));
        if (null != cs.getMinLatitude()) {
            pstmt.setInt(4, cs.getMinLatitude());
        } else {
            pstmt.setNull(4, java.sql.Types.INTEGER);
        }
        if (null != cs.getMaxLatitude()) {
            pstmt.setInt(5, cs.getMaxLatitude());
        } else {
            pstmt.setNull(5, java.sql.Types.INTEGER);
        }
        if (null != cs.getMinLongitude()) {
            pstmt.setInt(6, cs.getMinLongitude());
        } else {
            pstmt.setNull(6, java.sql.Types.INTEGER);
        }
        if (null != cs.getMaxLongitude()) {
            pstmt.setInt(7, cs.getMaxLongitude());
        } else {
            pstmt.setNull(7, java.sql.Types.INTEGER);
        }
        if (null != cs.getClosedAt()) {
            pstmt.setTimestamp(8, new java.sql.Timestamp(cs.getClosedAt()));
        } else {
            pstmt.setNull(8, java.sql.Types.TIMESTAMP);
        }
        pstmt.setInt(9, cs.getNumChanges());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);
            result += insertTags(cs);
        }
        return result;
    }

    @Override
    public OSMChangeSet read(Long key) throws SQLException {
        ResultSet rs = executeSelect("SELECT "
                + "user_id,created_at AT TIME ZONE 'UTC'"
                + ",min_lat,max_lat,min_lon,max_lon"
                + ",closed_at AT TIME ZONE 'UTC',num_changes" + " FROM "
                + getFullName() + SQL_WHERE_ID, key);
        OSMChangeSet cs = null;
        if (rs.next()) {
            cs = new OSMChangeSet();
            cs.setId(key);
            cs.setUserId(rs.getInt(1));
            cs.setCreatedAt(rs.getTimestamp(2).getTime());
            cs.setMinLatitude((Integer) rs.getObject(3));
            cs.setMaxLatitude((Integer) rs.getObject(4));
            cs.setMinLongitude((Integer) rs.getObject(5));
            cs.setMaxLongitude((Integer) rs.getObject(6));
            final Timestamp t = rs.getTimestamp(7);
            cs.setClosedAt((null == t) ? null : t.getTime());
            cs.setNumChanges(rs.getInt(8));
            selectTags(cs);
        }
        return cs;
    }

    @Override
    public int update(OSMChangeSet cs) throws SQLException {
        // Check if the data has changed, before actually updating it.
        final Boolean old = isOld(cs);
        if (null == old)
            return 0;
        if (old)
            return -1;

        PreparedStatement pstmt = target.cacheableStatement("UPDATE "
                + getFullName() + " SET "
                + "user_id=?,created_at=? AT TIME ZONE 'UTC'"
                + ",min_lat=?,max_lat=?,min_lon=?,max_lon=?"
                + ",closed_at=? AT TIME ZONE 'UTC',num_changes=?"
                + SQL_WHERE_ID);

        pstmt.setInt(1, cs.getUserId());
        pstmt.setTimestamp(2, new java.sql.Timestamp(cs.getCreatedAt()));
        if (null != cs.getMinLatitude()) {
            pstmt.setInt(3, cs.getMinLatitude());
        } else {
            pstmt.setNull(3, java.sql.Types.INTEGER);
        }
        if (null != cs.getMaxLatitude()) {
            pstmt.setInt(4, cs.getMaxLatitude());
        } else {
            pstmt.setNull(4, java.sql.Types.INTEGER);
        }
        if (null != cs.getMinLongitude()) {
            pstmt.setInt(5, cs.getMinLongitude());
        } else {
            pstmt.setNull(5, java.sql.Types.INTEGER);
        }
        if (null != cs.getMaxLongitude()) {
            pstmt.setInt(6, cs.getMaxLongitude());
        } else {
            pstmt.setNull(6, java.sql.Types.INTEGER);
        }
        if (null != cs.getClosedAt()) {
            pstmt.setTimestamp(7, new java.sql.Timestamp(cs.getClosedAt()));
        } else {
            pstmt.setNull(7, java.sql.Types.TIMESTAMP);
        }
        pstmt.setInt(8, cs.getNumChanges());
        pstmt.setLong(9, cs.getId());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countUpdate(this);
            deleteTags(cs.getId());

            result += insertTags(cs);
        }
        return result;
    }
}
