package com.yachiqo.spotgo.profiles.infrastructure.persistence.jpa.repositories;

import com.yachiqo.spotgo.profiles.domain.model.aggregates.Favorite;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, String> {
    List<Favorite> findAllByClientId(String clientId);
}
