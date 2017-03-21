package org.rivierarobotics;

public enum Source {
    PLAIN, PROCESSED;

    public Source other() {
        switch (this) {
            case PLAIN:
                return PROCESSED;
            case PROCESSED:
                return PLAIN;
            default:
                throw new IllegalStateException(this.toString());
        }
    }
}
