package org.ethelred.kiwiproc.meta;

import java.sql.JDBCType;

public record ArrayComponent(JDBCType jdbcType, String dbType) {}
