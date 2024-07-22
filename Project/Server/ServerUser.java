package Project.Server;

import java.util.List;

import Project.Common.User;

/**
 * Server-only data about a player
 * Added in ReadyCheck lesson/branch for non-chatroom projects.
 * If chatroom projects want to follow this design update the following in this
 * lesson:
 * Player class renamed to User
 * clientPlayer class renamed to ClientUser (or the original ClientData)
 * ServerPlayer class renamed to ServerUser
 */
public class ServerUser extends User {
    private ServerThread client; // reference to wrapped ServerThread

    public ServerUser(ServerThread clientToWrap) {
        client = clientToWrap;
        setClientId(client.getClientId());
    }

    /**
     * Used only for passing the ServerThread to the base class of Room.
     * Favor creating wrapper methods instead of interacting with this directly.
     * 
     * @return ServerThread reference
     */
    public ServerThread getServerThread() {
        return client;
    }

    public String getClientName() {
        return client.getClientName();
    }

    // Wrapper methods to interact with ServerThread
    public boolean sendMessage(String message) {
        return client.sendMessage(message);
    }

    public boolean sendMessage(long senderId, String message) {
        return client.sendMessage(senderId, message);
    }

    public boolean sendRooms(List<String> rooms) {
        return client.sendRooms(rooms);
    }

    public boolean sendClientSync(long clientId, String clientName) {
        return client.sendClientSync(clientId, clientName);
    }

    public boolean sendRoomAction(long clientId, String clientName, String room, boolean isJoin) {
        return client.sendRoomAction(clientId, clientName, room, isJoin);
    }

    public boolean sendDisconnect(long clientId, String clientName) {
        return client.sendDisconnect(clientId, clientName);
    }

    public boolean sendClientId(long clientId) {
        return client.sendClientId(clientId);
    }
}
