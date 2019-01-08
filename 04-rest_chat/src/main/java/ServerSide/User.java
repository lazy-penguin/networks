package ServerSide;

import java.util.UUID;

class User {
    private final String name;
    private final long lastActivity;
    private final String token;
    private static int LAST_ID = 0;
    private int id;
    private boolean status; //1 - online, 0 - offline

    User(String newName) {
        name = newName;
        lastActivity = System.currentTimeMillis();
        status = true;

        token = UUID.randomUUID().toString();
        LAST_ID++;
        id = LAST_ID;
    }

    long getLastActivity() {
        return lastActivity;
    }

    String getToken() {
        return token;
    }
    int getId() {
        return id;
    }
    String getName() {
        return name;
    }
    boolean getStatus() {
        return status;
    }
    void setStatus(boolean newStatus) {
        status = newStatus;
    }
}
