package Project.Server;

import java.io.*;
import java.net.Socket;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import Project.Common.ConnectionPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.PrivateMessagePayload;
import Project.Common.RoomResultsPayload;
import Project.Common.RollPayload;
import Project.Common.FlipPayload;

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
    private Set<String> mutedClients = new HashSet<>();

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

    public boolean addMutedClient(String clientName) {
        boolean added = mutedClients.add(clientName);
        if (added) {
            saveMuteList();
        }
        return added;
    }
    
    public boolean removeMutedClient(String clientName) {
        boolean removed = mutedClients.remove(clientName);
        if (removed) {
            saveMuteList();
        }
        return removed;
    }

    public boolean isClientMuted(String clientName) {
        return mutedClients.contains(clientName);
    }

    public void setClientName(String name) {
        if (name == null) {
            throw new NullPointerException("Client name can't be null");
        }
        this.clientName = name;
        loadMuteList();
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
                    if (!isClientMuted(payload.getClientName())) {
                        currentRoom.sendMessage(this, payload.getMessage());
                    } else {
                        LoggerUtil.INSTANCE.info("Message from " + getClientName() + " was skipped due to being muted.");
                    }
                    break;
                case PRIVATE_MESSAGE:
                    PrivateMessagePayload pmp = (PrivateMessagePayload) payload;
                    currentRoom.sendPrivateMessage(pmp.getTargetId(), pmp.getMessage(), this);
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
                case ROLL:
                    RollPayload rp = (RollPayload) payload;
                    currentRoom.handleRoll(this, rp);
                    break;
                case FLIP:
                    FlipPayload fp = (FlipPayload) payload;
                    currentRoom.handleFlip(this, fp);
                    break;
                case MUTE:
                    handleMuteUnmute(payload.getClientId(), true);
                    break;
                case UNMUTE:
                    handleMuteUnmute(payload.getClientId(), false);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Could not process Payload: " + payload, e);
        }
    }

    // Send methods to pass data back to the client

    private void handleMuteUnmute(long targetClientId, boolean isMute) {
        if (currentRoom != null) {
            ServerThread targetClient = currentRoom.getClientById(targetClientId);
            if (targetClient != null) {
                String targetClientName = targetClient.getClientName();
                boolean success = currentRoom.handleMuteUnmute(this, targetClientName, isMute);
                if (success) {
                    sendMessage("User " + targetClientName + (isMute ? " muted." : " unmuted."));
                    // Send notification to the target client
                    targetClient.sendMuteUnmuteNotification(this.getClientName(), isMute);
                } else {
                    sendMessage("User " + targetClientName + " not found or already in desired mute state.");
                }
            } else {
                sendMessage("User " + targetClientId + " not found.");
            }
        }
    }

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

    // yh68 7/27/24
    private void saveMuteList() {
        if (clientName == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(clientName + ".txt"))) {
            for (String mutedClient : mutedClients) {
                writer.write(mutedClient);
                writer.newLine();
            }
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error saving mute list for " + clientName, e);
        }
    }

    // yh68 7/27/24
    private void loadMuteList() {
        if (clientName == null) {
            return;
        }

        mutedClients.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(clientName + ".txt"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                mutedClients.add(line);
            }
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Error loading mute list for " + clientName, e);
        }
    }

    public boolean sendMuteUnmuteNotification(String actorName, boolean isMute) {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.MUTE_UNMUTE_NOTIFICATION);
        p.setMessage(actorName + (isMute ? " muted" : " unmuted") + " you");
        return send(p);
    }
}
