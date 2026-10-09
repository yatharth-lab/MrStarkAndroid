# Mr Stark Android Assistant — शुरुआती प्रोजेक्ट

यह Kotlin + Jetpack Compose का शुरुआती Android ऐप है। इसमें:
- नीला futuristic dashboard
- हिंदी speech recognition (Android की उपलब्ध speech service पर निर्भर)
- Text-to-Speech
- स्थानीय chat history
- Gemini API key settings
- Gemini API से basic chat
- Android web search intent
- GitHub Actions से debug APK build

## जरूरी सीमाएँ
1. यह v0.1 starter है, पूर्ण Jarvis नहीं। Wake word, always-on background listening, app-wide device control, scheduled notifications और dedicated search API अभी शामिल नहीं हैं।
2. API key ऐप में स्थानीय रूप से SharedPreferences में सेव होती है। यह सार्वजनिक/production app के लिए सुरक्षित व्यवस्था नहीं है। APK या app को किसी और से साझा करने से पहले key हटा दें। सार्वजनिक app के लिए backend proxy रखें।
3. Gemini model/API availability और free-tier limits बदल सकती हैं। यदि `gemini-2.0-flash` उपलब्ध न हो, तो code में model name को अपने AI Studio में उपलब्ध model से बदलें।
4. Android voice input के लिए इंटरनेट या डिवाइस पर उपलब्ध recognition service की जरूरत हो सकती है।
5. GitHub Actions APK को artifact के रूप में देगा; वह अपने-आप टैबलेट में install नहीं होगा।

## टैबलेट से GitHub पर चलाने के कदम
1. GitHub में नया public या private repository बनाएं, जैसे `MrStarkAndroid`.
2. इस ZIP के अंदर की सभी files और folders repository की root में upload करें। ध्यान दें: `.github/workflows/build-apk.yml` भी upload हो।
3. Commit changes करें।
4. Repository में **Actions** टैब खोलें।
5. `Build Mr Stark APK` workflow चुनकर **Run workflow** दबाएं। `main` branch पर push करने से भी build चलेगा।
6. Build पूरा होने पर run खोलें और नीचे **Artifacts** में `Mr-Stark-debug-apk` डाउनलोड करें।
7. ZIP artifact को Android पर extract करें। `app-debug.apk` खोलकर install करें। Android पूछे तो केवल अपनी बनाई हुई APK के लिए browser/files app को install permission दें।
8. यदि build fail हो, Actions log का लाल error हिस्सा कॉपी करके सहायता मांगें।

## Gemini API key
1. Google AI Studio में अपनी API key बनाएं और उसकी current terms/limits पढ़ें।
2. APK खोलें → Settings आइकन → API key डालें → Save key.
3. API key को GitHub code, screenshot, chat या public repository में न डालें।
4. अगर ऐप किसी को देना है, पहले Settings में key Clear करें। Production के लिए key को सुरक्षित server पर रखना चाहिए।

## फ़ोल्डर संरचना
```text
MrStarkAndroid/
├── .github/workflows/build-apk.yml
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/yatharth/mrstark/MainActivity.kt
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## अगला विकास क्रम
- Memory screen और delete/export controls
- Real reminders/notifications
- Search API + citations
- Secure backend for AI
- App shortcuts/intents with user confirmation
- Signed release APK
- Optional wake-word engine after battery/privacy evaluation
