/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.pb.aquajama.data;

import java.util.prefs.Preferences;

/**
 *
 * @author patrickballeux
 */
public class Config {
    private final Preferences userPrefs;
    private enum eKeys {
        last_model_name;
    }
    
    public Config(){
        this.userPrefs = Preferences.userNodeForPackage(Config.class);
    }
    public  String getLastModelName(){
        return userPrefs.get(eKeys.last_model_name.name(), null);
    }
    public  void setLastModelName(String name){
        userPrefs.put(eKeys.last_model_name.name(), name);
    }
}
