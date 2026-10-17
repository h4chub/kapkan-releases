FROM python:3.12-slim
WORKDIR /app
COPY server.py /app/server.py
RUN useradd --uid 10001 --create-home presence && mkdir /data && chown presence:presence /data
USER presence
ENV KAPKAN_BIND=0.0.0.0 PORT=8087 KAPKAN_DATABASE=/data/presence.sqlite3
EXPOSE 8087
VOLUME ["/data"]
CMD ["python3", "/app/server.py"]
