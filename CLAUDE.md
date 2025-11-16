# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

The Group Service is a Spring Boot microservice (port 8083) that manages groups, memberships, and invitations for the Quiz Play System. It uses PostgreSQL for persistence and integrates with other microservices via REST APIs.

**Key Characteristics:**
- Authorization model: Application admins create groups and designate group admins
- Two group types: PUBLIC (open join) and PRIVATE (invitation-only)
- Multi-role system: App admins, group admins, and members
- Service-to-service endpoints for permission validation

## Coding Standards

### Clean Code & Clean Architecture Principles

**Clean Architecture Layers:**
- **Entities** (model package): Core business entities with minimal dependencies
- **Use Cases** (service package): Business logic, independent of frameworks
- **Interface Adapters** (controller, dto packages): Convert data between use cases and external systems
- **Frameworks & Drivers** (config, repository packages): Spring Boot, JPA, external libraries

**Clean Code Practices:**
- Single Responsibility Principle: Each class/method does one thing well
- Meaningful names: Use descriptive names for classes, methods, and variables
- Small methods: Keep methods focused and concise (max 20-30 lines)
- DRY principle: Don't repeat yourself - extract common logic
- SOLID principles throughout the codebase

### Lombok Usage

**Required:** Use Lombok annotations to eliminate boilerplate code.

```java
// Entities and DTOs
@Data                    // Generates getters, setters, toString, equals, hashCode
@Builder                 // Builder pattern for object creation
@NoArgsConstructor       // Default constructor (required for JPA)
@AllArgsConstructor      // All-args constructor

// For entities specifically
@Entity
@Table(name = "groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    // fields...
}

// For immutable DTOs
@Value                   // Creates immutable class with final fields
@Builder
public class GroupDTO {
    Long id;
    String name;
    // fields...
}

// Services
@Slf4j                   // Adds logger: log.info(), log.error(), etc.
@Service
@RequiredArgsConstructor // Constructor injection for final fields
public class GroupService {
    private final GroupRepository groupRepository;
    // methods...
}
```

### Configuration Properties

**Required:** Use type-safe `@ConfigurationProperties` beans instead of `@Value` annotations.

```java
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private Long expiration;
}

// Usage in service
@Service
@RequiredArgsConstructor
public class JwtService {
    private final JwtProperties jwtProperties;

    public String generateToken() {
        // Use jwtProperties.getSecret()
    }
}
```

### Configuration Format

**Required:** Use YAML (application.yml) instead of properties files.

```yaml
server:
  port: 8083

spring:
  application:
    name: group-service
  datasource:
    url: jdbc:postgresql://localhost:5432/groups_db
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false

jwt:
  secret: ${JWT_SECRET:default-secret}
  expiration: 86400000
```

### OpenAPI Documentation

**Required:** Document all REST endpoints with OpenAPI annotations.

```java
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@Tag(name = "Group Management", description = "APIs for managing groups")
public class GroupController {

    @Operation(
        summary = "Create a new group",
        description = "Creates a new group. Only application admins can perform this operation."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Group created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden - requires ADMIN role")
    })
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupDTO> createGroup(
        @Valid @RequestBody CreateGroupRequest request
    ) {
        // implementation
    }
}

// DTOs should also be documented
@Schema(description = "Request to create a new group")
@Data
@Builder
public class CreateGroupRequest {
    @Schema(description = "Group name", example = "Engineering Team", required = true)
    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @Schema(description = "Group description", example = "Team for all engineers")
    private String description;

    @Schema(description = "Group type", example = "PUBLIC", required = true)
    @NotNull
    private GroupType type;
}
```

### Testing Requirements

**Required:** Write unit tests for all business logic in the service layer.

**Test Structure:**
```java
@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserServiceClient userServiceClient;

    @InjectMocks
    private GroupService groupService;

    @Test
    @DisplayName("Should create group and add group admin as first member")
    void shouldCreateGroupSuccessfully() {
        // Given
        CreateGroupRequest request = CreateGroupRequest.builder()
            .name("Test Group")
            .type(GroupType.PUBLIC)
            .groupAdminId(1L)
            .build();

        Group savedGroup = Group.builder()
            .id(1L)
            .name("Test Group")
            .build();

        when(groupRepository.save(any())).thenReturn(savedGroup);

        // When
        GroupDTO result = groupService.createGroup(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Group");
        verify(groupRepository).save(any(Group.class));
    }

    @Test
    @DisplayName("Should throw exception when group name already exists")
    void shouldThrowExceptionWhenGroupNameExists() {
        // Given
        CreateGroupRequest request = CreateGroupRequest.builder()
            .name("Existing Group")
            .build();

        when(groupRepository.existsByName("Existing Group")).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> groupService.createGroup(request))
            .isInstanceOf(GroupAlreadyExistsException.class)
            .hasMessage("Group with name 'Existing Group' already exists");
    }
}
```

