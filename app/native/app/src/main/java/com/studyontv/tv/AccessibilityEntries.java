package com.studyontv.tv;

import java.util.ArrayList;
import java.util.List;

/** Edits only our own service, preserving other apps and short component names. */
final class AccessibilityEntries {
    private AccessibilityEntries() {}

    static List<String> entries(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.equals("null")) return result;
        for (String entry : value.split(":")) if (!entry.trim().isEmpty()) result.add(entry);
        return result;
    }

    static boolean contains(String value, String target) {
        for (String entry : entries(value)) if (canonical(entry).equals(canonical(target))) return true;
        return false;
    }

    static String append(String value, String target) {
        List<String> result = entries(value);
        if (!contains(value, target)) result.add(target);
        return join(result);
    }

    static String remove(String value, String target) {
        List<String> result = new ArrayList<>();
        for (String entry : entries(value)) if (!canonical(entry).equals(canonical(target))) result.add(entry);
        return join(result);
    }

    private static String canonical(String component) {
        int slash = component.indexOf('/');
        if (slash >= 0 && slash + 1 < component.length() && component.charAt(slash + 1) == '.') {
            return component.substring(0, slash + 1) + component.substring(0, slash) + component.substring(slash + 1);
        }
        return component;
    }

    private static String join(List<String> entries) {
        StringBuilder result = new StringBuilder();
        for (String entry : entries) {
            if (result.length() > 0) result.append(':');
            result.append(entry);
        }
        return result.toString();
    }
}
