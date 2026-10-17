package ru.example.prodbackend.item.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;

public enum ItemFlag {
    FRAGILE("fragile"), FIRST_DAY("first_day"), CARRY_PERSONALLY("carry_personally");

    private final String value;

    ItemFlag(String value) { this.value = value; }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ItemFlag fromValue(String value) {
        for (ItemFlag flag : values()) {
            if (flag.value.equals(value)) return flag;
        }
        throw new IllegalArgumentException("Unknown item flag");
    }

    @JsonValue
    public String value() { return value; }
}
