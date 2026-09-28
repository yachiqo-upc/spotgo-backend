package com.yachiqo.spotgo.billing.application.internal.queryservices;

import com.yachiqo.spotgo.billing.application.queryservices.ClientPlanQueryService;
import com.yachiqo.spotgo.billing.domain.model.aggregates.ClientPlan;
import com.yachiqo.spotgo.billing.domain.model.queries.GetAllClientPlansQuery;
import com.yachiqo.spotgo.billing.domain.model.queries.GetClientPlanByIdQuery;
import com.yachiqo.spotgo.billing.domain.repositories.ClientPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class ClientPlanQueryServiceImpl implements ClientPlanQueryService {

    private final ClientPlanRepository clientPlanRepository;

    public ClientPlanQueryServiceImpl(ClientPlanRepository clientPlanRepository) {
        this.clientPlanRepository = clientPlanRepository;
    }

    @Override
    public Optional<ClientPlan> handle(GetClientPlanByIdQuery query) {
        return clientPlanRepository.findById(query.clientPlanId());
    }

    @Override
    public List<ClientPlan> handle(GetAllClientPlansQuery query) {
        return clientPlanRepository.findAll();
    }
}
