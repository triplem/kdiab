# Test Patterns by Stack

## Java — JUnit 5 + Mockito

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserService userService;

    @Test
    void shouldReturnUser_whenValidIdProvided() {
        when(userRepository.findById(new UserId(1L))).thenReturn(Optional.of(testUser));
        var result = userService.findUser(new UserId(1L));
        assertThat(result).isInstanceOf(UserResult.Found.class);
        assertThat(((UserResult.Found) result).user()).isEqualTo(testUser);
    }

    @Test
    void shouldReturnNotFound_whenUserDoesNotExist() {
        when(userRepository.findById(new UserId(99L))).thenReturn(Optional.empty());
        var result = userService.findUser(new UserId(99L));
        assertThat(result).isInstanceOf(UserResult.NotFound.class);
    }
}
```

### Java Maven test dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
    <!-- includes JUnit 5, Mockito, AssertJ, Testcontainers BOM -->
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
```

### Java Gradle test dependencies

```groovy
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:postgresql'
testImplementation 'org.testcontainers:junit-jupiter'
```

## Kotlin — JUnit 5 + Mockk

```kotlin
@ExtendWith(MockKExtension::class)
class UserServiceTest {
    @MockK lateinit var userRepository: UserRepository
    @InjectMockKs lateinit var userService: UserService

    @Test
    fun `should return user when valid id provided`() {
        every { userRepository.findById(UserId(1L)) } returns testUser
        val result = userService.findById(UserId(1L))
        assertThat(result).isInstanceOf(UserResult.Found::class.java)
        assertThat((result as UserResult.Found).user).isEqualTo(testUser)
    }

    @Test
    fun `should return NotFound when user does not exist`() {
        every { userRepository.findById(UserId(99L)) } returns null
        val result = userService.findById(UserId(99L))
        assertThat(result).isInstanceOf(UserResult.NotFound::class.java)
    }
}
```

## Kotlin Integration — SpringBootTest + Testcontainers

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserControllerIT {
    @Container companion object {
        @JvmField val postgres = PostgreSQLContainer<Nothing>("postgres:16")
    }
    @DynamicPropertySource companion object Props {
        @JvmStatic fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
        }
    }
    @Autowired lateinit var webTestClient: WebTestClient

    @Test
    fun `POST users returns 201 with created user`() {
        webTestClient.post().uri("/api/v1/users")
            .bodyValue(CreateUserRequest("test@example.com", "Test User"))
            .exchange()
            .expectStatus().isCreated
            .expectBody<UserResponse>()
            .value { assertThat(it.email).isEqualTo("test@example.com") }
    }
}
```

## TypeScript — Vitest + supertest

```typescript
// unit
describe('UserService', () => {
  it('should return user when valid id provided', async () => {
    mockRepo.findById.mockResolvedValue(testUser);
    const result = await userService.findById(createUserId(1));
    expect(result).toEqual({ ok: true, value: testUser });
  });
  it('should return NOT_FOUND when user does not exist', async () => {
    mockRepo.findById.mockResolvedValue(null);
    const result = await userService.findById(createUserId(99));
    expect(result).toEqual({ ok: false, error: 'NOT_FOUND' });
  });
});

// integration
it('POST /users returns 201', async () => {
  const res = await request(app).post('/api/v1/users')
    .send({ email: 'test@example.com', name: 'Test User' });
  expect(res.status).toBe(201);
  expect(res.body.email).toBe('test@example.com');
});
```

## React — Testing Library

```typescript
it('should display user after load', async () => {
  server.use(http.get('/api/v1/users/1', () => HttpResponse.json(testUser)));
  render(<UserDetail userId={createUserId(1)} />);
  await screen.findByText(testUser.name);
  expect(screen.getByRole('heading')).toHaveTextContent(testUser.name);
});
```

## .NET — xUnit + WebApplicationFactory

```csharp
[Fact]
public async Task CreateUser_Returns201_WithCreatedUser()
{
    var response = await _client.PostAsJsonAsync("/api/v1/users",
        new CreateUserRequest("test@example.com", "Test User"));
    response.EnsureSuccessStatusCode();
    Assert.Equal(HttpStatusCode.Created, response.StatusCode);
    var user = await response.Content.ReadFromJsonAsync<UserResponse>();
    Assert.Equal("test@example.com", user!.Email);
}
```
