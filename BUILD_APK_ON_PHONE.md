# Installing ZoomPlay Browser using only your Android phone

1. Open the repository **Actions** tab.
2. Open **Build ZoomPlay Android APK**. If Actions is disabled, enable it.
3. A push to main normally starts a build. You can also choose **Run workflow** on branch main.
4. Open the completed green check-mark run, then **Artifacts**, then **ZoomPlayBrowser-APK**.
5. Download the artifact ZIP and extract it using your file manager.
6. Tap **app-debug.apk** to install. Your browser or file manager may ask for permission to install unknown apps.

This is a debug APK, signed with a temporary developer key. It is intended for personal testing, not Play Store distribution. Later debug builds may use a different key, so upgrading in place may fail.

ZoomPlay controls ordinary HTML5 videos exposed by a site's page. DRM, embedded cross-origin video, and sites that block WebView may remain unsupported.
