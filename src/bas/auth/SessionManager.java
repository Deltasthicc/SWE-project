package bas.auth;

import bas.model.User;

/**
 * Singleton session manager.
 * Holds the active JWT token and decoded user information for the current session.
 * Protected operations check {@link #isAuthenticated()} and {@link #requireRole(User.Role...)}
 * before proceeding (NFR-3: authorized access only).
 */
public final class SessionManager {

    private static final SessionManager INSTANCE = new SessionManager();

    private String jwtToken;
    private String userId;
    private String name;
    private User.Role role;

    private SessionManager() {}

    public static SessionManager getInstance() { return INSTANCE; }

    // ── Login / Logout ────────────────────────────────────────────────────────

    /**
     * Establishes a session by generating and storing a JWT.
     */
    public void login(User user) {
        this.jwtToken = JWTUtil.generateToken(
            user.getUserId(), user.getName(), user.getRole().name());
        this.userId = user.getUserId();
        this.name   = user.getName();
        this.role   = user.getRole();
        System.out.println("[Session] Authenticated: " + userId + " [" + role + "]");
    }

    /**
     * Destroys the current session.
     */
    public void logout() {
        System.out.println("[Session] Logged out: " + userId);
        jwtToken = null; userId = null; name = null; role = null;
    }

    // ── Validation ────────────────────────────────────────────────────────────

    /**
     * Checks if the current JWT is valid and not expired.
     */
    public boolean isAuthenticated() {
        if (jwtToken == null) return false;
        String payload = JWTUtil.validateToken(jwtToken);
        if (payload == null) {
            System.err.println("[Session] Token expired or invalid — forcing logout.");
            logout();
            return false;
        }
        return true;
    }

    /**
     * Checks if the current user has one of the required roles.
     *
     * @param allowed roles that are permitted
     * @return true if current role is in the allowed set
     */
    public boolean hasRole(User.Role... allowed) {
        if (!isAuthenticated() || role == null) return false;
        for (User.Role r : allowed) if (role == r) return true;
        return false;
    }

    /**
     * Throws if the user does not have one of the required roles.
     */
    public void requireRole(User.Role... allowed) {
        if (!hasRole(allowed))
            throw new SecurityException(
                "Access denied. Required: " + java.util.Arrays.toString(allowed)
                + ", current: " + role);
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String    getToken()  { return jwtToken; }
    public String    getUserId() { return userId; }
    public String    getName()   { return name; }
    public User.Role getRole()   { return role; }
}
