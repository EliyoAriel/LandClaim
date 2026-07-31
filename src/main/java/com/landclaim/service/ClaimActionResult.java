package com.landclaim.service;

import net.kyori.adventure.text.Component;

import java.util.List;

public record ClaimActionResult(boolean success, List<Component> messages) {

    public static ClaimActionResult ok(Component... messages) {
        return new ClaimActionResult(true, List.of(messages));
    }

    public static ClaimActionResult fail(Component message) {
        return new ClaimActionResult(false, List.of(message));
    }
}
