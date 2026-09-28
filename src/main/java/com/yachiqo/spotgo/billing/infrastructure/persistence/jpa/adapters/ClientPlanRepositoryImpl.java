package com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.adapters;

import com.yachiqo.spotgo.billing.domain.model.aggregates.ClientPlan;
import com.yachiqo.spotgo.billing.domain.repositories.ClientPlanRepository;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.assemblers.ClientPlanPersistenceAssembler;
import com.yachiqo.spotgo.billing.infrastructure.persistence.jpa.repositories.ClientPlanPersistenceRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ClientPlanRepositoryImpl implements ClientPlanRepository {

    private final ClientPlanPersistenceRepository clientPlanPersistenceRepository;

    public ClientPlanRepositoryImpl(ClientPlanPersistenceRepository clientPlanPersistenceRepository) {
        this.clientPlanPersistenceRepository = clientPlanPersistenceRepository;
    }

    @Override
    public Optional<ClientPlan> findById(String id) {
        return clientPlanPersistenceRepository.findById(id)
                .map(ClientPlanPersistenceAssembler::toDomainFromPersistence);
    }

    @Override
    public List<ClientPlan> findAll() {
        return clientPlanPersistenceRepository.findAll().stream()
                .map(ClientPlanPersistenceAssembler::toDomainFromPersistence)
                .toList();
    }

    @Override
    public ClientPlan save(ClientPlan clientPlan) {
        var entity = ClientPlanPersistenceAssembler.toPersistenceFromDomain(clientPlan);
        var saved = clientPlanPersistenceRepository.save(entity);
        return ClientPlanPersistenceAssembler.toDomainFromPersistence(saved);
    }
}
