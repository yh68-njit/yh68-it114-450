package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import Project.Client.Interfaces.IConnectionEvents;
import Project.Client.Interfaces.IClientEvents;
import Project.Client.Interfaces.IMessageEvents;
import Project.Client.Interfaces.IRoomEvents;
import Project.Client.Views.ChatPanel;
import Project.Common.ConnectionPayload;
import Project.Common.Constants;
import Project.Common.FlipPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.PrivateMessagePayload;
import Project.Common.RoomResultsPayload;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import Project.Common.RollPayload;
import Project.Server.Room;

/**
 * Demoing bi-directional communication between client and server in a
 * multi-client scenario
 */
public enum Client {
    INSTANCE;

    {
        // TODO moved to ClientUI (this repeat doesn't do anything since config is set
        // only once)

        // statically initialize the client-side LoggerUtil
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // 2MB
        config.setFileCount(1);
        config.setLogLocation("client.log");
        // Set the logger configuration
        LoggerUtil.INSTANCE.setConfig(config);
    }
    private Socket server = null;
    private ObjectOutputStream out = null;
    private ObjectInputStream in = null;
    final Pattern ipAddressPattern = Pattern
            .compile("/connect\\s+(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}:\\d{3,5})");
    final Pattern localhostPattern = Pattern.compile("/connect\\s+(localhost:\\d{3,5})");
    private volatile boolean isRunning = true; // volatile for thread-safe visibility
    private ConcurrentHashMap<Long, ClientData> knownClients = new ConcurrentHashMap<>();
    private ClientData myData;

    // constants (used to reduce potential types when using them in code)
    private final String COMMAND_CHARACTER = "/";
    private final String CREATE_ROOM = "createroom";
    private final String JOIN_ROOM = "joinroom";
    private final String LIST_ROOMS = "listrooms";
    private final String DISCONNECT = "disconnect";
    private final String LOGOFF = "logoff";
    private final String LOGOUT = "logout";
    private final String SINGLE_SPACE = " ";
    private final String FLIP = "flip";
    private final String ROLL = "roll";


    // callback that updates the UI
    private static List<IClientEvents> events = new ArrayList<IClientEvents>();

    public void addCallback(IClientEvents e) {
        events.add(e);
    }

    // needs to be private now that the enum logic is handling this
    private Client() {
        LoggerUtil.INSTANCE.info("Client Created");
        myData = new ClientData();
    }

    public boolean isConnected() {
        if (server == null) {
            return false;
        }
        // https://stackoverflow.com/a/10241044
        // Note: these check the client's end of the socket connect; therefore they
        // don't really help determine if the server had a problem
        // and is just for lesson's sake
        return server.isConnected() && !server.isClosed() && !server.isInputShutdown() && !server.isOutputShutdown();
    }

