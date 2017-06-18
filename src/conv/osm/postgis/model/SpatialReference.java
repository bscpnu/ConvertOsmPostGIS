package conv.osm.postgis.model;

public interface SpatialReference
{
    public static final int SRID = 4326;

    public static final String WKT_SPHEROID =
            "SPHEROID[\"WGS 84\",6378137,298.257223563]";

    public static final double SPHERICAL_EARTH_RADIUS = 6378137d;
}
