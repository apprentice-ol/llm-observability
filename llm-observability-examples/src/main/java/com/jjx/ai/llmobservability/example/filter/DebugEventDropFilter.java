package com.jjx.ai.llmobservability.example.filter;

import com.jjx.ai.llmobservability.observation.event.TelemetryEvent;
import com.jjx.ai.llmobservability.observation.filter.TelemetryFilter;
import com.jjx.ai.llmobservability.observation.filter.TelemetryFilterChain;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 事件过滤器链示例：丢弃名为 {@code debug.trace} 的自定义事件。
 *
 * <p><b>演示点</b>：过滤器<b>不调用</b> {@code chain.doFilter(event)} 时，该事件被整条链丢弃，
 * 不会进入任何 processor / exporter（不写 span attribute、不发结构化日志）。</p>
 */
@Component
@Order(20)
public class DebugEventDropFilter implements TelemetryFilter {

    @Override
    public void doFilter(TelemetryEvent event, TelemetryFilterChain chain) {
        if ("debug.trace".equals(event.getName())) {
            return;  // 不调用 chain.doFilter(event) = 丢弃
        }
        chain.doFilter(event);
    }
}
