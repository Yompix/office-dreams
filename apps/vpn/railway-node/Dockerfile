# YURK: VPN-узел как сервис Railway (без своего сервера).
# Xray берёт настройки у панели раз в минуту. Railway даёт https-домен (-> 8080, VLESS WebSocket)
# и TCP-прокси (-> 9443, VLESS Reality). UDP на Railway нет, поэтому без Hysteria2.
FROM alpine:3.20
RUN apk add --no-cache ca-certificates curl unzip \
 && curl -fsSL -o /tmp/x.zip https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip \
 && unzip -q /tmp/x.zip xray geoip.dat geosite.dat -d /usr/local/bin \
 && rm /tmp/x.zip && /usr/local/bin/xray version
COPY run.sh /run.sh
RUN chmod +x /run.sh
EXPOSE 8080 9443
CMD ["/run.sh"]
