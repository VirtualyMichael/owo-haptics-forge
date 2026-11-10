package com.owohaptics.owo;

/** The ten OWO muscle zones (ids from the OWO wire protocol). */
public enum Muscle {
    PECTORAL_R(0), PECTORAL_L(1),
    ABDOMINAL_R(2), ABDOMINAL_L(3),
    ARM_R(4), ARM_L(5),
    DORSAL_R(6), DORSAL_L(7),
    LUMBAR_R(8), LUMBAR_L(9);

    public final int id;

    Muscle(int id) {
        this.id = id;
    }

    public static final Muscle[] FRONT = {PECTORAL_R, PECTORAL_L, ABDOMINAL_R, ABDOMINAL_L, ARM_R, ARM_L};
    public static final Muscle[] BACK = {DORSAL_R, DORSAL_L, LUMBAR_R, LUMBAR_L};
    public static final Muscle[] TORSO_FRONT = {PECTORAL_R, PECTORAL_L, ABDOMINAL_R, ABDOMINAL_L};
    public static final Muscle[] RIGHT = {PECTORAL_R, ABDOMINAL_R, ARM_R, DORSAL_R, LUMBAR_R};
    public static final Muscle[] LEFT = {PECTORAL_L, ABDOMINAL_L, ARM_L, DORSAL_L, LUMBAR_L};
    public static final Muscle[] ALL = values();
}
