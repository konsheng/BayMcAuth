package com.baymc.auth.common.blacklist;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 用户名和密码黑名单
 *
 * <p>匹配方式采用小写 contains, 用于拦截明显危险名称和弱密码片段
 * 服务本身只持有内存快照, 加载和降级由 BlacklistLoader 完成
 */
public final class BlacklistService {
    private final Set<String> usernameKeywords = ConcurrentHashMap.newKeySet();
    private final Set<String> passwordKeywords = ConcurrentHashMap.newKeySet();

    public void replaceUsernameKeywords(Collection<String> keywords) {
        usernameKeywords.clear();
        addAll(usernameKeywords, keywords);
    }

    public void replacePasswordKeywords(Collection<String> keywords) {
        passwordKeywords.clear();
        addAll(passwordKeywords, keywords);
    }

    public boolean usernameBlocked(String username) {
        return contains(usernameKeywords, username);
    }

    public boolean passwordBlocked(String password) {
        return contains(passwordKeywords, password);
    }

    private static void addAll(Set<String> target, Collection<String> keywords) {
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isBlank()) {
                target.add(keyword.toLowerCase(Locale.ROOT));
            }
        }
    }

    private static boolean contains(Set<String> keywords, String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (!keyword.isBlank() && lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
