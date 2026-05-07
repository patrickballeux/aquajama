/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pb.aquajama.ollama;

/**
 *
 * @author patrickballeux
 */
public record Model(String name, long sizeBytes, boolean canUseVision, boolean canUseTools, boolean canThink) {

    public String toString() {
        var label = name;
        if (sizeBytes > 0) {
            label += " [" + humanSize(sizeBytes) + "]";
        }
        if (canUseVision) {
            label += " [vision]";
        }
        if (canUseTools) {
            label += " [tools]";
        }
        if (canThink) {
            label += " [thinking]";
        }

        return label;
    }

    public boolean supports(Capability capability) {
        return switch (capability) {
            case VISION -> canUseVision;
            case TOOLS -> canUseTools;
            case THINKING -> canThink;
        };
    }

    public enum Capability {
        VISION,
        TOOLS,
        THINKING
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;

        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < units.length - 1);

        return "%.1f %s".formatted(value, units[unit]);
    }
}
