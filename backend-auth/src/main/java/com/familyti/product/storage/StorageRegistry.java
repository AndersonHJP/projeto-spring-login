package com.familyti.product.storage;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * As strategies que subiram nesta execucao, indexadas pelo provider gravado em
 * cada foto, mais o destino das gravacoes novas.
 *
 * <p>Existe para o consumidor receber um tipo proprio em vez de um
 * {@code Map<String, StorageStrategy>} cru, que o Spring resolveria por
 * varredura de beans e nao pelo bean que a {@link StorageConfig} monta.
 */
public record StorageRegistry(Map<String, StorageStrategy> byProvider, StorageStrategy writeTarget) {

    public StorageRegistry {
        byProvider = Map.copyOf(byProvider);
    }

    /** Atalho para testes: indexa pelo proprio {@code provider()} de cada strategy. */
    public static StorageRegistry of(String writeProvider, StorageStrategy... strategies) {
        Map<String, StorageStrategy> byProvider = new LinkedHashMap<>();
        for (StorageStrategy strategy : strategies) {
            byProvider.put(strategy.provider(), strategy);
        }

        StorageStrategy target = byProvider.get(StorageProperties.normalize(writeProvider));
        if (target == null) {
            throw new IllegalArgumentException("Nenhuma StorageStrategy para o destino '" + writeProvider
                    + "'. Registradas: " + byProvider.keySet() + ".");
        }
        return new StorageRegistry(byProvider, target);
    }

    /** A strategy do provedor, ou {@code null} se ele nao esta habilitado aqui. */
    public StorageStrategy find(String provider) {
        return byProvider.get(provider);
    }

    public Set<String> providers() {
        return byProvider.keySet();
    }

    public Collection<StorageStrategy> all() {
        return byProvider.values();
    }
}
