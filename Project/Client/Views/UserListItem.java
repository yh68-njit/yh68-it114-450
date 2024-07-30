package Project.Client.Views;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import Project.Common.LoggerUtil;

/**
 * UserListItem represents a user entry in the user list.
 */
public class UserListItem extends JPanel {
    private JEditorPane textContainer;
    private boolean isMuted = false;
    private boolean isHighlighted = false;

    /**
     * Constructor to create a UserListItem.
     *
     * @param clientId   The ID of the client.
     * @param clientName The name of the client.
     * @param parent     The parent container to calculate available width.
     */
    public UserListItem(long clientId, String clientName, JPanel parent) {
        textContainer = new JEditorPane("text/plain", clientName);
        textContainer.setName(Long.toString(clientId));
        textContainer.setEditable(false);
        textContainer.setBorder(new EmptyBorder(0, 0, 0, 0)); // Add padding

        // Clear background and border
        textContainer.setOpaque(false);
        textContainer.setBorder(BorderFactory.createEmptyBorder());
        textContainer.setBackground(new Color(0, 0, 0, 0));

        this.setLayout(new BorderLayout());
        JPanel mid = new JPanel(new BorderLayout());
        mid.add(textContainer, BorderLayout.NORTH);
        this.add(mid, BorderLayout.CENTER);
        // setPreferredSize(new Dimension(0,0));
    }

    // yh68 7/29/24
    public void setHighlighted(boolean highlighted) {
        LoggerUtil.INSTANCE.info("setHighlighted called with " + highlighted + " for client ");
        this.isHighlighted = highlighted;
        updateAppearance();
    }
    
    // yh68 7/29/24
    public void setMuted(boolean muted) {
        LoggerUtil.INSTANCE.info("setMuted called with " + muted + " for client ");
        this.isMuted = muted;
        updateAppearance();
    }
    
    private void updateAppearance() {
        LoggerUtil.INSTANCE.info("updateAppearance called. isMuted: " + isMuted + ", isHighlighted: " + isHighlighted);
    
        if (isMuted) {
            textContainer.setForeground(Color.GRAY);
        } else {
            textContainer.setForeground(Color.BLACK);
        }
    
        if (isHighlighted) {
            this.setBackground(Color.YELLOW);
        } else {
            this.setBackground(null);
        }
    
        this.revalidate();
        this.repaint();
    }
    

    public String getClientName() {
        return textContainer.getText();
    }
}
