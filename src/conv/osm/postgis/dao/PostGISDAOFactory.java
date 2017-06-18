package conv.osm.postgis.dao;

public class PostGISDAOFactory extends DAOFactory
{
    private static final PostGISDAOFactory INSTANCE;

    static {
        INSTANCE = new PostGISDAOFactory();
        new ChangeSetDAO(OutputTarget.getDefaultSchemaName());
        new NodeDAO(OutputTarget.getDefaultSchemaName());
        new RelationDAO(OutputTarget.getDefaultSchemaName());
        WayDAO.getInstance();

    }

    private PostGISDAOFactory() {
        super("PostGIS");
    }

    public static final PostGISDAOFactory getInstance() {
        return INSTANCE;
    }
}
