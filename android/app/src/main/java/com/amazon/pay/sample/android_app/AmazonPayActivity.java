package com.amazon.pay.sample.android_app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AmazonPayActivity extends AppCompatActivity {

    static volatile Map<String, String> params = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_amazon_pay);

        // Applinksからintentを取得
        Intent intent = getIntent();
        String appLinkAction = intent.getAction();
        Uri appLinkData = intent.getData();
        Log.d("[AppLink]", appLinkAction);
        Log.d("[AppLink]", "" + appLinkData);

        //　URLパラメータをparse
        Map<String, String> map = new HashMap<>();
        for (String kEqV : appLinkData.getEncodedQuery().split("&")) {
            String[] kv = kEqV.split("=");
            map.put(kv[0], kv[1]);
        }
        params = Collections.unmodifiableMap(map);

        this.finish();
    }
}
