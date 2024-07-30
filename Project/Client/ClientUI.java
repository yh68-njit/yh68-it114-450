package Project.Client;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import Project.Client.Interfaces.*;
import Project.Client.Views.*;
import Project.Common.*;

public class ClientUI extends JFrame implements IConnectionEvents, IMessageEvents, IRoomEvents, ICardControls {
    private CardLayout card = new CardLayout();
    private Container container;
    private JPanel cardContainer;
    private String originalTitle;
    private JPanel currentCardPanel;
    private CardView currentCard = CardView.CONNECT;
    private JMenuBar menu;
    private ConnectionPanel connectionPanel;
    private UserDetailsPanel userDetailsPanel;
    private ChatPanel chatPanel;
    private RoomsPanel roomsPanel;
    private JLabel roomLabel = new JLabel();

    {
        LoggerUtil.LoggerConfig config = new LoggerUtil.LoggerConfig();
        config.setFileSizeLimit(2048 * 1024); // 2MB
        config.setFileCount(1);
        config.setLogLocation("client.log");
        LoggerUtil.INSTANCE.setConfig(config);
    }

    public ClientUI(String title) {
        super(title);
        originalTitle = title;
        container = getContentPane();
        cardContainer = new JPanel();
        cardContainer.setLayout(card);
        container.add(roomLabel, BorderLayout.NORTH);
        container.add(cardContainer, BorderLayout.CENTER);

        cardContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                cardContainer.setPreferredSize(e.getComponent().getSize());
                cardContainer.revalidate();
                cardContainer.repaint();
            }
        });

        setMinimumSize(new Dimension(400, 400));
        setSize(getMinimumSize());
        setLocationRelativeTo(null);
        menu = new Menu(this);
        this.setJMenuBar(menu);

        connectionPanel = new ConnectionPanel(this);
        userDetailsPanel = new UserDetailsPanel(this);
        chatPanel = new ChatPanel(this);
        roomsPanel = new RoomsPanel(this);

        // Add panels to cardContainer
        cardContainer.add(connectionPanel, CardView.CONNECT.name());
        cardContainer.add(userDetailsPanel, CardView.USER_INFO.name());
        cardContainer.add(chatPanel, CardView.CHAT.name()); // Ensure this is correct
        cardContainer.add(roomsPanel, CardView.ROOMS.name());

        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent windowEvent) {
                int response = JOptionPane.showConfirmDialog(cardContainer,
                        "Are you sure you want to close this window?", "Close Window?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (response == JOptionPane.YES_OPTION) {
                    try {
                        Client.INSTANCE.sendDisconnect();
                    } catch (NullPointerException | IOException e) {
                        LoggerUtil.INSTANCE.severe("Error during disconnect: " + e.getMessage());
                    }
                    System.exit(0);
                }
            }
        });

        pack();
        setVisible(true);
    }

    private void findAndSetCurrentPanel() {
        for (Component c : cardContainer.getComponents()) {
            if (c.isVisible()) {
                currentCardPanel = (JPanel) c;
                currentCard = CardView.valueOf(currentCardPanel.getName());
                LoggerUtil.INSTANCE.fine("Detected panel: " + currentCardPanel.getName());
                if (Client.INSTANCE.getMyClientId() == ClientData.DEFAULT_CLIENT_ID
                        && currentCard.ordinal() >= CardView.CHAT.ordinal()) {
                    show(CardView.CONNECT.name());
                    setSize(getMinimumSize());
                    revalidate();
                }
                break;
            }
        }
    }

    @Override
    public void next() {
        card.next(cardContainer);
        findAndSetCurrentPanel();
    }

    @Override
    public void previous() {
        card.previous(cardContainer);
        findAndSetCurrentPanel();
    }

    @Override
    public void show(String cardName) {
        LoggerUtil.INSTANCE.fine("Showing panel: " + cardName);
        card.show(cardContainer, cardName);
        findAndSetCurrentPanel();
    }

    @Override
    public void addPanel(String cardName, JPanel panel) {
        cardContainer.add(panel, cardName);
    }

    @Override
    public void connect() {
        String username = userDetailsPanel.getUsername();
        String host = connectionPanel.getHost();
        int port = connectionPanel.getPort();
        setTitle(originalTitle + " - " + username);
        Client.INSTANCE.connect(host, port, username, this);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientUI("YH68-Client"));
    }

    @Override
    public void onClientDisconnect(long clientId, String clientName) {
        if (currentCard.ordinal() >= CardView.CHAT.ordinal()) {
            chatPanel.removeUserListItem(clientId);
            boolean isMe = clientId == Client.INSTANCE.getMyClientId();
            String message = String.format("*%s disconnected*",
                    isMe ? "You" : String.format("%s[%s]", clientName, clientId));
            chatPanel.addText(message);
            if (isMe) {
                LoggerUtil.INSTANCE.info("I disconnected");
                previous();
            }
        }
    }

    // yh68 7/27/24
    @Override
    public void onMessageReceive(long clientId, String message) {
        if (currentCard.ordinal() >= CardView.CHAT.ordinal()) {
            String clientName = Client.INSTANCE.getClientNameFromId(clientId);
            String formattedMessage = String.format("%s[%d]: %s", clientName, clientId, message);
            chatPanel.addText(formattedMessage);
            chatPanel.getUserListPanel().highlightLastMessageSender(clientId);
        }
    }

    @Override
    public void onUserMuteStatusChanged(long clientId, boolean isMuted) {
        if (currentCard.ordinal() >= CardView.CHAT.ordinal()) {
            chatPanel.getUserListPanel().updateUserMuteStatus(clientId, isMuted);
        }
    }
    
    @Override
    public void onReceiveClientId(long id) {
        LoggerUtil.INSTANCE.fine("Received client id: " + id);
        show(CardView.CHAT.name()); // Ensure the panel name is correct
        chatPanel.addText("*You connected*");
        setSize(new Dimension(600, 600));
        revalidate();
    }

    @Override
    public void onResetUserList() {
        chatPanel.clearUserList();
    }

    @Override
    public void onSyncClient(long clientId, String clientName) {
        if (currentCard.ordinal() >= CardView.CHAT.ordinal()) {
            chatPanel.addUserListItem(clientId, String.format("%s (%s)", clientName, clientId));
        }
    }

    @Override
    public void onReceiveRoomList(List<String> rooms, String message) {
        roomsPanel.removeAllRooms();
        if (message != null && !message.isEmpty()) {
            roomsPanel.setMessage(message);
        }
        if (rooms != null) {
            for (String room : rooms) {
                roomsPanel.addRoom(room);
            }
        }
    }

    @Override
    public void onRoomAction(long clientId, String clientName, String roomName, boolean isJoin) {
        LoggerUtil.INSTANCE.info("Current card: " + currentCard.name());
        if (currentCard.ordinal() >= CardView.CHAT.ordinal()) {
            boolean isMe = clientId == Client.INSTANCE.getMyClientId();
            String message = String.format("*%s %s the Room %s*",
                    isMe ? "You" : String.format("%s[%s]", clientName, clientId),
                    isJoin ? "joined" : "left",
                    roomName == null ? "" : roomName);
            chatPanel.addText(message);
            if (isJoin) {
                roomLabel.setText("Room: " + roomName);
                chatPanel.addUserListItem(clientId, String.format("%s (%s)", clientName, clientId));
            } else {
                chatPanel.removeUserListItem(clientId);
            }
        }
    }
}
