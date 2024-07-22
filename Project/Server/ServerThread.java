package Project.Server;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import Project.Common.ConnectionPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomResultsPayload;

/**
 * A server-side representation of a single client.
 * This class is more about the data and abstracted communication
 */
public class ServerThread extends BaseServerThread {
    public static final long DEFAULT_CLIENT_ID = -1;
    private Room currentRoom;
    private long clientId;
    private String clientName;
    private Consumer<ServerThread> onInitializationComplete; // callback to inform when this object is ready
    private Set<String> mutedClients = ConcurrentHashMap.newKeySet();


    /**
     * Wraps the Socket connection and takes a Server reference and a callback
     * 
     * @param myClient
     * @param server
     * @param onInitializationComplete method to inform listener that this object is
     *                                 ready
     */
    protected ServerThread(Socket myClient, Consumer<ServerThread> onInitializationComplete) {
        Objects.requireNonNull(myClient, "Client socket cannot be null");
        Objects.requireNonNull(onInitializationComplete, "callback cannot be null");
        info("ServerThread created");
        this.client = myClient;
        this.clientId = ServerThread.DEFAULT_CLIENT_ID; // this is updated later by the server
        this.onInitializationComplete = onInitializationComplete;
    }

    public void setClientName(String name) {
        if (name == null) {
            throw new NullPointerException("Client name can't be null");
        }
        this.clientName = name;
        onInitialized();
    }

    public String getClientName() {
        return clientName;
    }

    public long getClientId() {
        return this.clientId;
    }

    protected Room getCurrentRoom() {
        return this.currentRoom;
    }

    protected void setCurrentRoom(Room room) {
        if (room == null) {
            throw new NullPointerException("Room argument can't be null");
        }
        currentRoom = room;
    }

    @Override
    protected void onInitialized() {
        onInitializationComplete.accept(this); // Notify server that initialization is complete
    }

    @Override
    protected void info(String message) {
        LoggerUtil.INSTANCE.info(String.format("ServerThread[%s(%s)]: %s", getClientName(), getClientId(), message));
    }

    @Override
    protected void cleanup() {
        currentRoom = null;
        super.cleanup();
    }

    @Override
    protected void disconnect() {
        super.disconnect();
    }

    // handle received message from the Client
    // yh68 7/5/2024
    @Override
    protected void processPayload(Payload payload) {
        try {
            switch (payload.getPayloadType()) {
                case CLIENT_CONNECT:
                    ConnectionPayload cp = (ConnectionPayload) payload;
                    setClientName(cp.getClientName());
                    break;
                    case MESSAGE:
                    info("Received message payload: " + payload.getMessage());
                    currentRoom.sendMessage(this, payload.getMessage());
                    break;
                case ROOM_CREATE:
                    currentRoom.handleCreateRoom(this, payload.getMessage());
                    break;
                case ROOM_JOIN:
                    currentRoom.handleJoinRoom(this, payload.getMessage());
                    break;
                case ROOM_LIST:
                    currentRoom.handleListRooms(this, payload.getMessage());
                    break;
                case DISCONNECT:
                    currentRoom.disconnect(this);
                    break;
<<<<<<< HEAD:Project/Server/ServerThread.java
                    // yh68 7/22/24
                case PRIVATE_MESSAGE:
                    handlePrivateMessage(payload);
                    break;
                case MUTE:
                    handleMute(payload);
                    break;
                case UNMUTE:
                    handleUnmute(payload);
=======
                case ROLL:
                    currentRoom.sendMessage(this, payload.getMessage());
>>>>>>> 038c00d28b3f646155a23cc23b069f69ddda3e59:Project/ServerThread.java
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Could not process Payload: " + payload, e);
        }
    }
// yh68 7/22/24
    private void handleMute(Payload payload) {
        long targetClientId = payload.getClientId();
        if (currentRoom != null) {
            ServerThread targetClient = currentRoom.getClientById(targetClientId);
            if (targetClient != null) {
                targetClient.addMutedClient(this); // Assuming addMutedClient method exists
                sendConfirmation("Muted " + targetClient.getClientName());
            } else {
                sendConfirmation("Client not found.");
            }
        }
    }
    
    private void handleUnmute(Payload payload) {
        long targetClientId = payload.getClientId();
        if (currentRoom != null) {
            ServerThread targetClient = currentRoom.getClientById(targetClientId);
            if (targetClient != null) {
                targetClient.removeMutedClient(this); // Assuming removeMutedClient method exists
                sendConfirmation("Unmuted " + targetClient.getClientName());
            } else {
                sendConfirmation("Client not found.");
            }
        }
    }
    
    private void sendConfirmation(String message) {
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE);
        payload.setMessage(message);
        send(payload);
    }


    public void addMutedClient(ServerThread client) {
        mutedClients.add(client.getClientName());
    }

    public void removeMutedClient(ServerThread client) {
        mutedClients.remove(client.getClientName());
    }

    public boolean isMuted(String clientName) {
        return mutedClients.contains(clientName);
    }

// yh68 7/22/24
        private void handlePrivateMessage(Payload payload) throws IOException {
        long targetClientId = payload.getClientId();
        String message = payload.getMessage();
    
        if (currentRoom != null) {
            ServerThread targetClient = currentRoom.getClientById(targetClientId);
    
            if (targetClient != null) {
                // Send the message to the target client
                targetClient.sendMessage(payload.getClientId(), message);
    
                // Send confirmation or echo message to the sender
                sendMessage("Message sent to " + targetClientId + ": " + message);
            } else {
                // Notify the sender that the target client was not found
                sendMessage("Error: Client with ID " + targetClientId + " not found.");
            }
        } else {
            // Notify the sender that they're not in a room
            sendMessage("Error: You are not in a room.");
        }
    }
    

    // Send methods to pass data back to the client
    public boolean sendRooms(List<String> rooms) {
        RoomResultsPayload rrp = new RoomResultsPayload();
        rrp.setRooms(rooms);
        return send(rrp);
    }

    public boolean sendClientSync(long clientId, String clientName) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        cp.setConnect(true);
        cp.setPayloadType(PayloadType.SYNC_CLIENT);
        return send(cp);
    }

    public boolean sendMessage(String message) {
        return sendMessage(ServerThread.DEFAULT_CLIENT_ID, message);
    }

    public boolean sendMessage(long senderId, String message) {
        Payload p = new Payload();
        p.setClientId(senderId);
        p.setMessage(message);
        p.setPayloadType(PayloadType.MESSAGE);
        return send(p);
    }

    public boolean sendRoomAction(long clientId, String clientName, String room, boolean isJoin) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.ROOM_JOIN);
        cp.setConnect(isJoin);
        cp.setMessage(room);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

<<<<<<< HEAD:Project/Server/ServerThread.java
=======
    /**
     * Tells the client information about a disconnect (similar to leaving a room)
     * 
     * @param clientId   their unique identifier
     * @param clientName their name
     * @return success of sending the payload
     */
    // yh68 6/23/2024
>>>>>>> 038c00d28b3f646155a23cc23b069f69ddda3e59:Project/ServerThread.java
    public boolean sendDisconnect(long clientId, String clientName) {
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.DISCONNECT);
        cp.setConnect(false);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }

    public boolean sendClientId(long clientId) {
        this.clientId = clientId;
        ConnectionPayload cp = new ConnectionPayload();
        cp.setPayloadType(PayloadType.CLIENT_ID);
        cp.setConnect(true);
        cp.setClientId(clientId);
        cp.setClientName(clientName);
        return send(cp);
    }
}
