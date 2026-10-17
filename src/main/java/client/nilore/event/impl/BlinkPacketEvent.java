package client.nilore.events.impl;

import client.nilore.event.AbstractCancellable;
import client.nilore.event.EventPriority;
import net.minecraft.network.protocol.Packet;

public class BlinkPacketEvent extends AbstractCancellable {
   private final EventPriority type;
   private Packet<?> packet;

   public EventPriority getType() {
      return this.type;
   }

   public Packet<?> getPacket() {
      return this.packet;
   }

   public void setPacket(Packet<?> packet) {
      this.packet = packet;
   }

   public BlinkPacketEvent(EventPriority type, Packet<?> packet) {
      this.type = type;
      this.packet = packet;
   }
}
