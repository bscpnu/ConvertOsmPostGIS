package conv.osm.postgis.model;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.Attributes;

import conv.osm.postgis.Util;
import conv.osm.postgis.dao.Counter;
import conv.osm.postgis.dao.DAO;

public abstract class OSMPrimitive extends Entity
{
    private long changeset;

    
    private long time;
    private int version;

    
    private Map<String, String> tagMap = null;

    private long lineNumberEnd = -1;

    public Map<String, String> getTagMap() {
        if (null == tagMap) { // Lazy initialization.
            tagMap = new HashMap<String, String>();
        }
        return tagMap;
    }

    public long getChangeSet() {
        return changeset;
    }

    public void setChangeSet(long changeset) {
        this.changeset = changeset;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    
    public long getTime() {
        return time;
    }

    /**
     * @return
     */
    public String getTimestamp() {
        return Util.ISO8601.format(new java.util.Date(time));
    }

    
    public void setTimestamp(String timestamp) throws ParseException {
        this.time = Util.ISO8601.parse(timestamp).getTime();
    }

    
    public void setTime(long time) {
        this.time = time;
    }

    
    public final long getLineNumberEnd() {
        return lineNumberEnd;
    }

    public final void setLineNumberEnd(long lineNumber) {
        lineNumberEnd = lineNumber;
    }

    // SAX PARSER

    public void parseSAX(Attributes attributes) throws ParseException {
        setId(Long.parseLong(attributes.getValue("id")));
        setChangeSet(Long.parseLong(attributes.getValue("changeset")));
        setTimestamp(attributes.getValue("timestamp"));
        setVersion(Integer.parseInt(attributes.getValue("version")));
    }

    public void parseSAXTag(Attributes attributes) {
        final String key = attributes.getValue("k");
        final String value = attributes.getValue("v");
        getTagMap().put(key, value);
    }

    public boolean hasChanged(OSMPrimitive from, DAO dao) {
        if (getVersion() < from.getVersion()) {
            // Do not overwrite newer data.
            Counter.countIgnore(dao);
            return false;
        }
        else if (equals(from)) {
            // Replace same version data only if it has changed.
            Counter.countVerify(dao);
            return false;
        }
        else {
            return true;
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        OSMPrimitive other;
        if (obj instanceof OSMPrimitive) {
            other = (OSMPrimitive) obj;
        }
        else {
            return false;
        }
        if (changeset != other.changeset) return false;
        if (time != other.time) return false;
        if (version != other.version) return false;
        if ((null == tagMap) || tagMap.isEmpty()) {
            if (null == other.tagMap) {
                return true;
            }
            else {
                return other.tagMap.isEmpty();
            }
        }
        else {
            return tagMap.equals(other.tagMap);
        }
    }
}
