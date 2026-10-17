package client.nilore.utils.misc;

public final class MilliTimer {
    private long last = System.currentTimeMillis();

    public void reset() {
        last = System.currentTimeMillis();
    }

    public boolean passed(long ms) {
        return System.currentTimeMillis() - last >= ms;
    }
}
