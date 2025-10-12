# Android App (Prototype)
- Connects to BLE devices named `ND-WRIST` and `ND-CHEST`.
- Expects UTF-8 JSON packets on a notifiable characteristic.
- Stores in Room; syncs to FastAPI via WorkManager.

Open `mobile/` in Android Studio and build.
Update `Api.baseUrl` to your backend address.
