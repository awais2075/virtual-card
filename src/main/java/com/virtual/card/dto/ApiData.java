package com.virtual.card.dto;

public class ApiData<T> {
    public final T data;

    public ApiData(T data) {
        this.data = data;
    }
}
