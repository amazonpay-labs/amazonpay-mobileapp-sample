package com.amazon.pay.sample.server.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

/**
 * 受注Objectアクセス用tokenと受注IDを紐づけるのに使用するCacheプロダクトのMock.
 * 実際にはRedisやCouchbaseやMemcache、またはOracle・MySQL等のRDBMSを使用することを想定している.
 * Note: サンプルなので、メモリ上にデータを保持しています.よってサーバーを再起動したら情報が消えるのでご注意ください。
 */
public class CacheMock {

    /**
     * 本サンプルでは、tokenの有効期限は最終アクセス後1時間とする.
     * tokenはセキュリティを確保するために導入しているものなので、実際のシステムでも有効期限を定めた方が良い.
     * Note: 本サンプルでは実装していないが、実際のシステムでは有効期限を超えてExpireしたtokenへのアクセスが
     * ありエラーとなった場合には、その旨をユーザに伝えるエラー画面を表示するべきである.
     */
    private static final Cache<String, DatabaseMock.Order> cache = CacheBuilder.newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build();

    public static DatabaseMock.Order get(String key) {
        return cache.getIfPresent(key);
    }

    public static void put(String key, DatabaseMock.Order value) {
        cache.put(key, value);
    }
}
