package conv.osm.postgis.dao;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import conv.osm.postgis.model.Entity;
import conv.osm.postgis.model.OSMPrimitive;

public abstract class DAOFactory
{
    private static final Map<String, DAOFactory> instances = new HashMap<String, DAOFactory>();

    private static String defaultName = null;

    public static String getDefaultName() {
        return defaultName;
    }

    public static void setDefaultDAOFactory(DAOFactory factory) {
        defaultName = factory.getName();
    }

    
    public static final DAOFactory getDefaultDAOFactory() {
        return instances.get(defaultName);
    }

   
    public static final DAOFactory getDAOFactory(String name) {
        return instances.get(name);
    }

    // INSTANCE

    private final String name;

    private final Map<String, DAO> relationMap = new HashMap<String, DAO>();
    private final Map<String, List<DAO>> classMap = new HashMap<String, List<DAO>>();

    protected DAOFactory(String name) {
        if (instances.containsKey(name)) {
            throw new IllegalStateException("Already instantiated \""
                    + name + "\".");
        }
        this.name = name;
        instances.put(name, this);
    }

    /**
     * Returns the name of this DAO factory implementation.
     * 
     * @return database type name.
     */
    public final String getName() {
        return name;
    }

    /**
     * Every Data Access Object must register with its factory to become
     * obtainable.
     * 
     * @param dao
     */
    public synchronized final void register(DAO dao) {
        final String schema = dao.getSchemaName();
        final String relation = dao.getRelationName();
        final String key = schema + "." + relation;
        if (relationMap.containsKey(key)) {
            throw new IllegalStateException(
                    "DAO already registered for relation \"" + key + "\".");
        }
        //relationMap.put("public.osm_nodes", dao);
        relationMap.put(key, dao);

        final String className = dao.getEntityClass().getName();
        List<DAO> list = classMap.get(className);
        if (null == list) {
            list = new LinkedList<DAO>();
            classMap.put(className, list);
        }
        
        list.add(dao);
    }

    public synchronized final <E extends Entity> DAO<E, Long>[] getRegisteredDAOs() {
        DAO<E, Long>[] array = new DAO[relationMap.size()];
        
        
        final Map<String, DAO> relationMap2 = new TreeMap<String, DAO>(relationMap);
        return relationMap2.values().toArray(array);
    }

  
    public synchronized final <E extends Entity> DAO<E, Long> getDAO(
            String name) {
        return relationMap.get(name);
    }

    public synchronized final <E extends Entity> DAO<E, Long>[] getRegisteredDAOs(
            Class<E> e) {
        List<DAO> list = classMap.get(e.getName());
        DAO<E, Long>[] array = new DAO[list.size()];
        
        return list.toArray(array);
    }

    public synchronized final <P extends OSMPrimitive> DAO<P, Long> getDAO(
            Class<P> c) {
        return classMap.get(c.getName()).get(0);
    }

    public final <P extends OSMPrimitive> String getFullName(Class<P> c) {
        return getDAO(c).getFullName();
    }
}
