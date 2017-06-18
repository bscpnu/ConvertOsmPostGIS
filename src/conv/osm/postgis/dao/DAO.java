package conv.osm.postgis.dao;

import java.sql.SQLException;

import conv.osm.postgis.model.Entity;

public interface DAO<E extends Entity, PK>
{

    public Class<E> getEntityClass();

    public String getSchemaName();

    public String getRelationName();

    public String getFullName();

    public void createModel() throws SQLException;

    public void setTarget(OutputTarget output);

    public int create(E object) throws SQLException;

    public E read(PK key) throws SQLException;

    public int update(E object) throws SQLException;

    /**
     * 
     * @throws SQLException
     */
    public int delete(PK pk) throws SQLException;

    /**
     * 
     * @throws SQLException
     */
    public Iterable<E> readAll() throws SQLException;
}
