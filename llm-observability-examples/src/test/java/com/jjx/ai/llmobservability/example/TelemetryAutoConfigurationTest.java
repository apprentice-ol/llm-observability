package com.jjx.ai.llmobservability.example;

import com.jjx.ai.llmobservability.autoconfigure.SpanProcessorAutoConfiguration;
import com.jjx.ai.llmobservability.autoconfigure.TelemetryAutoConfiguration;
import com.jjx.ai.llmobservability.autoconfigure.springai.SpringAiTelemetryAutoConfiguration;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 自动装配防护测试：可选依赖（OTel SDK / Spring AI）拆在独立配置类后，
 * 宿主带 SDK 时 SpanProcessor 正常注册，不带 Spring AI 时其适配整类跳过，上下文都能正常启动。
 */
class TelemetryAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.application.name=test-app")
            .withUserConfiguration(ObservationRegistryConfig.class)
            .withConfiguration(AutoConfigurations.of(
                    TelemetryAutoConfiguration.class,
                    SpanProcessorAutoConfiguration.class,
                    SpringAiTelemetryAutoConfiguration.class));

    @Test
    void autoConfigurationStartsAndRegistersOtelSpanProcessors() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("telemetryFilterChain");
            assertThat(context).hasBean("baggageAttributeSpanProcessor");
            assertThat(context).hasBean("genAiAttributePropagationSpanProcessor");
            assertThat(context.getBeansOfType(SpanProcessor.class)).hasSize(2);
            // 示例未引入 Spring AI，其适配配置类应整类跳过，不注册任何 ObservationFilter
            assertThat(context.getBeansOfType(ObservationFilter.class)).isEmpty();
        });
    }

    @Test
    void baggageSpanProcessorCanBeDisabledByProperty() {
        new ApplicationContextRunner()
                .withPropertyValues("spring.application.name=test-app",
                        "telemetry.propagation.baggage-span-attributes=false")
                .withUserConfiguration(ObservationRegistryConfig.class)
                .withConfiguration(AutoConfigurations.of(
                        TelemetryAutoConfiguration.class,
                        SpanProcessorAutoConfiguration.class,
                        SpringAiTelemetryAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("baggageAttributeSpanProcessor");
                    assertThat(context).hasBean("genAiAttributePropagationSpanProcessor");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class ObservationRegistryConfig {

        @Bean
        ObservationRegistry observationRegistry() {
            return ObservationRegistry.create();
        }
    }
}