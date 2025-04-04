package com.drxproject.live.security.services;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.drxproject.live.models.Material;
import com.drxproject.live.repositories.BomMaterialRepository;
import com.drxproject.live.repositories.MaterialRepository;

@Service
public class MaterialService {
    @Autowired
    private MaterialRepository materialRepository;

    @Autowired
    private BomMaterialRepository bomMaterialRepository;

    public List<Material> getAllMaterials() {
        return materialRepository.findAll();
    }

    public Material saveMaterial(Material material) {
        return materialRepository.save(material);
    }

    public Material updateMaterial(Material material) {
        String id = material.getMaterialNumber();
        Optional<Material> materialOpt = materialRepository.findById(id);
        if (materialOpt.isPresent()) {
            Material existingMaterial = materialOpt.get();
            existingMaterial.setMaterialDescription(material.getMaterialDescription());
            existingMaterial.setHeight(material.getHeight());
            existingMaterial.setWidth(material.getWidth());
            existingMaterial.setWeight(material.getWeight());
            return materialRepository.save(existingMaterial);
        } else {
            throw new RuntimeException("Material with ID " + id + " not found");
        }
    }

    public void deleteMaterial(String id) {
        Optional<Material> materialOpt = materialRepository.findById(id);
        if (materialOpt.isPresent()) {
            Material material = materialOpt.get();
            int referencesRemoved = bomMaterialRepository.countByMaterial(material);
            bomMaterialRepository.deleteByMaterial(material);
            materialRepository.deleteById(id);
            System.out.println(
                    "Material with ID " + id + " deleted successfully. Removed from " + referencesRemoved + " BOMs.");
        } else {
            throw new RuntimeException("Material with ID " + id + " not found");
        }
    }

}
