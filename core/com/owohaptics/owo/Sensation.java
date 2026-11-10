package com.owohaptics.owo;

import java.util.Locale;

/**
 * One OWO micro-sensation bound to muscles. Wire form (OWO protocol):
 * {@code frequency,duration_ds,intensity,rampUp_ms,rampDown_ms,exitDelay_ds,name|id%int,id%int}
 */
public final class Sensation {
    public final int frequency;      // 1..100 Hz
    public final double duration;    // 0.1..20 s
    public final int intensity;      // 0..100 %
    public final double rampUp;      // 0..2 s
    public final double rampDown;    // 0..2 s
    public final Muscle[] muscles;
    public final int muscleIntensity; // 0..100 % per-muscle scale

    public Sensation(int frequency, double duration, int intensity, double rampUp, double rampDown,
                     int muscleIntensity, Muscle... muscles) {
        this.frequency = clamp(frequency, 1, 100);
        this.duration = round1(Math.max(0.1, Math.min(20.0, duration)));
        this.intensity = clamp(intensity, 0, 100);
        this.rampUp = round1(Math.max(0, Math.min(2.0, rampUp)));
        this.rampDown = round1(Math.max(0, Math.min(2.0, rampDown)));
        this.muscleIntensity = clamp(muscleIntensity, 0, 100);
        this.muscles = muscles;
    }

    public static Sensation of(int frequency, double duration, int intensity, Muscle... muscles) {
        return new Sensation(frequency, duration, intensity, 0, 0, 100, muscles);
    }

    /** Copy scaled by a percentage (applied to the micro-sensation intensity). */
    public Sensation scaled(int percent) {
        return new Sensation(frequency, duration, intensity * percent / 100, rampUp, rampDown, muscleIntensity, muscles);
    }

    public Sensation withMuscles(Muscle... m) {
        return new Sensation(frequency, duration, intensity, rampUp, rampDown, muscleIntensity, m);
    }

    public String toWire() {
        StringBuilder sb = new StringBuilder();
        sb.append(frequency).append(',')
          .append(Math.round(duration * 10)).append(',')
          .append(intensity).append(',')
          .append(Math.round(rampUp * 1000)).append(',')
          .append(Math.round(rampDown * 1000)).append(',')
          .append(0).append(',')   // exit delay
          .append('|');            // empty name, then muscle list
        for (int i = 0; i < muscles.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(muscles[i].id).append('%').append(muscleIntensity);
        }
        return sb.toString();
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Sensation[%s]", toWire());
    }
}
