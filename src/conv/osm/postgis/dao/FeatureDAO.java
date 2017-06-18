package conv.osm.postgis.dao;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.postgis.PGgeometryLW;

import conv.osm.postgis.model.Feature;
import conv.osm.postgis.model.OSMDataException;

public class FeatureDAO extends AbstractDAO<Feature>
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    public static final int SYMBOL_LENGTH_DEFAULT = 40;

    public static final FeatureDAO getInstance(String schema,
            String relation) {
        relation = schema + "." + relation;
        DAO dao = DAOFactory.getDefaultDAOFactory().getDAO(relation);
        if (dao instanceof FeatureDAO) {
            return (FeatureDAO) dao;
        }
        else throw new IllegalStateException(
                "Wrong DAO registered for relation " + relation + ": "
                        + dao.getClass().getName());
    }

    private final String[] attributes;
    private final String[] typeNames;
    private final int[] typePrecisions;

    
    private final String attributeCSV;
    private final String attrParamCSV;
    private final String attrUpdateCSV;

    public FeatureDAO(String schema, String relation, String[] attributes,
            String[] typeNames, int[] typePrecisions) {
        super(schema, relation);
        if (null == attributes || attributes.length < 1) {
            this.attributes = new String[0];
            this.typeNames = new String[0];
            this.typePrecisions = new int[0];
            attributeCSV = "";
            attrParamCSV = "";
            attrUpdateCSV = "";
        }
        else {
            this.attributes = Arrays.copyOf(attributes, attributes.length);
            this.typeNames = Arrays.copyOf(typeNames, typeNames.length);
            this.typePrecisions = Arrays.copyOf(typePrecisions,
                    typePrecisions.length);

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < this.getAttributes().length; i++) {
                sb.append(',').append(this.getAttributes()[i]);
            }
            attributeCSV = sb.toString();

            sb.setLength(0);
            for (int i = 0; i < this.getAttributes().length; i++) {
                sb.append(",?");
            }
            attrParamCSV = sb.toString();

            sb.setLength(0);
            for (int i = 0; i < this.getAttributes().length; i++) {
                sb.append(',').append(attributes[i]).append("=?");
            }
            attrUpdateCSV = sb.toString();
        }
    }

    @Override
    public Class<Feature> getEntityClass() {
        return Feature.class;
    }

    /**
     * @return the attributes
     */
    public String[] getAttributes() {
        return attributes;
    }

    @Override
    public void createModel() throws SQLException {
        Statement stmt = target.getConnection().createStatement();
        ResultSet rs = stmt.executeQuery("SELECT tablename FROM pg_tables "
                + "WHERE schemaname='" + getSchemaName()
                + "' AND tablename='" + getRelationName() + "'");
        if (rs.next()) {
            // Table exists, so we don't need to create it.
            rs.close();
            logger.info("Target relation \"" + getFullName() + "\" exists.");
            rs = stmt.executeQuery("SELECT"
                    + " column_name,data_type,character_maximum_length"
                    + " FROM information_schema.columns"
                    + " WHERE table_catalog='" + target.getDatabaseName()
                    + "' AND" + " table_schema='" + getSchemaName()
                    + "' AND table_name='" + getRelationName() + "'");

            ArrayList<String> required = new ArrayList<String>(
                    attributes.length);
            Map<String, String> types = new HashMap<String, String>();
            Map<String, Integer> precisions = new HashMap<String, Integer>();

            for (int i = 0; i < attributes.length; i++) {
                required.add(attributes[i]);
                types.put(attributes[i], typeNames[i]);
                precisions.put(attributes[i], typePrecisions[i]);
            }

            while (rs.next()) {
                String col = rs.getString(1);
                String type = rs.getString(2);
                int maxlen = rs.getInt(3);
                if (required.remove(col)) {
                    if (types.get(col).equals(type)) {
                        final int minlen = precisions.get(col);
                        if (maxlen >= minlen) {
                            logger.finer("Attribute " + getFullName() + "."
                                    + col + " " + type + "(" + maxlen
                                    + ") exists.");
                        }
                        else {
                            throw new IllegalStateException("Relation "
                                    + getFullName() + " attribute " + col
                                    + " maximum length is only " + maxlen
                                    + " when at least " + minlen
                                    + " is required.");
                        }
                    }
                    else {
                        throw new IllegalStateException("Relation "
                                + getFullName()
                                + " has conflicting attribute " + col
                                + " of type " + type + ".");
                    }
                }
                else {
                    logger.finer("Attribute " + getFullName() + "." + col
                            + " " + type);
                }
            }
            if (required.isEmpty()) {
                logger.fine("All the required attributes for target relation \""
                        + getFullName() + "\" exist.");
            }
            else {
                for (String attribute : required) {
                    logger.warning("Adding the required attribute "
                            + attribute + " to target relation \""
                            + getFullName() + "\".");
                    String sql = "ALTER TABLE " + getFullName()
                            + " ADD COLUMN " + attribute + " "
                            + types.get(attribute) + "("
                            + precisions.get(attribute) + ")";
                    logger.finer(sql);
                    stmt.executeUpdate(sql);
                }
            }
        }
        else {
            logger.fine("Target relation \"" + getFullName()
                    + "\" needs to be created.");
            StringBuilder sb = new StringBuilder();
            sb.append("CREATE TABLE ").append(getFullName());
            sb.append(" (" + "id bigint PRIMARY KEY");
            for (int i = 0; i < getAttributes().length; i++) {
                sb.append(',').append(getAttributes()[i]);
                sb.append(" " + typeNames[i] + "(").append(
                        typePrecisions[i]).append(")");
            }
            sb.append(")");
            String sql = sb.toString();
            logger.finer(sql);
            stmt.executeUpdate(sql);
            // Add the spatial column using the PostGIS method:
            stmt.executeQuery("SELECT AddGeometryColumn('"
                    + getSchemaName() + "','" + getRelationName() + "','"
                    + SQL_GEOM_COL + "'," + SRID + ",'GEOMETRY',2)");
            // Make geometry mandatory for features:
            stmt.executeUpdate("ALTER TABLE " + getFullName() + " ALTER "
                    + SQL_GEOM_COL + " SET NOT NULL");
            logger.info("Target relation \"" + getFullName()
                    + "\" was created.");
        }
        rs.close();

        stmt.close();
    }

    private String validateSymbol(final Feature feature, final int i) {
        String symbol = feature.getSymbol(i);
        if (null == symbol) {
        }
        else {
            int precision = typePrecisions[i - 1];
            if (symbol.length() > precision) {
                logger.finest("Truncated symbol " + i + " of: " + feature);
                symbol = symbol.substring(0, precision);
            }
        }
        return symbol;
    }

    @Override
    public int create(Feature feature) throws SQLException {

        PGgeometryLW geom;
        try {
            geom = new PGgeometryLW(feature.getGeometry());
        }
        catch (OSMDataException osm) {
            logger.log(Level.FINE, osm.getMessage(), osm);
            return 0;
        }

        String sql = "INSERT INTO " + getFullName() + " (id" + attributeCSV
                + "," + SQL_GEOM_COL + ") " + "VALUES (?" + attrParamCSV
                + ",?)";

        PreparedStatement pstmt = target.cacheableStatement(sql);
        pstmt.setLong(1, feature.getWay().getId());
        int i = 1;
        while (i < feature.getSymbolType().length) {
            pstmt.setString(i + 1, validateSymbol(feature, i));
            i++;
        }
        pstmt.setObject(getAttributes().length + 2, geom);

        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countInsert(this);
        }
        return result;
    }

    @Override
    public Feature read(Long key) throws SQLException {
        throw new UnsupportedOperationException(); // TODO:
    }

    @Override
    public Iterable<Feature> readAll() {
        throw new UnsupportedOperationException(); // TODO:
    }

    @Override
    public int update(Feature feature) throws SQLException {
        PGgeometryLW geom;
        try {
            geom = new PGgeometryLW(feature.getGeometry());
        }
        catch (OSMDataException osm) {
            logger.log(Level.FINE, osm.getMessage(), osm);
            return 0;
        }

        String sql = "UPDATE " + getFullName() + " SET " + SQL_GEOM_COL
                + "=?" + attrUpdateCSV + SQL_WHERE_ID;

        PreparedStatement pstmt = target.cacheableStatement(sql);
        pstmt.setObject(1, geom);
        for (int i = 1; i < feature.getSymbolType().length; i++) {
            pstmt.setString(i + 1, validateSymbol(feature, i));
        }
        pstmt.setLong(getAttributes().length + 2, feature.getWay().getId());

        int result = pstmt.executeUpdate();

        if (result > 0) {
            Counter.countUpdate(this);
        }
        return result;
    }
}
