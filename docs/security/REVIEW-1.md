# 安全審查 #1

方法：security-audit skill（Cloudflare）的 guidance mode，聚焦審查，**只讀原始碼與設定**，不連線 AO3、不執行針對線上服務的測試。
範圍：登入與 cookie 保存、App 內更新、對外公開的元件（intent）、解析不受信任的 HTML、字型下載、CI 簽章金鑰、備份。
基準：commit 前的 `ccr-82b63a71-vrh8m1`。

## 結論
**沒有發現可被利用的漏洞（無 confirmed finding）。** 以下皆為強化項目，已一併修正。

## 已確認安全的地方
| 範圍 | 檢查結果 |
|---|---|
| 不受信任的 HTML（作品內文、搜尋結果） | 用 Jsoup 轉成純文字區塊，再以 Compose `Text` 顯示；沒有 WebView、不執行腳本、連結不可點，圖片只顯示替代文字 |
| 登入 | 密碼不儲存；只保存 AO3 cookie，用 Android Keystore 的 AES-GCM 金鑰加密（金鑰無法匯出）；登出會清除 |
| Cookie 範圍 | OkHttp 依網域比對，AO3 cookie 不會送到 GitHub 等其他主機 |
| 對外公開的 Activity | 外部 App 傳來的連結只會被解析成作品編號（數字），不會執行其他動作 |
| FileProvider | 未公開，只分享 `cache/updates/` |
| 自訂字型匯入 | 檔名過濾掉路徑字元，無法寫到資料夾外；匯入前先驗證能載入 |
| CI 簽章金鑰 | 從未進入 git 歷史；只在 Release workflow 的單一步驟解碼，結束後刪除；版本號輸入以正規表示式驗證，沒有指令注入 |
| 真實作品內容 | 測試用的兩篇作品從未進入 git 歷史 |

## 強化項目（已修正）
| # | 項目 | 為什麼 | 修正 |
|---|---|---|---|
| H1 | 表單送出目的地 | 登入密碼會送到「從 AO3 頁面解析出的表單網址」；理論上若頁面被竄改，可能送到別處 | `Ao3Client.post` 只允許 `https://archiveofourown.org`（含 www），並加上測試 |
| H2 | 更新 APK 驗證 | 系統只會擋「同套件、不同簽章」；若下載到的是**不同套件**，會被當成新 App 安裝 | 安裝前檢查套件名稱必須相同、簽章憑證必須相同（讀不到憑證時交由系統把關）；下載網址必須是本專案的 GitHub Releases |
| H3 | 字型下載未固定版本 | 從 `main` 分支下載，內容可能改變；字型由系統原生程式解析 | 固定到 google/fonts 的特定 commit，並驗證每個檔案的 SHA-256 |
| H4 | 備份 | 加密的登入資料會進雲端備份／換機搬移（還原後也無法解密，沒有意義） | 新增 `data_extraction_rules.xml`、`backup_rules.xml` 排除 `cookies.enc`、`account.enc` |
| H5 | Cookie 持久化範圍 | 會一併保存非 AO3 網站的 cookie | 只保存 AO3 網域的 cookie |
| H6 | CI 權限與相依 | Build workflow 未限制權限；Actions 用浮動的版本標籤 | Build 改為 `contents: read`；所有 Actions 固定到 commit SHA |

## 資訊（不需修正）
- **外部 App 可以叫起作品頁**：開啟作品頁會下載該作品並記入書架；登入狀態下 AO3 也會記入你的閱讀紀錄。需要手機上有惡意 App，且畫面會切到本 App，使用者看得到。
- **Debug 版 APK**（Actions 的 Build artifact）可被 `adb run-as` 讀取資料，需實體接觸手機並開啟 USB 偵錯。日常請安裝 Release 版。
- **閱讀紀錄、書架** 仍會跟著 Android 備份（這是你自己的 Google 帳號）。若不希望，可以再整個關掉備份。
