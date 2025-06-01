package com.leclowndu93150.guichess.chess.util;

public enum TimeControl {
    BULLET_1_0(60, 0, "1+0 Bullet"),
    BULLET_2_1(120, 1, "2+1 Bullet"),
    BLITZ_3_0(180, 0, "3+0 Blitz"),
    BLITZ_3_2(180, 2, "3+2 Blitz"),
    BLITZ_5_0(300, 0, "5+0 Blitz"),
    BLITZ_5_3(300, 3, "5+3 Blitz"),
    RAPID_10_0(600, 0, "10+0 Rapid"),
    RAPID_15_10(900, 10, "15+10 Rapid"),
    RAPID_30_0(1800, 0, "30+0 Rapid"),
    CLASSICAL_60_0(3600, 0, "60+0 Classical"),
    UNLIMITED(-1, 0, "Unlimited");

    public final int initialSeconds;
    public final int incrementSeconds;
    public final String displayName;

    TimeControl(int initialSeconds, int incrementSeconds, String displayName) {
        this.initialSeconds = initialSeconds;
        this.incrementSeconds = incrementSeconds;
        this.displayName = displayName;
    }
}
