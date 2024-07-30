package Project.Common;
import java.io.Serializable;
// yh68 7/5/24
public class Payload implements Serializable {
    private PayloadType payloadType;
    private long clientId;
    private String message;
    private String username;
    private boolean isPrivate; // New field to indicate if the message is private
    private long targetClientId;
    private String clientName;

    public PayloadType getPayloadType() {
        return payloadType;
    }

    public String getClientName() {
        return clientName;
    }

    public void setPayloadType(PayloadType payloadType) {
        this.payloadType = payloadType;
    }



    public long getClientId() {
        return clientId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }


    public void setClientId(long clientId) {
        this.clientId = clientId;
    }



    public String getMessage() {
        return message;
    }



    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isPrivate() {
        return isPrivate;
    }
    
    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
    
    public long getTargetClientId() {
        return targetClientId;
    }
    
    public void setTargetClientId(long targetClientId) {
        this.targetClientId = targetClientId;
    }

    @Override
    public String toString(){
        return String.format("Payload[%s] Client Id [%s] Message: [%s]", getPayloadType(), getClientId(), getMessage());
    }
    
}
