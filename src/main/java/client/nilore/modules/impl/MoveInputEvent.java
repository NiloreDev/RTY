package client.nilore.modules.impl;

import client.nilore.event.Event;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MoveInputEvent extends Event {
    private float forward;
    private float strafe;
    private boolean jumping;
    private boolean sneaking;

    public MoveInputEvent(float forward, float strafe, boolean jumping, boolean sneaking) {
        this.forward = forward;
        this.strafe = strafe;
        this.jumping = jumping;
        this.sneaking = sneaking;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }
}