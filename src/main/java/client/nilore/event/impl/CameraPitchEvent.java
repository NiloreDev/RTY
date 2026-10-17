package client.nilore.event.impl;

import client.nilore.event.EventMarker;

/**
 * Mutable camera-pitch event.
 * Kept independent from CameraClip; consumers may change pitch through the
 * normal Nilore event bus without coupling it to the clip-distance hook.
 */
public class CameraPitchEvent implements EventMarker {

    private float pitch;

    public CameraPitchEvent(float pitch) {
        this.pitch = pitch;
    }

    public float getPitch() {
        return pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }
}
