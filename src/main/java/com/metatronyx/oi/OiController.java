package com.metatronyx.oi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class OiController {

    private final OiFetcher fetcher;

    public OiController(OiFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @GetMapping("/v1/oi")
    public Map<String, Object> getOi(@RequestParam(defaultValue = "XRP") String symbol) {
        return fetcher.getOi(symbol);
    }
}
