package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import Project.Client.CardView;
import Project.Client.Client;
import Project.Client.Interfaces.ICardControls;
import Project.Common.LoggerUtil;

/**
 * ChatPanel represents the main chat interface where messages can be sent and
 * received.
 */
// yh68 7/22/24
public class ChatPanel extends JPanel {
    private JPanel chatArea = null;
    private UserListPanel userListPanel;
    private final float CHAT_SPLIT_PERCENT = 0.7f;

    private JTextField textValue;
    private JButton button;
    private JButton exportButton;

    /**
     * Constructor to create the ChatPanel UI.
     * 
     * @param controls The controls to manage card transitions.
     */
    public ChatPanel(ICardControls controls) {
        super(new BorderLayout(10, 10));
    
        JPanel chatContent = new JPanel(new GridBagLayout());
        chatContent.setAlignmentY(Component.TOP_ALIGNMENT);
    
        // Wraps a viewport to provide scroll capabilities
        JScrollPane scroll = new JScrollPane(chatContent);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
    
        chatArea = chatContent;
    
        userListPanel = new UserListPanel();
    
        // JSplitPane setup with chat on the left and user list on the right
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scroll, userListPanel);
        splitPane.setResizeWeight(CHAT_SPLIT_PERCENT); // Allocate % space to the chat panel initially
    
