package com.drxproject.live.controllers;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.drxproject.live.models.Bom;
import com.drxproject.live.models.BomMaterial;
import com.drxproject.live.models.ERole;
import com.drxproject.live.models.EStage;
import com.drxproject.live.models.Material;
import com.drxproject.live.models.User;
import com.drxproject.live.models.Product;
import com.drxproject.live.models.ProductStageHistory;
import com.drxproject.live.models.Stage;
import com.drxproject.live.repositories.BomMaterialRepository;
import com.drxproject.live.repositories.BomRepository;
import com.drxproject.live.repositories.MaterialRepository;
import com.drxproject.live.repositories.ProductRepository;
import com.drxproject.live.repositories.StageRepository;
import com.drxproject.live.repositories.UserRepository;
import com.drxproject.live.repositories.ProductStageHistoryRepository;
import com.drxproject.live.security.services.ProductStageService;
import com.drxproject.live.security.services.UserDetailsImpl;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductStageService productStageService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StageRepository stageRepository;

    @Autowired
    private ProductStageHistoryRepository productStageHistoryRepository;

    @Autowired
    private BomRepository bomRepository;

    @Autowired
    private BomMaterialRepository bomMaterialRepository;

    @Autowired
    private MaterialRepository materialRepository;

    @PutMapping("/{productId}")
    @Transactional
    public ResponseEntity<?> updateProduct(@PathVariable Long productId, @RequestBody Product updatedProduct) {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
            }

            Optional<Product> existingProductOpt = productRepository.findById(productId);
            if (!existingProductOpt.isPresent()) {
                return ResponseEntity.notFound().build();
            }

            Product existingProduct = existingProductOpt.get();

            existingProduct.setName(updatedProduct.getName());
            existingProduct.setDescription(updatedProduct.getDescription());
            existingProduct.setEstimated_height(updatedProduct.getEstimated_height());
            existingProduct.setEstimated_width(updatedProduct.getEstimated_width());
            existingProduct.setEstimated_weight(updatedProduct.getEstimated_weight());

            if (existingProduct.getBom() != null) {
                Bom existingBom = existingProduct.getBom();

                if (updatedProduct.getBom() != null) {
                    existingBom.setName(updatedProduct.getBom().getName());
                    bomRepository.save(existingBom);
                }

                handleBomMaterials(existingBom,
                        updatedProduct.getBom() != null ? updatedProduct.getBom().getBomMaterials() : null);
            } else if (updatedProduct.getBom() != null) {
                Bom newBom = new Bom();
                newBom.setName(updatedProduct.getBom().getName());
                newBom.setProduct(existingProduct);
                newBom = bomRepository.save(newBom);
                existingProduct.setBom(newBom);

                if (updatedProduct.getBom().getBomMaterials() != null &&
                        !updatedProduct.getBom().getBomMaterials().isEmpty()) {
                    handleBomMaterials(newBom, updatedProduct.getBom().getBomMaterials());
                }
            }

            productRepository.save(existingProduct);

            return ResponseEntity.ok(productRepository.findById(productId).orElse(existingProduct));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating product: " + e.getMessage());
        }
    }

    private void handleBomMaterials(Bom bom, List<BomMaterial> updatedMaterials) {
        List<BomMaterial> currentMaterials = bomMaterialRepository.findByBomId(bom.getId());

        if (updatedMaterials == null || updatedMaterials.isEmpty()) {
            bomMaterialRepository.deleteAll(currentMaterials);
            return;
        }

        Map<String, BomMaterial> currentMaterialsMap = currentMaterials.stream()
                .collect(Collectors.toMap(
                        m -> m.getMaterial().getMaterialNumber(),
                        m -> m));

        Set<String> processedMaterialNumbers = new HashSet<>();

        for (BomMaterial updatedMaterial : updatedMaterials) {
            if (updatedMaterial.getMaterial() == null ||
                    updatedMaterial.getMaterial().getMaterialNumber() == null) {
                continue;
            }

            String materialNumber = updatedMaterial.getMaterial().getMaterialNumber();
            processedMaterialNumbers.add(materialNumber);

            if (currentMaterialsMap.containsKey(materialNumber)) {
                BomMaterial existingMaterial = currentMaterialsMap.get(materialNumber);
                existingMaterial.setQuantity(updatedMaterial.getQuantity());
                existingMaterial.setUnitMeasureCode(updatedMaterial.getUnitMeasureCode());
                bomMaterialRepository.save(existingMaterial);
            } else {
                Optional<Material> materialOpt = materialRepository.findById(materialNumber);
                if (materialOpt.isPresent()) {
                    BomMaterial newMaterial = new BomMaterial();
                    newMaterial.setBom(bom);
                    newMaterial.setMaterial(materialOpt.get());
                    newMaterial.setQuantity(updatedMaterial.getQuantity());
                    newMaterial.setUnitMeasureCode(updatedMaterial.getUnitMeasureCode());
                    bomMaterialRepository.save(newMaterial);
                }
            }
        }

        List<BomMaterial> materialsToDelete = currentMaterials.stream()
                .filter(m -> !processedMaterialNumbers.contains(m.getMaterial().getMaterialNumber()))
                .collect(Collectors.toList());

        if (!materialsToDelete.isEmpty()) {
            bomMaterialRepository.deleteAll(materialsToDelete);
        }
    }

    @PostMapping("/new")
    public ResponseEntity<?> createProduct(@RequestBody Product product) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (userHasRole(user, ERole.ROLE_ADMIN) || userHasRole(user, ERole.ROLE_DESIGNER)) {
                try {
                    if (product.getBom() != null) {
                        Bom bom = product.getBom();
                        for (BomMaterial bomMaterial : bom.getBomMaterials()) {
                            bomMaterial.setBom(bom);
                            Optional<Material> materialOpt = materialRepository
                                    .findById(bomMaterial.getMaterial().getMaterialNumber());
                            if (materialOpt.isPresent()) {
                                bomMaterial.setMaterial(materialOpt.get());
                            } else {
                                return ResponseEntity.badRequest().body("Material with ID "
                                        + bomMaterial.getMaterial().getMaterialNumber() + " does not exist.");
                            }
                        }
                        bom = bomRepository.save(bom);
                        bomMaterialRepository.saveAll(bom.getBomMaterials());
                    }
                    Product savedProduct = productRepository.save(product);

                    Stage initialStage = stageRepository.findByName(EStage.CONCEPT)
                            .orElseThrow(() -> new RuntimeException("Initial stage CONCEPT not found"));
                    ProductStageHistory stageHistory = new ProductStageHistory(savedProduct, initialStage,
                            new Timestamp(System.currentTimeMillis()), user);
                    productStageHistoryRepository.save(stageHistory);

                    return ResponseEntity.ok(savedProduct);
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            } else {
                return ResponseEntity.status(403).body("User does not have permission to create a product.");
            }
        } else {
            return ResponseEntity.status(404).body("User not found.");
        }
    }

    @PostMapping("/{productId}/next-stage")
    public ResponseEntity<?> moveToNextStage(@PathVariable Long productId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        try {
            productStageService.moveToNextStage(productId, userId);
            return ResponseEntity.ok("Product moved to the next stage successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{productId}/set-stage")
    public ResponseEntity<?> setStage(@PathVariable Long productId, @RequestBody Map<String, String> request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        try {
            String stageStr = request.get("stage");
            EStage newEStage = EStage.valueOf(stageStr.toUpperCase());

            productStageService.overrideStage(productId, newEStage, userId);
            return ResponseEntity.ok("Product stage set to " + newEStage);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{productId}/get-current-stage")
    public ResponseEntity<?> getCurrentStage(@PathVariable Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            Optional<ProductStageHistory> historyOpt = productStageHistoryRepository
                    .findTopByProductOrderByStartOfStageDesc(product);
            if (historyOpt.isPresent()) {
                Stage currentStage = historyOpt.get().getStage();
                return ResponseEntity.ok(currentStage.getName());
            } else {
                return ResponseEntity.badRequest().body("No stage history found for product.");
            }
        } else {
            return ResponseEntity.status(404).body("Product not found.");
        }
    }

    @GetMapping("/{productId}/get-stage-history")
    public ResponseEntity<?> getStageHistory(@PathVariable Long productId) {
        Optional<Product> productOpt = productRepository.findById(productId);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            List<ProductStageHistory> history = productStageHistoryRepository.findByProduct(product);
            return ResponseEntity.ok(history);
        } else {
            return ResponseEntity.status(404).body("Product not found.");
        }
    }

    @GetMapping("/get-all")
    public ResponseEntity<?> getAllProducts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();
        Optional<User> userOpt = userRepository.findById(userId);
        List<Product> products = new ArrayList<Product>();
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (userHasRole(user, ERole.ROLE_ADMIN)) {
                try {
                    products = productRepository.findAll();
                    return ResponseEntity.ok(products);
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            }
            if (userHasRole(user, ERole.ROLE_DESIGNER)) {
                try {
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.CONCEPT));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.FEASIBILITY));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.PROJECTION));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            }
            if (userHasRole(user, ERole.ROLE_PORTOFOLIO_MANAGER)) {
                try {
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.FEASIBILITY));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.PROJECTION));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.PRODUCTION));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.RETREAT));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.STANDBY));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            }
            if (userHasRole(user, ERole.ROLE_SELLER)) {
                try {
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.PRODUCTION));
                    products.addAll(productStageHistoryRepository.findProductsByCurrentStage(EStage.RETREAT));
                } catch (Exception e) {
                    return ResponseEntity.badRequest().body(e.getMessage());
                }
            }
        }
        if (products.isEmpty()) {
            return ResponseEntity.status(403).body("User does not have permission to see products.");
        } else {
            return ResponseEntity.ok(products);
        }
    }

    @DeleteMapping("/{productId}/delete")
    @Transactional
    public ResponseEntity<?> deleteProduct(@PathVariable Long productId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        Long userId = userDetails.getId();

        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();

            if (!userHasRole(user, ERole.ROLE_ADMIN)) {
                return ResponseEntity.status(403).body("User does not have permission to delete products.");
            }

            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isPresent()) {
                Product product = productOpt.get();

                try {
                    productStageHistoryRepository.deleteByProduct(product);

                    productRepository.delete(product);

                    return ResponseEntity.ok("Product and its history successfully deleted.");
                } catch (Exception e) {
                    return ResponseEntity.status(500).body("Error deleting product: " + e.getMessage());
                }
            } else {
                return ResponseEntity.status(404).body("Product not found.");
            }
        } else {
            return ResponseEntity.status(404).body("User not found.");
        }
    }

    private boolean userHasRole(User user, ERole role) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals(role));
    }
}
