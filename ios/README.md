# Amazon Pay モバイル サンプルアプリ iOSアプリの実装について
本サンプルアプリの、iOSアプリ側の実装です。

## 動作環境
iOS バージョン11.2以降: Safari Mobile 11以降  
[参考] https://pay.amazon.com/jp/help/202030010

## その他の前提条件
本サンプルアプリではUniversal Linksという技術を使っており、こちらを利用するためにはAppleにDeveloperとして事前に登録する必要があります。

## 概要
本サンプルアプリは、下記動画のように動作いたします。

![](img/ios-movie.gif)

詳細なフローは、[flow-ios.xlsx](./flow-ios.xlsx) をご参照下さい。

## インストール方法
先にWebアプリケーション側にあたる、[java](../java/README.md)側をインストールして下さい。

### プロジェクトのOpenとサンプルアプリの起動
本プロジェクトは、Mac上の[Xcode](https://developer.apple.com/jp/xcode/)で開きます。そのほかの環境での開き方は、ここでは扱いません。  
※ ここではversion 10.2.1を使用しています。  
まずはXcodeを立ち上げます。  
![androidstudio-welcome](img/xcode_open.png)
「Open another project」で、こちらのios/iOS-Appディレクトリを選択して、「Open」  
プロジェクトが開いたら、Menuの「Product」→「Run」か、画面上部の「Run」ボタンより、applicationを起動してください。
![androidstudio-project](img/xcode_project.png)
Simulatorが立ち上がり、サンプルアプリが起動します。(1〜2分かかります。)  
<img src="img/simu_start.png" width="300">

### 自己証明書のインストール
今回のサンプルでは、server側のSSL証明書に自己証明書が使用されているため、サンプルアプリを正しく動作させるためにはその自己証明書をiOS側にInstallする必要があります。  
ここでは、起動したSimulatorへのInstall方法を説明します。
※ 以下はiOS12.2で実施しておりますが、iOSのバージョンによっては手順が若干違う場合があります。

1. SSL自己証明書のDownload  
Safariを立ち上げ、下記のURLにアクセスします。(Chrome等の他のブラウザだとうまくいかないことがあるので、必ずSafariをご使用ください。)  
https://localhost:8443/crt/sample.crt  
下記のように警告が出るので、「Show Details」  
<img src="img/simu_warn.png" width="300">  
「visit this website」のリンクをタップし、表示されたダイアログで再度「Visit Website」をタップ  
<img src="img/simu_warn-detail.png" width="300">  
「Allow」をタップし、で開いたダイアログで「Close」をタップ  
<img src="img/simu_allow-download.png" width="300">  

2. SSL自己証明書のInstall  
Safariを閉じて、「Settings」 →　「General」 → 「Profile」  
今ダウンロードされた「localhost」をタップ  
<img src="img/simu_profile.png" width="300">  
「Install」をタップし、開いたダイアログで再度「Install」をタップ  
<img src="img/simu_install-profile.png" width="300">  
Installが完了します。  
<img src="img/simu_success.png" width="300">  

3. SSL自己証明書の有効化  
「Settings」 →　「General」 → 「About」で下記を開いて、「Certificate Trust Settings」  
<img src="img/simu_about.png" width="300">  
先ほどInstallした「localhost」をONにし、表示されたダイアログで「Continue」をタップして有効化します。  
<img src="img/simu_trust.png" width="300">  

あとはSimulator上でサンプルアプリを立ち上げて動作をご確認ください。

## WebView ←→ Native ←→ Secure WebViewの詳細説明

### WebView ←→ Native間の実装
WebView内のJavaScriptとNativeコード(Swift/Objective-C)との間で、お互いの関数を呼び出す機能があるので、こちらを利用しております。  
WebViewを使わないNativeアプリにAmazon PayをIntegrationされる方には、この章は関係ないのでSKIPして下さい。

#### WebView → Nativeの呼び出し
WebView内のJavaScriptから、Nativeコードを呼び出します。

まずはNative側でJavaScriptからの呼び出しをHandleできるように、extensionとしてWKScriptMessageHandlerを指定し、userContentControllerメソッドを実装します。
```swift
// ViewController.swiftから抜粋

extension ViewController: WKScriptMessageHandler {
    // JavaScript側からのCallback.
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        print("ViewController#userContentController")
        switch message.name {
        case "iosApp":
            print("iosApp")
            
            if let data = message.body as? NSDictionary {
                let secureWebviewSessionId = data["secureWebviewSessionId"] as! String?
                invokeButtonPage(secureWebviewSessionId!)
            }
        default:
            return
        }
    }
}
```

ここでは、「iosApp」という名前でメッセージが送られてきたときに、ViewControllerに実装された「invokeButtonPage」というメソッドを起動しています。

次に、WebViewを生成するとき、JavaScript側が下記のように「iosApp」というMessage Handlerを使用できるよう、Configとして設定します。
```swift
// ViewController.swiftから抜粋

            // JavaScript側からのCallback受付の設定
            let userContentController = WKUserContentController()
            userContentController.add(self, name: "iosApp")
            let webConfig = WKWebViewConfiguration();
            webConfig.userContentController = userContentController
            
            // WebViewの生成、cartページの読み込み
            webView = WKWebView(frame: rect, configuration: webConfig)
```

このようにして登録したJavaScript用のMessage Handler「iosApp」に対しては、下記のようにメッセージを送信できます。
```js
// cart.htmlの「openAmazonPay」関数から抜粋(見やすくするため、一部加工しています。)

    function openAmazonPay (data) {
            :
        webkit.messageHandlers.iosApp.postMessage(data);
            :
    }
```

#### Native → WebViewの呼び出し
今度はその逆に、Nativeコードから、WebView内のJavaScriptからを呼び出します。   

まずは呼び出される関数を、JavaScript側に実装します。  
```js
// cart.htmlより抜粋
    function purchase(secureWebviewSessionId, accessToken, orderReferenceId) {
        document.getElementById('filter').style.display = 'block';
            :
    }
```

あとは、Native側で下記のように「evaluateJavaScript」というWKWebViewのメソッドに呼び出すJavaScriptのコードを渡すことで、呼び出すことができます。
```swift
// ViewController.swiftより抜粋(見やすくするため、一部加工しています。)
            webView.evaluateJavaScript("purchase('XXXXX', 'YYYYY', 'ZZZZZ')", completionHandler: nil)
```

### Native ←→ SFSafariViewController(Secure WebView)間の実装
Nativeコード(Swift/Objective-C)とSecure WebViewの間で、データを伴ってお互いに起動する方法の説明です。  
WebViewを使わないNativeアプリにAmazon PayをIntegrationされる方は、主にこの章をご参照下さい。  

#### Native → SFSafariViewController(Secure WebView)の起動
下記のように起動します。データはURLパラメタとして渡しています。

```swift
// ViewController.swiftから抜粋
        let safariView = SFSafariViewController(url: NSURL(string: "https://localhost:8443/button?secureWebviewSessionId=\(secureWebviewSessionId)")! as URL)
        present(safariView, animated: true, completion: nil)
```

#### SFSafariViewController(Secure WebView) → Nativeの起動
おそらく、ここが一番の難関です。  
ブラウザからアプリを起動できる技術として、CustomURLSchemeというアプリ専用のSchemaを登録してiOSから起動してもらうものがあるのですが、こちらは全く同じSchemaで起動する
悪意のあるアプリを完全に排除する方法がないため、センシティブなデータの受け渡しを伴うアプリの起動には不向きです。  

そこで、Universal Linksというものを使います。  
こちらは特定のURLのLinkがSafari上でタップされたときに登録されたアプリを起動できる機能なのですが、そのURLとアプリとのMapping情報を自分が管理するServer上に置くことができるため、そのServerがクラックされない限りは悪意のあるアプリが間違って起動されてしまう心配はありません。  

まずは、URLとアプリとのMappingを行うJSONファイルを作成します。  

```json
{
    "applinks": {
        "apps": [],
        "details": [
            {
                "appID":"XXXXXXXX.com.amazon.pay.sample.amazonpay-ios",
                "paths":[ "*" ]
            }
        ]
    }
}
```

こちらのJSONファイル内の「appID」は、"{TeamID}.{Bundle Identifier}"で構成されます。  
TeamIDは、ご自身のAppleアカウントでApple Developer Centerにログインし、「Membership」→ 「Team ID」で確認できます。  
またBundle Identifierは、Xcodeで設定の「General」「Signing & Capabilities」等で確認できます。  

こうして作成したファイルは、「apple-app-site-association」という名前で保存します。   

この「apple-app-site-association」を、自身が管理するServerに配置します。  
このときの注意点としては、  
  * DomainがWebアプリケーションとは違うサーバーにすること  
  * httpsでファイルにアクセスできること  
  * ファイル取得時のContent-Typeは「application/json」とすること  
  * ファイルはドメインのルート or 「ドメインのルート/.well-known/」の下に配置すること  

などがあります。  
[こちら](https://dev.classmethod.jp/articles/universal-links/)の方のように、AWS S3を使うと比較的簡単にできますので、ご参考にして見て下さい。  
※ 本サンプルでも、AWS S3を使って「apple-app-site-association」を配置しております。  

そしてAssociated Domainsを追加します。  
Xcodeで「Signing & Capabilities」を開き、左上の「+ Capability」から「Associated Domains」を追加します。  
※ この操作により、Apple Developer Centerで「Certificates, Identifiers & Profiles」→ 「Identifiers」にアプリのBundle Identifierが自動的に登録されます。  

![](img/xcode_associateddomains.png)  

こうして表示されたAssociated Domainsに、上記画像のように下記二つを登録します。
  * applinks:{上記「apple-app-site-association」を配置したサーバーのドメイン}  
  * webcredentials:{上記「apple-app-site-association」を配置したサーバーのドメイン}  

ここまでで、Nativeコードを呼び出す準備が整いました。  
後は「https://{'apple-app-site-association'を配置したサーバーのドメイン}」/...」というURLのLinkをSFSafariViewController上でタップすれば、AppDelegate.swiftに追加した下記のコードが実行されるはずです。

```swift
// AppDelegate.swiftより抜粋
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        print("Universal Links!")
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb {
            print(userActivity.webpageURL!)
                :
        }
        return true;
    }
```

Note: 上記はSwift5の場合。Swift4以前の場合は下記。
```swift
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([Any]?) -> Void) -> Bool {
        print("Universal Links!")
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb {
            print(userActivity.webpageURL!)
                :
        }
        return true;
    }
```

なお、こちらのUniversal Linksにより上記コードが起動するのは、上にも書いたとおり「https://{'apple-app-site-association'を配置したサーバーのドメイン}」/...」というURLのLinkをタップしたときだけで、JavaScriptなどでこのURLをloadしても起動しません。  
※ 余談ですが、WebView上ではUniversal Linksは発動しません。  
なので、本サンプルでは「ご注文手続き」画面にて、下記のようにCSSを使ってボタンに見せかけた「購入」のリンクをユーザにタップさせることでUniversal Linksを発動し、上記Nativeコードを起動しています。

```html
<!-- confirm_order.htmlより抜粋(見やすくするため、一部加工しています。) -->
<a id="purchase_link" class="btn btn-info btn-lg btn-block" href="https://amazon-pay-links.s3-ap-northeast-1.amazonaws.com/index.html?secureWebviewSessionId=XXXX&old_secureWebviewSessionId=YYYY&accessToken=ZZZZ&orderReferenceId=S03-8186807-0189293">購　入</a>
```

画面のFlowを本サンプルアプリから変更する場合には、こちらのUniversal Linksの制約を頭に入れて設計するようにして下さい。


## その他の技術要素の詳細説明

### 元のActivity(ページ)への処理の戻し方について
本サンプルアプリではSFSafariViewからNativeを起動するとき、一旦AppDelegateで処理を受け付けてから、わざわざ前に起動していたViewControllerを検索して処理を戻してから、続きの処理を実行しています。  
アプリの仕様上可能であれば、このような面倒な処理は必要ではなく、単にAppDelegateから新しいControllerを起動して続きの処理が実行しても特に問題はないです。  

しかし、React.jsやVue.js等で作られたSPA(Single Page Application)のように、一つのページ( or Controller)が状態を保持して全ての動作を実現しているようなタイプのアプリケーションでは、起動していた元のControllerに戻らないと続きの処理が実行できません。  
そういったアプリケーションのことを考慮し、本サンプルアプリではわざと前に起動していたViewControllerに処理を戻すように実装しております。  
ここでは、そのViewControllerへの戻り方について説明します。  

```swift
// AppDelegate.swiftより抜粋(見やすくするため、一部加工しています。)
            // 現在最前面のSFSafariViewとその裏のViewControllerを取得
            var sfsv = UIApplication.shared.keyWindow?.rootViewController
            var vc:ViewController? = nil
            while (sfsv!.presentedViewController) != nil {
                if let v = sfsv as? ViewController {
                    vc = v
                }
                sfsv = sfsv!.presentedViewController
            }
            
            (sfsv as? SFSafariViewController)?.dismiss(animated: false, completion: nil)
            
            vc?.jsCall("XXX", "YYY", "ZZZ")
```

iOSのControllerは、```UIApplication.shared.keyWindow?.rootViewController```を起点として、新しく起動する度に上に積み重ねられていき、一番上に乗っているControllerの処理が実行されて画面に表示される仕組みになっています。  
上に乗っているControllerは```presentedViewController```に代入されており、一番上のControllerはこちらがnullとなります。  
このコードが実行されるのはSFSafariViewからNativeが起動されたタイミングで、一番上には必ずSFSafariViewControllerがいます。  
またその途中には必ず元々起動していた```ViewController```が存在しており、上記while文を実行することで```sfsv```にSFSafariViewControllerが、```vc```に元々起動していた```ViewController```が代入されます。  

よって```sfsv.dismiss```を呼び出すことでSFSafariViewControllerを閉じて、```vc.jsCall```を呼び出すことで元々起動していた```ViewController```に次の処理を実行させることができます。




## Security対策
Note: こちらは、Android側の説明とほぼ同じ内容です。  

本サンプルアプリで行われているSecurity対策は、下記の二つです。  
(A) SFSafariView(Secure WebView)を開くとき、新しいsecureWebviewSessionIdを発番し、secureWebviewSessionを登録しなおす  
(B) 上記で新しくされる前の古い方のsecureWebviewSessionIdをApp側で保持し、SFSafariView(Secure WebView)からNativeを起動するときにも送信してもらい一致判定を行う  

それぞれ、何のための対策なのかを説明します。  

まずは、下記の図を御覧ください。  
![security](../java/img/security-crack.png)

これは悪意を持ったユーザー（黒服の男）が、別のユーザ（右側の女性）に攻撃をしかける様子を示しています。  

攻撃のシナリオは下記です。  
(1) 黒服の男は自分の携帯端末を使ってSecure WebViewが起動するところまでオペレーションする。  
(2) このとき携帯端末をPCにつないで開発者用ツールで監視するなどして、Secure WebViewが起動するURLを手に入れる。  
(3) このURLにはsecureWebviewSessionが保存されているsecureWebviewSessionIdがURLパラメタとして付与されている。  
(4) (2)で手に入れたURLを、Eメール等で攻撃対象の女性に送信する。Eメールを受け取った女性は、Eメールのリンクをクリックしてしまう。  
(5) 女性の携帯端末がiPhone, iPadだった場合、通常はリンククリックによりSafariでページは開く。  
(6) 女性はそのままChrome上でAmazon Payのログイン・購入などのオペレーションをしてしまう。  
(7) このとき、女性のAmazonに登録されている個人情報が、secureWebviewSessionに保存される。  
(8) 黒服の男はこのsecureWebviewSessionが保存されているsecureWebviewSessionIdを知っているので、女性の個人情報を盗み見たり、女性に変わってOperationすることができてしまう。  

このような攻撃手法のことを、Session Fixationと呼びます。  
この攻撃を防ぐためには、女性が個人情報を保存するsecureWebviewSessionのsecureWebviewSessionIdを、黒服の男が知らない状態にする必要があります。  
そのため上記(A)のように、Secure WebViewが開いたらすぐ新しいsecureWebviewSessionIdを発番し、secureWebviewSessionを登録しなおす必要があるのです。  
これを行っているコードが、Webアプリケーション側の下記になります。  

```java
// Webアプリケーション側のコードの、AmazonPayController.javaより抜粋。

        // redirect処理でconfirm_orderに戻ってきたときにtokenが使用できるよう、Cookieに登録
        // Note: Session Fixation 対策に、tokenをこのタイミングで更新する.
        Cookie cookie = new Cookie("secureWebviewSessionId", TokenUtil.copy(secureWebviewSessionId));
        cookie.setSecure(true);
        response.addCookie(cookie);

        // 更新前のtokenも、APPに戻ったタイミングでの確認用に保持する
        cookie = new Cookie("old_secureWebviewSessionId", secureWebviewSessionId);
        cookie.setSecure(true);
        response.addCookie(cookie);
```

また、この女性が購入ボタンを押した後ですが、何が起こるでしょうか？  
通常は何も起こりませんが、もし女性がたまたま同じアプリをインストールしていた場合には、Universal LinksはSFSafariViewだけではなくSafariでも発動してしまうため、アプリが正常に起動して続きの処理が実行されて、購入が完了してしまう可能性があります。  
もちろん、女性は自分のAmazonアカウントにログインしていますし、ちゃんと確認画面をみてから購入ボタンを押しているはずではありますが、不正なフローで購入が成功してしまうのは本来は好ましい挙動ではありません。  

これを防ぐための対策が上記(B)です。  
古い方のsecureWebviewSessionIdが保持されているのは黒服の男の携帯端末のアプリで、女性の方には保持されていません。  
よって、この一致判定の結果が不一致だったら、不正なフローだったと判断することができます。  

古い方のsecureWebviewSessionIdをApp側で保持する処理が、下記です。  
```swift
// ViewController.swiftより抜粋。
            :
    var old_secureWebviewSessionId: String? = ""
            :
    func invokeButtonPage(_ secureWebviewSessionId: String) {
        print("ViewController#invokeButtonPage")
        
        old_secureWebviewSessionId = secureWebviewSessionId // ← ここで保持している
        let safariView = SFSafariViewController(url: NSURL(string: "https://localhost:8443/button?secureWebviewSessionId=\(secureWebviewSessionId)")! as URL)
        present(safariView, animated: true, completion: nil)
    }
            :
```

そして、これの一致判定を行っているのが、下記になります。  
```swift
// ViewController.swiftより抜粋。(見やすくするため、一部加工しています。)

    func jsCall(_ secureWebviewSessionId: String, _ old_secureWebviewSessionId: String, _ accessToken: String, _ orderReferenceId: String) {
        print("ViewController#jsCall")
        
        if(self.old_secureWebviewSessionId == old_secureWebviewSessionId) { // ← ここで判定
            webView.evaluateJavaScript("purchase('XXX', 'YYY', 'ZZZ')", completionHandler: nil)
        } else {
            webView.load(URLRequest(url: URL(string: "https://localhost:8443/error")!)) // ← 不一致の場合はエラー画面に遷移
        }
    }
```
