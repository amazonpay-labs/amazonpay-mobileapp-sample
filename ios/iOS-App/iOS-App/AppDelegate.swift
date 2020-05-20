//
//  AppDelegate.swift
//  iOS-App
//
//  Created by 内海徹生 on 2020/05/20.
//  Copyright © 2020 内海徹生. All rights reserved.
//

import UIKit
import SafariServices

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?
    
    func application(_ application: UIApplication, continue userActivity: NSUserActivity, restorationHandler: @escaping ([UIUserActivityRestoring]?) -> Void) -> Bool {
        print("Universal Links!")
        if userActivity.activityType == NSUserActivityTypeBrowsingWeb {
            print(userActivity.webpageURL!)
            
            var urlParams = Dictionary<String, String>.init()
            for param in userActivity.webpageURL!.query!.components(separatedBy: "&") {
                let kv = param.components(separatedBy: "=")
                urlParams[kv[0]] = kv[1].removingPercentEncoding
            }
            print(urlParams)
            
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
            
            vc?.jsCall(urlParams["secureWebviewSessionId"]!,urlParams["old_secureWebviewSessionId"]!, urlParams["accessToken"]!, urlParams["orderReferenceId"]!)
        }
        return true
    }
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        // Override point for customization after application launch.
        return true
    }

}

