package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.logging.Logger;

import conv.osm.postgis.model.OSMRelation;
import conv.osm.postgis.model.Status;
import conv.osm.postgis.model.OSMRelation.Member;

public class RelationDAO extends PrimitiveDAO<OSMRelation>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private static final String SQL_MEMBERS_TABLE = "osm_relation_members";

    RelationDAO(String schema) {
        super(schema, "osm_relations");
    }

    @Override
    public Class<OSMRelation> getEntityClass() {
        return OSMRelation.class;
    }

    @Override
    public void createModel() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT tablename "
                + "FROM pg_tables WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + getRelationName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + getFullName() + " ("
                    + SQL_PRIMITIVE_COLS + ",status smallint DEFAULT "
                    + Status.NONE + " NOT NULL" + ")");
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

        rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + SQL_MEMBERS_TABLE + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
        }
        else {
            stmt.executeUpdate("CREATE TABLE " + getSchemaName() + "."
                    + SQL_MEMBERS_TABLE + " ("
                    + "id bigint NOT NULL REFERENCES " + getFullName()
                    + "(id)," + "type char(1) NOT NULL,"
                    + "ref bigint NOT NULL,"
                    + "role varchar(255) NOT NULL,"
                    + "sequence integer DEFAULT 0 NOT NULL" + ")");
            stmt.executeUpdate("ALTER TABLE " + SQL_MEMBERS_TABLE
                    + " ADD CONSTRAINT " + SQL_MEMBERS_TABLE
                    + "_pkey PRIMARY KEY (id,type,ref,role,sequence)");
        }
        rs.close();

        stmt.close();
    }

    @Override
    public int create(OSMRelation rel) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getFullName() + " (" + SQL_PRIMITIVE_FIELDS
                + ",status) VALUES (" + SQL_PRIMITIVE_PARAMS + ",?)");
        preparePrimitiveInsert(rel, pstmt);
        pstmt.setInt(5, rel.getStatus().code());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);
            result += insertTags(rel);
            result += insertMembers(rel);
        }
        return result;
    }

    private int deleteMembers(long id) throws SQLException {
        PreparedStatement deleteMembersPstmt = target.cacheableStatement("DELETE FROM "
                + getSchemaName() + "." + SQL_MEMBERS_TABLE + SQL_WHERE_ID);
        return deleteById(id, deleteMembersPstmt);
    }

    @Override
    public int delete(Long pk) throws SQLException {
        int result = 0;
        result += deleteMembers(pk);
        result += super.delete(pk);
        return result;
    }

    @Override
    public OSMRelation read(Long key) throws SQLException {
        ResultSet rs = executeSelect(
                "SELECT changeset,timestamp AT TIME ZONE 'UTC',version FROM "
                        + getFullName() + SQL_WHERE_ID, key);
        OSMRelation relation = null;
        if (rs.next()) {
            relation = new OSMRelation();
            relation.setId(key);
            relation.setChangeSet(rs.getLong(1));
            relation.setTime(rs.getTimestamp(2).getTime());
            relation.setVersion(rs.getInt(3));

            selectTags(relation);
            selectMembers(relation);
        }
        return relation;
    }

    @Override
    public Iterable<OSMRelation> readAll() {
        throw new UnsupportedOperationException(); // TODO:
    }

    @Override
    public int update(OSMRelation rel) throws SQLException {
        final Boolean old = isOld(rel);
        if(null == old) return 0;
        if(old) return -1;

        PreparedStatement pstmt = target.cacheableStatement("UPDATE "
                + getFullName() + " SET " + SQL_PRIMITIVE_UPDATE
                + ",status=?" + SQL_WHERE_ID);
        preparePrimitiveUpdate(rel, pstmt);
        pstmt.setInt(4, rel.getStatus().code());
        pstmt.setLong(5, rel.getId());
        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countUpdate(this);
            deleteTags(rel.getId());
            deleteMembers(rel.getId());

            result += insertTags(rel);
            result += insertMembers(rel);
        }
        return result;
    }

    // JDBC DAO

    int insertMembers(OSMRelation rel) throws SQLException {
        PreparedStatement pstmt = target.cacheableStatement("INSERT INTO "
                + getSchemaName() + "." + SQL_MEMBERS_TABLE
                + " (id,type,ref,role,sequence) VALUES (?,?,?,?,?)");
        int result = 0, seq = 0;
        for (Member m : rel.getMembers()) {
            seq++;
            pstmt.setLong(1, rel.getId());
            pstmt.setString(2, m.getType().toString());
            pstmt.setLong(3, m.getRef());
            pstmt.setString(4, m.getRole());
            pstmt.setInt(5, seq);
            result += pstmt.executeUpdate();
        }
        return result;
    }

    @Override
    public final String getTagsName() {
        return "osm_relation_tags";
    }

    /**
     * 
     * @throws SQLException
     */
    protected final void selectMembers(OSMRelation relation)
            throws SQLException {
        ResultSet rs = executeSelect("SELECT type,ref,role FROM "
                + getSchemaName() + "." + SQL_MEMBERS_TABLE + SQL_WHERE_ID
                + " ORDER BY sequence", relation.getId());
        final ArrayList<OSMRelation.Member> members = relation.getMembers();
        if (members.isEmpty()) {
        }
        else {
            logger.finer("Reloading members of " + relation + ".");
            members.clear();
        }
        while (rs.next()) {
            Member member = relation.new Member();
            member.setType(rs.getString(1));
            member.setRef(rs.getLong(2));
            member.setRole(rs.getString(3));
            members.add(member);
        }
        rs.close();
    }

}
