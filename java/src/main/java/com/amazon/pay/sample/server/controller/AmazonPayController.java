package com.amazon.pay.sample.server.controller;

import com.amazon.pay.Client;
import com.amazon.pay.Config;
import com.amazon.pay.exceptions.AmazonServiceException;
import com.amazon.pay.impl.PayClient;
import com.amazon.pay.impl.PayConfig;
import com.amazon.pay.request.AuthorizeRequest;
import com.amazon.pay.request.ConfirmOrderReferenceRequest;
import com.amazon.pay.request.GetOrderReferenceDetailsRequest;
import com.amazon.pay.request.SetOrderReferenceDetailsRequest;
import com.amazon.pay.response.parser.AuthorizeResponseData;
import com.amazon.pay.response.parser.ConfirmOrderReferenceResponseData;
import com.amazon.pay.response.parser.GetOrderReferenceDetailsResponseData;
import com.amazon.pay.response.parser.SetOrderReferenceDetailsResponseData;
import com.amazon.pay.sample.server.storage.DatabaseMock;
import com.amazon.pay.sample.server.storage.DatabaseMock.Item;
import com.amazon.pay.sample.server.storage.DatabaseMock.SecureWebviewSession;
import com.amazon.pay.sample.server.utils.TokenUtil;
import com.amazon.pay.types.CurrencyCode;
import com.amazon.pay.types.Region;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Controller
public class AmazonPayController {

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${client.id}")
    private String clientId;

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${seller.id}")
    private String sellerId;

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${access.key}")
    private String accessKey;

    /**
     * merchant.propertiesからの読み込み
     */
    @Value("${secret.key}")
    private String secretKey;

    /**
     * 受注入力情報画面を表示.
     *
     * @param client    アクセス元のOS. android/ios/pc のどれか
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/{client}/cart")
    public String order(@PathVariable String client, Model model) {
        System.out.println("[cart] " + client);

        // 画面生成templateへの値の受け渡し
        model.addAttribute("client", client);

        return "cart";
    }

    /**
     * NATIVEの受註登録画面から呼び出されて、受注Objectを生成・保存する.
     *
     * @param client    アクセス元のOS. android/ios/pc のどれか
     * @param hd8  Kindle File HD8の購入数
     * @param hd10 Kindle File HD10の購入数
     * @return 受注Objectへのアクセス用token
     */
    @ResponseBody
    @PostMapping("/secureWebviewSession")
    public Map<String, String> createSecureWebviewSession(@RequestParam String client, @RequestParam int hd8, @RequestParam int hd10) {
        System.out.println("[createOrderREST] " + client + ", " + hd8 + ", " + hd10);
        return doCreateSecureWebviewSession(client, hd8, hd10);
    }

    private Map<String, String> doCreateSecureWebviewSession(String client, int hd8, int hd10) {

        // Sessionの生成
        SecureWebviewSession swSession = new DatabaseMock.SecureWebviewSession();
        swSession.client = client;
        swSession.items = new ArrayList<>();
        if (hd8 > 0) {
            swSession.items.add(new Item("item0008", "Fire HD8", hd8, 8980));
        }
        if (hd10 > 0) {
            swSession.items.add(new Item("item0010", "Fire HD10", hd10, 15980));
        }
        swSession.price = swSession.items.stream().mapToLong(item -> item.summary).sum();
        swSession.priceTaxIncluded = (long) (1.08 * swSession.price);

        // sessionの保存
        DatabaseMock.storeSession(swSession);

        // sessionのcacheへの保存と、アクセス用idの返却
        String secureWebviewSessionId = TokenUtil.storeByToken(swSession);
        Map<String, String> map = new HashMap<>();
        map.put("secureWebviewSessionId", secureWebviewSessionId);
        return map;
    }

