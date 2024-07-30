package Project.Common;

public class PrivateMessagePayload extends Payload {
    private long targetId;

    public PrivateMessagePayload(long targetId, String message) {
        this.targetId = targetId;
        this.setMessage(message);
        this.setPayloadType(PayloadType.PRIVATE_MESSAGE);
    }

    public long getTargetId() {
        return targetId;
    }

    public void setTargetId(long targetId) {
        this.targetId = targetId;
    }
}
