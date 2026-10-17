package client.nilore.utils;

import client.nilore.utils.animation.AnimationTimer;
import client.nilore.utils.math.Easing;
import client.nilore.utils.math.Easings;

public class SmoothAnimationTimer extends AnimationTimer {

   public void animate(double target, double duration) {
      this.animate(target, duration, Easings.EASE_OUT_QUAD);
   }

   public void animate(double target, double duration, Easing easing) {
      this.animate(target, duration, easing, false);
   }

   public void animate(double target, double duration, Easing easing, boolean debug) {
      this.setDebug(debug);
      if (this.isAnimating() && target == this.getToValue()) {
         return;
      }
      this.setEasing(easing);
      this.setDuration(duration * 1000.0);
      this.setStartTime(System.currentTimeMillis());
      this.setFromValue(this.getValueF());
      this.setToValue(target);
   }

   public double getValue() {
      return this.getValueF();
   }

   public double getTarget() {
      return this.getToValue();
   }

   public void setTarget(double target) {
      this.setToValue(target);
   }
}