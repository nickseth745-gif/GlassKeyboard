GLASS KEYBOARD - get the APK without Android Studio

1. Make a free account at github.com and create a new repository.
2. On the repository page choose "uploading an existing file" and drag in
   everything from inside this GlassKeyboard folder (including the hidden
   .github folder). Commit.
   If .github didn't upload: Add file > Create new file, type the name
   .github/workflows/build.yml and paste the contents of build.yml.
3. Open the Actions tab. "Build APK" runs by itself (about 3-5 minutes).
   If it doesn't, open it and press "Run workflow".
4. When it shows a green check, open the run and download
   GlassKeyboard-APK at the bottom. Unzip it to get app-debug.apk.
5. Copy app-debug.apk to your phone and open it. Allow "install unknown
   apps" when asked.
6. Open Glass Keyboard, tap "1. Turn on Glass Keyboard", switch it on,
   then tap "2. Switch to Glass Keyboard".
