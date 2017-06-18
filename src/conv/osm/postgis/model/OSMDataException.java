package conv.osm.postgis.model;

public class OSMDataException extends Exception
{
    
    private static final long serialVersionUID = 201002270300L;

    private Long id = null;

    public OSMDataException(String message) {
        super(message);
    }

    public OSMDataException(OSMPrimitive p, String message) {
        super(message + ": " + p);
        id = p.getId();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
