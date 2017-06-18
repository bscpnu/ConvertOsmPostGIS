package conv.osm.postgis.model;

import java.text.ParseException;
import java.util.logging.Logger;

import org.xml.sax.Attributes;

import conv.osm.postgis.Util;
import conv.osm.postgis.dao.Counter;
import conv.osm.postgis.dao.DAO;

public class OSMChangeSet extends OSMPrimitive
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private int userId;
    private String userName;

    private Long createdAt = null;
    private Long closedAt = null;

    private Integer minLatitude;
    private Integer maxLatitude;
    private Integer minLongitude;
    private Integer maxLongitude;

    private int numChanges = 0;

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserName(String user) {
        userName = user;
    }

    public String getUserName() {
        return userName;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setClosedAt(Long closedAt) {
        this.closedAt = closedAt;
    }

    public Long getClosedAt() {
        return closedAt;
    }

    public void setMinLatitude(Integer minLatitude) {
        this.minLatitude = minLatitude;
    }

    public Integer getMinLatitude() {
        return minLatitude;
    }

    public void setMaxLatitude(Integer maxLatitude) {
        this.maxLatitude = maxLatitude;
    }

    public Integer getMaxLatitude() {
        return maxLatitude;
    }

    public void setMinLongitude(Integer minLongitude) {
        this.minLongitude = minLongitude;
    }

    public Integer getMinLongitude() {
        return minLongitude;
    }

    public void setMaxLongitude(Integer maxLongitude) {
        this.maxLongitude = maxLongitude;
    }

    public Integer getMaxLongitude() {
        return maxLongitude;
    }

    public void setNumChanges(int numChanges) {
        this.numChanges = numChanges;
    }

    public int getNumChanges() {
        return numChanges;
    }

    @Override
    public boolean hasChanged(OSMPrimitive from, DAO dao) {
        if(equals(from)) {
            Counter.countVerify(dao);
            return false;
        }
        return true;
    }

    @Override
    public boolean equals(Object obj) {
        OSMChangeSet other;
        if (obj instanceof OSMChangeSet) {
            other = (OSMChangeSet) obj;
        }
        else {
            return false;
        }
        if (getId() != other.getId()) return false;

        if ((null == getTagMap()) || getTagMap().isEmpty()) {
            if (null == other.getTagMap() || other.getTagMap().isEmpty()) {
            }
            else return false;
        }
        else {
            if (!getTagMap().equals(other.getTagMap())) return false;
        }

        if (userId != other.userId) return false;

        if (null == userName || null == other.userName) {
            // Cannot compare user name, because it's not fully available.
        }
        else {
            // Only compares the user name if it's set for both objects.
            if (!Util.equalsOrNull(userName, other.userName)) return false;
        }

        if (!Util.equalsOrNull(createdAt, other.createdAt)) return false;
        if (!Util.equalsOrNull(closedAt, other.closedAt)) return false;

        if (!Util.equalsOrNull(minLatitude, other.minLatitude)) return false;
        if (!Util.equalsOrNull(maxLatitude, other.maxLatitude)) return false;
        if (!Util.equalsOrNull(minLongitude, other.minLongitude)) return false;
        if (!Util.equalsOrNull(maxLongitude, other.maxLongitude)) return false;

        if (numChanges > 0 && other.numChanges > 0) {
            // Only compares the number of changes if it's set.
            if (numChanges != other.numChanges) return false;
        }

        return true;
    }

    @Override
    public final long getChangeSet() {
        throw new UnsupportedOperationException();
    }

    @Override
    public final int getVersion() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return "OSMChangeSet{id=" + getId() + ",userId=" + userId
                + ",userName=" + userName + ",createdAt=" + createdAt
                + ",closedAt=" + closedAt + ",minLat=" + minLatitude
                + ",maxLat=" + maxLatitude + ",minLon=" + minLongitude
                + ",maxLon=" + maxLongitude + ",numChanges=" + numChanges
                + ",tags=" + getTagMap() + "}";
    }

    @Override
    public void parseSAX(Attributes attributes) throws ParseException {
        String value;
        setId(Long.parseLong(attributes.getValue("id")));

        value = attributes.getValue("created_at");
        if (null == value) {
            setCreatedAt(null);
            logger.fine(this + " has no created_at date.");
        }
        else {
            setCreatedAt(Util.ISO8601.parse(value).getTime());
        }

        value = attributes.getValue("closed_at");
        if (null == value) {
            setClosedAt(null);
        }
        else {
            setClosedAt(Util.ISO8601.parse(value).getTime());
        }

        value = attributes.getValue("min_lat");
        if (null != value) {
            setMinLatitude(Util.intify(Double.parseDouble(value)));
        }

        value = attributes.getValue("max_lat");
        if (null != value) {
            setMaxLatitude(Util.intify(Double.parseDouble(value)));
        }

        value = attributes.getValue("min_lon");
        if (null != value) {
            setMinLongitude(Util.intify(Double.parseDouble(value)));
        }

        value = attributes.getValue("max_lon");
        if (null != value) {
            setMaxLongitude(Util.intify(Double.parseDouble(value)));
        }

        value = attributes.getValue("user");
        if (null != value) {
            setUserName(value);
        }

        value = attributes.getValue("uid");
        if (null != value) {
            setUserId(Integer.parseInt(value));
        }
    }
}
