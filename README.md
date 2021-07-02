# 本サンプルアプリは「非推奨」となりました。 (2021/07/02)
[Amazon Pay Checkout v2(CV2)](https://developer.amazon.com/ja/docs/amazon-pay/intro.html)がReleaseされ、こちらのサンプルアプリが対象としている[Amazon Pay CV1](https://amazonpaylegacyintegrationguide.s3.amazonaws.com/docs/ja/amazon-pay/intro.html)は旧バージョンとなりました。  
それに伴い、本サンプルアプリも今後は更新を停止し、非推奨の扱いとさせていただきます。  
今後はCV2を対象とする[こちらのサンプルアプリ](https://github.com/amazonpay-labs/amazonpay-sample-application-v2)をご参照下さい。  

# Amazon Pay モバイル サンプルアプリについて
SmartPhone上でAmazon Payを使って商品を購入する、モバイルアプリのサンプル実装です。  
本サンプルアプリを参考にして様々なご実装のアプリにAmazon Payを簡単にカスタマイズして導入いただけるよう、アプリの構成は可能な限りシンプルに、また必要となる各技術要素については各々の技術の使い所・注意点をあわせて説明しております。

本サンプルアプリでは、Amazon Payの処理についてはモバイルアプリから使えるSecureなブラウザ技術である、
  * Android: Chrome Custom Tabs  
  * iOS: SFSafariViewController  

を起動して実行しており、実行が終わるとまたアプリ側に処理を戻しています。  
その他の部分は通常のAmazon Payと同じですので、下記Amazon Payの開発用ページを参考にご実装いただけますし、また通常のPC/Mobileのブラウザ向けのページとソースコードの多くを共有することができます。  
https://developer.amazon.com/ja/docs/amazon-pay/intro.html  
本サンプルアプリも、通常のPC/Mobileのブラウザでも動作させることができるように作られています。  
※ 本ドキュメント 及び サンプルアプリのコード内では、「Chrome Custom Tabs」と「SFSafariViewController」の両方を合わせて「*Secure WebView*」と呼んでおります。  

モバイルアプリから使うことができるブラウザ技術としては、Secure WebViewの他にもWebViewがあります。  
こちらはSecurity上の理由でAmazon Payではサポート対象となっておらず、WebViewで実装されているモバイルアプリの場合でも、そのままではAmazon Payを安全にご導入いただけません。  
その場合でも、本サンプルアプリのやり方に従えばサポート対象となる安全な形での実装が可能ですので、是非ご活用下さい。  

本サンプルアプリはサーバー側の実装の[java](java/README.md)と、[android](android/README.md)、[ios](ios/README.md)の3つのプロジェクトで構成されており、それぞれのセットアップ方法 及び 利用している技術要素に関する説明も、それぞれのREADMEをご参照下さい。  

## 動作環境
Android 7以降: Google Chrome 64以降  
iOS バージョン11.2以降: Safari Mobile 11以降  
[参考] https://pay.amazon.com/jp/help/202030010

## 概要
本サンプルアプリはAndroid, iOS共に下記の動画のように動作します。

![](ios/img/ios-movie.gif)

この動作は、下記の図のように  

* WebView ←→ Native ←→ Secure WebView  

が連携することで実現しています。

![](java/img/flow.png)

本サンプルアプリはWebViewで作成されておりますが、図を見ると分かる通り必ず一度Nativeの処理を経由してからSecure WebViewとやり取りをしています。
そのため、WebViewを使わないNativeアプリの場合でも、WebView関係の処理をとばして見ていただくことで本サンプルアプリを参考にAmazon Payをご実装いただくことが可能です。

## 本サンプルアプリの動かし方
最初に、[java](java/README.md)を参考に、Webアプリケーション側を動かして下さい。こちらは通常のブラウザからでも動作確認が可能です。  
その後に[android](android/README.md)と[ios](ios/README.md)を参考に、モバイルアプリを動かして下さい。  
※ androidとiOSは特に順番は関係ないので、お好きな方からお試しいただけます。