**Test Coverage:**
- Target: Minimum 80% code coverage on service layer
- All business logic paths must be tested
- Test happy paths and error scenarios
- Use descriptive test names with `@DisplayName`
- Use AssertJ for fluent assertions
- Mock external dependencies (repositories, external services)

## Development Commands

### Build and Run
```bash
# Build the project
mvn clean install

# Run tests
mvn test

# Run with coverage report
mvn clean test jacoco:report
# Coverage report: target/site/jacoco/index.html

# Run the application
mvn spring-boot:run

# Run with specific profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Package as JAR
mvn clean package
java -jar target/group-service-0.0.1-SNAPSHOT.jar
```

### Testing
```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=GroupServiceTest

# Run specific test method
mvn test -Dtest=GroupServiceTest#testCreateGroup

# Run integration tests only
mvn verify -P integration-tests
```

### Database
```bash
# Create database (PostgreSQL must be running)
createdb groups_db

# Liquibase commands (through Maven)
mvn liquibase:update
mvn liquibase:rollback -Dliquibase.rollbackCount=1
mvn liquibase:status
```

## Architecture

### Layer Structure
Standard Spring Boot layered architecture:

```
Controller Layer → Service Layer → Repository Layer → Database
     ↓                  ↓                ↓
   DTOs            Business Logic    JPA Entities
```

**Package organization:**
```
com.quizz.group
├── controller/       # REST endpoints (@RestController)
├── service/          # Business logic (@Service)
├── repository/       # Data access (@Repository, JPA)
├── model/            # JPA entities
├── dto/              # Data transfer objects
├── security/         # JWT auth, filters, security config
├── config/           # Spring configuration classes
├── exception/        # Custom exceptions and handlers
└── util/             # Helper utilities
```

### Entity Relationships

**groups** (1) ←→ (N) **group_members**
**groups** (1) ←→ (N) **group_invitations**

Key constraints:
- `group_members.group_id` + `user_id` is unique (one membership per user per group)
- Group admin ID stored in groups table (`group_admin_id`)
- Cascade deletes: removing a group removes all members and invitations

### Authorization Model

Three distinct roles with different capabilities:

1. **Application Admin (ROLE_ADMIN)**:
   - Create/update/delete groups
   - Assign group admins
   - Full access to all group operations

2. **Group Admin**:
   - Identified by `groups.group_admin_id`
   - Manage members: invite, remove, change roles
   - Update group settings (except deletion)

3. **Regular Users**:
   - Join public groups
   - Accept/decline invitations
   - View their group memberships
   - Leave groups

**Important**: Group admin is NOT a role in `group_members` but a specific user designated in the `groups` table. The `group_members.role` field (MEMBER/ADMIN) is for future group hierarchy features.

### Security Implementation

- **JWT Authentication**: Tokens passed via httpOnly cookies
- **JWT Claims**: Extract userId and role from token
- **Method Security**: Use `@PreAuthorize` annotations in controllers
- **Service-to-Service**: Separate endpoints with service token validation

Example authorization patterns:
```java
// App admin only
@PreAuthorize("hasRole('ADMIN')")

// Group admin or app admin
@PreAuthorize("@groupSecurityService.isGroupAdminOrAppAdmin(#groupId, authentication)")

// Group member
@PreAuthorize("@groupSecurityService.isGroupMember(#groupId, authentication)")
```

## Key Business Logic

### Group Creation Flow
1. App admin creates group → designates group admin
2. Group admin automatically added as first member (role: ADMIN, status: ACTIVE)
3. Member count initialized to 1

### Invitation Flow (Private Groups)
1. Group admin invites user by email or userId
2. Generate unique token, set 7-day expiration
3. User accepts invitation → create group_member record (status: ACTIVE)
4. User declines → update invitation status to DECLINED

