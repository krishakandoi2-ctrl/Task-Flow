package taskflow.common;

public class User {
    public enum Role { MANAGER, EMPLOYEE }
    private String username;
    private String password;
    private Role   role;

    public User(String username, String password, Role role) {
        this.username = username; this.password = password; this.role = role;
    }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role   getRole()     { return role; }

    public String toFileLine() { return username + "|" + password + "|" + role; }

    public static User fromFileLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p == null || p.length < 3) return null;
        return new User(p[0], p[1], Role.valueOf(p[2]));
    }
}
