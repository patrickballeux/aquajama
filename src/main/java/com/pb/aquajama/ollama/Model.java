/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pb.aquajama.ollama;

/**
 *
 * @author patrickballeux
 */
public record Model(String name, boolean canUseVision, boolean canThink) {

    public String toString() {
        var label = name;
        if (canUseVision) {
            label += " vision";
        }
        if (canThink) {
            label += " think";
        }

        return label;
    }
}
