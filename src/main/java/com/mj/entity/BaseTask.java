package com.mj.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.io.Serializable;

public class BaseTask implements Serializable {

    @JsonIgnore
    private final transient Object lock = new Object();

    public void sleep() throws InterruptedException {
        synchronized (this.lock) {
            this.lock.wait();
        }
    }

    public void awake() {
        synchronized (this.lock) {
            this.lock.notifyAll();
        }
    }
}
