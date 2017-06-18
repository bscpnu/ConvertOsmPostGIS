package conv.osm.postgis.model;

import java.util.ArrayList;

import org.xml.sax.Attributes;


public class OSMRelation extends OSMPrimitive
{
    public class Member
    {
        private OSMDataType type;
        private long ref;
        private String role;

        public OSMDataType getType() {
            return type;
        }

        public void setType(OSMDataType type) {
            this.type = type;
        }

        public void setType(String type) {
            this.type = OSMDataType.parse(type);
        }

        public long getRef() {
            return ref;
        }

        public void setRef(long ref) {
            this.ref = ref;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        @Override
        public boolean equals(Object obj) {
            Member other;
            if (obj instanceof Member) {
                other = (Member) obj;
            }
            else {
                return false;
            }
            if (null == role) {
                if (null != other.role) return false;
            }
            else {
                if (!role.equals(other.role)) return false;
            }
            return type.equals(other.type) && (ref == other.ref);
        }

        @Override
        public String toString() {
            return "Member{type=" + getType() + ",ref=" + getRef()
                    + ",role=" + getRole() + "}";
        }
    }

    // INSTANCE

    private final ArrayList<Member> members = new ArrayList<Member>();

    public ArrayList<Member> getMembers() {
        return members;
    }

    @Override
    public boolean equals(Object obj) {
        if (!super.equals(obj)) return false;
        OSMRelation other;
        if (obj instanceof OSMRelation) {
            other = (OSMRelation) obj;
        }
        else {
            return false;
        }
        return members.equals(other.members);
    }

    @Override
    public String toString() {
        return "OSMRelation{id=" + getId() + ",changeset=" + getChangeSet()
                + ",time=" + getTime() + ",version=" + getVersion()
                + ",members=" + getMembers().toString() + "}";
    }

    // SAX PARSER

    public void parseMember(Attributes attributes) {
        Member m = new Member();
        m.setType(attributes.getValue("type"));
        m.setRef(Long.parseLong(attributes.getValue("ref")));
        m.setRole(attributes.getValue("role"));
        getMembers().add(m);
    }
}
