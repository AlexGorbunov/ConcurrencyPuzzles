/**
 * Created by Oleksandr_Gorbunov on 10/7/2014.
 */
public class TransferTask {
    private final int fromAccountID;
    private final int toAccountID;
    private final int amount;

    public int getFromAccountID() {
        return fromAccountID;
    }

    public int getToAccountID() {
        return toAccountID;
    }

    public int getAmount() {
        return amount;
    }

    public TransferTask(int fromAccountID, int toAccountID, int amount) {
        this.fromAccountID = fromAccountID;
        this.toAccountID = toAccountID;
        this.amount = amount;
    }

    @Override
    public String toString() {
        return "from: " + getFromAccountID() + ", to: " + getToAccountID() + ", amount: " + getAmount();
    }
}
