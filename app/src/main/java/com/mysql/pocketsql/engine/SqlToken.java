package com.mysql.pocketsql.engine;

public class SqlToken {
    public enum Type {
        KEYWORD, IDENTIFIER, NUMBER, STRING, SYMBOL, EOF
    }

    public final Type type;
    public final String value;
    public final int position;

    public SqlToken(Type type, String value, int position) {
        this.type = type;
        this.value = value;
        this.position = position;
    }

    @Override
    public String toString() {
        return type + "('" + value + "', pos=" + position + ")";
    }
}
