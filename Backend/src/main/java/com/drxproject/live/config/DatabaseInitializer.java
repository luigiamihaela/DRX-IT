package com.drxproject.live.config;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.drxproject.live.models.ERole;
import com.drxproject.live.models.EStage;
import com.drxproject.live.models.Role;
import com.drxproject.live.models.Stage;
import com.drxproject.live.models.User;
import com.drxproject.live.repositories.RoleRepository;
import com.drxproject.live.repositories.StageRepository;
import com.drxproject.live.repositories.UserRepository;

@Component
public class DatabaseInitializer implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StageRepository stageRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        logger.info("Initializing database with required roles...");

        initRole(ERole.ROLE_USER);
        initRole(ERole.ROLE_ADMIN);
        initRole(ERole.ROLE_DESIGNER);
        initRole(ERole.ROLE_PORTOFOLIO_MANAGER);
        initRole(ERole.ROLE_SELLER);

        createAdminIfNotExists();

        initStage(EStage.CONCEPT,
                "Responsible for generating ideas and defining the vision for a new product. This stage focuses on brainstorming, identifying market needs, and outlining potential product features.");
        initStage(EStage.FEASIBILITY,
                "Evaluates the technical, economic, and commercial viability of the proposed product. This role ensures that the product can be realistically developed, manufactured, and sold at a sustainable cost.");
        initStage(EStage.PROJECTION,
                "Responsible for designing the technical details and specifications of the product. This includes creating blueprints, selecting materials, and defining production processes to ensure the product meets quality and performance standards.");
        initStage(EStage.PRODUCTION,
                "Handles the actual manufacturing of the product according to the established specifications. This stage involves assembling components, quality control, and ensuring efficient production workflows.");
        initStage(EStage.RETREAT,
                "Manages the gradual removal of the product from the market at the end of its lifecycle. This includes discontinuing manufacturing, handling remaining stock, and transitioning customers to alternative products if necessary.");
        initStage(EStage.STANDBY,
                "Oversees the temporary suspension of a product without permanently discontinuing it. This can be due to market conditions, supply chain issues, or strategic business decisions.");
        initStage(EStage.CANCEL,
                "Responsible for the complete termination of the products development or production. This happens when the product is deemed unfeasible, unprofitable, or no longer aligns with business goals.");

        logger.info("Database initialization completed.");
    }

    private void initRole(ERole name) {
        if (!roleRepository.existsByName(name)) {
            Role role = new Role(name);
            roleRepository.save(role);
            logger.info("Created role: {}", name);
        } else {
            logger.info("Role already exists: {}", name);
        }
    }

    private void initStage(EStage name, String description) {
        if (!stageRepository.existsByName(name)) {
            Stage stage = new Stage(name, description);
            stageRepository.save(stage);
            logger.info("Created stage: {}", name);
        } else {
            logger.info("Stage already exists: {}", name);
        }
    }

    private void createAdminIfNotExists() {
        if (!adminUserExists()) {
            User adminUser = new User("admin", "admin@mail.com", passwordEncoder.encode("admin123"));

            Set<Role> roles = new HashSet<>();
            Role adminRole = roleRepository.findByName(ERole.ROLE_ADMIN)
                    .orElseThrow(() -> new RuntimeException("Error: Role is not found."));
            roles.add(adminRole);
            adminUser.setRoles(roles);

            userRepository.save(adminUser);
            logger.info("Created admin user.");
        }
    }

    private boolean adminUserExists() {
        return userRepository.existsByRolesName(ERole.ROLE_ADMIN);
    }
}
