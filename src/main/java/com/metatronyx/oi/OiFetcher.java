package com.metatronyx.oi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class OiFetcher {

    private final Logger log = LoggerFactory.getLogger(OiFetcher.class);
    private final RestTemplate rest;

    // 🌟 Constructor: builds a pooled Apache HttpClient and plugs it into RestTemplate
    public OiFetcher(RestTemplateBuilder builder) {
        // Connection pool manager
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);              // max total connections
        cm.setDefaultMaxPerRoute(20);    // max per host
        cm.closeIdle(TimeValue.ofSeconds(30));

        // Build HttpClient with pooling + keep-alive
        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .build();

        HttpComponentsClientHttpRequestFactory factory =
                new HttpComponentsClientHttpRequestFactory(httpClient);

        this.rest = builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(5))
                .requestFactory(settings -> factory) // Spring Boot 3.3 compatible
                .build();
    }

    // --- Internal fetchers -------------------------------------------------

    private BigDecimal fetchOiOkx(String instId) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://www.okx.com/api/v5/public/open-interest")
                    .queryParam("instId", instId)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.getForObject(url, Map.class);
            if (resp != null && resp.containsKey("data")) {
                var data = (java.util.List<Map<String, Object>>) resp.get("data");
                if (!data.isEmpty()) {
                    Object oiVal = data.get(0).get("oi");
                    if (oiVal != null) {
                        return new BigDecimal(String.valueOf(oiVal));
                    }
                }
            }
        } catch (Exception e) {
            log.error("OKX fetch error: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal fetchOiBinance(String symbol) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://fapi.binance.com/fapi/v1/openInterest")
                    .queryParam("symbol", symbol)
                    .toUriString();

            @SuppressWarnings("unchecked")
            Map<String, Object> resp = rest.getForObject(url, Map.class);
            if (resp != null && resp.containsKey("openInterest")) {
                Object oiVal = resp.get("openInterest");
                if (oiVal != null) {
                    return new BigDecimal(String.valueOf(oiVal));
                }
            }
        } catch (Exception e) {
            log.error("Binance fetch error: {}", e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    // --- Public API --------------------------------------------------------

    public Map<String, Object> getOi(String symbol) {
        String okxInst = symbol + "-USDT-SWAP";
        String binSymbol = symbol + "USDT";

        BigDecimal oiOkx = fetchOiOkx(okxInst);
        BigDecimal oiBin = fetchOiBinance(binSymbol);

        BigDecimal selected;
        String source;
        if (oiOkx.compareTo(BigDecimal.ZERO) > 0) {
            selected = oiOkx;
            source = "okx";
        } else if (oiBin.compareTo(BigDecimal.ZERO) > 0) {
            selected = oiBin;
            source = "binance";
        } else {
            selected = BigDecimal.ZERO;
            source = "none";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("oi_okx", oiOkx);
        result.put("oi_binance", oiBin);
        result.put("oi_selected", selected);
        result.put("selected_source", source);
        result.put("fetched_at_ms", Instant.now().toEpochMilli());
        return result;
    }
}
