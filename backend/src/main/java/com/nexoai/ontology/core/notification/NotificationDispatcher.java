package com.nexoai.ontology.core.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Fans a NotificationRequest out to each enabled channel and returns a per-channel
 * delivery summary. Persisting retry state and scheduling belong to a separate
 * runner (out of scope here) — this class is just the dispatch step so it stays
 * easy to unit-test against mocked channels.
 */
@Service
@Slf4j
public class NotificationDispatcher {

    private final Map<String, NotificationChannel> channels;
    private final MeterRegistry meterRegistry;

    public NotificationDispatcher(List<NotificationChannel> channelList, MeterRegistry meterRegistry) {
        this.channels = channelList.stream()
                .collect(Collectors.toMap(NotificationChannel::name, c -> c, (a, b) -> a));
        this.meterRegistry = meterRegistry;
    }

    /**
     * Deliver to every channel in {@code enabledChannels} that is registered.
     * Unknown channels become a FAILED result instead of being silently dropped,
     * so the caller can see misconfiguration in the delivery log.
     *
     * @param config per-channel JSON configuration keyed by lowercase channel name
     *               (e.g. {"slack": {"webhookUrl": "..."}, "teams": {...}})
     */
    public List<DeliveryOutcome> dispatch(NotificationChannel.NotificationRequest req,
                                           List<String> enabledChannels,
                                           JsonNode config) {
        List<DeliveryOutcome> outcomes = new ArrayList<>();
        if (enabledChannels == null || enabledChannels.isEmpty()) return outcomes;

        for (String chName : enabledChannels) {
            NotificationChannel channel = channels.get(chName);
            if (channel == null) {
                outcomes.add(new DeliveryOutcome(chName, false, "channel not registered"));
                recordMetric(chName, false);
                continue;
            }
            JsonNode chCfg = config == null ? MissingNode.getInstance()
                    : config.path(chName.toLowerCase());
            NotificationChannel.DeliveryResult r = channel.send(req, chCfg);
            outcomes.add(new DeliveryOutcome(chName, r.success(), r.error()));
            recordMetric(chName, r.success());
        }
        return outcomes;
    }

    private void recordMetric(String channel, boolean ok) {
        if (meterRegistry != null) {
            meterRegistry.counter("nexo.notifications.sent",
                    "channel", channel, "status", ok ? "ok" : "fail").increment();
        }
    }

    public record DeliveryOutcome(String channel, boolean success, String error) {}
}
