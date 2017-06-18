package conv.osm.postgis.model;

public abstract class Entity implements SpatialReference, Comparable
{
    private long id = -1;
    private Status status = Status.NONE;

    /**
     * Returns the id of this entity, or -1 if unset.
     * 
     * @return
     */
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public void setStatus(Status s) {
        status = s;
    }

    public Status getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Entity) {
            Entity other;
            other = (Entity) obj;
            return id == other.id;
        }
        else {
            return false;
        }
    }

    @Override
    public int compareTo(Object obj) {
        if (obj instanceof Entity) {
            Entity other;
            other = (Entity) obj;
            if (id == other.id) return 0;
            return (id < other.id) ? -1 : 1;
        }
        else {
            if (null == obj) {
                throw new NullPointerException();
            }
            else {
                throw new UnsupportedOperationException(
                        "Compared to an object of class "
                                + obj.getClass().getName());
            }
        }
    }

    @Override
    public int hashCode() {
        return (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id=" + id + "}";
    }
}
