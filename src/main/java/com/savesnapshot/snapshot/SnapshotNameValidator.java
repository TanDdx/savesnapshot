package com.savesnapshot.snapshot;

import java.util.Set;
import java.util.regex.Pattern;

/** 纯逻辑：快照命名合法性校验。不引用任何 MC 类，可单测。 */
public final class SnapshotNameValidator {
    private static final Pattern ILLEGAL_CHARS = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");
    public static final int MAX_LENGTH = 64;

    private SnapshotNameValidator() {
    }

    /** @return null 表示合法；否则返回面向用户的错误提示文本。 */
    public static String validate(String name, Set<String> existingNames) {
        if (name == null || name.isBlank()) {
            return "名称不能为空";
        }
        String trimmed = name.trim();
        if (!trimmed.equals(name) && trimmed.endsWith(" ")) {
            return "名称不能以空格结尾";
        }
        if (trimmed.length() > MAX_LENGTH) {
            return "名称过长（最多 " + MAX_LENGTH + " 个字符）";
        }
        if (ILLEGAL_CHARS.matcher(trimmed).find()) {
            return "名称包含非法字符: \\ / : * ? \" < > |";
        }
        if (trimmed.startsWith(".") || trimmed.endsWith(".") || name.endsWith(" ")) {
            return "名称不能以点开头、以点或空格结尾";
        }
        if (WINDOWS_RESERVED.contains(trimmed.toLowerCase())) {
            return "名称是系统保留字";
        }
        if (existingNames.contains(trimmed)) {
            return "已存在同名快照";
        }
        return null;
    }

    /** 输入归一化：去首尾空白。 */
    public static String sanitize(String name) {
        return name == null ? "" : name.trim();
    }
}
