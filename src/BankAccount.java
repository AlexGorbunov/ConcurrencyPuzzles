/**
 * Created by Oleksandr_Gorbunov on 10/7/2014.
 */
public class BankAccount {
    private final long id;
    private int balance;

    public BankAccount(long id, int balance) {
        this.id = id;
        this.balance = balance;
    }

    public BankAccount(long id) {
        this(id, 0);
    }

    public void debit(double money) {
        balance += money;
    }

    public void credit(double money) {
        balance -= money;
    }

    public int getBalance() {
        return balance;
    }

    public long getID() {
        return id;
    }

    @Override
    public String toString() {
        return "Account ID: " + getID() + ", balance: " + getBalance();
    }
}