    /**
     * Chrome Custom Tabsの起動時に呼び出されて、ボタンWidget表示画面を表示する.
     * Note: ボタンWidget表示画面は見た目上ではLoading画像のみが表示されており、次の購入確定画面に自動的に遷移する.
     * 裏では非表示のボタンWidgetを読み込まれており、読み込みが完了すると自動的にJavaScriptでボタンWidgetがクリックされる.
     *
     * @param secureWebviewSessionId    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/button")
    public String button(@RequestParam String secureWebviewSessionId, HttpServletResponse response, Model model) {
        System.out.println("[button] secureWebviewSessionId: " + secureWebviewSessionId);

        // redirect処理でconfirm_orderに戻ってきたときにtokenが使用できるよう、Cookieに登録
        // Note: Session Fixation 対策に、tokenをこのタイミングで更新する.
        Cookie cookie = new Cookie("secureWebviewSessionId", TokenUtil.copy(secureWebviewSessionId));
        cookie.setSecure(true);
        response.addCookie(cookie);

        // 更新前のtokenも、APPに戻ったタイミングでの確認用に保持する
        cookie = new Cookie("old_secureWebviewSessionId", secureWebviewSessionId);
        cookie.setSecure(true);
        response.addCookie(cookie);

        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "button";
    }

    /**
     * ボタンWidget表示画面から呼び出されて、アドレスWidget・支払いWidgetのある購入確定画面を表示する.
     *
     * @param secureWebviewSessionId    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/confirm_order")
    public String confirmOrder(@CookieValue(required = false) String secureWebviewSessionId, @CookieValue(required = false) String old_secureWebviewSessionId, HttpServletResponse response, Model model) {
        if (secureWebviewSessionId == null) return "dummy"; // Chrome Custom Tabsが本URLを勝手にreloadすることがあるので、その対策.
        System.out.println("[confirm_order] secureWebviewSessionId = " + secureWebviewSessionId + ", old_secureWebviewSessionId = " + old_secureWebviewSessionId);

        model.addAttribute("secureWebviewSessionId", secureWebviewSessionId);
        model.addAttribute("old_secureWebviewSessionId", old_secureWebviewSessionId);
        model.addAttribute("swSession", TokenUtil.get(secureWebviewSessionId));
        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "confirm_order";
    }

    /**
     * 購入確定画面でアドレスWidgetで住所を選択した時にAjaxで非同期に呼び出されて、Amazon Pay APIから取得した住所情報より送料を計算する.
     *
     * @param secureWebviewSessionId            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @return 計算した送料・総合計金額を含んだJSON
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @ResponseBody
    @PostMapping("/calc_postage")
    public Map<String, String> calcPostage(@RequestParam String secureWebviewSessionId, @RequestParam String orderReferenceId
            , @RequestParam String accessToken) throws AmazonServiceException {
        System.out.println("[calc_postage]: " + secureWebviewSessionId + ", " + orderReferenceId + ", " + accessToken);

        Config config = new PayConfig()
                .withSellerId(sellerId)
                .withAccessKey(accessKey)
                .withSecretKey(secretKey)
                .withCurrencyCode(CurrencyCode.JPY)
                .withSandboxMode(true)
                .withRegion(Region.JP);

        Client client = new PayClient(config);

        //--------------------------------------------
        // Amazon Pay側のOrderReferenceの詳細情報の取得
        //--------------------------------------------
        GetOrderReferenceDetailsRequest request = new GetOrderReferenceDetailsRequest(orderReferenceId);
        // request.setAddressConsentToken(paramMap.get("access_token")); // Note: It's old! should be removed!
        request.setAccessToken(accessToken);
        GetOrderReferenceDetailsResponseData response = client.getOrderReferenceDetails(request);

        SecureWebviewSession session = TokenUtil.get(secureWebviewSessionId);
        session.postage = calcPostage(response);
        session.totalPrice = session.priceTaxIncluded + session.postage;
        DatabaseMock.storeSession(session);

        Map<String, String> map = new HashMap<>();
        map.put("postage", comma(session.postage));
        map.put("totalPrice", comma(session.totalPrice));
        return map;
    }

    private long calcPostage(GetOrderReferenceDetailsResponseData response) {
        String stateOrRegion = response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion();
        if (stateOrRegion.equals("沖縄県") || stateOrRegion.equals("北海道")) {
            return 1080;
        } else {
            return 540;
        }
    }

    /**
     * 購入確定画面から呼び出されて、購入処理を実行してThanks画面へ遷移させる.
     *
     * @param secureWebviewSessionId            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @param model            画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @ResponseBody
    @PostMapping("/purchase")
    public Map<String, String> purchase(@RequestParam String secureWebviewSessionId, @RequestParam String accessToken, @RequestParam String orderReferenceId, Model model) throws AmazonServiceException {
        System.out.println("[purchase] " + secureWebviewSessionId +  ", " + orderReferenceId + ", " + accessToken);

        SecureWebviewSession session = TokenUtil.get(secureWebviewSessionId);
        session.orderReferenceId = orderReferenceId;

        Config config = new PayConfig()
                .withSellerId(sellerId)
                .withAccessKey(accessKey)
                .withSecretKey(secretKey)
                .withCurrencyCode(CurrencyCode.JPY)
                .withSandboxMode(true)
                .withRegion(Region.JP);

        Client client = new PayClient(config);

        //--------------------------------------------
        // Amazon Pay側のOrderReferenceの詳細情報の取得
        //--------------------------------------------
        GetOrderReferenceDetailsRequest request = new GetOrderReferenceDetailsRequest(orderReferenceId);
        // request.setAddressConsentToken(paramMap.get("access_token")); // Note: It's old! should be removed!
        request.setAccessToken(accessToken);
        GetOrderReferenceDetailsResponseData response = client.getOrderReferenceDetails(request);

        System.out.println("<GetOrderReferenceDetailsResponseData>");
        System.out.println(response);
        System.out.println("</GetOrderReferenceDetailsResponseData>");

        // Amazon Pay側の受注詳細情報を、受注Objectに反映
        session.buyerName = emptyIfNull(response.getDetails().getBuyer().getName());
        session.buyerEmail = emptyIfNull(response.getDetails().getBuyer().getEmail());
        session.buyerPhone = emptyIfNull(response.getDetails().getBuyer().getPhone());
        session.destinationName = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getName());
        session.destinationPhone = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPhone());
        session.destinationPostalCode = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPostalCode());
        session.destinationStateOrRegion = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion());
        session.destinationCity = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getCity());
        session.destinationAddress1 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine1());
        session.destinationAddress2 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine2());
        session.destinationAddress3 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine3());

        //--------------------------------
        // OrderReferenceの詳細情報の設定
        //--------------------------------
        SetOrderReferenceDetailsRequest setOrderReferenceDetailsRequest = new SetOrderReferenceDetailsRequest(orderReferenceId, String.valueOf(session.priceTaxIncluded));

        //set optional parameters
        setOrderReferenceDetailsRequest.setOrderCurrencyCode(CurrencyCode.JPY);
        setOrderReferenceDetailsRequest.setSellerNote(String.valueOf(session.items));
        setOrderReferenceDetailsRequest.setSellerOrderId(session.myOrderId);
        setOrderReferenceDetailsRequest.setStoreName("My Sweet Shop");

        //call API
        SetOrderReferenceDetailsResponseData responseSet = client.setOrderReferenceDetails(setOrderReferenceDetailsRequest);

        System.out.println("<SetOrderReferenceDetailsResponseData>");
        System.out.println(responseSet);
        System.out.println("</SetOrderReferenceDetailsResponseData>");

        //--------------------------------
        // OrderReferenceの確認
        //--------------------------------
        ConfirmOrderReferenceResponseData responseCon = client.confirmOrderReference(new ConfirmOrderReferenceRequest(orderReferenceId));
        // Note: it was not String, but request object!

        System.out.println("<ConfirmOrderReferenceResponseData>");
        System.out.println(responseCon);
        System.out.println("</ConfirmOrderReferenceResponseData>");

        //----------------------------------
        // Authorize(オーソリ, 与信枠確保)処理
        //----------------------------------
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(orderReferenceId, generateId(), String.valueOf(session.totalPrice));

        //Set Optional parameters
        authorizeRequest.setAuthorizationCurrencyCode(CurrencyCode.JPY); //Overrides currency code set in Client
        authorizeRequest.setSellerAuthorizationNote("You can write something here.");
        authorizeRequest.setTransactionTimeout("0"); //Set to 0 for synchronous mode
//        authorizeRequest.setCaptureNow(true); // Set this to true if you want to capture the amount in the same API call

        //Call Authorize API
        AuthorizeResponseData authResponse = client.authorize(authorizeRequest);

        System.out.println("<AuthorizeResponseData>");
        System.out.println(authResponse);
        System.out.println("</AuthorizeResponseData>");

        // 受注Objectのステータスをオーソリ完了に設定して保存
        session.myOrderStatus = "AUTHORIZED";
        DatabaseMock.storeSession(session);

        Map<String, String> map = new HashMap<>();
        map.put("status", "200");
        map.put("message", "The order placed.");
        return map;
    }

    /**
     * Thanks画面Activity内のWebViewから呼び出されて、受注Objectの詳細情報を表示する.
     *
     * @param secureWebviewSessionId 受注Objectへのアクセス用token
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/thanks")
    public String thanks(@RequestParam String secureWebviewSessionId, Model model) {
        System.out.println("[thanks] token=" + secureWebviewSessionId);

        SecureWebviewSession session = TokenUtil.get(secureWebviewSessionId);

        model.addAttribute("swSession", session);
        model.addAttribute("client", session.client);

        return "thanks";
    }

    /**
     * Thanks画面Activity内のWebViewから呼び出されて、受注Objectの詳細情報を表示する.
     *
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/error")
    @GetMapping("/error")
    public String error() {
        System.out.println("[error] ");

        return "error";
    }

    private String generateId() {
        return String.valueOf(Math.abs(ThreadLocalRandom.current().nextLong()));
    }

    private String emptyIfNull(String s) {
        return s == null ? "" : s;
    }

    private String comma(long num) {
        return comma(String.valueOf(num));
    }

    private String comma(String num) {
        int index = num.length() - 3;
        if (index <= 0) return num;
        return comma(num.substring(0, index)) + "," + num.substring(index);
    }
}
