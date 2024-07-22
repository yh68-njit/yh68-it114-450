package Project.Common;

/**
 * Common Player data shared between Client and Server
 */
public class User {
    public static long DEFAULT_CLIENT_ID = -1L;
    private long clientId = User.DEFAULT_CLIENT_ID;

    public long getClientId() {
        return clientId;
    }

    public void setClientId(long clientId) {
        this.clientId = clientId;
    }
}