### Public Group Join
1. User requests to join
2. Validate group is PUBLIC type
3. Create group_member record immediately (status: ACTIVE)
4. Increment member_count

### Member Removal
1. Group admin removes member
2. Update `group_members.status` to REMOVED, set `removed_at`
3. Decrement `member_count`
4. Don't delete record (maintain history)

## Database Conventions

- **Primary Keys**: BIGSERIAL (Long in Java)
- **Timestamps**: Use `TIMESTAMP` (LocalDateTime in Java)
- **Enums**: Stored as VARCHAR with CHECK constraints
- **Migrations**: Liquibase changelogs in `src/main/resources/db/changelog/`
- **Naming**: snake_case in DB, camelCase in Java

## Testing Strategy

- **Unit Tests**: Mock dependencies, test service logic in isolation
- **Integration Tests**: Use `@SpringBootTest` with H2 in-memory database
- **Controller Tests**: Use `@WebMvcTest` with MockMvc
- **Security Tests**: Use `@WithMockUser` from spring-security-test

Test data setup:
- Use `@BeforeEach` for common setup
- Create test builders for entities/DTOs
- Use AssertJ for fluent assertions

## Configuration

### Required Environment Variables
```bash
JWT_SECRET=<base64-encoded-secret>  # Required for production
EUREKA_ENABLED=true                  # Optional, default: false
```

### Database Connection
Default dev setup expects:
- PostgreSQL on localhost:5432
- Database: `groups_db`
- User/Password: postgres/postgres

Override via environment or application properties.

### Actuator Endpoints
- Health: http://localhost:8083/actuator/health
- Metrics: http://localhost:8083/actuator/metrics
- Info: http://localhost:8083/actuator/info

### API Documentation
- Swagger UI: http://localhost:8083/swagger-ui.html
- OpenAPI JSON: http://localhost:8083/api-docs

## Inter-Service Communication

### Consumed Services
- User Service (expected): For user validation and profile data
  - Validate userId exists
  - Fetch user display names and avatars for denormalization

### Exposed Service Endpoints
Other services call these for permission checks:
- `GET /api/groups/{id}/members/{userId}` - Check membership
- `GET /api/groups/{id}/validate` - Validate group exists and get type

**Note**: Service-to-service calls should use service tokens, not user JWTs.

## Important Implementation Notes

### Denormalization
Store frequently-accessed related data to avoid joins:
- `groups.member_count` - Updated on member add/remove
- DTOs include: `groupAdminName`, `userDisplayName`, `userAvatarUrl`
- Requires calls to User Service to populate

### Token Generation
Invitation tokens must be:
- Cryptographically random (use `SecureRandom`)
- Unique (check database)
- URL-safe (Base64 URL encoding)

### Validation Rules
- Group names: unique, 1-100 characters
- Emails: valid format for invitations
- User IDs: must exist (validate with User Service)
- Group deletion: only if no questions/quizzes attached (check with other services)

### Error Handling
Standard exception handling pattern:
- Custom exceptions: `GroupNotFoundException`, `UnauthorizedException`, etc.
- `@ControllerAdvice` for global exception handling
- Return appropriate HTTP status codes (404, 403, 400, etc.)
- Include error details in response body

## Common Patterns

### Pagination
All list endpoints support:
```
?page=0&size=20&sort=createdAt,desc
```
Use Spring Data's `Pageable` and return `Page<DTO>`.

### DTO Mapping
- Use MapStruct or manual mappers (not entity exposure)
- Service layer returns DTOs, not entities
- Consistent DTO structure across all endpoints

### Transaction Management
Service methods that modify data should be `@Transactional`.

Critical transactions:
- Group creation (insert group + first member)
- Invitation acceptance (update invitation + create member + increment count)
- Member removal (update member + decrement count)

## Technology Versions

- Java: 21
- Spring Boot: 3.4.0
- Spring Cloud: 2024.0.0
- PostgreSQL: 16 (recommended)
- JJWT: 0.12.6
- SpringDoc OpenAPI: 2.7.0

## Deployment Notes

- Default port: 8083
- Health check endpoint: `/actuator/health`
- Graceful shutdown supported
- Can run standalone or with Eureka service discovery
- API Gateway integration: routes `/api/groups/**` to this service
