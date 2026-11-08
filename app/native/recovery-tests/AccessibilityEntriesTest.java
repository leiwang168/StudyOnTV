package com.studyontv.tv;

/** Runs on a plain JDK; verifies recovery never removes another app's accessibility service. */
public final class AccessibilityEntriesTest {
    public static void main(String[] args) {
        String me = "com.lion.studyontv/com.studyontv.tv.WatchAccessibilityService";
        String others = "app.orchard.tv/.guard.HomeGuardService:com.xiaomi.voicecontrol/.VoiceAccessibilityService";
        require(AccessibilityEntries.append(null, me).equals(me), "initial empty setting");
        require(AccessibilityEntries.append("null", me).equals(me), "shell null representation");
        require(AccessibilityEntries.remove(others + ":" + me, me).equals(others), "keep Aerial and voice assistant");
        require(!AccessibilityEntries.contains("com.studyontv.tv/.WatchAccessibilityService", me), "official package is not personal package");
        String shorthand = "app.orchard.tv/.guard.HomeGuardService";
        String full = "app.orchard.tv/app.orchard.tv.guard.HomeGuardService";
        require(AccessibilityEntries.append(shorthand, full).equals(shorthand), "do not duplicate short components");
        require(AccessibilityEntries.remove(shorthand + ":" + me, full).equals(me), "recognize short components on removal");
        String removed = AccessibilityEntries.remove(others + ":" + me, me);
        String currentAfterAnotherAppChangedSettings = removed + ":org.example.reader/.ReaderService";
        String restored = AccessibilityEntries.append(currentAfterAnotherAppChangedSettings, me);
        require(restored.equals(others + ":org.example.reader/.ReaderService:" + me), "preserve concurrent settings changes at reinsert");
        require(AccessibilityEntries.append(restored, me).equals(restored), "recovery is idempotent");
        require(AccessibilityEntries.remove(me + ":" + me + ":" + others, me).equals(others), "remove stale duplicates only for our service");
        System.out.println("AccessibilityEntriesTest: 9 checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
