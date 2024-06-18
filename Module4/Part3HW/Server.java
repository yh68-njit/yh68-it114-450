package Module4.Part3HW;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class Server {
    private int port = 3000;
    // connected clients
    // Use ConcurrentHashMap for thread-safe client management
    private final ConcurrentHashMap<Long, ServerThread> connectedClients = new ConcurrentHashMap<>();
    private boolean isRunning = true;
    private boolean isGameActive = false;
    private int targetNumber = -1;


    private void start(int port) {
        this.port = port;
        // server listening
        System.out.println("Listening on port " + this.port);
        // Simplified client connection loop
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (isRunning) {
                System.out.println("Waiting for next client");
                Socket incomingClient = serverSocket.accept(); // blocking action, waits for a client connection
                System.out.println("Client connected");
                // wrap socket in a ServerThread, pass a callback to notify the Server they're initialized
                ServerThread sClient = new ServerThread(incomingClient, this, this::onClientInitialized);
                // start the thread (typically an external entity manages the lifecycle and we
                // don't have the thread start itself)
                sClient.start();
            }
        } catch (IOException e) {
            System.err.println("Error accepting connection");
            e.printStackTrace();
        } finally {
            System.out.println("Closing server socket");
        }
    }
    /**
     * Callback passed to ServerThread to inform Server they're ready to receive data
     * @param sClient
     */
    private void onClientInitialized(ServerThread sClient) {
        // add to connected clients list
        connectedClients.put(sClient.getClientId(), sClient);
        relay(String.format("*User[%s] connected*", sClient.getClientId()), null);
    }
    /**
     * Takes a ServerThread and removes them from the Server
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param client
     */
    protected synchronized void disconnect(ServerThread client) {
        long id = client.getClientId();
        client.disconnect();
        connectedClients.remove(id);
        // Improved logging with user ID
        relay("User[" + id + "] disconnected", null);
    }

    /**
     * Relays the message from the sender to all connectedClients
     * Internally calls processCommand and evaluates as necessary.
     * Note: Clients that fail to receive a message get removed from
     * connectedClients.
     * Adding the synchronized keyword ensures that only one thread can execute
     * these methods at a time,
     * preventing concurrent modification issues and ensuring thread safety
     * 
     * @param message
     * @param sender ServerThread (client) sending the message or null if it's a server-generated message
     */
    protected synchronized void relay(String message, ServerThread sender) {
        if (sender != null && processCommand(message, sender)) {

            return;
        }
        // let's temporarily use the thread id as the client identifier to
        // show in all client's chat. This isn't good practice since it's subject to
        // change as clients connect/disconnect
        // Note: any desired changes to the message must be done before this line
        String senderString = sender == null ? "Server" : String.format("User[%s]", sender.getClientId());
        final String formattedMessage = String.format("%s: %s", senderString, message);
        // end temp identifier

        // loop over clients and send out the message; remove client if message failed
        // to be sent
        // Note: this uses a lambda expression for each item in the values() collection,
        // it's one way we can safely remove items during iteration
        
        connectedClients.values().removeIf(client -> {
            boolean failedToSend = !client.send(formattedMessage);
            if (failedToSend) {
                System.out.println(String.format("Removing disconnected client[%s] from list", client.getClientId()));
                disconnect(client);
            }
            return failedToSend;
        });
    }

    /**
     * Attempts to see if the message is a command and process its action
     * 
     * @param message
     * @param sender
     * @return true if it was a command, false otherwise
     */
    // yh68 6/17/2024
    private boolean processCommand(String message, ServerThread sender) {
        if (sender == null) {
            return false;
        }
        System.out.println("Checking command: " + message);
    
        if ("/disconnect".equalsIgnoreCase(message)) {
            ServerThread removedClient = connectedClients.get(sender.getClientId());
            if (removedClient != null) {
                this.disconnect(removedClient);
            }
            return true;
        } else if ("/start".equalsIgnoreCase(message)) {
            this.startGame();
            return true;
        } else if ("/stop".equalsIgnoreCase(message)) {
            this.stopGame();
            return true;
        } else if (message.startsWith("/guess ")) {
            this.processGuess(message, sender);
            return true;
        } else if (message.startsWith("/randomize ")) {
            String text = message.substring("/randomize ".length());
            String randomizedMessage = randomize(text);
            this.relay(randomizedMessage, sender);
            return true;
        }
    
        return false;
    }
    

    private String randomize(String message) {
        List<String> characters = List.of(message.split(""));
        Collections.shuffle(characters);
        StringBuilder randomizedMessage = new StringBuilder();
        for (String character : characters) {
            randomizedMessage.append(character);
        }
        return randomizedMessage.toString();
    }
    
    
    
    private void startGame() {
    isGameActive = true;
    targetNumber = ThreadLocalRandom.current().nextInt(1, 101);
    relay("Game started! Guess a number between 1 and 100.", null);
}

private void stopGame() {
    isGameActive = false;
    relay("Game stopped.", null);
}

private void processGuess(String message, ServerThread sender) {
    if (!isGameActive) {
        sender.send("The game is not active. Please start the game first.");
        return;
    }

    try {
        int guess = Integer.parseInt(message.split(" ")[1]);
        String resultMessage = String.format("User[%s] guessed %d but it was not correct.", sender.getClientId(), guess);

        if (guess == targetNumber) {
            resultMessage = String.format("User[%s] guessed %d and it was correct!", sender.getClientId(), guess);
            stopGame();
        }

        relay(resultMessage, null);
    } catch (NumberFormatException e) {
        sender.send("Invalid guess. Please enter a number.");
    }
}

    public static void main(String[] args) {
        System.out.println("Server Starting");
        Server server = new Server();
        int port = 3000;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            // can ignore, will either be index out of bounds or type mismatch
            // will default to the defined value prior to the try/catch
        }
        server.start(port);
        System.out.println("Server Stopped");
    }
}