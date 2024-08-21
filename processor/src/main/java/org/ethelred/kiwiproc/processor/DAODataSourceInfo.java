package org.ethelred.kiwiproc.processor;

public record DAODataSourceInfo(String dataSourceName, String packageName) {
    public String className(String suffix) {
        return "$" + Util.toTitleCase(dataSourceName) + "$" + suffix;
    }
}
