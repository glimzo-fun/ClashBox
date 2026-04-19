package net.glimzo.clashbox.economy;

public class TransactionResult {

    private final boolean success;
    private final long    amount;
    private final String  message;

    private TransactionResult(boolean success, long amount, String message) {
        this.success = success;
        this.amount  = amount;
        this.message = message;
    }

    public static TransactionResult success(long amount, String message) {
        return new TransactionResult(true, amount, message);
    }

    public static TransactionResult fail(String message) {
        return new TransactionResult(false, 0, message);
    }

    public boolean isSuccess() { return success; }
    public long    getAmount() { return amount;  }
    public String  getMessage(){ return message; }
}
