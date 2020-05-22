//
//  ViewController.swift
//  iOS-App
//
//  Created by 内海徹生 on 2020/05/20.
//  Copyright © 2020 内海徹生. All rights reserved.
//

import UIKit
import WebKit
import SafariServices

class ViewController: UIViewController {

    var webView: WKWebView!
    var old_secureWebviewSessionId: String? = ""
    
    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        print("ViewController#viewDidAppear")
        
        if webView == nil {
            
            print("start to generate webView")
            
            // WebViewの画面サイズの設定
            var webViewPadding: CGFloat = 0
            if #available(iOS 11.0, *) {
                let window = UIApplication.shared.keyWindow
                webViewPadding = window!.safeAreaInsets.top
            }
            let webViewHeight = view.frame.size.height - webViewPadding
            let rect = CGRect(x: 0, y: webViewPadding, width: view.frame.size.width, height: webViewHeight)
            
            // JavaScript側からのCallback受付の設定
            let userContentController = WKUserContentController()
            userContentController.add(self, name: "iosApp")
            let webConfig = WKWebViewConfiguration();
            webConfig.userContentController = userContentController
            
            // WebViewの生成、cartページの読み込み
            webView = WKWebView(frame: rect, configuration: webConfig)
            let webUrl = URL(string: "https://localhost:8443/iosApp/cart")!
            let myRequest = URLRequest(url: webUrl)
            webView.load(myRequest)
            
            // 生成したWebViewの画面への追加
            self.view.addSubview(webView)
            
            print("finished generating webView")
        }
    }
    
    func invokeButtonPage(_ secureWebviewSessionId: String) {
        print("ViewController#invokeButtonPage")
        
        old_secureWebviewSessionId = secureWebviewSessionId
        let safariView = SFSafariViewController(url: NSURL(string: "https://localhost:8443/button?secureWebviewSessionId=\(secureWebviewSessionId)")! as URL)
        present(safariView, animated: true, completion: nil)
    }
    
    func jsCall(_ secureWebviewSessionId: String, _ old_secureWebviewSessionId: String, _ accessToken: String, _ orderReferenceId: String) {
        print("ViewController#jsCall")
        
        if(self.old_secureWebviewSessionId == old_secureWebviewSessionId) {
            webView.evaluateJavaScript("purchase('\(secureWebviewSessionId)', '\(accessToken)', '\(orderReferenceId)')", completionHandler: nil)
        } else {
            webView.load(URLRequest(url: URL(string: "https://localhost:8443/error")!))
        }
    }

}

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
