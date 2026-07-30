package com.landclaim.config;

public class TierConfig {
    private final int tier;
    private final int radius;
    private final double cost;

    public TierConfig(int tier, int radius, double cost) {
        this.tier = tier;
        this.radius = radius;
        this.cost = cost;
    }

    public int getTier() { return tier; }
    public int getRadius() { return radius; }
    public double getCost() { return cost; }
}
