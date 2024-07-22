package Project.Common;

import java.util.Random;

public class RollPayload extends Payload {
    private int numberOfRolls;
    private int diceSides;

    public RollPayload(int numberOfRolls, int diceSides) {
        this.numberOfRolls = numberOfRolls;
        this.diceSides = diceSides;
        setPayloadType(PayloadType.ROLL);
    }

    public int getNumberOfRolls() {
        return numberOfRolls;
    }

    public int getDiceSides() {
        return diceSides;
    }

    public int rollDice() {
        Random random = new Random();
        int total = 0;
        for (int i = 0; i < numberOfRolls; i++) {
            total += random.nextInt(diceSides) + 1;
        }
        return total;
    }

    @Override
    public String toString() {
        return super.toString() + String.format(" Rolls [%d] Dice Sides [%d]", numberOfRolls, diceSides);
    }
}
