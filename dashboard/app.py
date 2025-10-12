import os, requests, pandas as pd, streamlit as st

API = os.environ.get("API_URL", "http://localhost:8000")
st.title("Rescuer Live Dashboard")

team = st.sidebar.text_input("Team", value="default")

@st.cache_data(ttl=5)
def latest(team):
    r = requests.get(f"{API}/latest", params={"team": team}, timeout=5)
    r.raise_for_status()
    return r.json()

tab1, tab2 = st.tabs(["Fleet", "User History"])

with tab1:
    data = latest(team)
    if not data:
        st.info("No snapshots yet.")
    else:
        df = pd.DataFrame(data).sort_values(by=["risk","uid"])
        st.dataframe(df, use_container_width=True)

with tab2:
    uid = st.text_input("User ID", value=(latest(team)[0]["uid"] if latest(team) else "user_01"))
    if st.button("Load history"):
        r = requests.get(f"{API}/history", params={"uid": uid, "limit": 1000})
        if r.ok:
            hist = pd.DataFrame(r.json())
            if not hist.empty:
                st.line_chart(hist.set_index("ts")[["hr","spo2","temp"]])
                st.dataframe(hist.head(50))
            else:
                st.info("No history found.")
        else:
            st.error("Backend error")
