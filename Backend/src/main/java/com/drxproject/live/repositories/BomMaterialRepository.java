package com.drxproject.live.repositories;

import org.springframework.stereotype.Repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.drxproject.live.models.BomMaterial;
import com.drxproject.live.models.Material;

@Repository
public interface BomMaterialRepository extends JpaRepository<BomMaterial, Integer> {
    void deleteByMaterial(Material material);

    void deleteById(Long id);

    int countByMaterial(Material material);

    List<BomMaterial> findByBomId(Long bomId);
}
