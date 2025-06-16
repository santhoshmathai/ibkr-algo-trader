package com.ibkr.models;

import java.util.Objects;

public class SupportResistanceLevel {
    private final double levelPrice;
    private final LevelType type;
    private final LevelStrength strength;
    private final String calculationMethod; // e.g., "DailyHighLow", "PivotPoint", "Fibonacci"

    public SupportResistanceLevel(double levelPrice, LevelType type, LevelStrength strength, String calculationMethod) {
        if (type == null) {
            throw new IllegalArgumentException("LevelType cannot be null.");
        }
        if (strength == null) {
            throw new IllegalArgumentException("LevelStrength cannot be null.");
        }
        if (calculationMethod == null || calculationMethod.trim().isEmpty()) {
            throw new IllegalArgumentException("CalculationMethod cannot be null or empty.");
        }
        this.levelPrice = levelPrice;
        this.type = type;
        this.strength = strength;
        this.calculationMethod = calculationMethod;
    }

    public double getLevelPrice() {
        return levelPrice;
    }

    public LevelType getType() {
        return type;
    }

    public LevelStrength getStrength() {
        return strength;
    }

    public String getCalculationMethod() {
        return calculationMethod;
    }

    @Override
    public String toString() {
        return "SupportResistanceLevel{" +
                "levelPrice=" + levelPrice +
                ", type=" + type +
                ", strength=" + strength +
                ", calculationMethod='" + calculationMethod + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportResistanceLevel that = (SupportResistanceLevel) o;
        return Double.compare(that.levelPrice, levelPrice) == 0 &&
                type == that.type &&
                strength == that.strength &&
                Objects.equals(calculationMethod, that.calculationMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(levelPrice, type, strength, calculationMethod);
    }
}
