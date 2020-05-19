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
import com.amazon.pay.sample.server.storage.DatabaseMock.Order;
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
     * @param os    アクセス元のOS. android/ios/pc のどれか
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/{os}/order")
    public String order(@PathVariable String os, Model model) {
        System.out.println("[order] " + os);

        // 画面生成templateへの値の受け渡し
        model.addAttribute("os", os);

        return "order";
    }

    /**
     * order.htmlから呼び出されて、受注Objectを生成・保存する.
     *
     * @param os    アクセス元のOS. android/ios/pc のどれか
     * @param hd8   Kindle File HD8の購入数
     * @param hd10  Kindle File HD10の購入数
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/{os}/create_order")
    public String createOrder(@PathVariable String os, @RequestParam int hd8, @RequestParam int hd10, Model model) {
        System.out.println("[createOrder] " + os + ", " + hd8 + ", " + hd10);

        // 受注Objectの生成
        Map<String, String> map = doCreateOrder(!os.contains("-") ? os : os.substring(0, os.indexOf('-')), hd8, hd10);
        String token = map.get("token");

        // 画面生成templateへの値の受け渡し
        model.addAttribute("os", os);
        model.addAttribute("token", token);
        model.addAttribute("appKey", map.get("appKey"));
        model.addAttribute("order", TokenUtil.get(token));

        return "cart";
    }

    /**
     * NATIVEの受註登録画面から呼び出されて、受注Objectを生成・保存する.
     *
     * @param os    アクセス元のOS. android/ios/pc のどれか
     * @param hd8  Kindle File HD8の購入数
     * @param hd10 Kindle File HD10の購入数
     * @return 受注Objectへのアクセス用token
     */
    @ResponseBody
    @PostMapping("/{os}/create_order_rest")
    public Map<String, String> createOrderREST(@PathVariable String os, @RequestParam int hd8, @RequestParam int hd10) {
        System.out.println("[createOrderREST] " + os + ", " + hd8 + ", " + hd10);
        return doCreateOrder(os, hd8, hd10);
    }

    private Map<String, String> doCreateOrder(String os, int hd8, int hd10) {

        // 受注Objectの生成/更新
        Order order = new Order();
        order.os = os;
        order.items = new ArrayList<>();
        if (hd8 > 0) {
            order.items.add(new Item("item0008", "Fire HD8", hd8, 8980));
        }
        if (hd10 > 0) {
            order.items.add(new Item("item0010", "Fire HD10", hd10, 15980));
        }
        order.price = order.items.stream().mapToLong(item -> item.summary).sum();
        order.priceTaxIncluded = (long) (1.08 * order.price);
        order.myOrderStatus = "CREATED";
        order.appKey = TokenUtil.createToken();

        // 受注Objectの保存
        DatabaseMock.storeOrder(order);

        // 受注Objectのcacheへの保存と、アクセス用tokenの返却
        // Note: tokenを用いる理由については、TokenUtilのJavadoc参照.
        String token = TokenUtil.storeByToken(order);
        Map<String, String> map = new HashMap<>();
        map.put("token", token);
        map.put("appKey", order.appKey);
        return map;
    }

    /**
     * Chrome Custom Tabsの起動時に呼び出されて、ボタンWidget表示画面を表示する.
     * Note: ボタンWidget表示画面は見た目上ではLoading画像のみが表示されており、次の購入確定画面に自動的に遷移する.
     * 裏では非表示のボタンWidgetを読み込まれており、読み込みが完了すると自動的にJavaScriptでボタンWidgetがクリックされる.
     *
     * @param token    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/button")
    public String button(@RequestParam String token, @RequestParam(required = false) String mode,
                         @RequestParam(required = false) String showWidgets, HttpServletResponse response, Model model) {
        System.out.println("[button] mode: " + mode + ", token: " + token + ", showWidgets: " + showWidgets);

        // tokenが削除済みの場合(購入処理後、「戻る」で戻ってきてAmazonPayボタンがクリックされた場合)、エラーとする.
        if (!TokenUtil.exists(token)) return "error";

        // redirect処理でconfirm_orderに戻ってきたときにtokenが使用できるよう、Cookieに登録
        // Note: Session Fixation 対策に、tokenをこのタイミングで更新する.
        Cookie cookie = new Cookie("token", TokenUtil.copy(token));
        cookie.setSecure(true);
        response.addCookie(cookie);

        // 更新前のtokenも、APPに戻ったタイミングでの確認用に保持する
        cookie = new Cookie("appToken", token);
        cookie.setSecure(true);
        response.addCookie(cookie);

        // widget表示・非表示フラグ(mode=appの確認画面で、「送付先・支払い方法変更」ボタン押下時)
        if(showWidgets != null) {
            cookie = new Cookie("showWidgets", "true");
            cookie.setSecure(true);
            response.addCookie(cookie);
        }

        model.addAttribute("mode", mode);
        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "button";
    }

    /**
     * ボタンWidget表示画面から呼び出されて、アドレスWidget・支払いWidget画面を表示する.
     *
     * @param token    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/widgets")
    public String widgets(@CookieValue(required = false) String token, @CookieValue(required = false) String appToken,
                          @CookieValue(required = false) String showWidgets, HttpServletResponse response, Model model) {
        if (token == null) return "dummy"; // Chrome Custom Tabsが本URLを勝手にreloadすることがあるので、その対策.
        System.out.println("[widgets] token = " + token + ", appToken = " + appToken + ", showWidgets = " + showWidgets);

        // Cookieの削除
        removeCookie(response, "token");
        removeCookie(response, "appToken");
        removeCookie(response, "showWidgets");

        model.addAttribute("token", token);
        model.addAttribute("appToken", appToken);
        model.addAttribute("order", TokenUtil.get(token));
        model.addAttribute("showWidgets", String.valueOf(showWidgets != null));
        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "widgets";
    }

    private void removeCookie(HttpServletResponse response, String key) {
        Cookie cookie = new Cookie(key, "");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    /**
     * ボタンWidget表示画面から呼び出されて、アドレスWidget・支払いWidgetのある購入確定画面を表示する.
     *
     * @param token    受注Objectへのアクセス用token
     * @param response responseオブジェクト
     * @param model    画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @GetMapping("/confirm_order")
    public String confirmOrder(@CookieValue(required = false) String token, @CookieValue(required = false) String appToken, HttpServletResponse response, Model model) {
        if (token == null) return "dummy"; // Chrome Custom Tabsが本URLを勝手にreloadすることがあるので、その対策.
        System.out.println("[confirm_order] token = " + token + ", appToken = " + appToken);

        // token & appToken のCookieからの削除
        Cookie cookie = new Cookie("token", token);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        cookie = new Cookie("appToken", appToken);
        cookie.setMaxAge(0);
        response.addCookie(cookie);

        model.addAttribute("token", token);
        model.addAttribute("appToken", appToken);
        model.addAttribute("order", TokenUtil.get(token));
        model.addAttribute("clientId", clientId);
        model.addAttribute("sellerId", sellerId);

        return "confirm_order";
    }

    /**
     * 購入確定画面でアドレスWidgetで住所を選択した時にAjaxで非同期に呼び出されて、Amazon Pay APIから取得した住所情報より送料を計算する.
     *
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @return 計算した送料・総合計金額を含んだJSON
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @ResponseBody
    @PostMapping("/calc_postage")
    public Map<String, String> calcPostage(@RequestParam String token, @RequestParam String orderReferenceId
            , @RequestParam String accessToken) throws AmazonServiceException {
        System.out.println("[calc_postage]: " + token + ", " + orderReferenceId + ", " + accessToken);

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

        Order order = TokenUtil.get(token);
        order.postage = calcPostage(response);
        order.totalPrice = order.priceTaxIncluded + order.postage;
        DatabaseMock.storeOrder(order);

        Map<String, String> map = new HashMap<>();
        map.put("postage", comma(order.postage));
        map.put("totalPrice", comma(order.totalPrice));
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
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @param model            画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @PostMapping("/next")
    public String next(@RequestParam String token, @RequestParam String appToken, @RequestParam String accessToken
            , @RequestParam String orderReferenceId, Model model) throws AmazonServiceException {
        System.out.println("[next] " + token + ", " + appToken + ", " + orderReferenceId + ", " + accessToken);

        Order order = TokenUtil.get(token);
        order.orderReferenceId = orderReferenceId;

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
        order.buyerName = emptyIfNull(response.getDetails().getBuyer().getName());
        order.buyerEmail = emptyIfNull(response.getDetails().getBuyer().getEmail());
        order.buyerPhone = emptyIfNull(response.getDetails().getBuyer().getPhone());
        order.destinationName = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getName());
        order.destinationPhone = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPhone());
        order.destinationPostalCode = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPostalCode());
        order.destinationStateOrRegion = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion());
        order.destinationCity = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getCity());
        order.destinationAddress1 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine1());
        order.destinationAddress2 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine2());
        order.destinationAddress3 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine3());

        DatabaseMock.storeOrder(order);

        model.addAttribute("os", order.os);
        model.addAttribute("token", token);
        model.addAttribute("appToken", appToken);
        model.addAttribute("accessToken", accessToken);

        return "next";
    }

    /**
     * Thanks画面Activity内のWebViewから呼び出されて、受注Objectの詳細情報を表示する.
     *
     * @param token 受注Objectへのアクセス用token
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/confirm_purchase")
    public String confirmPurchase(@RequestParam String token, @RequestParam String appKey, @RequestParam String accessToken, Model model) {
        System.out.println("[confirm_purchase] token=" + token + ", appKey=" + appKey);

        Order order = TokenUtil.get(token);

        if(!order.appKey.equals(appKey)) {
            throw new RuntimeException("Unknown access");
        }

        model.addAttribute("order", order);
        model.addAttribute("os", order.os);
        model.addAttribute("token", token);
        model.addAttribute("appKey", order.appKey);
        model.addAttribute("accessToken", accessToken);

        return "confirm_purchase";
    }

    /**
     * 購入確定画面から呼び出されて、購入処理を実行してThanks画面へ遷移させる.
     *
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param model            画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @PostMapping("/do_purchase")
    public String doPurchase(@RequestParam String token, @RequestParam String accessToken, Model model) throws AmazonServiceException {
        System.out.println("[do_purchase] " + token + ", " + accessToken);

        Order order = TokenUtil.get(token);

        Config config = new PayConfig()
                .withSellerId(sellerId)
                .withAccessKey(accessKey)
                .withSecretKey(secretKey)
                .withCurrencyCode(CurrencyCode.JPY)
                .withSandboxMode(true)
                .withRegion(Region.JP);

        Client client = new PayClient(config);

        //--------------------------------
        // OrderReferenceの詳細情報の設定
        //--------------------------------
        SetOrderReferenceDetailsRequest setOrderReferenceDetailsRequest = new SetOrderReferenceDetailsRequest(order.orderReferenceId, String.valueOf(order.priceTaxIncluded));

        //set optional parameters
        setOrderReferenceDetailsRequest.setOrderCurrencyCode(CurrencyCode.JPY);
        setOrderReferenceDetailsRequest.setSellerNote(String.valueOf(order.items));
        setOrderReferenceDetailsRequest.setSellerOrderId(order.myOrderId);
        setOrderReferenceDetailsRequest.setStoreName("My Sweet Shop");

        //call API
        SetOrderReferenceDetailsResponseData responseSet = client.setOrderReferenceDetails(setOrderReferenceDetailsRequest);

        System.out.println("<SetOrderReferenceDetailsResponseData>");
        System.out.println(responseSet);
        System.out.println("</SetOrderReferenceDetailsResponseData>");

        //--------------------------------
        // OrderReferenceの確認
        //--------------------------------
        ConfirmOrderReferenceResponseData responseCon = client.confirmOrderReference(new ConfirmOrderReferenceRequest(order.orderReferenceId));
        // Note: it was not String, but request object!

        System.out.println("<ConfirmOrderReferenceResponseData>");
        System.out.println(responseCon);
        System.out.println("</ConfirmOrderReferenceResponseData>");

        //----------------------------------
        // Authorize(オーソリ, 与信枠確保)処理
        //----------------------------------
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(order.orderReferenceId, generateId(), String.valueOf(order.totalPrice));

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
        order.myOrderStatus = "AUTHORIZED";
        DatabaseMock.storeOrder(order);

        model.addAttribute("order", order);
        model.addAttribute("os", order.os);

        return "thanks";
    }

    /**
     * 購入確定画面から呼び出されて、購入処理を実行してThanks画面へ遷移させる.
     *
     * @param token            受注Objectへのアクセス用token
     * @param accessToken      Amazon Pay側の情報にアクセスするためのToken. ボタンWidgetクリック時に取得する.
     * @param orderReferenceId Amazon Pay側の受注管理番号.
     * @param model            画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     * @throws AmazonServiceException Amazon PayのAPIがthrowするエラー. 今回はサンプルなので特に何もしていないが、実際のコードでは正しく対処する.
     */
    @PostMapping("/purchase")
    public String purchase(@RequestParam String token, @RequestParam String appToken, @RequestParam String accessToken, @RequestParam String orderReferenceId, Model model) throws AmazonServiceException {
        System.out.println("[purchase] " + token + ", " + appToken + ", " + orderReferenceId + ", " + accessToken);

        Order order = TokenUtil.get(token);
        order.orderReferenceId = orderReferenceId;

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
        order.buyerName = emptyIfNull(response.getDetails().getBuyer().getName());
        order.buyerEmail = emptyIfNull(response.getDetails().getBuyer().getEmail());
        order.buyerPhone = emptyIfNull(response.getDetails().getBuyer().getPhone());
        order.destinationName = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getName());
        order.destinationPhone = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPhone());
        order.destinationPostalCode = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getPostalCode());
        order.destinationStateOrRegion = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getStateOrRegion());
        order.destinationCity = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getCity());
        order.destinationAddress1 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine1());
        order.destinationAddress2 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine2());
        order.destinationAddress3 = emptyIfNull(response.getDetails().getDestination().getPhysicalDestination().getAddressLine3());

        //--------------------------------
        // OrderReferenceの詳細情報の設定
        //--------------------------------
        SetOrderReferenceDetailsRequest setOrderReferenceDetailsRequest = new SetOrderReferenceDetailsRequest(orderReferenceId, String.valueOf(order.priceTaxIncluded));

        //set optional parameters
        setOrderReferenceDetailsRequest.setOrderCurrencyCode(CurrencyCode.JPY);
        setOrderReferenceDetailsRequest.setSellerNote(String.valueOf(order.items));
        setOrderReferenceDetailsRequest.setSellerOrderId(order.myOrderId);
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
        AuthorizeRequest authorizeRequest = new AuthorizeRequest(orderReferenceId, generateId(), String.valueOf(order.totalPrice));

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
        order.myOrderStatus = "AUTHORIZED";
        DatabaseMock.storeOrder(order);

        model.addAttribute("os", order.os);
        model.addAttribute("token", token);
        model.addAttribute("appToken", appToken);

        return "purchase";
    }

    /**
     * Thanks画面Activity内のWebViewから呼び出されて、受注Objectの詳細情報を表示する.
     *
     * @param token 受注Objectへのアクセス用token
     * @param model 画面生成templateに渡す値を設定するObject
     * @return 画面生成templateの名前. "cart"の時、「./src/main/resources/templates/cart.html」
     */
    @PostMapping("/thanks")
    public String thanks(@RequestParam String token, Model model) {
        System.out.println("[thanks] token=" + token);

        Order order = TokenUtil.get(token);

        model.addAttribute("order", order);
        model.addAttribute("os", order.os);

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
