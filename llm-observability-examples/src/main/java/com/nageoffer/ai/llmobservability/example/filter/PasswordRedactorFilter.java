package com.nageoffer.ai.llmobservability.example.filter;

import com.nageoffer.ai.llmobservability.observation.event.TelemetryEvent;
import com.nageoffer.ai.llmobservability.observation.filter.TelemetryFilter;
import com.nageoffer.ai.llmobservability.observation.filter.TelemetryFilterChain;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 事件过滤器链示例：把 Map 载荷中的 password / passwd / secret 字段就地替换为 {@code [REDACTED]}。
 *
 * <p><b>演示点</b>：</p>
 * <ul>
 *   <li>实现 {@link TelemetryFilter} 并注册为 Spring bean（{@code @Component} + {@code @Order}）；</li>
 *   <li><b>前置</b>：{@code chain.doFilter(event)} 之前修改事件数据；</li>
 *   <li><b>放行</b>：调用 {@code chain.doFilter(event)} 进入下游 processor / exporter；</li>
 *   <li><b>后置</b>：{@code chain.doFilter(event)} 之后执行（下游全部完成后，本示例不做事）。</li>
 * </ul>
 *
 * <p>丢弃事件的演示见 {@link DebugEventDropFilter}（不调用 {@code chain.doFilter(event)}）。</p>
 */
@Component
@Order(30)
public class PasswordRedactorFilter implements TelemetryFilter {

    private static final String REDACTED = "[REDACTED]";

    @Override
    public void doFilter(TelemetryEvent event, TelemetryFilterChain chain) {
        // 前置：拦截 / 修改（在摘要/截断/exporter 之前）
        if (event.getData() instanceof Map<?, ?> data && containsSensitive(data)) {
            event.setData(redact(data));
        }
        // 放行到下游；不调用 chain.doFilter(event) 则丢弃该事件
        chain.doFilter(event);
        // 后置：下游处理器与 exporter 全部完成后执行（这里不做事）
    }

    private Map<String, Object> redact(Map<?, ?> data) {
        Map<String, Object> redacted = new LinkedHashMap<>();
        data.forEach((key, value) -> {
            String name = String.valueOf(key);
            redacted.put(name, isSensitive(name) ? REDACTED : value);
        });
        return redacted;
    }

    private boolean containsSensitive(Map<?, ?> data) {
        return data.keySet().stream().anyMatch(key -> isSensitive(String.valueOf(key)));
    }

    private boolean isSensitive(String key) {
        String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return "password".equals(normalized)
                || "passwd".equals(normalized)
                || "secret".equals(normalized);
    }
}
