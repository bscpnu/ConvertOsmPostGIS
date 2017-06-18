package conv.osm.postgis.model;

import java.text.ParseException;

import org.postgis.Point;
import org.xml.sax.Attributes;

public class OSMNode extends OSMPrimitive
{
    // INSTANCE

    
    private double latitude;
 
    private double longitude;

    private transient Point geometry = null;

    public OSMNode() {
    }

    public OSMNode(double longitude, double latitude) {
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double lat) {
        geometry = null;
        this.latitude = lat;
    }

    private void setLatitude(String lat) {
        latitude = Double.parseDouble(lat);
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double lon) {
        geometry = null;
        this.longitude = lon;
    }

    private void setLongitude(String lon) {
        longitude = Double.parseDouble(lon);
    }

    /**
     * Returns the node location as a PostGIS point type (x = longitude,
     * y = latitude). Sets the SRID.
     * 
     * @return the node coordinates as a PGpoint object.
     */
    public Point getGeometry() {
        if (null == geometry) {
            geometry = new Point(longitude, latitude);
            geometry.setSrid(SRID);
        }
        return geometry;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        OSMNode other;
        if (obj instanceof OSMNode) {
            other = (OSMNode) obj;
        }
        else {
            return false;
        }
        if (latitude != other.latitude) return false;
        if (longitude != other.longitude) return false;
        return true;
    }

    @Override
    public String toString() {
        return "OSMNode{id=" + getId() + ",changeset=" + getChangeSet()
                + ",time=" + getTime() + ",version=" + getVersion()
                + ",lat=" + getLatitude() + ",lon=" + getLongitude() + "}";
    }

    // SAX PARSER

    @Override
    public void parseSAX(Attributes attributes) throws ParseException {
        super.parseSAX(attributes);
        setLatitude(attributes.getValue("lat"));
        setLongitude(attributes.getValue("lon"));
    }
}
