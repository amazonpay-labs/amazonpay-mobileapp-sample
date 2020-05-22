package com.amazon.pay.sample.android_app;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.browser.customtabs.CustomTabsIntent;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private String old_secureWebviewSessionId = "";

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);

        // enable JavaScript
        webView.getSettings().setJavaScriptEnabled(true);

        // enable Web Storage
        webView.getSettings().setDomStorageEnabled(true);

        // allow redirect by JavaScript
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // redirect console log into AndroidStudio's Run console.
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cslMsg) {
                Log.d("MyApp", cslMsg.message() + " at line "
                        + cslMsg.lineNumber() + " of "
                        + cslMsg.sourceId());
                return super.onConsoleMessage(cslMsg);
            }
        });

        webView.addJavascriptInterface(this, "androidApp");

        webView.loadUrl("https://10.0.2.2:8443/androidApp/cart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        Map<String, String> p = AmazonPayActivity.params;
        if (p != null) {
            AmazonPayActivity.params = null;
            if (old_secureWebviewSessionId.equals(p.get("old_secureWebviewSessionId"))) {
                webView.loadUrl("javascript:purchase('" + p.get("secureWebviewSessionId")
                        + "', '" + p.get("accessToken")
                        + "', '" + p.get("orderReferenceId") + "')");
            } else {
                webView.loadUrl("https://10.0.2.2:8443/error");
            }
        }
    }

    @JavascriptInterface
    public void handle(String secureWebviewSessionId) {
        Log.d("[JsCallback]", secureWebviewSessionId);
        invokeButtonPage(secureWebviewSessionId);
    }

    private void invokeButtonPage(String secureWebviewSessionId) {
        old_secureWebviewSessionId = secureWebviewSessionId;
        invokeSecureWebview("https://10.0.2.2:8443/button?secureWebviewSessionId=" + secureWebviewSessionId);
    }

    private void invokeSecureWebview(String url) {
        CustomTabsIntent tabsIntent = new CustomTabsIntent.Builder().build();

        // 起動するBrowserにChromeを指定
        // Note: Amazon Payでは他のブラウザがサポート対象に入っていないため、ここではChromeを指定している.
        // [参考] https://pay.amazon.com/jp/help/202030010
        // もしその他のChrome Custom Tabs対応のブラウザを起動する必要がある場合には、下記リンク先ソースなどを参考に実装する.
        // [参考] https://github.com/GoogleChrome/custom-tabs-client/blob/master/shared/src/main/java/org/chromium/customtabsclient/shared/CustomTabsHelper.java#L64
        tabsIntent.intent.setPackage("com.android.chrome");

        // 別のActivityへの遷移時に、自動的にChrome Custom Tabsを終了させるためのフラグ設定.
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Chrome Custom Tabs終了時に、Historyとして残らないようにするためのフラグ設定.
        tabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);

        // Chrome Custom Tabsの起動
        tabsIntent.launchUrl(getApplicationContext(), Uri.parse(url));
    }

}