        this.add(splitPane, BorderLayout.CENTER);
    
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5)); // Add padding
    
        textValue = new JTextField();
        inputPanel.add(textValue);
    
        button = new JButton("Send");
        // Allows submission with the enter key instead of just the button click
        textValue.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {}
    
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    button.doClick();
                }
            }
    
            @Override
            public void keyReleased(KeyEvent e) {}
        });
    
        button.addActionListener((event) -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    String text = textValue.getText().trim();
                    if (!text.isEmpty()) {
                        LoggerUtil.INSTANCE.info("Preparing to send message: " + text);
                        sendMessage(text); // Use the new sendMessage method
                        textValue.setText(""); // Clear the original text
                    }
                } catch (NullPointerException e) {
                    LoggerUtil.INSTANCE.severe("Error sending message", e);
                }
            });
        });
    
        exportButton = new JButton("Export Chat");
        exportButton.addActionListener(e -> exportChatHistory());
    
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(button);
        buttonPanel.add(Box.createHorizontalStrut(10)); // Add some space between buttons
        buttonPanel.add(exportButton);
    
        // Add components to the input panel
        inputPanel.add(buttonPanel);
    
        this.add(inputPanel, BorderLayout.SOUTH);
    
        this.setName(CardView.CHAT.name());
    
        chatArea.addContainerListener(new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (chatArea.isVisible()) {
                        chatArea.revalidate();
                        chatArea.repaint();
                    }
                });
            }
    
            @Override
            public void componentRemoved(ContainerEvent e) {
                SwingUtilities.invokeLater(() -> {
                    if (chatArea.isVisible()) {
                        chatArea.revalidate();
                        chatArea.repaint();
                    }
                });
            }
        });
    
        // Add vertical glue to push messages to the top
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0; // Column index 0
        gbc.gridy = GridBagConstraints.RELATIVE; // Automatically move to the next row
        gbc.weighty = 1.0; // Give extra space vertically to this component
        gbc.fill = GridBagConstraints.BOTH; // Fill both horizontally and vertically
        chatArea.add(Box.createVerticalGlue(), gbc);
    
        // Ensure editor panes resize when the scroll pane viewport changes
        scroll.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeEditorPanes();
            }
        });
    }

    /**
     * Adds a user to the user list.
     * 
     * @param clientId   The ID of the client.
     * @param clientName The name of the client.
     */
    public void addUserListItem(long clientId, String clientName) {
        SwingUtilities.invokeLater(() -> userListPanel.addUserListItem(clientId, clientName));
    }

    /**
     * Removes a user from the user list.
     * 
     * @param clientId The ID of the client to be removed.
     */
    public void removeUserListItem(long clientId) {
        SwingUtilities.invokeLater(() -> userListPanel.removeUserListItem(clientId));
    }

    /**
     * Clears the user list.
     */
    public void clearUserList() {
        SwingUtilities.invokeLater(() -> userListPanel.clearUserList());
    }

    /**
     * Adds a message to the chat area.
     * 
     * @param text The text of the message.
     */
    // yh68 7/22/24
    public void addText(String text) {
        SwingUtilities.invokeLater(() -> {
            JEditorPane textContainer = new JEditorPane("text/html", text); // Change to "text/html"
            textContainer.setEditable(false);
            textContainer.setBorder(BorderFactory.createEmptyBorder());
            textContainer.setOpaque(false);
            textContainer.setBackground(new Color(0, 0, 0, 0));

            // GridBagConstraints settings for each message
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; // Column index 0
            gbc.gridy = GridBagConstraints.RELATIVE; // Automatically move to the next row
            gbc.weightx = 1; // Let the component grow horizontally to fill the space
            gbc.fill = GridBagConstraints.HORIZONTAL; // Fill horizontally
            gbc.insets = new Insets(0, 0, 5, 0); // Add spacing between messages

            long clientId = extractClientIdFromMessage(text);
            if (clientId != -1) {
                LoggerUtil.INSTANCE.info("Highlighting last message sender: " + clientId);
                userListPanel.highlightLastMessageSender(clientId);
            } else {
                LoggerUtil.INSTANCE.warning("Client ID extraction failed for message: " + text);
            }
            chatArea.add(textContainer, gbc);
            chatArea.revalidate();
            chatArea.repaint();

            // Scroll down on new message
            JScrollPane parentScrollPane = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, chatArea);
            if (parentScrollPane != null) {
                SwingUtilities.invokeLater(() -> {
                    JScrollBar vertical = parentScrollPane.getVerticalScrollBar();
                    vertical.setValue(vertical.getMaximum());
                });
            }
        });
    }

    public UserListPanel getUserListPanel() {
        return userListPanel;
    }

    private long extractClientIdFromMessage(String message) {
        int startIndex = message.indexOf('[');
        int endIndex = message.indexOf(']');
        if (startIndex != -1 && endIndex != -1) {
            try {
                return Long.parseLong(message.substring(startIndex + 1, endIndex));
            } catch (NumberFormatException e) {
                return -1;
            }
        }
        return -1;
    }

    public void updateUserMuteStatus(long clientId, boolean isMuted) {
        userListPanel.updateUserMuteStatus(clientId, isMuted);
    }

    // yh68 7/29/24
    private void exportChatHistory() {
        StringBuilder chatHistory = new StringBuilder();
        
        // Iterate through all components in chatArea
        for (Component comp : chatArea.getComponents()) {
            if (comp instanceof JEditorPane) {
                JEditorPane editorPane = (JEditorPane) comp;
                chatHistory.append(editorPane.getText()).append("\n");
            }
        }

        // Generate unique filename with date and time
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String fileName = "chat_history_" + dateFormat.format(new Date()) + ".txt";

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(chatHistory.toString());
            JOptionPane.showMessageDialog(this, "Chat history exported to " + fileName, "Export Successful", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error exporting chat history: " + ex.getMessage(), "Export Failed", JOptionPane.ERROR_MESSAGE);
            LoggerUtil.INSTANCE.severe("Error exporting chat history", ex);
        }
    }

    public void sendMessage(String text) {
        try {
            Client.INSTANCE.sendMessage(text);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void resizeEditorPanes() {
        int width = chatArea.getParent().getWidth();
        for (Component comp : chatArea.getComponents()) {
            if (comp instanceof JEditorPane) {
                JEditorPane editorPane = (JEditorPane) comp;
                editorPane.setSize(new Dimension(width, Integer.MAX_VALUE));
                Dimension d = editorPane.getPreferredSize();
                editorPane.setPreferredSize(new Dimension(width, d.height));
            }
        }
        chatArea.revalidate();
        chatArea.repaint();
    }
}
