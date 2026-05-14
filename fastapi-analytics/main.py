from fastapi import FastAPI
from routers.analytics_router import router

app = FastAPI(title="FD Shield Analytics Service", version="1.0.0")

app.include_router(router)

@app.get("/health")
def health():
    return {"status": "FastAPI Analytics Service is running"}
