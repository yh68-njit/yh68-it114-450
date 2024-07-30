package Project.Common;

import java.util.Random;

public class FlipPayload extends Payload {
    private boolean isHeads;

    public FlipPayload() {
        this.isHeads = new Random().nextBoolean();
        setPayloadType(PayloadType.FLIP);
    }

    public boolean isHeads() {
        return isHeads;
    }

    @Override
    public String toString() {
        return super.toString() + " flipped a coin and got: " + (isHeads ? "heads" : "tails");
    }
}
