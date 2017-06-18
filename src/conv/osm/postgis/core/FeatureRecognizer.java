package conv.osm.postgis.core;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.postgis.Geometry;

import conv.osm.postgis.Util;
import conv.osm.postgis.dao.FeatureDAO;
import conv.osm.postgis.dao.OutputTarget;
import conv.osm.postgis.dao.RouteSegmentDAO;
import conv.osm.postgis.dao.SegmentDAO;
import conv.osm.postgis.model.OSMPrimitive;

public class FeatureRecognizer
{
    private static Logger logger = Logger.getLogger("osmtopostgis");

    private ArrayList<FeatureType> featureTypes = new ArrayList<FeatureType>();

    
    private Map<String, String[]> defaultsMap = new HashMap<String, String[]>();

    private Map<String, String> topologyRelations = new HashMap<String, String>();

    public FeatureRecognizer(String url) throws IOException, JSONException {
        readJSON(url);
    }

    public FeatureType recognize(OSMPrimitive primitive) {
        return recognize(primitive.getTagMap());
    }

    private FeatureType recognize(Map<String, String> tags) {
        for (FeatureType feature : featureTypes) {
            if (feature.match(tags)) {
                return feature;
            }
        }
        return null;
    }

    private FeatureType covers(Map<String, String[]> profile) {
        // Check all the existing featureTypes for overlap.
        for (FeatureType feature : featureTypes) {
            Map<String, String[]> existing = feature.getProfile();

            HashSet<String> valuesToMatch = new HashSet<String>();

            // Check all the keys of an existing feature.
            boolean overlap = false;
            for (String key : existing.keySet()) {
                String[] existingValues = existing.get(key);
                String[] values = profile.get(key);
                if (null == values) {
                    valuesToMatch.add(null);
                }
                else {
                    for (String value : profile.get(key)) {
                        value = Util.validateNull(value);
                        if (!valuesToMatch.add(value)) {
                            throw new IllegalArgumentException(
                                    "Duplicate value=\"" + value
                                            + "\" for key=\"" + key + "\".");
                        }
                    }
                }
                /*
                 * If the existing values match all the new profile
                 * values, then this key does not help differentiating.
                 */
                for (String existingValue : existingValues) {
                    existingValue = Util.validateNull(existingValue);
                    if (null == existingValue) {
                        valuesToMatch.remove(null);
                    }
                    else if ("*".equals(existingValue)) {
                        boolean hasNull = valuesToMatch.contains(null);
                        valuesToMatch.clear();
                        if (hasNull) valuesToMatch.add(null);
                        break;
                    }
                    else {
                        valuesToMatch.remove(existingValue);
                    }
                }
                if (valuesToMatch.isEmpty()) {
                    overlap = true;
                    // Need to check if another key would help to
                    // differentiate.
                }
                else {
                    // It's confirmed a different filter.
                    overlap = false;
                    break;
                }
            }
            if (overlap) {
                return feature; // The returned filter overlaps the given
                // one.
            }
            else {
                // Need to still check the other existing featureTypes.
            }
        }
        return null; // It's a new filter.
    }

