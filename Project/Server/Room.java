package Project.Server;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Project.Common.LoggerUtil;

public class Room implements AutoCloseable {
    private String name; // unique name of the Room
    protected volatile boolean isRunning = false;
    private ConcurrentHashMap<Long, ServerThread> clientsInRoom = new ConcurrentHashMap<>();

    public final static String LOBBY = "lobby";

    private void info(String message) {
        LoggerUtil.INSTANCE.info(String.format("Room[%s]: %s", name, message));
    }

    public Room(String name) {
        this.name = name;
        isRunning = true;
        info("created");
    }

    public String getName() {
        return this.name;
    }

    protected synchronized void addClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        if (clientsInRoom.containsKey(client.getClientId())) {
            info("Attempting to add a client that already exists in the room");
            return;
        }
        clientsInRoom.put(client.getClientId(), client);
        client.setCurrentRoom(this);

        // notify clients of someone joining
        sendRoomStatus(client.getClientId(), client.getClientName(), true);
        // sync room state to joiner
        syncRoomList(client);

        info(String.format("%s[%s] joined the Room[%s]", client.getClientName(), client.getClientId(), getName()));
    }

    protected synchronized void removedClient(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        // notify remaining clients of someone leaving
        // happen before removal so leaving client gets the data
        sendRoomStatus(client.getClientId(), client.getClientName(), false);
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());

        info(String.format("%s[%s] left the room", client.getClientName(), client.getClientId(), getName()));

        autoCleanup();
    }

    protected synchronized void disconnect(ServerThread client) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }
        long id = client.getClientId();
        sendDisconnect(client);
        client.disconnect();
        clientsInRoom.remove(client.getClientId());
        LoggerUtil.INSTANCE.fine("Clients remaining in Room: " + clientsInRoom.size());

        // Improved logging with user data
        info(String.format("%s[%s] disconnected", client.getClientName(), id));
        autoCleanup();
    }

    protected synchronized void disconnectAll() {
        info("Disconnect All triggered");
        if (!isRunning) {
            return;
        }
        clientsInRoom.values().removeIf(client -> {
            disconnect(client);
            return true;
        });
        info("Disconnect All finished");
        autoCleanup();
    }

    private void autoCleanup() {
        if (!Room.LOBBY.equalsIgnoreCase(name) && clientsInRoom.isEmpty()) {
            close();
        }
    }

    public void close() {
        if (!clientsInRoom.isEmpty()) {
            sendMessage(null, "Room is shutting down, migrating to lobby");
            info(String.format("migrating %s clients", clientsInRoom.size()));
            clientsInRoom.values().removeIf(client -> {
                Server.INSTANCE.joinRoom(Room.LOBBY, client);
                return true;
            });
        }
        Server.INSTANCE.removeRoom(this);
        isRunning = false;
        clientsInRoom.clear();
        info("closed");
    }

    protected synchronized void sendDisconnect(ServerThread client) {
        info(String.format("sending disconnect status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(clientInRoom -> {
            boolean failedToSend = !clientInRoom.sendDisconnect(client.getClientId(), client.getClientName());
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    protected synchronized void syncRoomList(ServerThread client) {
        clientsInRoom.values().forEach(clientInRoom -> {
            if (clientInRoom.getClientId() != client.getClientId()) {
                client.sendClientSync(clientInRoom.getClientId(), clientInRoom.getClientName());
            }
        });
    }

    protected synchronized void sendRoomStatus(long clientId, String clientName, boolean isConnect) {
        info(String.format("sending room status to %s recipients", clientsInRoom.size()));
        clientsInRoom.values().removeIf(client -> {
            boolean failedToSend = !client.sendRoomAction(clientId, clientName, getName(), isConnect);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    protected synchronized void sendMessage(ServerThread sender, String message) {
        if (!isRunning) { // block action if Room isn't running
            return;
        }

        long senderId = sender == null ? ServerThread.DEFAULT_CLIENT_ID : sender.getClientId();

        final String formattedMessage = processMessageFormat(message);

        info(String.format("sending message to %s recipients", getName()));
        clientsInRoom.values().removeIf(client -> {
            if (sender != null && sender.isMuted(client.getClientName())) {
                // yh68 7/22/24
                LoggerUtil.INSTANCE.info("Skipped sending message to muted client: " + client.getClientName());
                return true;
            }
            boolean failedToSend = !client.sendMessage(senderId, formattedMessage);
            if (failedToSend) {
                info(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    public ServerThread getClientById(long clientId) {
        return clientsInRoom.get(clientId);
    }

    private String processMessageFormat(String message) {
        String boldPattern = "\\*\\*(.*?)\\*\\*";
        String italicPattern = "\\*(.*?)\\*";
        String underlinePattern = "_(.*?)_";
        String colorPattern = "#(r|g|b|[0-9a-fA-F]{6}) (.*?) \\1#";

        message = message.replaceAll(boldPattern, "<b>$1</b>");
        message = message.replaceAll(italicPattern, "<i>$1</i>");
        message = message.replaceAll(underlinePattern, "<u>$1</u>");

        Pattern pattern = Pattern.compile(colorPattern);
        Matcher matcher = pattern.matcher(message);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String colorCode = matcher.group(1);
            String coloredText = matcher.group(2);
            String colorTag;
            switch (colorCode.toLowerCase()) {
                case "r":
                    colorTag = "red";
                    break;
                case "g":
                    colorTag = "green";
                    break;
                case "b":
                    colorTag = "blue";
                    break;
                default:
                    colorTag = "#" + colorCode;
                    break;
            }
            matcher.appendReplacement(sb, "<span style=\"color:" + colorTag + "\">" + coloredText + "</span>");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    protected void handleCreateRoom(ServerThread sender, String room) {
        if (Server.INSTANCE.createRoom(room)) {
            Server.INSTANCE.joinRoom(room, sender);
        } else {
            sender.sendMessage(String.format("Room %s already exists", room));
        }
    }

    protected void handleJoinRoom(ServerThread sender, String room) {
        if (!Server.INSTANCE.joinRoom(room, sender)) {
            sender.sendMessage(String.format("Room %s doesn't exist", room));
        }
    }

    protected void handleListRooms(ServerThread sender, String roomQuery) {
        sender.sendRooms(Server.INSTANCE.listRooms(roomQuery));
    }

    protected void clientDisconnect(ServerThread sender) {
        disconnect(sender);
    }
}
