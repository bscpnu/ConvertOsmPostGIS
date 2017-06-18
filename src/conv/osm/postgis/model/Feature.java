package conv.osm.postgis.model;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.logging.Logger;

import org.postgis.Geometry;
import org.postgis.LineString;
import org.postgis.LinearRing;
import org.postgis.Polygon;

import conv.osm.postgis.core.FeatureType;

public class Feature extends Entity
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private OSMWay way = null;

    private int geometryType = FeatureType.UNKNOWN;
    private String[] symbolType = null;

    private Geometry geometry = null;

    public Feature(OSMWay way) {
        setWay(way);
    }

    public OSMWay getWay() {
        return way;
    }

    
    private void setWay(OSMWay way) {
        this.way = way;
    }

    public int getGeometryType() {
        return geometryType;
    }

   
    public String getSymbol(int i) {
        return symbolType[i];
    }

    
    public String[] getSymbolType() {
        return symbolType;
    }

    
    public void setType(FeatureType type) {
        geometryType = type.getGeometryType();
        symbolType = Arrays.copyOf(type.getSymbols(),
                type.getSymbols().length);
        // Substitute variables with their values.
        for (int i = 1; i < symbolType.length; i++) {
            if (null != symbolType[i] && symbolType[i].startsWith("$")) {
                String variable = symbolType[i].substring(1);
                if (variable.startsWith("key:")) {
                    String key = variable.substring(4);
                    if ("*".equals(key)) {
                        symbolType[i] = way.getTagMap().toString();
                    }
                    else {
                        symbolType[i] = way.getTagMap().get(key);
                    }
                }
                else {
                    // Don't substitute unknown variables.
                }
            }
        }
    }

    public Geometry getGeometry() throws SQLException, OSMDataException {
        if (null == geometry) { // lazy initialization

            switch (getGeometryType()) {
            case Geometry.POINT:
                throw new UnsupportedOperationException("Point feature");
            case Geometry.LINESTRING:
                geometry = way.getLineString();
                break;
            case Geometry.LINEARRING:               
                geometry = new LineString(way.getPointsClosed());
                break;
            case Geometry.POLYGON:
                LinearRing[] array = new LinearRing[] { way.getLinearRing() };
                geometry = new Polygon(array);
                break;
            default:
                throw new UnsupportedOperationException("Geometry type: "
                        + getGeometryType());
            }
            geometry.setSrid(SRID);
        }
        return geometry;
    }

    public String getName() {
        return getWay().getTagMap().get("name");
    }

    @Override
    public String toString() {
        return "Feature{" + Arrays.deepToString(symbolType) + ","
                + String.valueOf(way) + "}";
    }
}
