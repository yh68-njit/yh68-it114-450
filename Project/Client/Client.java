package Project.Client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import Project.Common.ConnectionPayload;
import Project.Common.LoggerUtil;
import Project.Common.Payload;
import Project.Common.PayloadType;
import Project.Common.RoomResultsPayload;
import Project.Common.TextFX;
import Project.Common.TextFX.Color;
import Project.Common.RollPayload;

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
    private final Pattern rollSingle = Pattern.compile("/roll\\s+(\\d+)");
    private final Pattern rollMulti = Pattern.compile("/roll\\s+(\\d+)d(\\d+)");


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
    } else if ("/quit".equalsIgnoreCase(text)) {
        close();
        return true;
        // yh68 7/22/24
    } else if (text.startsWith("@")) {
        // Handle private message
        String[] parts = text.substring(1).split(" ", 2);
        if (parts.length < 2) {
            JOptionPane.showMessageDialog(null, "Invalid private message format.", "Error", JOptionPane.ERROR_MESSAGE);
            return true;
        }
        String username = parts[0];
        String messageContent = parts[1];
        try {
            sendPrivateMessage(username, messageContent);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Failed to send private message", e);
        }
        return true;
    } else if (text.startsWith("/roll")) {
        processRollCommand(text);
        return true;
    } else if (text.equalsIgnoreCase("/flip")) {
        processFlipCommand();
        return true;
    } else if (text.startsWith("/name")) {
        myData.setClientName(text.replace("/name", "").trim());
        System.out.println(TextFX.colorize("Set client name to " + myData.getClientName(), Color.CYAN));
        return true;
    } else if (text.equalsIgnoreCase("/users")) {
        System.out.println(String.join("\n", knownClients.values().stream().map(c -> String.format("%s(%s)", c.getClientName(), c.getClientId())).toList()));
        return true;
    } else if (text.startsWith("/mute")) {
        String username = text.replace("/mute", "").trim();
        try {
            sendMute(username);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Failed to send mute command", e);
        }
        return true;
    } else if (text.startsWith("/unmute")) {
        String username = text.replace("/unmute", "").trim();
        try {
            sendUnmute(username);
        } catch (IOException e) {
            LoggerUtil.INSTANCE.severe("Failed to send unmute command", e);
        }
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
    return isRunning;
}

private void processPrivateMessage(Payload payload) {
    try {
        String text = payload.getMessage(); // Assuming message contains text in the format "@username message"
        int spaceIndex = text.indexOf(' ');
        if (spaceIndex == -1) {
            System.out.println(TextFX.colorize("Invalid private message format", Color.RED));
            return;
        }
        
        String targetUsername = text.substring(1, spaceIndex).trim(); // Remove '@' and get the username
        String messageContent = text.substring(spaceIndex + 1).trim(); // Get the message content
        
        // Construct and send the private message payload
        Payload responsePayload = new Payload();
        responsePayload.setPayloadType(PayloadType.PRIVATE_MESSAGE);
        responsePayload.setUsername(targetUsername); // Use username instead of client ID
        responsePayload.setMessage(messageContent);
        send(responsePayload);
    } catch (Exception e) {
        System.out.println(TextFX.colorize("Error processing private message", Color.RED));
        LoggerUtil.INSTANCE.severe("Error processing private message", e);
    }
}
//yh68 7/22/24
public void sendMute(String username) throws IOException {
    Payload payload = new Payload();
    payload.setPayloadType(PayloadType.MUTE);
    payload.setUsername(username);
    send(payload);
}

public void sendUnmute(String username) throws IOException {
    Payload payload = new Payload();
    payload.setPayloadType(PayloadType.UNMUTE);
    payload.setUsername(username);
    send(payload);
}

public void sendPrivateMessage(String username, String message) throws IOException {
    Payload payload = new Payload();
    payload.setPayloadType(PayloadType.PRIVATE_MESSAGE);
    payload.setMessage(message);
    payload.setUsername(username); // Add this field to your Payload class
    send(payload);
}

// yh68 7/5/24
private void processRollCommand(String text) throws IOException {
    Matcher singleMatcher = rollSingle.matcher(text);
    Matcher multiMatcher = rollMulti.matcher(text);

    if (singleMatcher.matches()) {
        int max = Integer.parseInt(singleMatcher.group(1));
        sendRoll(1, max);
    } else if (multiMatcher.matches()) {
        int numberOfRolls = Integer.parseInt(multiMatcher.group(1));
        int diceSides = Integer.parseInt(multiMatcher.group(2));
        sendRoll(numberOfRolls, diceSides);
    } else {
        System.out.println("Invalid roll command format");
    }
}

// yh68 7/22/24
private void processFlipCommand() throws IOException {
    String result = new Random().nextBoolean() ? "heads" : "tails";
    String message = String.format("%s flipped a coin and got %s", myData.getClientName(), result);
    System.out.println(message);
    sendMessage(message);
}

// send methods to pass data to the ServerThread

/**
 * Sends a roll command with specified number of rolls and dice sides
 * 
 * @param numberOfRolls
 * @param diceSides
 * @throws IOException 
 */
private void sendRoll(int numberOfRolls, int diceSides) throws IOException {
    if (numberOfRolls <= 0 || diceSides <= 0) {
        System.out.println("Invalid roll parameters");
        return;
    }

    Random random = new Random();
    String clientName = myData.getClientName();

    if (numberOfRolls == 1) {
        int result = random.nextInt(diceSides) + 1;
        String message = String.format("%s rolled %d and got %d", clientName, diceSides, result);
        System.out.println(message);
        Payload p = new Payload();
        p.setPayloadType(PayloadType.ROLL);
        p.setMessage(message);
        send(p);
    } else {
        RollPayload rollPayload = new RollPayload(numberOfRolls, diceSides);
        rollPayload.setPayloadType(PayloadType.ROLL);
        int result = rollPayload.rollDice();
        String message = String.format("%s rolled %dd%d and got %d", clientName, numberOfRolls, diceSides, result);
        System.out.println(message);
        rollPayload.setMessage(message);
        send(rollPayload);
    }
}

    public long getMyClientId() {
        return myData.getClientId();
    }


    // send methods to pass data to the ServerThread

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
                case PRIVATE_MESSAGE:
                    processPrivateMessage(payload);
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
        String name = knownClients.containsKey(clientId) ? knownClients.get(clientId).getClientName() : "Room";
        System.out.println(TextFX.colorize(String.format("%s: %s", name, message), Color.BLUE));
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