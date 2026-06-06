package com.mysql.pocketsql.engine;

public class SqlSyntaxException extends Exception {
    private final int position;

    public SqlSyntaxException(String message, int position) {
        super(message + " (at position " + position + ")");
        this.position = position;
    }

    public int getPosition() {
        return position;
    }
}
