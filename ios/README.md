# iPhone client

This folder contains the native SwiftUI iPhone client for LAMWorkOrder.

The iPhone app uses the same FastAPI backend as the Android, web, and Windows clients.

## Requirements

- Mac with Xcode installed
- iPhone with Developer Mode enabled
- Apple ID signed in to Xcode
- Apple Developer Program membership for TestFlight, App Store, or client distribution

## Run from Xcode

1. On the Mac, clone or pull this GitHub repository.
2. Open:

   ```text
   ios/LAMWorkOrder.xcodeproj
   ```

3. In Xcode, select the `LAMWorkOrder` target.
4. Open **Signing & Capabilities**.
5. Select your Apple developer team.
6. Connect the iPhone by USB.
7. Select the iPhone in Xcode's device picker.
8. Press **Run**.

This is the iPhone equivalent of using Android Studio to run the Android client on a phone.

## Backend address

The iPhone client currently uses:

```text
http://50.190.210.154:5081/
```

To change it, edit:

```text
ios/LAMWorkOrder/AppConfig.swift
```

Keep the trailing slash.

For TestFlight or App Store release, use an HTTPS backend URL. The project currently allows HTTP so local/internal development can work against the existing server.

## TestFlight

For client testing across multiple iPhones:

1. Enroll in the Apple Developer Program.
2. In Xcode, set the signing team and a unique bundle identifier.
3. Choose **Product > Archive**.
4. Upload the archive to App Store Connect.
5. Add testers in TestFlight.

Users install the app from Apple's TestFlight app.
