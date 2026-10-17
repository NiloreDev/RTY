package client.nilore.event.impl;

import client.nilore.event.Event;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PreMotionEvent extends Event {
    private double x;
    private double y;
    private double z;
    private boolean onGround;

    public PreMotionEvent(double x, double y, double z, boolean onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.onGround = onGround;
    }

    public PreMotionEvent() {

    }

    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public boolean isOnGround() { return onGround; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setZ(double z) { this.z = z; }
    public void setOnGround(boolean onGround) { this.onGround = onGround; }
}