    /**
     * Takes an IP address and a port to attempt a socket connection to a server.
     * 
     * @param address
     * @param port
     * @return true if connection was successful
     */
    @Deprecated
    private boolean connect(String address, int port) {
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            // Use CompletableFuture to run listenToServer() in a separate thread
            CompletableFuture.runAsync(this::listenToServer);
        } catch (UnknownHostException e) {
            LoggerUtil.INSTANCE.warning("Unknown host", e);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("IOException", e);
        }
        return isConnected();
    }

    /**
     * Takes an ip address and a port to attempt a socket connection to a server.
     * 
     * @param address
     * @param port
     * @param username
     * @param callback (for triggering UI events)
     * @return true if connection was successful
     */
    public boolean connect(String address, int port, String username, IClientEvents callback) {
        myData.setClientName(username);
        addCallback(callback);
        try {
            server = new Socket(address, port);
            // channel to send to server
            out = new ObjectOutputStream(server.getOutputStream());
            // channel to listen to server
            in = new ObjectInputStream(server.getInputStream());
            LoggerUtil.INSTANCE.info("Client connected");
            // Use CompletableFuture to run listenToServer() in a separate thread
            CompletableFuture.runAsync(this::listenToServer);
            sendClientName();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return isConnected();
    }

    /**
     * <p>
     * Check if the string contains the <i>connect</i> command
     * followed by an IP address and port or localhost and port.
     * </p>
     * <p>
     * Example format: 123.123.123.123:3000
     * </p>
     * <p>
     * Example format: localhost:3000
     * </p>
     * https://www.w3schools.com/java/java_regex.asp
     * 
     * @param text
     * @return true if the text is a valid connection command
     */
    private boolean isConnection(String text) {
        Matcher ipMatcher = ipAddressPattern.matcher(text);
        Matcher localhostMatcher = localhostPattern.matcher(text);
        return ipMatcher.matches() || localhostMatcher.matches();
    }

    /**
     * Controller for handling various text commands.
     * <p>
     * Add more here as needed
     * </p>
     * 
     * @param text
     * @return true if the text was a command or triggered a command
     * @throws IOException
     */
    private boolean processClientCommand(String text) throws IOException {
        if (isConnection(text)) {
            if (myData.getClientName() == null || myData.getClientName().isEmpty()) {
                System.out.println(TextFX.colorize("Name must be set first via /name command", Color.RED));
                return true;
            }
            // Parse connection details
            String[] parts = text.trim().replaceAll(" +", " ").split(" ")[1].split(":");
            connect(parts[0].trim(), Integer.parseInt(parts[1].trim()));
            sendClientName();
            return true;
        }
        
        if (text.startsWith("@")) {
            int spaceIndex = text.indexOf(' ');
            if (spaceIndex != -1) {
                String targetName = text.substring(1, spaceIndex);
                String privateMessage = text.substring(spaceIndex + 1);
                Long targetId = findClientIdByName(targetName);
                
                if (targetId != null) {
                    sendPrivateMessage(targetId, privateMessage);
                } else {
                    System.out.println("User not found: " + targetName);
                }
                return true;
            }
        }

        if (text.startsWith("/mute ")) {
            String targetName = text.substring(6);
            long targetClientId = findClientIdByName(targetName);
            if (targetClientId == -1L) {
                System.out.println("User " + targetName + " not found.");
            } else {
                sendMuteUnmutePayload(targetClientId, true);
            }
        } else if (text.startsWith("/unmute ")) {
            String targetName = text.substring(8);
            long targetClientId = findClientIdByName(targetName);
            if (targetClientId == -1L) {
                System.out.println("User " + targetName + " not found.");
            } else {
                sendMuteUnmutePayload(targetClientId, false);
            }

        } else if ("/quit".equalsIgnoreCase(text)) {
            close();
            return true;
        } else if (text.startsWith("/name")) {
            myData.setClientName(text.replace("/name", "").trim());
            System.out.println(TextFX.colorize("Set client name to " + myData.getClientName(), Color.CYAN));
            return true;
        } else if (text.equalsIgnoreCase("/users")) {
            System.out.println(String.join("\n", knownClients.values().stream().map(c -> String.format("%s(%s)", c.getClientName(), c.getClientId())).toList()));
            return true;
        } else if (text.startsWith(COMMAND_CHARACTER)) {
            // Handle commands prefixed with COMMAND_CHARACTER
            String fullCommand = text.replace(COMMAND_CHARACTER, "");
            String[] commandParts = fullCommand.split(SINGLE_SPACE, 2);
            String command = commandParts[0];
            String commandValue = commandParts.length >= 2 ? commandParts[1] : "";

            switch (command) {
                case CREATE_ROOM:
                    sendCreateRoom(commandValue);
                    break;
                case JOIN_ROOM:
                    sendJoinRoom(commandValue);
                    break;
                case LIST_ROOMS:
                    sendListRooms(commandValue);
                    break;
                case ROLL:
                    sendRoll(commandValue);
                    return true;  // Add this line
                case FLIP:
                    sendFlip();
                    break;
                case DISCONNECT:
                case LOGOFF:
                case LOGOUT:
                    sendDisconnect();
                    break;
                default:
                    // Handle unknown commands if necessary
                    break;
            }
            return true;
        }
        return false;
    }

// send methods to pass data to the ServerThread

    private void sendRoll(String rollCommand) throws IOException {
        RollPayload rp;
        if (rollCommand.contains("d")) {
            String[] rollParts = rollCommand.split("d");
            int numberOfRolls = Integer.parseInt(rollParts[0]);
            int diceSides = Integer.parseInt(rollParts[1]);
            rp = new RollPayload(numberOfRolls, diceSides);
        } else {
            int diceSides = Integer.parseInt(rollCommand);
            rp = new RollPayload(1, diceSides);
        }
        send(rp);
    }

    private void sendFlip() throws IOException {
        FlipPayload fp = new FlipPayload();
        send(fp);
    }

    public long getMyClientId() {
        return myData.getClientId();
    }

    private Long findClientIdByName(String targetName) {
        // Assuming there is a method or map to get client ID by name
        for (ClientData cd : knownClients.values()) {
            if (cd.getClientName().equals(targetName)) {
                return cd.getClientId();
            }
        }
        return null;
    }

        // send methods to pass data to the ServerThread

    private void sendMuteUnmutePayload(long targetClientId, boolean isMute) throws IOException {
        Payload payload = new Payload();
        payload.setPayloadType(isMute ? PayloadType.MUTE : PayloadType.UNMUTE);
        payload.setClientId(targetClientId);
        send(payload);
    }

    private void sendPrivateMessage(Long targetId, String message) throws IOException {
        out.writeObject(new PrivateMessagePayload(targetId, message));
        out.flush();
    }

    /**
     * Sends a search to the server-side to get a list of potentially matching Rooms
     * 
     * @param roomQuery optional partial match search String
     * @throws IOException
     */
    public void sendListRooms(String roomQuery) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_LIST);
        p.setMessage(roomQuery);
        send(p);
    }

    /**
     * Sends the room name we intend to create
     * 
     * @param room
     * @throws IOException
     */
    public void sendCreateRoom(String room) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_CREATE);
        p.setMessage(room);
        send(p);
    }

    /**
     * Sends the room name we intend to join
     * 
     * @param room
     * @throws IOException
     */
    public void sendJoinRoom(String room) throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROOM_JOIN);
        p.setMessage(room);
        send(p);
    }

    /**
     * Tells the server-side we want to disconnect
     * 
     * @throws IOException
     */
    void sendDisconnect() throws IOException {
        Payload p = new Payload();
        p.setPayloadType(PayloadType.DISCONNECT);
        send(p);
    }

    /**
     * Sends desired message over the socket
     * 
     * @param message
     * @throws IOException
     */
    public void sendMessage(String message) throws IOException {
        if (processClientCommand(message)) {
            return; // If it's a command, we've handled it, so don't send as a regular message
        }
        // Initialize the Payload object
        Payload payload = new Payload();
        payload.setPayloadType(PayloadType.MESSAGE); // Make sure PayloadType is correctly set
        payload.setClientId(this.getMyClientId()); // Ensure clientId is properly set
        payload.setMessage(message);
    
        // Log the payload for debugging
        LoggerUtil.INSTANCE.info("Payload created: " + payload.toString());
    
        // Send the Payload object
        out.writeObject(payload);
        out.flush();
    }

    /**
     * Sends chosen client name after socket handshake
     * 
     * @throws IOException
     */
    private void sendClientName() throws IOException {
        if (myData.getClientName() == null || myData.getClientName().length() == 0) {
            System.out.println(TextFX.colorize("Name must be set first via /name command", Color.RED));
            return;
        }
        ConnectionPayload cp = new ConnectionPayload();
        cp.setClientName(myData.getClientName());
        send(cp);
    }

    /**
     * Generic send that passes any Payload over the socket (to ServerThread)
     * 
     * @param p
     * @throws IOException
     */
    private void send(Payload p) throws IOException {
        try {
            out.writeObject(p);
            out.flush();
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Socket send exception", e);
            throw e;
        }

    }
    // end send methods

    public void start() throws IOException {
        LoggerUtil.INSTANCE.info("Client starting");

        // Use CompletableFuture to run listenToInput() in a separate thread
        CompletableFuture<Void> inputFuture = CompletableFuture.runAsync(this::listenToInput);

        // Wait for inputFuture to complete to ensure proper termination
        inputFuture.join();
    }

    /**
     * Listens for messages from the server
     */
    private void listenToServer() {
        try {
            while (isRunning && isConnected()) {
                Payload fromServer = (Payload) in.readObject(); // blocking read
                if (fromServer != null) {
                    // System.out.println(fromServer);
                    processPayload(fromServer);
                } else {
                    LoggerUtil.INSTANCE.info("Server disconnected");
                    break;
                }
            }
        } catch (ClassCastException | ClassNotFoundException cce) {
            LoggerUtil.INSTANCE.severe("Error reading object as specified type: ", cce);
        } catch (IOException e) {
            if (isRunning) {
                LoggerUtil.INSTANCE.info("Connection dropped", e);
            }
        } finally {
            closeServerConnection();
        }
        LoggerUtil.INSTANCE.info("listenToServer thread stopped");
    }

    /**
     * Listens for keyboard input from the user
     */
    @Deprecated
    private void listenToInput() {
        try (Scanner si = new Scanner(System.in)) {
            System.out.println("Waiting for input"); // moved here to avoid console spam
            while (isRunning) { // Run until isRunning is false
                String line = si.nextLine();
                LoggerUtil.INSTANCE.severe(
                        "You shouldn't be using terminal input for Milestone 3. Interaction should be done through the UI");
                if (!processClientCommand(line)) {
                    if (isConnected()) {
                        sendMessage(line);
                    } else {
                        System.out.println(
                                "Not connected to server (hint: type `/connect host:port` without the quotes and replace host/port with the necessary info)");
                    }
                }
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Error in listentToInput()", e);
        }
        LoggerUtil.INSTANCE.info("listenToInput thread stopped");
    }

    /**
     * Closes the client connection and associated resources
     */
    private void close() {
        isRunning = false;
        closeServerConnection();
        LoggerUtil.INSTANCE.info("Client terminated");
        // System.exit(0); // Terminate the application
    }

    /**
     * Closes the server connection and associated resources
     */
    private void closeServerConnection() {
        myData.reset();
        knownClients.clear();
        try {
            if (out != null) {
                LoggerUtil.INSTANCE.info("Closing output stream");
                out.close();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.info("Error closing output stream", e);
        }
        try {
            if (in != null) {
                LoggerUtil.INSTANCE.info("Closing input stream");
                in.close();
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.info("Error closing input stream", e);
        }
        try {
            if (server != null) {
                LoggerUtil.INSTANCE.info("Closing connection");
                server.close();
                LoggerUtil.INSTANCE.info("Closed socket");
            }
        } catch (IOException e) {
            LoggerUtil.INSTANCE.info("Error closing socket", e);
        }
    }

    public static void main(String[] args) {
        Client client = Client.INSTANCE;
        try {
            client.start();
        } catch (IOException e) {
            LoggerUtil.INSTANCE.info("Exception from main()", e);
        }
    }

    /**
     * Handles received message from the ServerThread
     * 
     * @param payload
     */
    private void processPayload(Payload payload) {
        try {
            LoggerUtil.INSTANCE.info("Received Payload: " + payload);
            switch (payload.getPayloadType()) {
                case PayloadType.CLIENT_ID: // get id assigned
                    ConnectionPayload cp = (ConnectionPayload) payload;
                    processClientData(cp.getClientId(), cp.getClientName());
                    break;
                case PayloadType.SYNC_CLIENT: // silent add
                    cp = (ConnectionPayload) payload;
                    processClientSync(cp.getClientId(), cp.getClientName());
                    break;
                case PayloadType.DISCONNECT: // remove a disconnected client (mostly for the specific message vs leaving
                                             // a room)
                    cp = (ConnectionPayload) payload;
                    processDisconnect(cp.getClientId(), cp.getClientName());
                    // note: we want this to cascade
                case PayloadType.ROOM_JOIN: // add/remove client info from known clients
                    cp = (ConnectionPayload) payload;
                    processRoomAction(cp.getClientId(), cp.getClientName(), cp.getMessage(), cp.isConnect());
                    break;
                case PayloadType.ROOM_LIST:
                    RoomResultsPayload rrp = (RoomResultsPayload) payload;
                    processRoomsList(rrp.getRooms(), rrp.getMessage());
                    break;
                case PayloadType.MESSAGE: // displays a received message
                    processMessage(payload.getClientId(), payload.getMessage());
                    break;
                case PayloadType.MUTE_UNMUTE_NOTIFICATION:
                    processMessage(payload.getClientId(), payload.getMessage());
                    break;
                case PayloadType.MUTE:
                    processMuteUnmute(payload.getClientId(), true);
                    break;
                case PayloadType.UNMUTE:
                    processMuteUnmute(payload.getClientId(), false);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            LoggerUtil.INSTANCE.severe("Could not process Payload: " + payload, e);
        }
    }

    /**
     * Returns the ClientName of a specific Client by ID.
     * 
     * @param id
     * @return the name, or Room if id is -1, or [Unknown] if failed to find
     */
    public String getClientNameFromId(long id) {
        if (id == ClientData.DEFAULT_CLIENT_ID) {
            return "Room";
        }
        if (knownClients.containsKey(id)) {
            return knownClients.get(id).getClientName();
        }
        return "[Unknown]";
    }

    // payload processors

    private void processMuteUnmute(long clientId, boolean isMuted) {
        // Update the UI
        events.forEach(event -> {
            if (event instanceof ChatPanel) {
                ((ChatPanel) event).updateUserMuteStatus(clientId, isMuted);
            }
            if (event instanceof IConnectionEvents) {
                ((IConnectionEvents) event).onUserMuteStatusChanged(clientId, isMuted);
            }
        });
        // Log the mute/unmute action
        String action = isMuted ? "muted" : "unmuted";
        String message = String.format("User %s has been %s", getClientNameFromId(clientId), action);
        processMessage(ClientData.DEFAULT_CLIENT_ID, message);
    }

    private void processRoomsList(List<String> rooms, String message) {
        // invoke onReceiveRoomList callback
        events.forEach(event -> {
            if (event instanceof IRoomEvents) {
                ((IRoomEvents) event).onReceiveRoomList(rooms, message);
            }
        });

        if (rooms == null || rooms.size() == 0) {
            System.out.println(
                    TextFX.colorize("No rooms found matching your query",
                            Color.RED));
            return;
        }
        System.out.println(TextFX.colorize("Room Results:", Color.PURPLE));
        System.out.println(
                String.join("\n", rooms));

    }

    private void processDisconnect(long clientId, String clientName) {
        // invoke onClientDisconnect callback
        events.forEach(event -> {
            if (event instanceof IConnectionEvents) {
                ((IConnectionEvents) event).onClientDisconnect(clientId, clientName);
            }
        });
        System.out.println(
                TextFX.colorize(String.format("*%s disconnected*",
                        clientId == myData.getClientId() ? "You" : clientName),
                        Color.RED));
        if (clientId == myData.getClientId()) {
            closeServerConnection();
        }
    }

    private void processClientData(long clientId, String clientName) {
        if (myData.getClientId() == ClientData.DEFAULT_CLIENT_ID) {
            myData.setClientId(clientId);
            myData.setClientName(clientName);
            // invoke onReceiveClientId callback
            events.forEach(event -> {
                if (event instanceof IConnectionEvents) {
                    ((IConnectionEvents) event).onReceiveClientId(clientId);
                }
            });
            // knownClients.put(cp.getClientId(), myData);// <-- this is handled later
        }
    }
    
    private void processMessage(long clientId, String message) {
        System.out.println(TextFX.colorize(message, Color.BLUE));
        // invoke onMessageReceive callback
        events.forEach(event -> {
            if (event instanceof IMessageEvents) {
                ((IMessageEvents) event).onMessageReceive(clientId, message);
            }
        });
    }

    private void processClientSync(long clientId, String clientName) {

        if (!knownClients.containsKey(clientId)) {
            ClientData cd = new ClientData();
            cd.setClientId(clientId);
            cd.setClientName(clientName);
            knownClients.put(clientId, cd);
            // invoke onSyncClient callback
            events.forEach(event -> {
                if (event instanceof IConnectionEvents) {
                    ((IConnectionEvents) event).onSyncClient(clientId, clientName);
                }
            });
        }
    }

    private void processRoomAction(long clientId, String clientName, String message, boolean isJoin) {

        if (isJoin && !knownClients.containsKey(clientId)) {
            ClientData cd = new ClientData();
            cd.setClientId(clientId);
            cd.setClientName(clientName);
            knownClients.put(clientId, cd);
            System.out.println(TextFX
                    .colorize(String.format("*%s[%s] joined the Room %s*", clientName, clientId, message),
                            Color.GREEN));
            // invoke onRoomJoin callback
            events.forEach(event -> {
                if (event instanceof IRoomEvents) {
                    ((IRoomEvents) event).onRoomAction(clientId, clientName, message, isJoin);
                }
            });
        } else if (!isJoin) {
            ClientData removed = knownClients.remove(clientId);
            if (removed != null) {
                System.out.println(
                        TextFX.colorize(String.format("*%s[%s] left the Room %s*", clientName, clientId, message),
                                Color.YELLOW));
                // invoke onRoomJoin callback
                events.forEach(event -> {
                    if (event instanceof IRoomEvents) {
                        ((IRoomEvents) event).onRoomAction(clientId, clientName, message, isJoin);
                    }
                });
            }
            // clear our list
            if (clientId == myData.getClientId()) {
                knownClients.clear();
                // invoke onResetUserList()
                events.forEach(event -> {
                    if (event instanceof IConnectionEvents) {
                        ((IConnectionEvents) event).onResetUserList();
                    }
                });
            }
        }
    }
    // end payload processors

}