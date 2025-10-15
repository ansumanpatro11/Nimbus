
- **Wearables:** ESP32 devices with HR, SpO₂, IMU, and temperature sensors.  
- **Android App:** Collects BLE data, stores offline, syncs with backend.  
- **Backend:** FastAPI + SQLAlchemy with REST + WebSocket APIs.  
- **Dashboard:** Streamlit app for multi-user live monitoring and history.  

---

## 🛠 Tech Stack

### **Hardware**
- ESP32 (BLE microcontroller)  
- MAX30102 (Heart Rate & SpO₂ sensor)  
- IMU (MPU6050/ICM-20948 for motion & fall detection)  
- DS18B20/MLX90614 (Temperature sensor)  
- OLED Display (SSD1306), buzzer, vibration motor, SOS button  
- Li-ion battery + TP4056 charging module  

### **Mobile (Android)**
- Kotlin + Android Studio  
- BLE GATT APIs (dual device connections)  
- Room DB (offline logging)  
- WorkManager (background sync)  
- OkHttp (REST networking)  

### **Backend**
- FastAPI + Uvicorn (ASGI server)  
- SQLAlchemy ORM + SQLite (default) / PostgreSQL (production)  
- Pydantic (data validation)  
- WebSockets (live updates)  
- Docker-ready for deployment  

### **Dashboard**
- Streamlit (web UI)  
- Pandas + Requests (data + API calls)  
- Simple fleet view + user history visualization  





