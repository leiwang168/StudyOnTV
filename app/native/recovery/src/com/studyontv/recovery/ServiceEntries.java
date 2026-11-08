package com.studyontv.recovery;

import java.util.ArrayList;
import java.util.List;

final class ServiceEntries {
    private ServiceEntries() {
    }

    static boolean contains(String string, String string2) {
        for (String string3 : ServiceEntries.entries((String)string)) {
            if (!ServiceEntries.canonical((String)string3).equals(ServiceEntries.canonical((String)string2))) continue;
            return true;
        }
        return false;
    }

    static String append(String string, String string2) {
        List list = ServiceEntries.entries((String)string);
        if (!ServiceEntries.contains((String)string, (String)string2)) {
            list.add(string2);
        }
        return ServiceEntries.join((List)list);
    }

    static String remove(String string, String string2) {
        ArrayList<String> arrayList = new ArrayList<String>();
        for (String string3 : ServiceEntries.entries((String)string)) {
            if (ServiceEntries.canonical((String)string3).equals(ServiceEntries.canonical((String)string2))) continue;
            arrayList.add(string3);
        }
        return ServiceEntries.join(arrayList);
    }

    private static List<String> entries(String string) {
        ArrayList<String> arrayList = new ArrayList<String>();
        if (string == null || string.equals("null")) {
            return arrayList;
        }
        for (String string2 : string.split(":")) {
            if (string2.trim().isEmpty()) continue;
            arrayList.add(string2);
        }
        return arrayList;
    }

    private static String canonical(String string) {
        int n = string.indexOf(47);
        if (n >= 0 && n + 1 < string.length() && string.charAt(n + 1) == '.') {
            return string.substring(0, n + 1) + string.substring(0, n) + string.substring(n + 1);
        }
        return string;
    }

    private static String join(List<String> list) {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : list) {
            if (stringBuilder.length() > 0) {
                stringBuilder.append(':');
            }
            stringBuilder.append(string);
        }
        return stringBuilder.toString();
    }
}
