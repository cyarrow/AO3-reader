# AO3 Reader

一個用來閱讀 [Archive of Our Own](https://archiveofourown.org) 作品的 Android 閱讀器，以閱讀舒適度為優先（個人使用、側載安裝）。

需求與開發規劃請見 [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)。

## 安裝與更新
- 到 [Releases](../../releases) 下載最新的 `ao3reader-x.y.z.apk` 並安裝
- 安裝後，App 會在啟動時自動檢查新版本（每天最多一次），也可以在「設定」手動檢查

## 發佈新版本
1. 首次發佈前，先在 repo 的 Settings → Secrets and variables → Actions 設定 4 個 Secrets：
   `SIGNING_KEYSTORE_BASE64`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`
2. 到 Actions → **Release** → Run workflow，輸入版本號（例如 `0.2.0`）；或者推送 `v0.2.0` 這樣的 tag
3. 完成後 GitHub Releases 上會出現已簽章的 APK

每次 push 都會跑 **Build** workflow，可以在該次執行頁面的 Artifacts 下載 debug APK（debug 版可以和正式版同時安裝）。

## 本機建置
```
./gradlew assembleDebug
```
需要 JDK 17 以上與 Android SDK（compileSdk 36）。
