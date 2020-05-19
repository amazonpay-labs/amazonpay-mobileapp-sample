package com.amazon.pay.sample.server.utils;

import com.amazon.pay.sample.server.storage.CacheMock;
import com.amazon.pay.sample.server.storage.DatabaseMock;

import java.util.UUID;

/**
 * 受注Objectアクセス用のtokenを生成し、受注Objectを管理するUtility.
 *
 * <pre>
 *     [アプリ-アプリ内ブラウザ間の処理では受註IDではなく、tokenを発行して管理する理由]
 *     一般に受注IDは重複しないことが主な要件であるため、「日付＋シーケンス番号」のような、
 *     採番された値を推測できる実装であることが多い.
 *     本サンプルでは受注を特定するためにtokenをURLパラメタやCookie, Intentのパラメタ等で
 *     指定しており、もしこれを推測可能な受注IDでやってしまうと、推測した値を埋め込んでアクセス
 *     すれば他人の受注情報を見たり更新したりできるセキュリティバグになってしまう.
 *     よって、本サンプルでは受注情報を充分に推測困難なtokenをキーとしてCacheに登録することで、
 *     外部から直接受注IDを指定できなくして、セキュリティを担保している.
 * </pre>
 */
public class TokenUtil {

    /**
     * tokenを生成し、パラメタの受注IDと紐付けてから返却する.
     * @param order 受注Object
     * @return token
     */
    public static String storeByToken(DatabaseMock.Order order) {
        String token = createToken();
        CacheMock.put(token, order);
        return token;
    }

    /**
     * パラメタのtokenに紐付けられた受注IDを返却する.
     * @param token 受注Objectアクセス用のtoken
     * @return 受注Object
     */
    public static DatabaseMock.Order get(String token) {
        return CacheMock.get(token);
    }

    /**
     * パラメタのtokenに紐づいた受注Objectをコピーし、cacheに登録して新しいtokenを返却する.
     * @param token 受注Objectアクセス用のtoken
     * @return コピーされた受注Objectアクセス用token
     */
    public static String copy(String token) {
        return storeByToken(CacheMock.get(token).clone());
    }

    /**
     * パラメタのtokenに紐付けられた受注IDが存在するか判定する.
     * @param token 受注Objectアクセス用のtoken
     * @return 受注IDが存在するときtrue, 削除されていればfalse
     */
    public static boolean exists(String token) {
        return CacheMock.get(token) != null;
    }

    /**
     * 受注Objectアクセス用のtokenを生成する.
     * tokenは推測困難であることが求められるので、下記の要件を満たす必要がある.
     * <ul>
     *     <li>暗号論的に強度の高い擬似乱数生成器により生成された乱数部分を含んでいる</li>
     *     <li>上記乱数部分の桁数が十分な長さである.(※一般的なSessionID・tokenの実装で128bit前後)</li>
     * </ul>
     * ここで採用しているUUID v4のJDKによる実装は上記を満たしている.
     *   参考: https://docs.oracle.com/javase/jp/8/docs/api/java/util/UUID.html#randomUUID--
     * もし別の実装を採用する場合には、上記を満たしているか確認すること.<br/>
     * Note: UUID v4の乱数部分の仕様では、生成に暗号論的な強度が十分な乱数を使うことを推奨しているものの、
     * 規定はしていない.
     *   参考: https://tools.ietf.org/html/rfc4122#section-4.4
     * つまり実装によっては推測可能な乱数生成方法を採用している可能性もある. よって、JDK以外のUUID v4の
     * 実装の採用を検討する場合には乱数生成の暗号論的な強度が十分かを確認すること.
     * @return 受注Objectアクセス用のtoken
     */
    public static String createToken() {
        return UUID.randomUUID().toString();
    }
}
