package com.example.jialechatweb.error;

import java.io.Serializable;

public class ErrorAggregate implements Serializable {
    private static final long serialVersionUID = 1L;
    private String key;
    private Integer count;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
