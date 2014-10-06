/**
 * Created by alexgorbunov on 10/6/14.
 */
class GSMTower {
    private final int x;
    private final int y;
    private int actionRadius;

    public GSMTower(int x, int y, int radius) {
        this.x = x;
        this.y = y;
        this.actionRadius = radius;
    }

    public int getActionRadius() {
        return actionRadius;
    }

    public int getXCoordinate() {
        return x;
    }

    public int getYCoordinate() {
        return y;
    }
}

class GSMCall {
    private final int x;
    private final int y;

    public GSMCall(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int getXCoordinate() {
        return x;
    }

    public int getYCoordinate() {
        return y;
    }
}

public class GSMTowersSimulation {
    public static void main(String[] args) {
        final String input = "";
    }
}
