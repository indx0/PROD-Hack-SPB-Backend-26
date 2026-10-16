package ru.example.prodbackend.box.entity;

public enum BoxStatus {
    PACKING,
    PACKED,
    LOADED,
    DELIVERED,
    UNPACKED;

    public boolean canTransitionTo(BoxStatus next) {
        return next != null && (next == this || next.ordinal() == ordinal() + 1);
    }
}
