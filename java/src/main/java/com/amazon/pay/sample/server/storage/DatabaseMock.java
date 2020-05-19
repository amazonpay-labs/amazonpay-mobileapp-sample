package com.amazon.pay.sample.server.storage;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 受注情報を保持するDatabaseのMock.
 * 実際にはOracle・MySQL等のRDBMSを使用することを想定している.
 * Note: サンプルなので、メモリ上にデータを保持しています.よってサーバーを再起動したら情報が消えるのでご注意ください。
 */
public class DatabaseMock {

    /**
     * 購入した商品の情報を保持するテーブルを想定したclass.
     */
    public static class Item {
        public final String itemId;
        public final String itemName;
        public final int number;
        public final long price;
        public final long summary;

        public Item(String itemId, String itemName, int number, long price) {
            this.itemId = itemId;
            this.itemName = itemName;
            this.number = number;
            this.price = price;
            this.summary = number * price;
        }

        public String toString() {
            return String.format("{\"itemId\":\"%s\",\"itemName\":\"%s\",\"number\":%d,\"price\":%d,\"summary\":%d}"
                    , itemId, itemName, number, price, summary);
        }
    }

    /**
     * 受注Objectを保持するテーブルを想定したclass.
     * Itemクラスのレコード(インスタンス)と１対多である.
     */
    public static class Order implements Cloneable{
        public String myOrderId;
        public String myOrderStatus;
        public String appKey;
        public String os;
        public List<Item> items;
        public long price;
        public long priceTaxIncluded;
        public long postage;
        public long totalPrice;
        public String orderReferenceId;
        public String buyerName;
        public String buyerEmail;
        public String buyerPhone;
        public String destinationName;
        public String destinationPostalCode;
        public String destinationStateOrRegion;
        public String destinationCity;
        public String destinationAddress1;
        public String destinationAddress2;
        public String destinationAddress3;
        public String destinationPhone;

        @Override
        public Order clone() {
            try {
                return (Order)super.clone();
            } catch (CloneNotSupportedException e) {
                // Unreachable...
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * Note: 本サンプルでは受注情報をメモリ上に保持するため、メモリが枯渇しないように念の為保持できる上限を定めている.
     */
    private static final Cache<String, Order> cache = CacheBuilder.newBuilder()
            .maximumSize(10000)
            .build();

    private static final AtomicLong atomicLong = new AtomicLong();

    /**
     * 受注Objectの保存.
     *
     * @param order 受注Object
     * @return 受注ID
     */
    public static String storeOrder(Order order) {
        if (order.myOrderId == null) {
            // 受注IDの採番
            order.myOrderId = "my-order-" + atomicLong.incrementAndGet();
        }
        cache.put(order.myOrderId, order);
        return order.myOrderId;
    }

    /**
     * 受注Objectの取得
     * @param myOrderId 受注ID
     * @return 受注Object
     */
    public static Order getOrder(String myOrderId) {
        return cache.getIfPresent(myOrderId);
    }

}
