package com.example.customtelinkapp.model;

import java.io.Serializable;

public class GroupInfo implements Serializable {
    /**
     * Group name
     */
    public String name;

    /**
     * Group address
     */
    public int address;

    public boolean selected = false;
}