    private void readJSON(String featUrl) throws IOException, JSONException {
        StringBuilder json = new StringBuilder(4096);
        try {
            BufferedReader in = new BufferedReader(new FileReader(featUrl));
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("#")) {
                    // Skip comment lines.
                }
                else {
                    json.append(line);
                }
                // Retain line numbering even in case of comment lines.
                json.append('\n');
            }
            in.close();
        }
        catch (IOException e) {
            logger.severe("Could not read the feature specification file.");
            throw e;
        }
        try {
            JSONArray specJson = new JSONArray(json.toString());
            if (specJson.length() != 2) {
                throw new IllegalArgumentException(
                        "Invalid feature specification contains "
                                + specJson.length()
                                + " sections when 2 expected.");
            }
            readModel(specJson.getJSONObject(0));
            readFilters(specJson.getJSONArray(1));
        }
        catch (JSONException e) {
            String msg = "Could not parse the feature specification.";
            System.err.println(msg);
            logger.log(Level.SEVERE, msg, e);
            throw e;
        }
    }

    private void readModel(JSONObject modelJson) throws JSONException {
        logger.info("The feature specification defines"
                + " a target relational model with " + modelJson.length()
                + " relations.");
        Iterator<String> it = modelJson.keys();
        while (it.hasNext()) {
            String key = it.next();
            String[] names = schemaRelation(key);
            String relation = names[0] + "." + names[1];

            Object relationJson = modelJson.get(key);
            if (relationJson instanceof JSONArray) {
                // It's a feature layer.
                readFeature(relation, (JSONArray) relationJson);
            }
            else if (relationJson instanceof JSONObject) {
                // It's a topological graph.
                JSONObject topoJson = (JSONObject) relationJson;
                logger.config("Topological graph \"" + relation + "\": "
                        + topoJson);
                String type = topoJson.getString("type");
                if ("bound".equals(type)) {
                    new SegmentDAO(names[0], names[1]);
                }
                else if ("route".equals(type)) {
                    new RouteSegmentDAO(names[0], names[1]);
                }
                String name = topoJson.getString("name");
                topologyRelations.put(name, relation);
            }
        }
    }

    private void readFeature(String relation, JSONArray columnsJson)
            throws JSONException {
        logger.config("Feature layer \"" + relation + "\" attributes: "
                + columnsJson);
        /*
         * The number of attributes is one smaller than the number of
         * symbols for each feature type, because the layer table itself
         * is also a symbol.
         */
        int numAttrs = columnsJson.length();
        String[] attributes = new String[numAttrs];
        String[] typeNames = new String[numAttrs];
        int[] typePrecisions = new int[numAttrs];
        String[] defaults = new String[numAttrs];
        for (int a = 0; a < numAttrs; a++) {
            final String attrDef = columnsJson.getString(a);
            readAttribute(a, attrDef, attributes, typeNames,
                    typePrecisions, defaults);
        }
        defaultsMap.put(relation, defaults);
        String[] names = schemaRelation(relation);
        new FeatureDAO(names[0], names[1], attributes, typeNames,
                typePrecisions);
    }

    private void readAttribute(int a, String attrDef, String[] attributes,
            String[] typeNames, int[] typePrecisions, String[] defaults) {
        attrDef = attrDef.trim();

        // First, take the default value if present.
        int i = attrDef.indexOf('=');
        if (i > -1) {
            defaults[a] = Util.validateNull(attrDef.substring(i + 1).trim());
            attrDef = attrDef.substring(0, i).trim();
        }
        else {
            defaults[a] = null;
        }

        // Then, take the precision if present.
        typePrecisions[a] = FeatureDAO.SYMBOL_LENGTH_DEFAULT;
        i = attrDef.indexOf('(');
        if (i > -1) {
            String prec = attrDef.substring(i + 1);
            if (prec.endsWith(")")) {
                typePrecisions[a] = Integer.parseInt(prec.substring(0,
                        prec.length() - 1));
            }
            else {
                throw new IllegalArgumentException(
                        "Mismatch parenthesis in \"" + attrDef + "\".");
            }
            attrDef = attrDef.substring(0, i).trim();
        }

        // Attribute type, if present
        typeNames[a] = "character varying";
        i = attrDef.indexOf(' ');
        if (i > -1) {
            typeNames[a] = attrDef.substring(i).trim();
            if ("varchar".equals(typeNames[a])
                    || "char varying".equals(typeNames[a])
                    || "character varying".equals(typeNames[a])) {
                typeNames[a] = "character varying";
            }
            else {
                throw new IllegalArgumentException(
                        "Unsupported attribute type \"" + typeNames[a]
                                + "\".");
            }
            attrDef = attrDef.substring(0, i);
        }

        // Only the attribute name is remaining.
        attributes[a] = attrDef;
    }

    private String[] schemaRelation(String layer) {
        String[] names = layer.split("\\.");
        if (1 == names.length) {
            names = new String[] { OutputTarget.getDefaultSchemaName(),
                    layer };
        }
        else if (2 == names.length) {
        }
        else {
            throw new IllegalArgumentException("Unexpected layer name has "
                    + names.length + " parts: " + layer);
        }
        return names;
    }

    private void readFilters(JSONArray filtersJson) throws JSONException {
        int n = filtersJson.length();
        logger.info("The feature specification declares " + n
                + " feature types.");
        for (int i = 0; i < n; i++) {
            JSONArray filterJson = filtersJson.getJSONArray(i);
            String msg = "FeatureType " + (i + 1) + ": " + filterJson;
            logger.config(msg);

            FeatureType feature = readFilter(filterJson);
            FeatureType existing = null;

            if ((existing = covers(feature.getProfile())) != null) {
                msg = "FeatureType " + feature
                        + " is unreachable: Was recognized as " + existing;
                IllegalArgumentException ex = new IllegalArgumentException(
                        msg);
                logger.log(Level.FINE, msg, ex);
                throw ex;
            }
            featureTypes.add(feature);
        }
    }

    public FeatureType readFilter(JSONArray json) throws JSONException {
        FeatureType ft = new FeatureType();
        readProfile(json.getJSONObject(0), ft);
        readGeometryType(Util.validateNull(json.getString(1)), ft);
        readSymbols(json, ft);
        readTopologyNames(json, ft);
        return ft;
    }

    private void readProfile(JSONObject tagsJson, FeatureType ft) {
        Iterator<String> keys = tagsJson.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            // Check if the value is an array.
            JSONArray optionsJson = tagsJson.optJSONArray(key);
            String[] options;
            if (null == optionsJson) { // It's not an array.
                // It should be a string or null.
                String option = tagsJson.optString(key);
                options = new String[] { Util.validateNull(option) };
            }
            else { // It's an array.
                int n = optionsJson.length();
                options = new String[n];
                for (int i = 0; i < n; i++) {
                    // The array may contain strings and/or null.
                    String option = optionsJson.optString(i);
                    options[i] = Util.validateNull(option);
                }
            }
            ft.getProfile().put(key, options);
        }
    }

    private void readGeometryType(String geometryType, FeatureType ft)
            throws JSONException {
        if (null == geometryType) {
            ft.setGeometryType(FeatureType.UNKNOWN);
        }
        else if ("point".equals(geometryType)) {
            ft.setGeometryType(Geometry.POINT);
        }
        else if ("line".equals(geometryType)) {
            ft.setGeometryType(Geometry.LINESTRING);
        }
        else if ("ring".equals(geometryType)) {
            ft.setGeometryType(Geometry.LINEARRING);
        }
        else if ("area".equals(geometryType)) {
            ft.setGeometryType(Geometry.POLYGON);
        }
        else {
            throw new JSONException("FeatureType " + ft.getProfile()
                    + " has unknown geometry type \"" + geometryType + "\"");
        }
    }

    private void readSymbols(JSONArray json, FeatureType ft)
            throws JSONException {
        JSONArray symbolJson = json.getJSONArray(2);
        if (null == symbolJson) {
            throw new JSONException("FeatureType " + ft.getProfile()
                    + " does not have the symbol type array.");
        }
        FeatureDAO dao;
        /*
         * Be flexible with the number of symbol type definitions.
         */
        int n = symbolJson.length();
        String relation;
        if (n > 0) {
            // The maximum number of attributes depends on the layer.
            relation = Util.validateNull(symbolJson.getString(0));
            if (null == relation) {
                /*
                 * This feature type is not rendered on any layer,
                 * so any number of attributes given is okay.
                 */
                ft.initSymbols(n);
            }
            else {
                String[] names = schemaRelation(relation);
                relation = names[0] + "." + names[1];
                /*
                 * This feature type will be rendered, so the maximum number
                 * of attributes depends on the target layer.
                 */
                dao = FeatureDAO.getInstance(names[0], names[1]);
                ft.initSymbols(dao.getAttributes().length + 1);
            }
        }
        else {
            throw new JSONException("FeatureType " + json
                    + " has empty symbol type array.");
        }
        if (n > ft.getSymbols().length) {
            logger.warning("FeatureType " + json + " has " + n
                    + " symbols defined when " + ft.getSymbols().length
                    + " were expected. Extra symbols are ignored.");
            n = ft.getSymbols().length;
        }
        ft.setSymbol(0, relation);
        // All the symbol type definitions are optional.
        for (int i = 1; i < n; i++) {
            String symbol = Util.validateNull(symbolJson.optString(i));
            if ((null == symbol) && (null != relation)) {
                symbol = defaultsMap.get(relation)[i - 1];
            }
            ft.setSymbol(i, symbol);
        }
        // Set defaults for omitted attributes.
        for (int i = n; i < ft.getSymbols().length; i++) {
            ft.setSymbol(i, defaultsMap.get(relation)[i - 1]);
        }
    }

    private void readTopologyNames(JSONArray json, FeatureType ft)
            throws JSONException {
        // Read the topological graph names.
        if (json.length() > 3) {
            // There is a topological graphs specification.
            JSONArray topologyJson = json.getJSONArray(3);
            int n = topologyJson.length();
            if (n > 0) {
                ArrayList<String> relationNames = new ArrayList<String>(n);
                for (int i = 0; i < n; i++) {
                    String name = Util.validateNull(topologyJson.getString(i));
                    if (null == name) {
                        // Skip empty topological graph names.
                    }
                    else {
                        String relation = topologyRelations.get(name);
                        if (null == relation) {
                            // The given name is not a shorthand.
                            String[] names = schemaRelation(name);
                            relation = names[0] + "." + names[1];
                            if (topologyRelations.values().contains(
                                    relation)) {
                                // It's the full name.
                            }
                            else throw new JSONException(
                                    "Unknown topological graph name \""
                                            + name + "\" in " + json + ".");
                        }
                        relationNames.add(relation);
                    }
                }
                if (relationNames.size() > 0) {
                    ft.setTopologyNames(relationNames.toArray(new String[relationNames.size()]));
                }
            }
        }
    }
}
