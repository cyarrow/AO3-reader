# AO3 Reader

一個用來閱讀 [Archive of Our Own](https://archiveofourown.org) 作品的 Android 閱讀器，以閱讀舒適度為優先（個人使用、側載安裝）。

需求與開發規劃請見 [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)，介面截圖在 [docs/screenshots](docs/screenshots)。

## 功能
- **閱讀**：上下捲動／左右翻頁、字級、字重、行距、段距、邊距、五種主題、App 內亮度、目錄、章節內進度拖曳、本章剩餘時間、記住閱讀位置、音量鍵翻頁
- **中文**：簡體自動轉繁（OpenCC，可逐部作品關閉、可選台灣用語）；內建思源宋體，可下載霞鶩文楷、思源黑體、芫荽，或匯入自己的字型
- **搜尋與瀏覽**：完整篩選、標籤自動完成、點標籤直接搜尋、常看的 Fandom
- **帳號**：登入、Kudos、書籤、稍後閱讀、閱讀紀錄、鎖定作品
- **書架**：開啟過的作品自動離線保存、檢查並下載新章節
- **更新**：App 內自動檢查 GitHub Releases 新版本

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
