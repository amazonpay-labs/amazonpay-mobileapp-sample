# SSL自己証明書の生成方法

## 前提条件
- opensslがインストールされていること (されていない場合には、各環境に合わせてインストール)
- current directoryを本ディレクトリとすること

## 生成コマンド
```sh
# sample.key, sample.crtの生成
openssl req -x509 -nodes -days 825 -newkey rsa:2048 \
  -keyout sample.key -out sample.crt \
  -subj "/C=JP/ST=Tokyo/L=Tokyo/O=Sample Inc./CN=localhost" \
  -reqexts v3_req -reqexts SAN -extensions SAN -config v3.ext

# sample.crtの確認
openssl x509 -in sample.crt -text -noout

# sample.crtをDER形式へ変換してsample.der.crtを生成(Android端末へのインストール用)
openssl x509 -in sample.crt -outform der -out sample.der.crt

# sample.p12の生成(本サンプルのserver側への設定用)
openssl pkcs12 -export -in sample.crt -inkey sample.key -out sample.p12
#   passwordの設定が求められる。設定した値は「./src/main/resources/application.properties」の「server.ssl.key-store-password」に反映する。
```

## 生成した証明書ファイルをDownload用ディレクトリへ移動
```sh
# sample.crtの移動(current directoryが./sslと想定)
mv sample.crt ../src/main/resources/static/crt/

# sample.der.crtの移動(current directoryが./sslと想定)
mv sample.der.crt ../src/main/resources/static/crt/
```
