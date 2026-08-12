package com.example.translator.translation;

import com.example.translator.aimodel.AiProviderType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AiProviderClientRegistry {

    private final Map<AiProviderType, AiProviderClient> clients;

    public AiProviderClientRegistry(List<AiProviderClient> allClients) {
        this.clients = allClients.stream().collect(Collectors.toMap(AiProviderClient::supports, c -> c));
    }

    public AiProviderClient get(AiProviderType type) {
        AiProviderClient client = clients.get(type);
        if (client == null) {
            throw new IllegalStateException("No AiProviderClient registered for " + type);
        }
        return client;
    }
}
