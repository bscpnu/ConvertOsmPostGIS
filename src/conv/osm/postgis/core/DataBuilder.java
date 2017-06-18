package conv.osm.postgis.core;

import conv.osm.postgis.model.OSMPrimitive;


public interface DataBuilder
{
    public int generate(OSMPrimitive osm);
}
