package bas.model;

public class User {
    public enum Role { CUSTOMER, CLERK, MANAGER, OWNER }

    private String userId;
    private String name;
    private String passwordHash;
    private Role   role;

    public User(String userId, String name, String passwordHash, Role role) {
        this.userId = userId; this.name = name;
        this.passwordHash = passwordHash; this.role = role;
    }

    public String getUserId()      { return userId; }
    public String getName()        { return name; }
    public String getPasswordHash(){ return passwordHash; }
    public Role   getRole()        { return role; }

    public void setUserId(String v)      { userId = v; }
    public void setName(String v)        { name = v; }
    public void setPasswordHash(String v){ passwordHash = v; }
    public void setRole(Role v)          { role = v; }
}
