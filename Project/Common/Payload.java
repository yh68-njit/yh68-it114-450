package Project.Common;
import java.io.Serializable;

public class Payload implements Serializable {
    private PayloadType payloadType;
    private long clientId;
    private String message;
    private String username;

    

    public PayloadType getPayloadType() {
        return payloadType;
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



    @Override
    public String toString(){
        return String.format("Payload[%s] Client Id [%s] Message: [%s]", getPayloadType(), getClientId(), getMessage());
    }
}
